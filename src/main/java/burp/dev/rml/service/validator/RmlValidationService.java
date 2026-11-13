package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import burp.parse.ValidationMode;
import burp.reporting.BurpException;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic modular validation engine for RML mappings.
 * Orchestrates a pipeline of independent {@link MappingValidator} components based on their {@link ValidationExecutionPolicy}.
 * Maintains granular per-(document, validator) diagnostic caches and handles debounce execution and LSP push notifications.
 */
@NullMarked
public class RmlValidationService {

    public static final String REVALIDATING_PREFIX = "[revalidating...] ";
    private static final long TOAST_THROTTLE_MS = 8000L;

    private final List<MappingValidator> validators = new CopyOnWriteArrayList<>();
    private long debounceDelayMs = 500L;

    // documentUri -> (validatorId -> List<Diagnostic>)
    private final Map<String, Map<String, List<Diagnostic>>> documentValidatorDiagnostics = new ConcurrentHashMap<>();
    private final Map<String, List<Diagnostic>> lastPublishedDiagnostics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> documentVersions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingDynamicTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rml-validation-debounce");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Long> lastToastTimes = new ConcurrentHashMap<>();
    private final Map<String, String> lastToastMessages = new ConcurrentHashMap<>();
    private final Set<String> documentsWithActiveUnlocatedErrors = ConcurrentHashMap.newKeySet();

    public RmlValidationService() {
        this(List.of(
                new SyntaxAndReportValidator(),
                new FunctionExecutionValidator(),
                new UnusedElementValidator(),
                new OntologyValidator(),
                new ReferenceValidator()
        ));
    }

    public RmlValidationService(List<MappingValidator> initialValidators) {
        this.validators.addAll(initialValidators);
    }

    public void registerValidator(MappingValidator validator) {
        this.validators.add(validator);
    }

    public List<MappingValidator> getValidators() {
        return Collections.unmodifiableList(validators);
    }

    public long getDebounceDelayMs() {
        return debounceDelayMs;
    }

    public void setDebounceDelayMs(long debounceDelayMs) {
        this.debounceDelayMs = debounceDelayMs;
    }

    public List<Diagnostic> getLastPublishedDiagnostics(String uri) {
        return lastPublishedDiagnostics.getOrDefault(uri, Collections.emptyList());
    }

    public void shutdown() {
        debounceExecutor.shutdownNow();
        pendingDynamicTasks.values().forEach(f -> f.cancel(true));
        pendingDynamicTasks.clear();
    }

    /**
     * Synchronous complete validation for RPC (rml/validate) used by non-textual systems (e.g. Baukasten UI / PTR navigation).
     */
    public CompletableFuture<List<RmlDiagnostic>> validate(
            @Nullable ValidateParams params,
            MappingWorkspace workspace
    ) {
        if (params == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(params.mappingFileUri(), ValidationMode.LENIENT);
            ValidationContext context = new ValidationContext(loaded, params.mappingFileUri(), workspace);
            List<RmlDiagnostic> diagnostics = new ArrayList<>();

            for (MappingValidator validator : validators) {
                diagnostics.addAll(validator.validate(context));
            }

            return CompletableFuture.completedFuture(deduplicateAndSort(diagnostics));
        } catch (BurpException be) {
            Range range = SyntaxAndReportValidator.resolveOriginRange(be.getError() != null ? be.getError().origin() : null);
            String code = resolveErrorCode(be);
            return CompletableFuture.completedFuture(List.of(new RmlDiagnostic(
                    range,
                    DiagnosticSeverity.Error,
                    code,
                    "Failed to validate mapping: " + (be.getMessage() != null ? be.getMessage() : be.toString()),
                    "RER Validator"
            )));
        } catch (Exception e) {
            String code = resolveExceptionErrorCode(e);
            return CompletableFuture.completedFuture(List.of(new RmlDiagnostic(
                    null,
                    DiagnosticSeverity.Error,
                    code,
                    "Failed to validate mapping: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    "BURP Validator"
            )));
        }
    }

    // -------------------------------------------------------------------------------------------------
    // Lifecycle Event Handlers (Called by MappingWorkspace)
    // -------------------------------------------------------------------------------------------------

    public void onDocumentOpened(String uri, MappingWorkspace workspace, @Nullable LanguageClient client) {
        long version = documentVersions.computeIfAbsent(uri, u -> new AtomicLong(0)).incrementAndGet();
        cancelPendingDynamicTask(uri);

        boolean loadedOk = executeImmediateValidators(uri, version, workspace, client, false);
        if (loadedOk) {
            // Schedule debounced async validators immediately (delay 0)
            ScheduledFuture<?> scheduledFuture = debounceExecutor.schedule(
                    () -> executeAsyncValidators(uri, version, workspace, client),
                    0,
                    TimeUnit.MILLISECONDS
            );
            pendingDynamicTasks.put(uri, scheduledFuture);
        }
    }

    public void onDocumentChanged(String uri, MappingWorkspace workspace, @Nullable LanguageClient client) {
        long version = documentVersions.computeIfAbsent(uri, u -> new AtomicLong(0)).incrementAndGet();
        cancelPendingDynamicTask(uri);

        // Run immediate validators right away, marking cached async diagnostics with [revalidating...]
        boolean loadedOk = executeImmediateValidators(uri, version, workspace, client, true);
        if (!loadedOk) {
            return;
        }

        // Schedule async validators after debounce delay
        ScheduledFuture<?> scheduledFuture = debounceExecutor.schedule(
                () -> executeAsyncValidators(uri, version, workspace, client),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
        );
        pendingDynamicTasks.put(uri, scheduledFuture);
    }

    public void onDocumentSaved(String uri, MappingWorkspace workspace, @Nullable LanguageClient client) {
        long version = documentVersions.computeIfAbsent(uri, u -> new AtomicLong(0)).incrementAndGet();
        cancelPendingDynamicTask(uri);

        // Run both immediate and async validators synchronously
        boolean loadedOk = executeImmediateValidators(uri, version, workspace, client, false);
        if (loadedOk) {
            executeAsyncValidators(uri, version, workspace, client);
        }
    }

    public void onDocumentClosed(String uri, @Nullable LanguageClient client) {
        cancelPendingDynamicTask(uri);
        documentValidatorDiagnostics.remove(uri);
        lastPublishedDiagnostics.remove(uri);
        documentVersions.remove(uri);
        lastToastTimes.remove(uri);
        lastToastMessages.remove(uri);
        documentsWithActiveUnlocatedErrors.remove(uri);

        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.emptyList()));
        }
    }

    private void cancelPendingDynamicTask(String uri) {
        ScheduledFuture<?> prev = pendingDynamicTasks.remove(uri);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    private boolean executeImmediateValidators(
            String uri,
            long version,
            MappingWorkspace workspace,
            @Nullable LanguageClient client,
            boolean markStaleAsync
    ) {
        MappingWorkspace.LoadedMapping loaded;
        try {
            loaded = workspace.loadMappingDetailed(uri, ValidationMode.LENIENT);
        } catch (BurpException be) {
            publishFatalException(uri, be, client);
            return false;
        } catch (Exception e) {
            publishFatalGenericException(uri, e, client);
            return false;
        }

        ValidationContext context = new ValidationContext(loaded, uri, workspace);
        Map<String, List<Diagnostic>> validatorMap = documentValidatorDiagnostics.computeIfAbsent(uri, u -> new ConcurrentHashMap<>());
        List<RmlDiagnostic> unlocatedAccumulator = new ArrayList<>();

        for (MappingValidator validator : validators) {
            if (validator.getExecutionPolicy() == ValidationExecutionPolicy.IMMEDIATE) {
                List<RmlDiagnostic> results = validator.validate(context);
                Partitioned partitioned = partition(results);
                validatorMap.put(validator.getId(), partitioned.located());
                unlocatedAccumulator.addAll(partitioned.unlocated());
            }
        }

        handleUnlocatedDiagnostics(uri, unlocatedAccumulator, client);

        List<Diagnostic> merged = new ArrayList<>();
        for (MappingValidator validator : validators) {
            List<Diagnostic> cached = validatorMap.getOrDefault(validator.getId(), Collections.emptyList());
            if (validator.getExecutionPolicy() == ValidationExecutionPolicy.DEBOUNCED_ASYNC && markStaleAsync) {
                for (Diagnostic d : cached) {
                    merged.add(cloneWithRevalidating(d));
                }
            } else {
                merged.addAll(cached);
            }
        }

        publishMergedDiagnostics(uri, version, merged, client);
        return true;
    }

    private void executeAsyncValidators(
            String uri,
            long version,
            MappingWorkspace workspace,
            @Nullable LanguageClient client
    ) {
        AtomicLong curVersion = documentVersions.get(uri);
        if (curVersion == null || curVersion.get() != version) {
            return;
        }

        MappingWorkspace.LoadedMapping loaded;
        try {
            loaded = workspace.loadMappingDetailed(uri, ValidationMode.LENIENT);
        } catch (Exception e) {
            return;
        }

        curVersion = documentVersions.get(uri);
        if (curVersion == null || curVersion.get() != version) {
            return;
        }

        ValidationContext context = new ValidationContext(loaded, uri, workspace);
        Map<String, List<Diagnostic>> validatorMap = documentValidatorDiagnostics.computeIfAbsent(uri, u -> new ConcurrentHashMap<>());
        List<RmlDiagnostic> unlocatedAccumulator = new ArrayList<>();

        for (MappingValidator validator : validators) {
            if (validator.getExecutionPolicy() == ValidationExecutionPolicy.DEBOUNCED_ASYNC) {
                List<RmlDiagnostic> results = validator.validate(context);
                Partitioned partitioned = partition(results);
                validatorMap.put(validator.getId(), partitioned.located());
                unlocatedAccumulator.addAll(partitioned.unlocated());
            }
        }

        handleUnlocatedDiagnostics(uri, unlocatedAccumulator, client);

        List<Diagnostic> merged = new ArrayList<>();
        for (MappingValidator validator : validators) {
            merged.addAll(validatorMap.getOrDefault(validator.getId(), Collections.emptyList()));
        }

        publishMergedDiagnostics(uri, version, merged, client);
    }

    // -------------------------------------------------------------------------------------------------
    // Helper & Partitioning Methods
    // -------------------------------------------------------------------------------------------------

    private record Partitioned(List<Diagnostic> located, List<RmlDiagnostic> unlocated) {}

    private Partitioned partition(List<RmlDiagnostic> list) {
        List<Diagnostic> located = new ArrayList<>();
        List<RmlDiagnostic> unlocated = new ArrayList<>();
        for (RmlDiagnostic d : list) {
            if (d.range() != null) {
                located.add(toLspDiagnostic(d));
            } else {
                unlocated.add(d);
            }
        }
        return new Partitioned(located, unlocated);
    }

    private void publishFatalException(String uri, BurpException be, @Nullable LanguageClient client) {
        Range range = SyntaxAndReportValidator.resolveOriginRange(be.getError() != null ? be.getError().origin() : null);
        String code = resolveErrorCode(be);
        RmlDiagnostic diag = new RmlDiagnostic(
                range,
                DiagnosticSeverity.Error,
                code,
                "Failed to validate mapping: " + (be.getMessage() != null ? be.getMessage() : be.toString()),
                "RER Validator"
        );

        Partitioned partitioned = partition(List.of(diag));
        Map<String, List<Diagnostic>> validatorMap = documentValidatorDiagnostics.computeIfAbsent(uri, u -> new ConcurrentHashMap<>());
        validatorMap.clear();
        validatorMap.put("fatal-error", partitioned.located());
        handleUnlocatedDiagnostics(uri, partitioned.unlocated(), client);

        lastPublishedDiagnostics.put(uri, partitioned.located());
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, partitioned.located()));
        }
    }

    private void publishFatalGenericException(String uri, Exception e, @Nullable LanguageClient client) {
        String code = resolveExceptionErrorCode(e);
        RmlDiagnostic diag = new RmlDiagnostic(
                null,
                DiagnosticSeverity.Error,
                code,
                "Failed to validate mapping: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                "BURP Validator"
        );

        Partitioned partitioned = partition(List.of(diag));
        Map<String, List<Diagnostic>> validatorMap = documentValidatorDiagnostics.computeIfAbsent(uri, u -> new ConcurrentHashMap<>());
        validatorMap.clear();
        handleUnlocatedDiagnostics(uri, partitioned.unlocated(), client);

        lastPublishedDiagnostics.put(uri, Collections.emptyList());
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.emptyList()));
        }
    }

    private void publishMergedDiagnostics(String uri, long version, List<Diagnostic> diagnostics, @Nullable LanguageClient client) {
        AtomicLong curVersion = documentVersions.get(uri);
        if (curVersion != null && curVersion.get() != version) {
            return;
        }
        List<Diagnostic> sorted = deduplicateAndSortLsp(diagnostics);
        lastPublishedDiagnostics.put(uri, sorted);

        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, sorted));
        }
    }

    private List<Diagnostic> deduplicateAndSortLsp(List<Diagnostic> list) {
        Map<String, Diagnostic> unique = new LinkedHashMap<>();
        for (Diagnostic d : list) {
            Range r = d.getRange();
            String msg = (d.getMessage() != null && d.getMessage().isLeft()) ? d.getMessage().getLeft() : "";
            String key = (r != null ? (r.getStart().getLine() + ":" + r.getStart().getCharacter()) : "null")
                    + ":" + d.getSeverity() + ":" + msg;
            unique.putIfAbsent(key, d);
        }
        List<Diagnostic> result = new ArrayList<>(unique.values());
        result.sort((d1, d2) -> {
            Range r1 = d1.getRange();
            Range r2 = d2.getRange();
            if (r1 == null && r2 == null) return 0;
            if (r1 == null) return 1;
            if (r2 == null) return -1;
            int lineComp = Integer.compare(r1.getStart().getLine(), r2.getStart().getLine());
            if (lineComp != 0) return lineComp;
            return Integer.compare(r1.getStart().getCharacter(), r2.getStart().getCharacter());
        });
        return result;
    }

    public List<RmlDiagnostic> deduplicateAndSort(List<RmlDiagnostic> list) {
        Map<String, RmlDiagnostic> uniqueWithRange = new LinkedHashMap<>();
        Map<String, RmlDiagnostic> uniqueWithoutRange = new LinkedHashMap<>();

        for (RmlDiagnostic d : list) {
            Range range = d.range();
            if (range != null) {
                String key = range.getStart().getLine() + ":" + range.getStart().getCharacter()
                        + ":" + d.severity() + ":" + d.message();
                uniqueWithRange.putIfAbsent(key, d);
            } else {
                String key = "null:" + d.severity() + ":" + d.message();
                uniqueWithoutRange.putIfAbsent(key, d);
            }
        }

        List<RmlDiagnostic> result = new ArrayList<>(uniqueWithRange.values());
        result.sort(Comparator.comparingInt((RmlDiagnostic d) -> Objects.requireNonNull(d.range()).getStart().getLine())
                .thenComparingInt(d -> Objects.requireNonNull(d.range()).getStart().getCharacter()));
        result.addAll(uniqueWithoutRange.values());
        return result;
    }

    public Diagnostic cloneWithRevalidating(Diagnostic original) {
        Diagnostic copy = new Diagnostic();
        copy.setRange(original.getRange());
        copy.setSeverity(original.getSeverity());
        copy.setCode(original.getCode());
        copy.setCodeDescription(original.getCodeDescription());
        copy.setSource(original.getSource());
        copy.setTags(original.getTags());
        copy.setRelatedInformation(original.getRelatedInformation());
        copy.setData(original.getData());
        String msg = (original.getMessage() != null && original.getMessage().isLeft()) ? original.getMessage().getLeft() : "";
        if (!msg.startsWith(REVALIDATING_PREFIX)) {
            copy.setMessage(REVALIDATING_PREFIX + msg);
        } else {
            copy.setMessage(msg);
        }
        return copy;
    }

    public Diagnostic toLspDiagnostic(RmlDiagnostic d) {
        Diagnostic diag = new Diagnostic();
        diag.setRange(d.range() != null ? d.range() : new Range(new Position(0, 0), new Position(0, 0)));
        diag.setSeverity(d.severity());
        if (d.rerCodeUri() != null) {
            diag.setCode(d.rerCodeUri());
            diag.setCodeDescription(new DiagnosticCodeDescription(d.rerCodeUri()));
        }
        diag.setMessage(d.message());
        diag.setSource(d.source() != null ? d.source() : "BURP Validator");
        if (RER.UnusedRmlElement.getURI().equals(d.rerCodeUri())) {
            diag.setTags(List.of(DiagnosticTag.Unnecessary));
        }
        return diag;
    }

    private void handleUnlocatedDiagnostics(String uri, List<RmlDiagnostic> unlocated, @Nullable LanguageClient client) {
        if (unlocated.isEmpty()) {
            if (documentsWithActiveUnlocatedErrors.remove(uri)) {
                if (client != null) {
                    client.showMessage(new MessageParams(MessageType.Info, "Mapping loaded successfully."));
                    client.logMessage(new MessageParams(MessageType.Info, "Mapping issues resolved for " + uri));
                }
            }
            return;
        }

        documentsWithActiveUnlocatedErrors.add(uri);

        for (RmlDiagnostic d : unlocated) {
            String msg = d.message();
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Error, "[Unlocated Error] " + msg));
            }

            long now = System.currentTimeMillis();
            Long lastTime = lastToastTimes.get(uri);
            String lastMsg = lastToastMessages.get(uri);

            boolean shouldShow = (lastTime == null || (now - lastTime > TOAST_THROTTLE_MS) || !Objects.equals(lastMsg, msg));
            if (shouldShow && client != null) {
                client.showMessage(new MessageParams(MessageType.Error, msg));
                lastToastTimes.put(uri, now);
                lastToastMessages.put(uri, msg);
            }
        }
    }

    public String resolveErrorCode(BurpException be) {
        if (be.getError() != null && be.getError().errorType() != null && be.getError().errorType().getURI() != null) {
            String uri = be.getError().errorType().getURI();
            if (!RER.MappingError.getURI().equals(uri) && !RER.ConfigurationError.getURI().equals(uri)) {
                return uri;
            }
        }
        String msg = be.getMessage() != null ? be.getMessage() : "";
        if (msg.contains("RelativePathSource specified root") || msg.contains("not supported")) {
            return RER.UnsupportedMapping.getURI();
        }
        if (msg.contains("Could not load mapping file") || msg.contains("NoSuchFile") || msg.contains("not found")) {
            return RER.CannotReadMappingDocument.getURI();
        }
        if (msg.contains("mismatched input") || msg.contains("Syntax error") || msg.contains("Turtle")) {
            return RER.RDFMappingSyntaxError.getURI();
        }
        return (be.getError() != null && be.getError().errorType() != null && be.getError().errorType().getURI() != null)
                ? be.getError().errorType().getURI()
                : RER.UnexpectedError.getURI();
    }

    public String resolveExceptionErrorCode(Exception e) {
        if (e instanceof java.nio.file.NoSuchFileException || e instanceof java.io.FileNotFoundException) {
            return RER.CannotReadMappingDocument.getURI();
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("NoSuchFile") || msg.contains("not found") || msg.contains("cannot find")) {
            return RER.CannotReadMappingDocument.getURI();
        }
        return RER.UnexpectedError.getURI();
    }
}

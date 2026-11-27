package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import burp.parse.ValidationMode;
import burp.reporting.AggregatedRmlError;
import burp.reporting.BurpException;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.RmlErrorAggregator;
import burp.reporting.Severity;
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
                new ReferenceValidator(),
                new RuntimeOntologyValidator()
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
    public CompletableFuture<List<RmlErrorDto>> validate(
            @Nullable ValidateParams params,
            MappingWorkspace workspace
    ) {
        if (params == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(params.mappingFileUri(), ValidationMode.LENIENT);
            ValidationContext context = new ValidationContext(loaded, params.mappingFileUri(), workspace);
            List<RmlErrorDto> dtos = new ArrayList<>();

            for (MappingValidator validator : validators) {
                RmlErrorAggregator aggregator = new RmlErrorAggregator();
                for (RmlError err : validator.validate(context)) {
                    aggregator.add(err);
                }
                for (AggregatedRmlError agg : aggregator.getAggregatedLogs()) {
                    dtos.add(RmlErrorDto.from(agg, validator.getSourceName()));
                }
            }

            return CompletableFuture.completedFuture(deduplicateAndSortDtos(dtos));
        } catch (BurpException be) {
            List<PointRange> ranges = be.getError() != null && be.getError().origin() != null
                    ? be.getError().origin().ranges()
                    : Collections.emptyList();
            String code = resolveErrorCode(be);
            return CompletableFuture.completedFuture(List.of(new RmlErrorDto(
                    "Failed to validate mapping: " + (be.getMessage() != null ? be.getMessage() : be.toString()),
                    code,
                    RER.superClassesOf(be.getError() != null ? be.getError().errorType() : null),
                    Severity.ERROR.name(),
                    ranges,
                    Collections.emptyList(),
                    0,
                    "RER Validator"
            )));
        } catch (Exception e) {
            String code = resolveExceptionErrorCode(e);
            return CompletableFuture.completedFuture(List.of(new RmlErrorDto(
                    "Failed to validate mapping: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    code,
                    Collections.emptyList(),
                    Severity.ERROR.name(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    0,
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

        boolean loadedOk = executeImmediateValidators(uri, version, workspace, client, false);
        if (loadedOk) {
            ScheduledFuture<?> scheduledFuture = debounceExecutor.schedule(
                    () -> executeAsyncValidators(uri, version, workspace, client),
                    0,
                    TimeUnit.MILLISECONDS
            );
            pendingDynamicTasks.put(uri, scheduledFuture);
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
        List<RmlErrorDto> unlocatedAccumulator = new ArrayList<>();

        for (MappingValidator validator : validators) {
            if (validator.getExecutionPolicy() == ValidationExecutionPolicy.IMMEDIATE) {
                RmlErrorAggregator aggregator = new RmlErrorAggregator();
                for (RmlError err : validator.validate(context)) {
                    aggregator.add(err);
                }
                Partitioned partitioned = partition(aggregator.getAggregatedLogs(), validator.getSourceName());
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

        ValidationContext context = new ValidationContext(loaded, uri, workspace, () -> {
            AtomicLong latest = documentVersions.get(uri);
            return latest == null || latest.get() != version;
        });
        Map<String, List<Diagnostic>> validatorMap = documentValidatorDiagnostics.computeIfAbsent(uri, u -> new ConcurrentHashMap<>());
        List<RmlErrorDto> unlocatedAccumulator = new ArrayList<>();

        for (MappingValidator validator : validators) {
            if (context.isCancelled()) {
                return;
            }
            if (validator.getExecutionPolicy() == ValidationExecutionPolicy.DEBOUNCED_ASYNC) {
                RmlErrorAggregator aggregator = new RmlErrorAggregator();
                for (RmlError err : validator.validate(context)) {
                    if (context.isCancelled()) {
                        return;
                    }
                    aggregator.add(err);
                }
                Partitioned partitioned = partition(aggregator.getAggregatedLogs(), validator.getSourceName());
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

    private record Partitioned(List<Diagnostic> located, List<RmlErrorDto> unlocated) {}

    private Partitioned partition(List<AggregatedRmlError> list, String sourceName) {
        List<Diagnostic> located = new ArrayList<>();
        List<RmlErrorDto> unlocated = new ArrayList<>();
        for (AggregatedRmlError agg : list) {
            RmlErrorDto dto = RmlErrorDto.from(agg, sourceName);
            if (!dto.ranges().isEmpty()) {
                located.add(toLspDiagnostic(dto));
            } else {
                unlocated.add(dto);
            }
        }
        return new Partitioned(located, unlocated);
    }

    private Partitioned partitionDtos(List<RmlErrorDto> list) {
        List<Diagnostic> located = new ArrayList<>();
        List<RmlErrorDto> unlocated = new ArrayList<>();
        for (RmlErrorDto dto : list) {
            if (!dto.ranges().isEmpty()) {
                located.add(toLspDiagnostic(dto));
            } else {
                unlocated.add(dto);
            }
        }
        return new Partitioned(located, unlocated);
    }

    private void publishFatalException(String uri, BurpException be, @Nullable LanguageClient client) {
        List<PointRange> ranges = be.getError() != null && be.getError().origin() != null
                ? be.getError().origin().ranges()
                : Collections.emptyList();
        String code = resolveErrorCode(be);
        RmlErrorDto dto = new RmlErrorDto(
                "Failed to validate mapping: " + (be.getMessage() != null ? be.getMessage() : be.toString()),
                code,
                RER.superClassesOf(be.getError() != null ? be.getError().errorType() : null),
                Severity.ERROR.name(),
                ranges,
                Collections.emptyList(),
                0,
                "RER Validator"
        );

        Partitioned partitioned = partitionDtos(List.of(dto));
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
        RmlErrorDto dto = new RmlErrorDto(
                "Failed to validate mapping: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                code,
                Collections.emptyList(),
                Severity.ERROR.name(),
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                "BURP Validator"
        );

        Partitioned partitioned = partitionDtos(List.of(dto));
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

    public List<RmlErrorDto> deduplicateAndSortDtos(List<RmlErrorDto> list) {
        Map<String, RmlErrorDto> uniqueWithRange = new LinkedHashMap<>();
        Map<String, RmlErrorDto> uniqueWithoutRange = new LinkedHashMap<>();

        for (RmlErrorDto d : list) {
            if (!d.ranges().isEmpty()) {
                PointRange pr = d.ranges().getFirst();
                String key = pr.start().line() + ":" + pr.start().column()
                        + ":" + d.severity() + ":" + d.message();
                uniqueWithRange.putIfAbsent(key, d);
            } else {
                String key = "null:" + d.severity() + ":" + d.message();
                uniqueWithoutRange.putIfAbsent(key, d);
            }
        }

        List<RmlErrorDto> result = new ArrayList<>(uniqueWithRange.values());
        result.sort(Comparator.comparingInt((RmlErrorDto d) -> d.ranges().getFirst().start().line())
                .thenComparingInt(d -> d.ranges().getFirst().start().column()));
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

    public Diagnostic toLspDiagnostic(RmlErrorDto d) {
        Diagnostic diag = new Diagnostic();
        Range lspRange;
        if (!d.ranges().isEmpty()) {
            PointRange first = d.ranges().getFirst();
            PointRange last = d.ranges().getLast();
            lspRange = new Range(
                    first.start().toLsp(),
                    (last.end() != null ? last.end() : last.start()).toLsp()
            );
        } else {
            lspRange = new Range(new Position(0, 0), new Position(0, 0));
        }
        diag.setRange(lspRange);
        diag.setSeverity(mapSeverity(d.severity()));
        diag.setCode(d.errorTypeUri());
        diag.setCodeDescription(new DiagnosticCodeDescription(d.errorTypeUri()));
        diag.setMessage(formatDiagnosticMessage(d));
        diag.setSource(d.source() != null ? d.source() : "BURP Validator");
        if (RER.UnusedRmlElement.getURI().equals(d.errorTypeUri())
                || d.superClassUris().contains(RER.UnusedRmlElement.getURI())) {
            diag.setTags(List.of(DiagnosticTag.Unnecessary));
        }
        return diag;
    }

    private static String formatDiagnosticMessage(RmlErrorDto d) {
        List<String> details = new ArrayList<>();
        if (!d.samples().isEmpty()) {
            var lineage = d.samples().getFirst().originLineage();
            if (lineage != null) {
                details.add("iteration " + lineage.primary().index());
            }
        }
        if (d.additionalCount() > 0) {
            details.add(d.additionalCount() + " more");
        }
        return details.isEmpty() ? d.message() : d.message() + " (" + String.join(", ", details) + ")";
    }

    private static DiagnosticSeverity mapSeverity(String severity) {
        return switch (severity) {
            case "WARNING" -> DiagnosticSeverity.Warning;
            case "INFORMATION" -> DiagnosticSeverity.Information;
            default -> DiagnosticSeverity.Error;
        };
    }

    private void handleUnlocatedDiagnostics(String uri, List<RmlErrorDto> unlocated, @Nullable LanguageClient client) {
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

        for (RmlErrorDto d : unlocated) {
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

package burp.dev.rml.service;

import burp.dev.NodeSelectorMatcher;
import burp.dev.rml.service.completion.ReferenceCompleter;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.dev.rml.service.source.LspCodeLensProvider;
import burp.dev.rml.service.hint.LspInlineHintProvider;
import burp.dev.rml.service.selector.*;
import burp.dev.rml.service.cache.DevServerCache;
import burp.dev.rml.service.schema.SampledDataSchemaProvider;
import burp.model.MappingDocument;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.model.TriplesMap;
import burp.parse.RmlLoader;
import burp.parse.ValidationMode;
import burp.reporting.BurpException;
import burp.reporting.RmlExecutionReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.apache.jena.rdf.model.Model;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory document buffers, file parsing, AST tree wiring, and structure selector resolution.
 */
@NullMarked
public class MappingWorkspace implements WorkspaceService {

    private final Map<String, String> openDocuments = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> documentPrefixes = new ConcurrentHashMap<>();
    private Path currentWorkingDirectory = Path.of("");
    private @Nullable String baseIri = null;
    private boolean inlineHintsEvaluationEnabled = true;
    private @Nullable Integer iterationSampleLimit = null;
    private @Nullable LanguageClient client = null;

    public void connect(LanguageClient client) {
        this.client = client;
        pullConfigurationFromClient();
    }

    public void pullConfigurationFromClient() {
        if (client == null) {
            return;
        }
        ConfigurationItem item = new ConfigurationItem();
        item.setSection("rml");
        ConfigurationParams params = new ConfigurationParams(List.of(item));
        try {
            client.configuration(params).thenAccept(results -> {
                if (results != null && !results.isEmpty() && results.getFirst() != null) {
                    updateSettings(results.getFirst());
                }
            }).exceptionally(ex -> null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        updateSettings(params.getSettings());
        pullConfigurationFromClient();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        cache.invalidateAll();
    }

    public @Nullable String getBaseIri() {
        return baseIri;
    }

    public void setBaseIri(@Nullable String baseIri) {
        this.baseIri = (baseIri != null && !baseIri.isBlank()) ? baseIri : null;
    }

    public boolean isInlineHintsEvaluationEnabled() {
        return inlineHintsEvaluationEnabled;
    }

    public @Nullable Integer getIterationSampleLimit() {
        return iterationSampleLimit;
    }

    public void setIterationSampleLimit(@Nullable Integer iterationSampleLimit) {
        this.iterationSampleLimit = iterationSampleLimit;
    }

   public record Settings(@Nullable RmlSettings rml) {
    }

    public record RmlSettings(
            @Nullable String baseIRI,
            @Nullable InlineHintsSettings inlineHints,
            @Nullable Integer iterationSampleLimit
    ) {
    }

    public record InlineHintsSettings(@Nullable Boolean enableEvaluation) {
    }

    public void updateSettings(@Nullable Object rawSettings) {
        if (rawSettings == null) return;
        try {
            Gson gson = new GsonBuilder().serializeNulls().create();
            JsonElement jsonElement = (rawSettings instanceof JsonElement je) ? je : gson.toJsonTree(rawSettings);
            Settings settings = gson.fromJson(jsonElement, Settings.class);
            if (settings != null && settings.rml != null && settings.rml.baseIRI != null) {
                setBaseIri(settings.rml.baseIRI);
            }
            if (settings != null && settings.rml != null && settings.rml.inlineHints != null
                    && settings.rml.inlineHints.enableEvaluation != null) {
                inlineHintsEvaluationEnabled = settings.rml.inlineHints.enableEvaluation;
            }
            if (jsonElement.isJsonObject()) {
                var obj = jsonElement.getAsJsonObject();
                var rmlObj = (obj.has("rml") && obj.get("rml").isJsonObject())
                        ? obj.getAsJsonObject("rml")
                        : obj;

                if (rmlObj.has("baseIRI")) {
                    setBaseIri(rmlObj.get("baseIRI").isJsonNull() ? null : rmlObj.get("baseIRI").getAsString());
                } else if (rmlObj.has("baseIri")) {
                    setBaseIri(rmlObj.get("baseIri").isJsonNull() ? null : rmlObj.get("baseIri").getAsString());
                }

                if (rmlObj.has("iterationSampleLimit")) {
                    var elem = rmlObj.get("iterationSampleLimit");
                    setIterationSampleLimit(elem.isJsonNull() ? null : elem.getAsInt());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public void setCurrentWorkingDirectory(@Nullable Path currentWorkingDirectory) {
        this.currentWorkingDirectory = currentWorkingDirectory != null ? currentWorkingDirectory : Path.of("");
    }

    public void updateDocumentText(String uri, String text) {
        openDocuments.put(uri, text);
    }

    public void removeDocument(String uri) {
        openDocuments.remove(uri);
    }

    private final DevServerCache cache = new DevServerCache();
    private final ReferenceCompleter referenceCompleter = new ReferenceCompleter();
    private final LspInlineHintProvider inlayHintProvider = new LspInlineHintProvider();
    private final LspCodeLensProvider codeLensProvider = new LspCodeLensProvider();
    private final RmlEvaluator evaluator = new RmlEvaluator(cache);

    public DevServerCache getCache() {
        return cache;
    }

    private final TextDocumentService textDocumentService = new TextDocumentService() {
        @Override
        public void didOpen(DidOpenTextDocumentParams params) {
            openDocuments.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        }

        @Override
        public void didChange(DidChangeTextDocumentParams params) {
            if (!params.getContentChanges().isEmpty()) {
                openDocuments.put(params.getTextDocument().getUri(), params.getContentChanges().getFirst().getText());
            }
        }

        @Override
        public void didClose(DidCloseTextDocumentParams params) {
            openDocuments.remove(params.getTextDocument().getUri());
        }

        @Override
        public void didSave(DidSaveTextDocumentParams params) {
            if (params.getText() != null) {
                openDocuments.put(params.getTextDocument().getUri(), params.getText());
            }
        }

        @Override
        public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
            return referenceCompleter.complete(params, MappingWorkspace.this);
        }

        @Override
        public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
            return inlayHintProvider.provideInlayHints(params, MappingWorkspace.this, evaluator);
        }

        @Override
        public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
            return codeLensProvider.provideCodeLenses(params, MappingWorkspace.this);
        }
    };

    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    public record ResolvedNode(PlanNode targetNode, MappingDocument mappingDocument) {
    }

    public ResolvedNode resolveNode(@Nullable StructureSelector selector) throws Exception {
        if (selector == null) {
            throw new IllegalArgumentException("Structure selector cannot be null");
        }
        String mappingFileUri = getMappingFileUri(selector);
        var mappingDocument = loadMappingWithStableIds(mappingFileUri);
        PlanNode found = findNodeBySelector(selector, mappingDocument);
        if (found == null && !mappingDocument.triplesMaps.isEmpty()) {
            found = mappingDocument.triplesMaps.getFirst();
        }
        if (found == null) {
            throw new IllegalArgumentException("Could not resolve node for structure selector: " + selector);
        }
        return new ResolvedNode(found, mappingDocument);
    }

    public record LoadedMapping(
            MappingDocument document,
            RmlLoader loader,
            @Nullable Model model,
            RmlExecutionReport report
    ) {
    }

    public LoadedMapping loadMappingDetailed(String mappingFileUri) throws Exception {
        return loadMappingDetailed(mappingFileUri, ValidationMode.LENIENT);
    }

    public LoadedMapping loadMappingDetailed(String mappingFileUri, ValidationMode validationMode) throws Exception {
        String documentText = getOpenDocumentText(mappingFileUri);
        Path mappingPath = normalizePath(mappingFileUri);
        if (!mappingPath.isAbsolute()) {
            mappingPath = currentWorkingDirectory.resolve(mappingPath).normalize();
        }

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = (documentText != null)
                ? new RmlLoader(documentText).withMappingPath(mappingPath)
                : new RmlLoader(mappingPath);
        loader.withCurrentDirectory(currentWorkingDirectory)
                .withReport(report)
                .withValidationMode(validationMode);

        MappingDocument document = loader.loadMappingDocument();
        if (!report.getErrors().isEmpty() && document.triplesMaps.isEmpty()) {
            throw new BurpException(report.getErrors().getFirst());
        }
        documentPrefixes.put(mappingFileUri, new HashMap<>(document.prefixMap));
        return new LoadedMapping(document, loader, loader.getMappingModel(), report);
    }

    public List<TriplesMap> loadMapping(String mappingFileUri) throws Exception {
        return loadMappingDetailed(mappingFileUri, ValidationMode.LENIENT).document().triplesMaps;
    }

    public Path normalizePath(String uriOrPath) {
        if (uriOrPath.startsWith("file:///")) {
            try {
                return Path.of(URI.create(uriOrPath));
            } catch (Exception ignored) {
            }
        } else if (uriOrPath.startsWith("file://")) {
            return Path.of(uriOrPath.substring(7));
        } else if (uriOrPath.startsWith("file:")) {
            return Path.of(uriOrPath.substring(5));
        }
        return Path.of(uriOrPath);
    }

    public Map<String, String> getPrefixMap(String mappingFileUri) {
        Map<String, String> map = new LinkedHashMap<>(RmlEvaluationResultFormatter.DEFAULT_PREFIXES);
        Map<String, String> docMap = documentPrefixes.get(mappingFileUri);
        if (docMap != null) {
            map.putAll(docMap);
        }
        return map;
    }

    public MappingDocument loadMappingWithStableIds(String mappingFileUri) throws Exception {
        List<TriplesMap> triplesMaps = loadMapping(mappingFileUri);
        MappingDocument document = new MappingDocument(triplesMaps);

        PlanNodeIdRegistry.clear();

        // Generate and assign deterministic IDs to all nodes
        List<PlanNode> allNodes = new ArrayList<>();
        collectAllNodes(document, allNodes);
        for (PlanNode node : allNodes) {
            PlanNodeIdRegistry.getId(node);
        }

        return document;
    }


    public String getMappingFileUri(StructureSelector selector) {
        return switch (selector) {
            case CursorSelector cursorSel -> cursorSel.mappingFileUri();
            case IdSelector idSel -> idSel.mappingFileUri();
            case IriSelector iriSel -> iriSel.mappingFileUri();
            case DocumentSelector docSel -> docSel.mappingFileUri();
        };
    }

    public @Nullable String getOpenDocumentText(@Nullable String mappingFileUri) {
        if (mappingFileUri == null) {
            return null;
        }
        if (openDocuments.containsKey(mappingFileUri)) {
            return openDocuments.get(mappingFileUri);
        }
        if (!mappingFileUri.startsWith("file://")) {
            String withPrefix = "file://" + (mappingFileUri.startsWith("/") ? "" : "/") + mappingFileUri;
            if (openDocuments.containsKey(withPrefix)) {
                return openDocuments.get(withPrefix);
            }
            String withOneSlash = "file:" + (mappingFileUri.startsWith("/") ? "" : "/") + mappingFileUri;
            if (openDocuments.containsKey(withOneSlash)) {
                return openDocuments.get(withOneSlash);
            }
        }
        return null;
    }

    public @Nullable PlanNode findNodeBySelector(@Nullable StructureSelector selector, MappingDocument mappingDocument) {
        if (selector == null) {
            return null;
        }

        List<PlanNode> allNodes = new ArrayList<>();
        collectAllNodes(mappingDocument, allNodes);

        switch (selector) {
            case CursorSelector cursorSelector -> {
                List<PlanNode> candidates = allNodes.stream()
                        .filter(node -> NodeSelectorMatcher.matchesExact(node, cursorSelector))
                        .toList();
                if (candidates.isEmpty()) {
                    candidates = allNodes.stream()
                            .filter(node -> NodeSelectorMatcher.matches(node, cursorSelector))
                            .toList();
                }
                if (candidates.isEmpty()) return null;

                PlanNode bestMatch = candidates.getFirst();
                int maxDepth = getNodeDepth(bestMatch);
                for (PlanNode node : candidates) {
                    int depth = getNodeDepth(node);
                    if (depth > maxDepth) {
                        maxDepth = depth;
                        bestMatch = node;
                    }
                }
                return bestMatch;
            }
            case IdSelector idSel -> {
                PlanNode node = PlanNodeIdRegistry.getNodeById(idSel.id());
                if (node != null) {
                    return node;
                }
                for (PlanNode n : allNodes) {
                    if (NodeSelectorMatcher.matches(n, idSel)) {
                        return n;
                    }
                }
            }
            case IriSelector iriSel -> {
                for (PlanNode node : allNodes) {
                    if (NodeSelectorMatcher.matches(node, iriSel)) {
                        return node;
                    }
                }
                String fileUri = iriSel.mappingFileUri();
                if (fileUri != null) {
                    var prefixMap = getPrefixMap(fileUri);
                    // FIXME Instead expand iriSelector thanks to prefixes, if the expansion was possible search again.
                    if (!prefixMap.isEmpty()) {
                        for (PlanNode node : allNodes) {
                            if (node.getIri() != null) {
                                String rawIri = PlanNode.resourceToIriOrBNode(node.getIri());
                                String compacted = RmlEvaluationResultFormatter.compactIri(rawIri, prefixMap);
                                if (compacted.equals(iriSel.iri())) {
                                    return node;
                                }
                            }
                        }
                    }
                }
            }
            case DocumentSelector docSel -> {
                return !mappingDocument.triplesMaps.isEmpty() ? mappingDocument.triplesMaps.getFirst() : null;
            }
            default -> {
            }
        }
        return null;
    }

    /**
     * Get all the children of a node added to the list.
     *
     * @param node will collect itself and its children
     * @param list list accumulator of children
     * @see PlanNode#descendants(Class) to collect all children of a given type.
     */
    public void collectAllNodes(PlanNode node, List<PlanNode> list) {
        if (list.contains(node)) return;
        list.add(node);
        if (node.children() != null) {
            for (PlanNode child : node.children()) {
                collectAllNodes(child, list);
            }
        }
        if (node.dependencies() != null) {
            for (PlanNode dep : node.dependencies()) {
                collectAllNodes(dep, list);
            }
        }
        if (node.references() != null) {
            for (PlanNode ref : node.references()) {
                collectAllNodes(ref, list);
            }
        }
    }

    public int getNodeDepth(PlanNode node) {
        int depth = 0;
        PlanNode current = node;
        while (current != null && current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    public boolean isDescendantOf(PlanNode node, @Nullable PlanNode potentialAncestor) {
        PlanNode p = node.getParent();
        while (p != null) {
            if (p == potentialAncestor) return true;
            p = p.getParent();
        }
        return false;
    }
}

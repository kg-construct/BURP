package burp.dev.rml.service.eval;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.cache.DevServerCache;
import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import burp.dev.rml.service.selector.CursorSelector;
import burp.ls.JSONSourceRFC;
import burp.ls.LogicalSourceProvider;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.join.ParentJoinIndex;
import burp.model.key.AstKey;
import burp.model.lv.LogicalIteration;
import burp.reporting.BurpException;
import burp.reporting.RmlError;
import kotlinx.serialization.json.*;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static burp.dev.rml.service.eval.EvaluationContexts.resolveBaseIri;
import static burp.parse.ValidationMode.LENIENT;

/**
 * Handles RML evaluation requests ({@code rml/evaluate}), data context extraction,
 * source iteration resolution, and plan node evaluation with memoization caching.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Resolving source iterations against {@link EvaluationDataContext} (all rows, ranges,
 *       mocked raw payloads, or per-source {@link MultiSourceDataContext}).</li>
 *   <li>Pre-indexing and caching join conditions for {@link ReferencingObjectMap} and {@code ViewJoin}.</li>
 *   <li>Evaluating target AST nodes per iteration and capturing RDF generation, diagnostics, and lineage.</li>
 *   <li>Memoizing node evaluation results in {@link DevServerCache} for fast incremental updates.</li>
 * </ul>
 */
@NullMarked
public class RmlEvaluator {

    private final @Nullable DevServerCache cache;

    public RmlEvaluator() {
        this(null);
    }

    public RmlEvaluator(@Nullable DevServerCache cache) {
        this.cache = cache;
    }

    /**
     * @return the active dev server cache, or {@code null} if caching is disabled.
     */
    public @Nullable DevServerCache getCache() {
        return cache;
    }

    /**
     * Evaluates a target mapping node (identified by {@link EvaluateParams#structure()}) against
     * the requested {@link EvaluationDataContext}.
     *
     * @param params    the evaluation parameters including node selector and data context
     * @param workspace the active mapping workspace containing AST and document state
     * @return a completable future resolving to the list of {@link EvaluateResult} per iteration
     */
    public CompletableFuture<List<EvaluateResult>> evaluate(EvaluateParams params, MappingWorkspace workspace) {
        try {
            MappingWorkspace.ResolvedNode resolved = workspace.resolveNode(params.structure());
            AbstractLogicalSource targetSource = EvaluationContexts.resolveLogicalSource(resolved.targetNode());

            List<Iteration> iterations;
            try {
                iterations = EvaluationContexts.resolveNodeIterations(
                        resolved.targetNode(),
                        resolveIterations(params.dataContext(), targetSource)
                );
            } catch (Exception iterEx) {
                String errMsg = iterEx.getMessage() != null ? iterEx.getMessage() : iterEx.toString();
                EvaluateResult errResult = new EvaluateResult("0", "", Collections.emptyList(), List.of(errMsg), null, Collections.emptyMap());
                return CompletableFuture.completedFuture(List.of(errResult));
            }

            String fileUri = params.structure() != null ? params.structure().mappingFileUri() : null;
            Map<String, String> prefixMap = (fileUri != null && !workspace.getPrefixMap(fileUri).isEmpty())
                    ? workspace.getPrefixMap(fileUri)
                    : (resolved.mappingDocument().prefixMap != null ? resolved.mappingDocument().prefixMap : Collections.emptyMap());
            String baseIri = resolveBaseIri(resolved.targetNode(), workspace.getBaseIri());
            EvaluationContext context = EvaluationContext.createDefault().withBaseIri(baseIri).withValidationMode(LENIENT);

            Map<AbstractLogicalSource, List<Iteration>> allSourceIterations = new HashMap<>();
            Integer joinLimit = workspace.getJoinIndexLimit();
            EvaluationDataContext parentDataContext = (joinLimit != null && joinLimit > 0)
                    ? new RangeDataContext(0, joinLimit)
                    : new AllDataContext();

            for (TriplesMap tm : resolved.mappingDocument().triplesMaps) {
                if (tm.logicalSource != null && !allSourceIterations.containsKey(tm.logicalSource)) {
                    try {
                        EvaluationDataContext srcContext = (params.dataContext() instanceof MultiSourceDataContext || tm.logicalSource == targetSource)
                                ? params.dataContext()
                                : parentDataContext;
                        var resolvedIters = resolveIterations(srcContext, tm.logicalSource);
                        allSourceIterations.put(tm.logicalSource, resolvedIters);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!allSourceIterations.isEmpty()) {
                context = context.withSourceIterations(allSourceIterations);
            }

            // Pre-populate and cache ParentJoinIndex for any ReferencingObjectMap or ViewJoin
            Map<PlanNode, ParentJoinIndex> joinIndexes = new HashMap<>();
            EvaluationContext unsafeCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
            for (TriplesMap tm : resolved.mappingDocument().triplesMaps) {
                for (ReferencingObjectMap rom : tm.descendants(ReferencingObjectMap.class).toList()) {
                    if (rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
                        AbstractLogicalSource pSource = rom.parentTriplesMap.logicalSource;
                        ParentJoinIndex idx = null;
                        if (cache != null) {
                            idx = cache.getParentJoinIndex(rom, pSource, params.dataContext());
                        }
                        if (idx == null) {
                            Iterable<Iteration> pIters = context.getIterations(pSource);
                            idx = ParentJoinIndex.build(pIters, rom.joinConditions, unsafeCtx);
                            if (cache != null) {
                                cache.putParentJoinIndex(rom, pSource, params.dataContext(), idx);
                            }
                        }
                        joinIndexes.put(rom, idx);
                    }
                }
            }
            if (!joinIndexes.isEmpty()) {
                context = context.withJoinIndexes(joinIndexes);
            }

            Position cursorPosition = params.structure() instanceof CursorSelector cs ? cs.position() : null;
            var resolution = CoherentIterationResolver.resolve(
                    resolved.mappingDocument(),
                    params.dataContext(),
                    null,
                    resolved.targetNode(),
                    cursorPosition,
                    this,
                    workspace.getInlineHintsEvaluationMode(),
                    workspace.getJoinIndexLimit(),
                    allSourceIterations,
                    prefixMap,
                    baseIri
            );
            List<String> traceSummary = resolution.traceSummary();

            PlanNode targetNode = resolved.targetNode();
            AstKey targetKey = cache != null ? targetNode.getAstKey() : null;

            TriplesMap enclosingTm = null;
            PlanNode curr = targetNode;
            while (curr != null) {
                if (curr instanceof TriplesMap tm) {
                    enclosingTm = tm;
                    break;
                }
                curr = curr.getParent();
            }
            String targetNodeType = targetNode.getClass().getSimpleName();
            String targetParentTriplesMap = null;
            if (enclosingTm != null) {
                String raw = PlanNode.resourceToIriOrBNode(enclosingTm.getIri());
                targetParentTriplesMap = RmlEvaluationResultFormatter.compactIri(raw, prefixMap);
            }
            String targetNodeSummary = computeNodeSummary(targetNode, prefixMap);
            InlineHintsEvaluationMode evalMode = workspace.getInlineHintsEvaluationMode();

            if (iterations.isEmpty()) {
                EvaluateResult emptyResult = new EvaluateResult("0", "", Collections.emptyList(), Collections.emptyList(), null, prefixMap, traceSummary, targetNodeType, targetParentTriplesMap, targetNodeSummary, evalMode);
                return CompletableFuture.completedFuture(List.of(emptyResult));
            }
            List<EvaluateResult> results = new ArrayList<>(iterations.size());
            for (int i = 0; i < iterations.size(); i++) {
                Iteration iter = iterations.get(i);
                int index = iter.getId() != null ? iter.getId().index() : i;
                results.add(evaluateIteration(targetNode, targetKey, iter, index, context, prefixMap, traceSummary, targetNodeType, targetParentTriplesMap, targetNodeSummary, evalMode));
            }
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            EvaluateResult errResult = new EvaluateResult("0", "", Collections.emptyList(), List.of(errMsg), null, Collections.emptyMap(), null, null, null, null, null);
            return CompletableFuture.completedFuture(List.of(errResult));
        }
    }

    /**
     * Resolves iterations for a given triples map's logical source under the specified data context.
     *
     * @param dataContext the execution data context
     * @param triplesMap  the triples map whose logical source to resolve
     * @return the list of resolved iterations
     */
    public List<Iteration> resolveIterations(EvaluationDataContext dataContext, @Nullable TriplesMap triplesMap) {
        return resolveIterations(dataContext, triplesMap != null ? triplesMap.logicalSource : null);
    }

    /**
     * Resolves iterations for a logical source under the specified data context, utilizing cache when available.
     *
     * <p>If {@code dataContext} is a {@link MultiSourceDataContext}, the source is matched by filename,
     * IRI, or node ID to retrieve its specific data context. If no matching context or default is found,
     * an empty list is returned.
     *
     * @param dataContext   the execution data context (e.g. all rows, range slice, mock data, or multi-source)
     * @param logicalSource the logical source to resolve iterations from
     * @return the resolved iterations, or an empty list if the source is null or unmapped
     */
    public List<Iteration> resolveIterations(EvaluationDataContext dataContext, @Nullable AbstractLogicalSource logicalSource) {
        if (logicalSource == null) {
            return Collections.emptyList();
        }

        if (dataContext instanceof MultiSourceDataContext msd) {
            String matchedKey = findMatchingSourceKey(msd, logicalSource);
            EvaluationDataContext matchedContext = msd.getForSource(matchedKey);
            if (matchedContext == null) {
                return Collections.emptyList();
            }
            return resolveIterations(matchedContext, logicalSource);
        }

        if (cache != null) {
            List<Iteration> cached = cache.getIterations(logicalSource, dataContext);
            if (cached != null) return cached;
        }

        List<Iteration> computed = resolveIterationsInternal(dataContext, logicalSource);
        if (cache != null && !computed.isEmpty()) {
            if (dataContext instanceof CustomDataContext) {
                cache.putIterations(logicalSource, dataContext, computed);
            } else if (dataContext instanceof AllDataContext) {
                cache.putIterations(logicalSource, computed);
            } else {
                List<Iteration> all = new ArrayList<>();
                for (Iteration iter : logicalSource.iterator()) {
                    all.add(iter);
                }
                cache.putIterations(logicalSource, all);
            }
        }
        return computed;
    }

    /**
     * Internal extraction strategy per concrete {@link EvaluationDataContext} subtype.
     */
    private List<Iteration> resolveIterationsInternal(EvaluationDataContext dataContext, AbstractLogicalSource logicalSource) {
        switch (dataContext) {
            case AllDataContext allDataContext -> {
                List<Iteration> list = new ArrayList<>();
                for (Iteration iter : logicalSource.iterator()) {
                    list.add(iter);
                }
                return list;
            }
            case RangeDataContext(int startIndex, int endIndex, List<Integer> specificIndices) -> {
                List<Iteration> list = new ArrayList<>();
                int idx = 0;
                for (Iteration iter : logicalSource.iterator()) {
                    boolean include;
                    if (specificIndices != null && !specificIndices.isEmpty()) {
                        include = specificIndices.contains(idx);
                    } else {
                        include = (idx >= startIndex && idx <= endIndex);
                    }
                    if (include) {
                        list.add(iter);
                    }
                    idx++;
                    if (specificIndices == null && idx > endIndex) break;
                }
                return list;
            }
            case RawStringCustomDataContext(String rawString) -> {
                if (logicalSource instanceof JSONSourceRFC) {
                    try {
                        JsonElement jsonElement = Json.Default.parseToJsonElement(rawString);
                        if (jsonElement instanceof JsonObject) {
                            Iteration iter = JSONSourceRFC.createJsonIteration(jsonElement, Collections.emptySet());
                            return Collections.singletonList(iter);
                        }
                    } catch (Exception ignored) {
                    }
                }
                Resource refFormulation = logicalSource.getReferenceFormulation();
                if (refFormulation != null) {
                    ServiceLoader<LogicalSourceProvider> loader = ServiceLoader.load(LogicalSourceProvider.class);
                    LogicalSourceProvider provider = null;
                    for (LogicalSourceProvider p : loader) {
                        if (p.supports(refFormulation)) {
                            provider = p;
                            break;
                        }
                    }
                    if (provider != null) {
                        String iterator = logicalSource instanceof JSONSourceRFC js && js.iterator != null ? js.iterator : "";
                        return provider.parseStringPayload(rawString, iterator, logicalSource.emitOrigin());
                    }
                }
            }
            case ReferenceValuesCustomDataContext(var referenceValues) -> {
                if (logicalSource instanceof JSONSourceRFC) {
                    Map<String, JsonElement> jsonMap = new LinkedHashMap<>();
                    for (var entry : referenceValues.entrySet()) {
                        String key = entry.getKey();
                        if (key.startsWith("$.")) key = key.substring(2);
                        Object val = entry.getValue();
                        if (val instanceof Number num) {
                            jsonMap.put(key, JsonElementKt.JsonPrimitive(num));
                        } else if (val instanceof Boolean b) {
                            jsonMap.put(key, JsonElementKt.JsonPrimitive(b));
                        } else if (val != null) {
                            jsonMap.put(key, JsonElementKt.JsonPrimitive(val.toString()));
                        } else {
                            jsonMap.put(key, JsonNull.INSTANCE);
                        }
                    }
                    JsonObject jsonObj = new JsonObject(jsonMap);
                    Iteration iter = JSONSourceRFC.createJsonIteration(jsonObj, Collections.emptySet());
                    return Collections.singletonList(iter);
                } else if (logicalSource instanceof burp.ls.CSVSource) {
                    String[] header = referenceValues.keySet().toArray(new String[0]);
                    String[] rec = new String[header.length];
                    for (int i = 0; i < header.length; i++) {
                        Object val = referenceValues.get(header[i]);
                        rec[i] = val != null ? val.toString() : "";
                    }
                    Iteration iter = new burp.ls.CSVIteration(header, rec, Collections.emptySet());
                    return Collections.singletonList(iter);
                } else {
                    LogicalIteration li = new LogicalIteration(Collections.emptySet());
                    for (Map.Entry<String, Object> entry : referenceValues.entrySet()) {
                        li.put(entry.getKey(), entry.getValue());
                    }
                    return Collections.singletonList(li);
                }
            }
            case null, default -> {
            }
        }

        return Collections.emptyList();
    }

    /**
     * Evaluates a single iteration for a target plan node with AST cache key, trace summary, and target node metadata.
     */
    public EvaluateResult evaluateIteration(PlanNode targetNode, @Nullable AstKey targetKey, @Nullable Iteration iter, int index, EvaluationContext context, Map<String, String> prefixMap, @Nullable List<String> traceSummary, @Nullable String targetNodeType, @Nullable String targetParentTriplesMap, @Nullable String targetNodeSummary, @Nullable InlineHintsEvaluationMode evaluationMode) {
        String iterationId = String.valueOf(index);
        String iterationData = iter != null ? iter.asString() : "";
        List<RmlEvaluationResult> results = evaluateNodeSafe(targetNode, targetKey, iter, context);

        List<String> diagnostics = context.report().getErrors() != null ?
                context.report().getErrors().stream().map(RmlError::message).toList() :
                Collections.emptyList();

        return new EvaluateResult(iterationId, iterationData, results, diagnostics, null, prefixMap, traceSummary, targetNodeType, targetParentTriplesMap, targetNodeSummary, evaluationMode);
    }

    private @Nullable String computeNodeSummary(@Nullable PlanNode node, @Nullable Map<String, String> prefixMap) {
        if (node == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (PlanNodeAttribute attr : node.nodeAttributes()) {
            if ("termType".equals(attr.key())) {
                continue;
            }
            String val = attr.value();
            if (val != null && !val.isBlank()) {
                if (prefixMap != null) {
                    val = RmlEvaluationResultFormatter.compactIri(val, prefixMap);
                }
                parts.add(attr.key() + ": " + val);
            }
        }
        for (PlanNodeReference ref : node.nodeReferences()) {
            PlanNode target = ref.target();
            String targetResource = PlanNode.resourceToIriOrBNode(target.getIri());
            String targetLabel = targetResource != null && prefixMap != null
                    ? RmlEvaluationResultFormatter.compactIri(targetResource, prefixMap)
                    : (targetResource != null ? targetResource : PlanNodeIdRegistry.getId(target));
            parts.add(ref.role() + ": " + targetLabel);
        }
        if (node instanceof ReferencingObjectMap rom && !rom.joinConditions.isEmpty()) {
            for (JoinCondition jc : rom.joinConditions) {
                List<String> jcParts = new ArrayList<>();
                for (PlanNodeAttribute jcAttr : jc.nodeAttributes()) {
                    jcParts.add(jcAttr.key() + "=" + jcAttr.value());
                }
                if (!jcParts.isEmpty()) {
                    parts.add(String.join(", ", jcParts));
                }
            }
        }
        if (parts.isEmpty()) {
            for (PlanNodeAttribute attr : node.nodeAttributes()) {
                String val = attr.value();
                if (val != null && !val.isBlank()) {
                    if (prefixMap != null) {
                        val = RmlEvaluationResultFormatter.compactIri(val, prefixMap);
                    }
                    parts.add(attr.key() + ": " + val);
                }
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /**
     * Safely evaluates a plan node against an iteration, checking mock overrides, memoized cache,
     * and recording any generated diagnostics without crashing.
     */
    public List<RmlEvaluationResult> evaluateNodeSafe(@Nullable PlanNode node, @Nullable Iteration iter, @Nullable EvaluationContext context) {
        return evaluateNodeSafe(node, null, iter, context);
    }

    /**
     * Safely evaluates a plan node against an iteration with an explicit AST cache key.
     */
    public List<RmlEvaluationResult> evaluateNodeSafe(@Nullable PlanNode node, @Nullable AstKey nodeKey, @Nullable Iteration iter, @Nullable EvaluationContext context) {
        if (node == null) {
            return Collections.emptyList();
        }
        // 1. Central Mock Check: If an intermediate override is present for this node ID, return it immediately
        String nodeId = PlanNodeIdRegistry.getId(node);
        if (context != null && context.nodeOverrides().containsKey(nodeId)) {
            return context.nodeOverrides().get(nodeId);
        }

        // 2. Incremental Cache Check (Tier 3: Node Evaluation Memoization)
        int iterId = (iter != null) ? (iter.asString().hashCode()) : 0;
        AstKey key = nodeKey != null ? nodeKey : (cache != null ? node.getAstKey() : null);
        if (cache != null && key != null) {
            List<RmlEvaluationResult> cached = cache.getEvaluation(key, iterId);
            if (cached != null) {
                return cached;
            }
        }

        try {
            List<RmlEvaluationResult> eval = evaluateNode(node, iter, context);
            if (cache != null && key != null) {
                cache.putEvaluation(key, iterId, eval);
            }
            return eval;
        } catch (BurpException e) {
            if (context != null && e.getError() != null) {
                context.report().getErrors().add(e.getError());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Direct node evaluation delegating to {@link PlanNode#evaluateNode(Iteration, EvaluationContext)}.
     */
    public List<RmlEvaluationResult> evaluateNode(@Nullable PlanNode node, @Nullable Iteration iter, @Nullable EvaluationContext context) {
        if (node == null) {
            return Collections.emptyList();
        }
        EvaluationContext effectiveContext = context != null ? context : EvaluationContext.createDefault();
        return node.evaluateNode(iter, effectiveContext);
    }

    /**
     * Matches an AST {@link AbstractLogicalSource} against keys registered in a {@link MultiSourceDataContext}.
     * Checks local/remote source file name, logical source IRI, node ID, and suffix matches.
     */
    private String findMatchingSourceKey(MultiSourceDataContext msd, AbstractLogicalSource logicalSource) {
        String fileName = null;
        if (logicalSource instanceof burp.ls.FileBasedLogicalSource fbls && fbls.file != null) {
            if (fbls.file instanceof burp.ls.SourceFile.Local local) {
                fileName = new java.io.File(local.getPath()).getName();
            } else if (fbls.file instanceof burp.ls.SourceFile.Remote remote) {
                fileName = new java.io.File(remote.getUrl()).getName();
            }
        }
        if (fileName != null && msd.sources().containsKey(fileName)) {
            return fileName;
        }
        String lsId = PlanNodeIdRegistry.getId(logicalSource);
        String lsIri = PlanNode.resourceToIriOrBNode(logicalSource.getIri());
        if (lsIri != null && msd.sources().containsKey(lsIri)) {
            return lsIri;
        }
        if (msd.sources().containsKey(lsId)) {
            return lsId;
        }
        // Check partial/contains match
        for (String k : msd.sources().keySet()) {
            if (fileName != null && (fileName.equalsIgnoreCase(k) || fileName.endsWith("/" + k) || fileName.endsWith("\\" + k))) {
                return k;
            }
        }
        return fileName != null ? fileName : (lsIri != null ? lsIri : lsId);
    }
}

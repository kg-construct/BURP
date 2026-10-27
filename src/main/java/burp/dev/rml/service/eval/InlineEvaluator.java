package burp.dev.rml.service.eval;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.selector.CursorSelector;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.fnml.FunctionExecution;
import burp.model.gathermap.GatherMap;
import burp.model.join.ParentJoinIndex;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Positions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static burp.parse.ValidationMode.LENIENT;

/**
 * Service to evaluate all non-constant expressions and provide parameter hints in a mapping document
 * for non-textual clients (e.g. visual mapping editors, diagrammatic webviews) and LSP inline hints.
 */
@NullMarked
public class InlineEvaluator {

    public StepInlineIterationResult stepInlineIteration(
            StepInlineIterationParams params,
            MappingWorkspace workspace,
            RmlEvaluator evaluator
    ) {
        String uri = params.structure().mappingFileUri();
        Position cursor = params.structure() instanceof CursorSelector cs ? cs.position() : null;
        if (cursor != null) {
            workspace.setCursorPosition(uri, cursor);
        }

        try {
            var resolved = workspace.resolveNode(params.structure());
            TriplesMap anchorTm = null;
            PlanNode target = resolved.targetNode();
            while (target != null) {
                if (target instanceof TriplesMap tm) {
                    anchorTm = tm;
                    break;
                }
                target = target.getParent();
            }
            if (anchorTm == null && !resolved.mappingDocument().triplesMaps.isEmpty()) {
                anchorTm = resolved.mappingDocument().triplesMaps.getFirst();
            }

            EvaluationDataContext currentCtx = workspace.getInlineDataContext(uri);
            if (currentCtx == null) {
                currentCtx = new RangeDataContext(0, 1);
            }
            int currentIndex = 0;
            if (currentCtx instanceof RangeDataContext rdc) {
                currentIndex = rdc.startIndex();
            }
            int newIndex = Math.max(0, currentIndex + params.delta());
            RangeDataContext newCtx = new RangeDataContext(newIndex, newIndex + 1);
            workspace.setInlineDataContext(uri, newCtx);

            Map<String, String> prefixMap = workspace.getPrefixMap(uri);
            var resolution = CoherentIterationResolver.resolve(
                    resolved.mappingDocument(),
                    newCtx,
                    null,
                    anchorTm,
                    cursor,
                    evaluator,
                    workspace.getInlineHintsEvaluationMode(),
                    workspace.getJoinIndexLimit(),
                    new HashMap<>(),
                    prefixMap,
                    workspace.getBaseIri()
            );

            return new StepInlineIterationResult(newCtx, newIndex, resolution.traceSummary());
        } catch (Exception e) {
            RangeDataContext fallbackCtx = new RangeDataContext(0, 1);
            return new StepInlineIterationResult(fallbackCtx, 0, Collections.emptyList());
        }
    }

    public CompletableFuture<List<InlineEvaluateItem>> inlineEvaluate(
            @Nullable InlineEvaluateParams params,
            MappingWorkspace workspace,
            RmlEvaluator evaluator
    ) {
        return CompletableFuture.completedFuture(evaluateItems(params, workspace, evaluator));
    }

    public List<InlineEvaluateItem> evaluateItems(
            @Nullable InlineEvaluateParams params,
            MappingWorkspace workspace,
            RmlEvaluator evaluator
    ) {
        if (params == null) {
            return Collections.emptyList();
        }

        try {
            var mappingDocument = workspace.loadMappingWithStableIds(params.mappingFileUri());
            Map<String, String> prefixMap = workspace.getPrefixMap(params.mappingFileUri());

            var mode = params.evaluationMode() != null ? params.evaluationMode() : workspace.getInlineHintsEvaluationMode();
            var limit = params.joinIndexLimit() != null ? params.joinIndexLimit() : workspace.getJoinIndexLimit();

            return evaluateItems(mappingDocument, params.dataContext(), params.range(), params.cursorPosition(), evaluator, workspace.getBaseIri(), prefixMap, mode, limit);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<InlineEvaluateItem> evaluateItems(
            MappingDocument mappingDocument,
            @Nullable EvaluationDataContext dataContext,
            @Nullable Range requestedRange,
            @Nullable Position cursorPosition,
            RmlEvaluator evaluator,
            @Nullable String fallbackBaseIri,
            @Nullable Map<String, String> prefixMap,
            InlineHintsEvaluationMode mode,
            @Nullable Integer joinIndexLimit
    ) {
        if (mappingDocument.triplesMaps == null || mappingDocument.triplesMaps.isEmpty()) {
            return Collections.emptyList();
        }

        EvaluationDataContext effectiveDataContext = dataContext != null
                ? dataContext
                : new RangeDataContext(0, 1);

        try {
            List<InlineEvaluateItem> items = new ArrayList<>();
            Set<String> seenPositions = new HashSet<>();

            // Pre-resolve all source iterations for multi-source joins and referencing object maps
            Map<AbstractLogicalSource, List<Iteration>> allSourceIterations = new HashMap<>();

            var resolution = CoherentIterationResolver.resolve(
                    mappingDocument,
                    effectiveDataContext,
                    requestedRange,
                    null,
                    cursorPosition,
                    evaluator,
                    mode,
                    joinIndexLimit,
                    allSourceIterations,
                    prefixMap,
                    fallbackBaseIri
            );
            Map<TriplesMap, CoherentIterationResolver.AssignedIteration> assignedMap = resolution.assignedIterations();
            Map<PlanNode, ParentJoinIndex> joinIndexes = resolution.joinIndexes();

            // Dynamic data evaluation of non-constant expressions
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                CoherentIterationResolver.AssignedIteration assigned = assignedMap.get(tm);
                if (assigned == null) {
                    continue;
                }

                Iteration selectedIteration = assigned.iteration();
                int iterationIndex = assigned.index();
                IterationReason iterationReason = assigned.reason();

                RmlExecutionReport report = new RmlExecutionReport();
                EvaluationContext defaultContext = EvaluationContext.createDefault()
                        .withReport(report)
                        .withBaseIri(tm.getBaseIri() != null ? tm.getBaseIri() : fallbackBaseIri)
                        .withSourceIterations(allSourceIterations)
                        .withJoinIndexes(joinIndexes)
                        .withValidationMode(LENIENT);

                List<PlanNode> nodesToEvaluate = new ArrayList<>();
                collectNodesToEvaluate(tm, nodesToEvaluate, new HashSet<>());

                for (PlanNode node : nodesToEvaluate) {
                    Range range = PlanNodeDescriptor.getFirstRangeForNode(node);
                    if (range == null || range.getEnd() == null) {
                        continue;
                    }
                    if (requestedRange != null && !intersects(range, requestedRange)) {
                        continue;
                    }

                    Iteration nodeIteration = selectedIteration;
                    EvaluationContext nodeContext = defaultContext;

                    ParentMap foundParentMap = null;
                    PlanNode curr = node;
                    while (curr != null && !(curr instanceof TriplesMap)) {
                        if (curr instanceof ParentMap pm) {
                            foundParentMap = pm;
                            break;
                        }
                        curr = curr.getParent();
                    }

                    if (foundParentMap != null && foundParentMap.getParent() instanceof JoinCondition jc && jc.getParent() instanceof ReferencingObjectMap rom) {
                        var parentAssigned = assignedMap.get(rom.parentTriplesMap);
                        if (parentAssigned != null) {
                            nodeIteration = parentAssigned.iteration();
                            String baseIri = EvaluationContexts.resolveBaseIri(rom.parentTriplesMap, defaultContext.baseIri());
                            nodeContext = defaultContext.withBaseIri(baseIri);
                        }
                    } else {
                        EvaluationContexts.NodeEvaluationScope scope = EvaluationContexts.resolveNodeScope(
                                node,
                                effectiveDataContext,
                                tm.logicalSource,
                                selectedIteration,
                                defaultContext,
                                evaluator
                        );
                        if (scope != null) {
                            nodeIteration = scope.iteration();
                            nodeContext = scope.context();
                        } else {
                            continue;
                        }
                    }

                    List<RmlEvaluationResult> evalResults = evaluator.evaluateNodeSafe(node, nodeIteration, nodeContext);
                    if (evalResults.isEmpty()) {
                        continue;
                    }

                    // Deduplicate by end position and node ID
                    String key = getKey(range, PlanNodeIdRegistry.getId(node));
                    if (seenPositions.add(key)) {
                        List<String> formattedValues = evalResults.stream()
                                .map(r -> RmlEvaluationResultFormatter.formatSummary(r, prefixMap))
                                .filter(Objects::nonNull)
                                .toList();

                        List<String> rawFormattedValues = evalResults.stream()
                                .map(r -> RmlEvaluationResultFormatter.formatSummary(r, null))
                                .filter(Objects::nonNull)
                                .toList();

                        List<String> diagnostics = report.getErrors().stream().map(RmlError::message).toList();
                        String typeName = node.getClass().getSimpleName();
                        items.add(new InlineEvaluateItem(
                                PlanNodeIdRegistry.getId(node),
                                typeName,
                                range,
                                evalResults,
                                formattedValues,
                                rawFormattedValues,
                                diagnostics,
                                iterationReason
                        ));
                    }
                }
            }

            return items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String getKey(Range range, String nodeId) {
        return range.getEnd().getLine() + ":" + range.getEnd().getCharacter() + ":" + nodeId;
    }

    private void collectNodesToEvaluate(@Nullable PlanNode node, List<PlanNode> list, Set<PlanNode> visited) {
        if (node == null || !visited.add(node)) return;
        if (shouldEvaluateNode(node)) list.add(node);
        if (node.children() != null) {
            for (PlanNode child : node.children()) {
                collectNodesToEvaluate(child, list, visited);
            }
        }
    }

    private boolean shouldEvaluateNode(PlanNode node) {
        if (PlanNodeDescriptor.getFirstRangeForNode(node) == null) return false;
        if (node instanceof ExpressionMap em) {
            if (em.expression instanceof RDFNodeConstant && em.condition == null) return false;
            return em.expression != null || em.condition != null || (em instanceof TermMap tm && tm.gatherMap != null);
        }
        return node instanceof FunctionExecution
                || node instanceof GatherMap
                || node instanceof Template
                || node instanceof Template.ReferenceSegment
                || node instanceof ReferencingObjectMap;
    }

    public static boolean intersects(Range a, Range b) {
        return !Positions.isBefore(a.getEnd(), b.getStart()) && !Positions.isBefore(b.getEnd(), a.getStart());
    }
}

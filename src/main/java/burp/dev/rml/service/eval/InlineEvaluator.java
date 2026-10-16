package burp.dev.rml.service.eval;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.fnml.FunctionExecution;
import burp.model.gathermap.GatherMap;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
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
            return evaluateItems(mappingDocument, params.dataContext(), params.range(), evaluator, workspace.getBaseIri(), prefixMap);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<InlineEvaluateItem> evaluateItems(
            MappingDocument mappingDocument,
            @Nullable EvaluationDataContext dataContext,
            @Nullable Range requestedRange,
            RmlEvaluator evaluator,
            @Nullable String fallbackBaseIri,
            @Nullable Map<String, String> prefixMap
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
            for (TriplesMap map : mappingDocument.triplesMaps) {
                if (map.logicalSource != null && !allSourceIterations.containsKey(map.logicalSource)) {
                    try {
                        var resolved = evaluator.resolveIterations(effectiveDataContext, map.logicalSource);
                        allSourceIterations.put(map.logicalSource, resolved);
                    } catch (Exception ignored) {
                    }
                }
                if (map.predicateObjectMaps != null) {
                    for (PredicateObjectMap pom : map.predicateObjectMaps) {
                        if (pom.objectMaps != null) {
                            for (BaseObjectMap bom : pom.objectMaps) {
                                if (bom instanceof ReferencingObjectMap rom && rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
                                    AbstractLogicalSource parentLs = rom.parentTriplesMap.logicalSource;
                                    if (!allSourceIterations.containsKey(parentLs)) {
                                        try {
                                            var resolved = evaluator.resolveIterations(effectiveDataContext, parentLs);
                                            allSourceIterations.put(parentLs, resolved);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic data evaluation of non-constant expressions
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                List<Iteration> iterations;
                try {
                    iterations = evaluator.resolveIterations(effectiveDataContext, tm);
                } catch (Exception ignored) {
                    continue;
                }

                if (iterations.isEmpty()) {
                    continue;
                }

                Iteration firstIteration = iterations.getFirst();
                RmlExecutionReport report = new RmlExecutionReport();
                EvaluationContext defaultContext = EvaluationContext.createDefault()
                        .withReport(report)
                        .withBaseIri(tm.getBaseIri() != null ? tm.getBaseIri() : fallbackBaseIri)
                        .withSourceIterations(allSourceIterations)
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

                    EvaluationContexts.NodeEvaluationScope scope = EvaluationContexts.resolveNodeScope(
                            node,
                            effectiveDataContext,
                            tm.logicalSource,
                            firstIteration,
                            defaultContext,
                            evaluator
                    );

                    if (scope == null) {
                        continue;
                    }

                    List<RmlEvaluationResult> evalResults = evaluator.evaluateNodeSafe(node, scope.iteration(), scope.context());
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
                                diagnostics
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

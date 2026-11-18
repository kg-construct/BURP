package burp.dev.rml.service.trace;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.join.ParentJoinIndex;
import burp.model.TemplateReferenceSafety;
import burp.model.RdfStatement;
import burp.model.rdf.*;
import burp.parse.ValidationMode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Evaluates AST execution plan nodes in topological order and populates a [`TraceGraph`].
///
/// ### Core Responsibilities & Flow
///
/// 1. **Logical Source Scoping**: Resolves the root [`AbstractLogicalSource`] and iterates
///    through input records for the targeted mapping component.
/// 2. **Parent Join Isolation**:
///    - Identifies all nodes participating in child-parent joins via [`ReferencingObjectMap`].
///    - Evaluates parent branch nodes contextually per matched parent iteration using a pre-computed
///      [`ParentJoinIndex`], preventing parent iterations from polluting child source nodes.
///    - Parent `TriplesMap` itself is omitted from join trace graphs; only the parent `SubjectMap`
///      and its upstream inputs are connected.
/// 3. **Fine-Grained Provenance Wiring**:
///    - Each evaluated node links its output [`TraceGraph.Value`] to its immediate input dependencies.
///    - For statement-producing nodes ([`TriplesMap`]), [`wireStatementProvenance`] matches subject,
///      predicate, and object back to specific incoming term maps and expression values.
///
/// ### Reading the Code
///
/// * [`execute`]: Entry point coordinating scope discovery, topological sorting, and iteration execution.
/// * [`executeStandardNode`]: Evaluates standard expression and term maps, populating node inputs/outputs.
/// * [`executeReferencingObjectMap`]: Handles cross-source joins, evaluates matched parent iterations,
///   and connects parent subject terms to child referencing object values.
/// * [`collectParentBranchNodes`]: Binds and extracts only the upstream nodes belonging to the parent branch.
@NullMarked
public final class TraceExecutor {

    private TraceExecutor() {
    }

    public static TraceGraph execute(
            TraceParams params,
            MappingWorkspace workspace,
            RmlEvaluator evaluator
    ) throws Exception {
        String mappingFileUri = workspace.getMappingFileUri(params.structure());
        MappingDocument mappingDocument = workspace.loadMappingWithStableIds(mappingFileUri);
        var prefixMap = workspace.getPrefixMap(mappingFileUri);
        PlanNode targetNode = workspace.findNodeBySelector(params.structure(), mappingDocument);

        AbstractLogicalSource rootLogicalSource = resolveRootLogicalSource(targetNode, mappingDocument);
        TriplesMap rootTm = mappingDocument.triplesMaps.stream()
                .filter(t -> t.logicalSource == rootLogicalSource)
                .findFirst()
                .orElse(mappingDocument.triplesMaps.getFirst());

        List<Iteration> iterations = evaluator.resolveIterations(params.dataContext(), rootTm);

        EvaluationContext traceContext = EvaluationContext.createDefault()
                .withBaseIri(rootTm.getBaseIri() != null ? rootTm.getBaseIri() : workspace.getBaseIri())
                .withValidationMode(ValidationMode.LENIENT);

        // Pre-populate join indexes for ReferencingObjectMaps so join evaluation is fast and accurate
        Map<PlanNode, ParentJoinIndex> joinIndexes = new HashMap<>();
        EvaluationContext unsafeCtx = traceContext.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
        for (TriplesMap tm : mappingDocument.triplesMaps) {
            for (ReferencingObjectMap rom : tm.descendants(ReferencingObjectMap.class).toList()) {
                if (rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
                    Iterable<Iteration> parentIters = traceContext.getIterations(rom.parentTriplesMap.logicalSource);
                    ParentJoinIndex idx = ParentJoinIndex.build(parentIters, rom.joinConditions, unsafeCtx);
                    joinIndexes.put(rom, idx);
                }
            }
        }
        traceContext = traceContext.withJoinIndexes(joinIndexes);

        Set<PlanNode> rawNodes = new LinkedHashSet<>();
        if (targetNode instanceof AbstractLogicalSource als) {
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                if (tm.logicalSource == als) {
                    collectTraceGraphNodes(tm, rawNodes);
                }
            }
            if (rawNodes.isEmpty()) {
                collectTraceGraphNodes(rootTm, rawNodes);
            }
        } else if (targetNode != null) {
            TriplesMap parentTm = targetNode instanceof TriplesMap t ? t : targetNode.ancestor(TriplesMap.class);
            collectTraceGraphNodes(Objects.requireNonNullElse(parentTm, targetNode), rawNodes);
        } else {
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                collectTraceGraphNodes(tm, rawNodes);
            }
        }

        List<PlanNode> sortedNodes = PlanNodeTopologicalSorter.sort(rawNodes);

        TraceGraph graph = new TraceGraph();

        // Register all nodes and their static structural edges in the graph
        for (PlanNode node : sortedNodes) {
            TraceGraph.Node traceNode = graph.getOrCreateNode(node);
            if (node.dependencies() != null) {
                for (PlanNode dep : node.dependencies()) {
                    if (dep != null && rawNodes.contains(dep)) {
                        traceNode.addUpstream(graph.getOrCreateNode(dep));
                    }
                }
            }
        }

        Set<PlanNode> parentJoinNodes = new HashSet<>();
        for (PlanNode n : rawNodes) {
            if (n instanceof ReferencingObjectMap rom && rom.parentTriplesMap != null) {
                collectParentBranchNodes(rom, parentJoinNodes);
            }
        }

        // Execute primary iterations
        for (Iteration iter : iterations) {
            Map<PlanNode, List<TraceGraph.Value>> currentIterValues = new IdentityHashMap<>();

            for (PlanNode node : sortedNodes) {
                if (parentJoinNodes.contains(node)) {
                    // Parent join nodes are evaluated contextually per matched parent iteration
                    continue;
                }

                switch (node) {
                    case ReferencingObjectMap rom ->
                            executeReferencingObjectMap(rom, iter, traceContext, graph, currentIterValues, rawNodes, prefixMap);
                    case TriplesMap tm -> executeTriplesMap(tm, iter, traceContext, graph, currentIterValues, prefixMap);
                    default -> executeStandardNode(node, iter, traceContext, graph, currentIterValues, prefixMap);
                }
            }
        }

        return graph;
    }

    public static @Nullable AbstractLogicalSource resolveNodeLogicalSource(PlanNode node) {
        if (node instanceof AbstractLogicalSource als) {
            return als;
        }
        if (node instanceof LocalReferenceScope lrs && lrs.getLogicalSource() != null) {
            return lrs.getLogicalSource();
        }
        if (node.ancestor(LocalReferenceScope.class) != null) {
            LocalReferenceScope scope = node.ancestor(LocalReferenceScope.class);
            if (scope != null && scope.getLogicalSource() != null) {
                return scope.getLogicalSource();
            }
        }
        TriplesMap tm = node.ancestor(TriplesMap.class);
        return tm != null ? tm.logicalSource : null;
    }

    private static void collectParentBranchNodes(ReferencingObjectMap rom, Set<PlanNode> target) {
        if (rom.parentTriplesMap == null) return;
        if (rom.parentTriplesMap.logicalSource != null) {
            target.add(rom.parentTriplesMap.logicalSource);
        }
        if (rom.parentTriplesMap.subjectMap != null) {
            collectTraceGraphNodes(rom.parentTriplesMap.subjectMap, target);
        }
        for (JoinCondition jc : rom.joinConditions) {
            if (jc.parentMap != null) {
                collectTraceGraphNodes(jc.parentMap, target);
            }
        }
    }

    private static List<RmlEvaluationResult> evaluateSafely(PlanNode node, Iteration iter, EvaluationContext traceContext) {
        try {
            return node.evaluateNode(iter, traceContext);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static void executeStandardNode(
            PlanNode node,
            Iteration iter,
            EvaluationContext traceContext,
            TraceGraph graph,
            Map<PlanNode, List<TraceGraph.Value>> currentIterValues,
            @Nullable Map<String, String> prefixMap) {
        List<TraceGraph.Value> inputVals = collectInputs(node, currentIterValues);
        TraceGraph.Node traceNode = graph.getOrCreateNode(node);
        traceNode.addInputs(inputVals);

        List<RmlEvaluationResult> evalResults = evaluateSafely(node, iter, traceContext);

        List<TraceGraph.Value> outputVals = new ArrayList<>(evalResults.size());
        for (RmlEvaluationResult r : evalResults) {
            String text = RmlEvaluationResultFormatter.formatSummaryAlways(r, prefixMap);
            if (node instanceof Template) {
                if (r instanceof IRITerm iri) {
                    text = iri.uri();
                } else if (r instanceof RawValue rv && rv.value() instanceof String s) {
                    text = s;
                }
            }
            TraceGraph.Value val = new TraceGraph.Value(node, iter, r, text);
            val.addAllDerivedFrom(inputVals);
            traceNode.addOutput(val);
            outputVals.add(val);
        }
        currentIterValues.put(node, outputVals);
    }

    private static void executeReferencingObjectMap(
            ReferencingObjectMap rom,
            Iteration childIter,
            EvaluationContext traceContext,
            TraceGraph graph,
            Map<PlanNode, List<TraceGraph.Value>> currentIterValues,
            Set<PlanNode> rawNodes,
            @Nullable Map<String, String> prefixMap) {
        List<TraceGraph.Value> childInputs = collectInputs(rom, currentIterValues);
        TraceGraph.Node romNode = graph.getOrCreateNode(rom);
        romNode.addInputs(childInputs);

        ParentJoinIndex joinIndex = traceContext.getJoinIndex(rom);
        if (joinIndex == null && rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
            Iterable<Iteration> parentIters = traceContext.getIterations(rom.parentTriplesMap.logicalSource);
            joinIndex = ParentJoinIndex.build(parentIters, rom.joinConditions, traceContext.withTemplateSafety(TemplateReferenceSafety.UNSAFE));
        }

        List<Iteration> matchingParents = joinIndex != null
                ? joinIndex.findMatches(childIter, rom.joinConditions, traceContext.withTemplateSafety(TemplateReferenceSafety.UNSAFE))
                : Collections.emptyList();

        List<TraceGraph.Value> romOutputs = new ArrayList<>();

        if (rom.parentTriplesMap != null) {
            Set<PlanNode> parentBranchNodes = new LinkedHashSet<>();
            collectParentBranchNodes(rom, parentBranchNodes);

            List<PlanNode> sortedParentBranch = PlanNodeTopologicalSorter.sort(parentBranchNodes);

            for (Iteration pIter : matchingParents) {
                Map<PlanNode, List<TraceGraph.Value>> parentIterValues = new IdentityHashMap<>();

                for (PlanNode pNode : sortedParentBranch) {
                    executeStandardNode(pNode, pIter, traceContext, graph, parentIterValues, prefixMap);
                }

                // Connect JoinCondition inputs from ChildMap and ParentMap
                for (JoinCondition jc : rom.joinConditions) {
                    TraceGraph.Node jcNode = graph.getOrCreateNode(jc);
                    if (jc.childMap != null && currentIterValues.containsKey(jc.childMap)) {
                        jcNode.addInputs(currentIterValues.get(jc.childMap));
                    }
                    if (jc.parentMap != null && parentIterValues.containsKey(jc.parentMap)) {
                        jcNode.addInputs(parentIterValues.get(jc.parentMap));
                    }
                }

                // Generate terms from parent subject map for the matched parent iteration
                if (rom.parentTriplesMap.subjectMap != null) {
                    SubjectMap pSm = rom.parentTriplesMap.subjectMap;
                    List<Term> generatedTerms = pSm.generateTerms(pIter, traceContext);

                    List<TraceGraph.Value> pSmValues = parentIterValues.getOrDefault(pSm, Collections.emptyList());
                    List<TraceGraph.Value> parentJoinInputs = new ArrayList<>(pSmValues);
                    for (JoinCondition jc : rom.joinConditions) {
                        if (jc.parentMap != null) {
                            parentJoinInputs.addAll(parentIterValues.getOrDefault(jc.parentMap, Collections.emptyList()));
                        }
                        if (jc.childMap != null) {
                            parentJoinInputs.addAll(currentIterValues.getOrDefault(jc.childMap, Collections.emptyList()));
                        }
                    }

                    for (Term term : generatedTerms) {
                        TraceGraph.Value romVal = new TraceGraph.Value(rom, childIter, term, RmlEvaluationResultFormatter.formatSummaryAlways(term, prefixMap));
                        romVal.addAllDerivedFrom(parentJoinInputs);
                        romVal.addAllDerivedFrom(childInputs);
                        romNode.addOutput(romVal);
                        romOutputs.add(romVal);
                    }
                }
            }
        }

        currentIterValues.put(rom, romOutputs);
    }

    private static void executeTriplesMap(
            TriplesMap tm,
            Iteration iter,
            EvaluationContext traceContext,
            TraceGraph graph,
            Map<PlanNode, List<TraceGraph.Value>> currentIterValues,
            @Nullable Map<String, String> prefixMap) {
        TraceGraph.Node tmNode = graph.getOrCreateNode(tm);
        List<TraceGraph.Value> inputVals = collectInputs(tm, currentIterValues);
        tmNode.addInputs(inputVals);

        List<RmlEvaluationResult> evalResults = evaluateSafely(tm, iter, traceContext);

        for (RmlEvaluationResult r : evalResults) {
            String text = RmlEvaluationResultFormatter.formatSummaryAlways(r, prefixMap);
            TraceGraph.Value stmtVal = new TraceGraph.Value(tm, iter, r, text);

            if (r instanceof RdfStatement stmt) {
                wireStatementProvenance(stmt, stmtVal, inputVals);
            } else {
                stmtVal.addAllDerivedFrom(inputVals);
            }
            tmNode.addOutput(stmtVal);
        }
    }

    private static void wireStatementProvenance(
            RdfStatement stmt,
            TraceGraph.Value stmtVal,
            List<TraceGraph.Value> inputVals
    ) {
        String subjStr = stmt.getSubject().toString();
        String predStr = stmt.getPredicate().toString();
        String objStr = stmt.getObject().toString();

        boolean wired = false;
        for (TraceGraph.Value tv : inputVals) {
            if (tv.result() != null && tv.result().toString().equals(subjStr)) {
                stmtVal.addDerivedFrom(tv);
                wired = true;
            } else if (tv.text().equals(subjStr)) {
                stmtVal.addDerivedFrom(tv);
                wired = true;
            }

            if (tv.text().contains(predStr) && tv.text().contains(objStr)) {
                stmtVal.addDerivedFrom(tv);
                wired = true;
            } else if (tv.result() instanceof RdfPredicateObject po && po.predicate().toString().equals(predStr) && po.object().toString().equals(objStr)) {
                stmtVal.addDerivedFrom(tv);
                wired = true;
            }
        }
        if (!wired) {
            stmtVal.addAllDerivedFrom(inputVals);
        }
    }

    private static List<TraceGraph.Value> collectInputs(
            PlanNode node,
            Map<PlanNode, List<TraceGraph.Value>> availableValues
    ) {
        List<TraceGraph.Value> inputs = new ArrayList<>();
        if (node.dependencies() != null) {
            for (PlanNode dep : node.dependencies()) {
                if (dep != null) {
                    List<TraceGraph.Value> vals = availableValues.get(dep);
                    if (vals != null) {
                        inputs.addAll(vals);
                    }
                }
            }
        }
        return inputs;
    }

    private static AbstractLogicalSource resolveRootLogicalSource(@Nullable PlanNode targetNode, MappingDocument mappingDocument) {
        if (targetNode instanceof AbstractLogicalSource als) {
            return als;
        }
        if (targetNode != null) {
            TriplesMap tm = targetNode instanceof TriplesMap t ? t : targetNode.ancestor(TriplesMap.class);
            if (tm != null && tm.logicalSource != null) {
                return tm.logicalSource;
            }
        }
        if (!mappingDocument.triplesMaps.isEmpty() && mappingDocument.triplesMaps.getFirst().logicalSource != null) {
            return mappingDocument.triplesMaps.getFirst().logicalSource;
        }
        throw new IllegalStateException("Could not resolve root logical source for mapping.");
    }

    private static void collectTraceGraphNodes(PlanNode node, Set<PlanNode> visited) {
        if (!visited.add(node)) return;

        if (node.children() != null) {
            for (PlanNode child : node.children()) {
                collectTraceGraphNodes(child, visited);
            }
        }

        if (node.dependencies() != null) {
            for (PlanNode dep : node.dependencies()) {
                collectTraceGraphNodes(dep, visited);
            }
        }
    }
}

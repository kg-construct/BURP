package burp.dev.rml.service.trace;

import burp.model.PlanNode;
import burp.model.RdfStatement;
import burp.model.TriplesMap;
import burp.model.rdf.Datardf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Extracts a backward provenance slice from an in-memory [`TraceGraph`].
///
/// ### Purpose & Mechanism
///
/// Given a targeted output RDF statement (subject, predicate, object, graph), `TraceSlicer`
/// performs a backward breadth-first search along [`TraceGraph.Value#derivedFrom`] edges.
///
/// * **Value Pruning**: Retains only values that directly or indirectly contributed to the target statement.
/// * **Node Pruning**: Retains only AST [`PlanNode`] instances that produced at least one kept value.
/// * **Edge Reconstruction**: Re-wires upstream/downstream and dependency relationships to keep the
///   resulting subgraph self-contained and consistent for JSON-LD/PROV-O serialization.
@NullMarked
public final class TraceSlicer {

    private TraceSlicer() {}

    public static TraceGraph slice(TraceGraph fullGraph, TraceResultParams params) {
        TraceGraph.Value targetValue = findTargetQuadValue(fullGraph, params);
        if (targetValue == null) {
            return fullGraph;
        }

        Set<TraceGraph.Value> keptValues = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<TraceGraph.Value> queue = new ArrayDeque<>();
        queue.add(targetValue);
        keptValues.add(targetValue);

        while (!queue.isEmpty()) {
            TraceGraph.Value curr = queue.poll();
            for (TraceGraph.Value dep : curr.derivedFrom()) {
                if (keptValues.add(dep)) {
                    queue.add(dep);
                }
            }
        }

        Set<PlanNode> keptPlanNodes = new HashSet<>();
        for (TraceGraph.Value v : keptValues) {
            keptPlanNodes.add(v.producer());
        }

        TraceGraph sliced = new TraceGraph();
        for (TraceGraph.Node node : fullGraph.getNodes()) {
            if (keptPlanNodes.contains(node.planNode())) {
                TraceGraph.Node slicedNode = sliced.getOrCreateNode(node.planNode());

                for (TraceGraph.Value in : node.inputs()) {
                    if (keptValues.contains(in)) {
                        slicedNode.addInput(in);
                    }
                }
                for (TraceGraph.Value out : node.outputs()) {
                    if (keptValues.contains(out)) {
                        slicedNode.addOutput(out);
                    }
                }
            }
        }

        // Restore upstream/downstream edges among kept nodes
        for (TraceGraph.Node node : fullGraph.getNodes()) {
            if (sliced.containsNode(node.planNode())) {
                TraceGraph.Node slicedNode = Objects.requireNonNull(sliced.getNode(node.planNode()));
                for (TraceGraph.Node up : node.upstream()) {
                    if (sliced.containsNode(up.planNode())) {
                        TraceGraph.Node slicedUp = Objects.requireNonNull(sliced.getNode(up.planNode()));
                        slicedNode.addUpstream(slicedUp);
                    }
                }
            }
        }

        return sliced;
    }

    private static TraceGraph.@Nullable Value findTargetQuadValue(TraceGraph graph, TraceResultParams params) {
        // First look in TriplesMap outputs
        for (TraceGraph.Node node : graph.getNodes()) {
            if (node.planNode() instanceof TriplesMap) {
                for (TraceGraph.Value val : node.outputs()) {
                    if (matchesTargetQuad(val, params)) {
                        return val;
                    }
                }
            }
        }
        // Fallback: search all outputs
        for (TraceGraph.Node node : graph.getNodes()) {
            for (TraceGraph.Value val : node.outputs()) {
                if (matchesTargetQuad(val, params)) {
                    return val;
                }
            }
        }
        return null;
    }

    private static boolean matchesTargetQuad(TraceGraph.Value val, TraceResultParams params) {
        if (val.result() instanceof RdfStatement stmt) {
            boolean subjMatches = Datardf.semanticEquals(stmt.getSubject(), params.getSubjectTerm());
            boolean predMatches = Datardf.semanticEquals(stmt.getPredicate(), params.getPredicateTerm());
            boolean objMatches = Datardf.semanticEquals(stmt.getObject(), params.getObjectTerm());
            boolean graphMatches = (params.getGraphTerm() == null && stmt.getGraph() == null)
                    || (stmt.getGraph() != null && params.getGraphTerm() != null && Datardf.semanticEquals(stmt.getGraph(), params.getGraphTerm()));
            if (subjMatches && predMatches && objMatches && graphMatches) {
                return true;
            }
        }

        String text = val.text();
        return text.contains(params.subject())
                && text.contains(params.predicate())
                && text.contains(params.object());
    }
}

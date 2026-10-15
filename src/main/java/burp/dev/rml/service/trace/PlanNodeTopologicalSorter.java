package burp.dev.rml.service.trace;

import burp.model.*;
import burp.model.fnml.FunctionExecution;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * Computes a deterministic topological order of AST {@link PlanNode}s matching the execution dataflow:
 * <pre>
 * LogicalSource -> References/Constants -> Function Inputs -> Functions -> Templates/Expressions -> TermMaps -> POMs -> TriplesMaps
 * </pre>
 */
@NullMarked
public final class PlanNodeTopologicalSorter {

    private PlanNodeTopologicalSorter() {}

    /**
     * Sorts the given collection of {@link PlanNode}s into deterministic topological order.
     *
     * @param nodes the collection of nodes to sort
     * @return an immutable, topologically sorted list of nodes
     */
    public static List<PlanNode> sort(Collection<? extends PlanNode> nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }

        Set<PlanNode> nodeSet = new LinkedHashSet<>(nodes);
        if (nodeSet.size() <= 1) {
            return List.copyOf(nodeSet);
        }

        // Build adjacency graph: upstream prerequisite U -> downstream consumer V (U -> V)
        Map<PlanNode, Set<PlanNode>> successors = new HashMap<>();
        Map<PlanNode, Set<PlanNode>> prerequisites = new HashMap<>();
        for (PlanNode n : nodeSet) {
            successors.put(n, new HashSet<>());
            prerequisites.put(n, new HashSet<>());
        }

        for (PlanNode v : nodeSet) {
            Set<PlanNode> upstream = findUpstreamDependencies(v, nodeSet);
            for (PlanNode u : upstream) {
                if (!u.equals(v)) {
                    successors.computeIfAbsent(u, k -> new HashSet<>()).add(v);
                    prerequisites.computeIfAbsent(v, k -> new HashSet<>()).add(u);
                }
            }
        }

        // In-degree tracking
        Map<PlanNode, Integer> inDegree = new HashMap<>();
        for (PlanNode n : nodeSet) {
            inDegree.put(n, prerequisites.getOrDefault(n, Set.of()).size());
        }

        Comparator<PlanNode> comparator = Comparator
                .comparingInt((PlanNode n) -> PlanNodeStage.determineStage(n).getOrder())
                .thenComparing(PlanNode::getId);

        PriorityQueue<PlanNode> queue = new PriorityQueue<>(comparator);
        for (Map.Entry<PlanNode, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<PlanNode> sorted = new ArrayList<>(nodeSet.size());
        while (!queue.isEmpty()) {
            PlanNode current = queue.poll();
            sorted.add(current);

            for (PlanNode succ : successors.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.getOrDefault(succ, 1) - 1;
                inDegree.put(succ, newDegree);
                if (newDegree == 0) {
                    queue.add(succ);
                }
            }
        }

        // Cycle fallback: append any remaining nodes sorted by stage and id
        if (sorted.size() < nodeSet.size()) {
            List<PlanNode> remaining = new ArrayList<>();
            for (PlanNode n : nodeSet) {
                if (!sorted.contains(n)) {
                    remaining.add(n);
                }
            }
            remaining.sort(comparator);
            sorted.addAll(remaining);
        }

        return Collections.unmodifiableList(sorted);
    }

    private static Set<PlanNode> findUpstreamDependencies(PlanNode v, Set<PlanNode> nodeSet) {
        Set<PlanNode> upstream = new HashSet<>();

        // 1. Explicit declared dependencies
        for (PlanNode dep : v.dependencies()) {
            if (dep != null && nodeSet.contains(dep)) {
                upstream.add(dep);
            }
        }

        // 2. References depend on their TriplesMap's LogicalSource
        if (v instanceof Reference || v instanceof Template.ReferenceSegment) {
            TriplesMap tm = v.ancestor(TriplesMap.class);
            if (tm != null && tm.logicalSource != null && nodeSet.contains(tm.logicalSource)) {
                upstream.add(tm.logicalSource);
            }
        }

        // 3. ReferencingObjectMap depends on ParentTriplesMap and its LogicalSource/SubjectMap
        if (v instanceof ReferencingObjectMap rom && rom.parentTriplesMap != null) {
            if (nodeSet.contains(rom.parentTriplesMap)) {
                upstream.add(rom.parentTriplesMap);
            }
            if (rom.parentTriplesMap.logicalSource != null && nodeSet.contains(rom.parentTriplesMap.logicalSource)) {
                upstream.add(rom.parentTriplesMap.logicalSource);
            }
            if (rom.parentTriplesMap.subjectMap != null && nodeSet.contains(rom.parentTriplesMap.subjectMap)) {
                upstream.add(rom.parentTriplesMap.subjectMap);
            }
        }

        // 4. ExpressionMap depends on its expression
        if (v instanceof ExpressionMap em && em.expression != null && nodeSet.contains(em.expression)) {
            upstream.add(em.expression);
        }

        // 5. Template depends on its segments
        if (v instanceof Template tpl) {
            for (Template.Segment seg : tpl.getSegments()) {
                if (seg != null && nodeSet.contains(seg)) {
                    upstream.add(seg);
                }
            }
        }

        // 6. Input depends on its inputValueMap and parameterMap
        if (v instanceof Input in) {
            if (in.inputValueMap != null && nodeSet.contains(in.inputValueMap)) {
                upstream.add(in.inputValueMap);
            }
            if (in.parameterMap != null && nodeSet.contains(in.parameterMap)) {
                upstream.add(in.parameterMap);
            }
        }

        // 7. FunctionExecution depends on its inputs and functionMap
        if (v instanceof FunctionExecution fe) {
            if (fe.functionMap != null && nodeSet.contains(fe.functionMap)) {
                upstream.add(fe.functionMap);
            }
            for (Input in : fe.inputs) {
                if (in != null && nodeSet.contains(in)) {
                    upstream.add(in);
                }
            }
        }

        // 8. PredicateObjectMap depends on predicateMaps, objectMaps, and graphMaps
        if (v instanceof PredicateObjectMap pom) {
            for (PredicateMap pm : pom.predicateMaps) {
                if (pm != null && nodeSet.contains(pm)) upstream.add(pm);
            }
            for (BaseObjectMap om : pom.objectMaps) {
                if (om != null && nodeSet.contains(om)) upstream.add(om);
            }
            for (GraphMap gm : pom.graphMaps) {
                if (gm != null && nodeSet.contains(gm)) upstream.add(gm);
            }
        }

        // 9. TriplesMap depends on subjectMap, predicateObjectMaps, and logicalSource
        if (v instanceof TriplesMap tm) {
            if (tm.logicalSource != null && nodeSet.contains(tm.logicalSource)) {
                upstream.add(tm.logicalSource);
            }
            if (tm.subjectMap != null && nodeSet.contains(tm.subjectMap)) {
                upstream.add(tm.subjectMap);
            }
            for (PredicateObjectMap pom : tm.predicateObjectMaps) {
                if (pom != null && nodeSet.contains(pom)) {
                    upstream.add(pom);
                }
            }
        }

        return upstream;
    }
}

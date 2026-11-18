package burp.dev.rml.service.trace;

import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.RmlEvaluationResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// In-memory execution and fine-grained provenance graph.
///
/// ### Architecture & Rationale
///
/// Unlike earlier string/map-based trace representations, `TraceGraph` uses direct
/// object references between AST [`PlanNode`] instances and intermediate evaluated values.
///
/// * **[Node]**: A vertex corresponding to an AST `PlanNode`. Tracks static dependencies
///   (`upstream`, `downstream`) and runtime data values (`inputs`, `outputs`). Uses identity
///   equality based on `planNode` to avoid cyclic recursion.
/// * **[Value]**: An intermediate runtime value produced during execution of a specific [Iteration].
///   Maintains a provenance lineage via `derivedFrom` to enable precise backward slicing.
///
/// ### Lifecycle
///
/// 1. **Built** by [`TraceExecutor`] during forward execution.
/// 2. **Optionally sliced** by [`TraceSlicer`] to isolate the lineage of a single triple.
/// 3. **Exported** by [`TraceGraphExporter`] into serialized DTOs ([`TraceResult`]) and PROV-O JSON-LD.
@NullMarked
public class TraceGraph {

    public record Value(
            PlanNode producer,
            @Nullable Iteration iteration,
            @Nullable RmlEvaluationResult result,
            String text,
            List<Value> derivedFrom
    ) {
        public Value(PlanNode producer, @Nullable Iteration iteration, @Nullable RmlEvaluationResult result, String text) {
            this(producer, iteration, result, text, new ArrayList<>());
        }

        public void addDerivedFrom(Value value) {
            if (!derivedFrom.contains(value)) {
                derivedFrom.add(value);
            }
        }

        public void addAllDerivedFrom(Collection<Value> values) {
            for (Value v : values) {
                addDerivedFrom(v);
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    public record Node(
            PlanNode planNode,
            List<Value> inputs,
            List<Value> outputs,
            Set<Node> upstream,
            Set<Node> downstream
    ) {
        public Node(PlanNode planNode) {
            this(planNode, new ArrayList<>(), new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        public void addInput(Value value) {
            inputs.add(value);
        }

        public void addInputs(Collection<Value> values) {
            inputs.addAll(values);
        }

        public void addOutput(Value value) {
            outputs.add(value);
        }

        public void addOutputs(Collection<Value> values) {
            outputs.addAll(values);
        }

        public void addUpstream(Node node) {
            upstream.add(node);
            node.downstream.add(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node other)) return false;
            return Objects.equals(planNode, other.planNode);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(planNode);
        }
    }

    private final Map<PlanNode, Node> nodes = new LinkedHashMap<>();

    public Node getOrCreateNode(PlanNode planNode) {
        return nodes.computeIfAbsent(planNode, Node::new);
    }

    public @Nullable Node getNode(PlanNode planNode) {
        return nodes.get(planNode);
    }

    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public boolean containsNode(PlanNode planNode) {
        return nodes.containsKey(planNode);
    }
}

package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.reporting.PointRange;
import burp.reporting.RDFPointer;
import org.apache.jena.rdf.model.Resource;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface PlanNode {
    default @Nullable Path getSourceFile() {
        return null;
    }
    @Nullable
    PlanNode getParent();

    void setParent(@Nullable PlanNode parent);

    default Set<PlanNode> getDependents() {
        // By default returning an empty or modifiable set might be tricky,
        // we'll leave it abstract or default to a new HashSet if overridden.
        return new HashSet<>();
    }

    default Iterable<PlanNode> children() {
        return Collections.emptyList();
    }

    default Iterable<PlanNode> dependencies() {
        return Collections.emptyList();
    }

    default Iterable<PlanNode> dependents() {
        return getDependents();
    }

    /// Returns the semantic cross-tree references to other [PlanNode] instances (not owned tree children).
    default List<PlanNodeReference> nodeReferences() {
        return Collections.emptyList();
    }

    /// Returns the referenced [PlanNode] targets for traversals and reachability analysis.
    default Iterable<PlanNode> references() {
        return nodeReferences().stream().map(PlanNodeReference::target).toList();
    }

    /// Returns the configuration and defining attributes of this node with their source origins.
    default List<PlanNodeAttribute> nodeAttributes() {
        List<PlanNodeAttribute> list = new ArrayList<>();
        collectAttributes((k, v, o) -> list.add(new PlanNodeAttribute(k, v, o)));
        return list.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /// Internal accumulator hook overridden by classes to contribute attributes without intermediate allocations.
    default void collectAttributes(AttributeCollector sink) {
    }

    default List<PointRange> nodeRanges() {
        return Collections.emptyList();
    }

    default List<RDFPointer> getSourcePointers() {
        return Collections.emptyList();
    }

    /// Returns the canonical RDF resource identifier associated with this AST plan node, if any.
    /// Think of [Template] for example, of PlanNode not having an iri.
    ///
    /// @return the RDF IRI, blank node identifier, or `null` if not backed by an RDF resource.
    @Nullable Resource getIri();

    @Contract("!null -> !null; null -> null")
    static @Nullable String resourceToIriOrBNode(@Nullable Resource resource) {
        if (resource == null) return null;
        if (resource.isURIResource()) return resource.getURI();
        if (resource.isAnon()) {
            String label = resource.getId().getLabelString();
            return label.startsWith("_:") ? label : "_:" + label;
        }
        return resource.toString();
    }

    /// Returns a short id for a node unique per node.
    /// This is different from [getIri] because all nodes don't have an iri or blank node,
    /// and it is different from [getAstKey] which can be shared by multiple nodes.
    ///
    /// @return a short string id.
    default String getId() {
        return PlanNodeIdRegistry.getId(this);
    }

    /// Returns a concise type name for this node (e.g. CSVReference, SubjectMap, etc.).
    default String getNodeType() {
        return getClass().getSimpleName();
    }

    /// Returns the deterministic, content-addressed Merkle DAG cache key for this AST plan node.
    ///
    /// - **Bottom-Up Composition**: The key depends **strictly** on the node's local operation attributes and the Merkle hashes of its children/inputs.
    /// - A node's key never depends on its parent or surrounding container, ensuring subtrees with identical semantics share cache entries.
    /// - Used by the Dev Server and execution engines to cache and memoize intermediary evaluation results and data streams across edits and runs.
    ///
    /// @return the content-addressed [AstKey] for this node.
    default AstKey getAstKey() {
        return computeAstKey();
    }

    AstKey computeAstKey();

    default @Nullable <T extends PlanNode> T ancestor(Class<T> clazz) {
        PlanNode p = getParent();
        while (p != null) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
            p = p.getParent();
        }
        return null;
    }

    default <T extends PlanNode> Stream<T> descendants(Class<T> clazz) {
        return descendants(clazz, Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    default <T extends PlanNode> Stream<T> descendants(Class<T> clazz, Set<PlanNode> visited) {
        if (!visited.add(this)) {
            return Stream.empty();
        }
        return StreamSupport.stream(children().spliterator(), false).flatMap(child -> {
            Stream<T> rest = child.descendants(clazz, visited);
            if (clazz.isInstance(child)) {
                return Stream.concat(Stream.of(clazz.cast(child)), rest);
            }
            return rest;
        });
    }

    default <T extends PlanNode> Stream<T> descendantsAndReferences(Class<T> clazz) {
        return descendantsAndReferences(clazz, Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    default <T extends PlanNode> Stream<T> descendantsAndReferences(Class<T> clazz, Set<PlanNode> visited) {
        if (!visited.add(this)) {
            return Stream.empty();
        }
        Stream<PlanNode> allLinked = Stream.concat(
                StreamSupport.stream(children().spliterator(), false),
                StreamSupport.stream(references().spliterator(), false)
        );
        return allLinked.flatMap(child -> {
            Stream<T> rest = child.descendantsAndReferences(clazz, visited);
            if (clazz.isInstance(child)) {
                return Stream.concat(Stream.of(clazz.cast(child)), rest);
            }
            return rest;
        });
    }

    default boolean isDescendantOrSelf(@Nullable PlanNode root) {
        if (root == null) return false;
        PlanNode current = this;
        Set<PlanNode> visited = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            if (current == root) return true;
            current = current.getParent();
        }
        return false;
    }

    default List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, EvaluationContext context) {
        return Collections.emptyList();
    }
}

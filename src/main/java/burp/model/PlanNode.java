package burp.model;

import burp.reporting.PointRange;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface PlanNode {
    PlanNode getParent();
    void setParent(PlanNode parent);

    default Set<PlanNode> getDependents() {
        // By default returning an empty or modifiable set might be tricky,
        // we'll leave it abstract or default to a new HashSet if overridden.
        return new HashSet<>();
    }

    default Iterable<PlanNode> children() { return Collections.emptyList(); }
    default Iterable<PlanNode> dependencies() { return Collections.emptyList(); }

    default Iterable<PlanNode> dependents() {
        return getDependents();
    }

    default List<PointRange> nodeRanges() {
        return Collections.emptyList();
    }

    default <T extends PlanNode> T ancestor(Class<T> clazz) {
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
        return StreamSupport.stream(children().spliterator(), false).flatMap(child -> {
            Stream<T> rest = child.descendants(clazz);
            if (clazz.isInstance(child)) {
                return Stream.concat(Stream.of(clazz.cast(child)), rest);
            }
            return rest;
        });
    }
}

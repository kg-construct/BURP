package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.Nullable;

public interface LocalReferenceScope extends PlanNode {
    /**
     * Builds a reference for the local scope context.
     * Use this for normal properties, subjects, objects, or the childMap of a join condition.
     */
    Reference buildLocalReference(String reference, Origin origin);

    /**
     * Returns the effective logical source provided by this scope.
     */
    @Nullable AbstractLogicalSource getLogicalSource();
}

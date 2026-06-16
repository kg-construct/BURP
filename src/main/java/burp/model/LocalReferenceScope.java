package burp.model;

import burp.reporting.Origin;

public interface LocalReferenceScope extends PlanNode {
    /**
     * Builds a reference for the local scope context.
     * Use this for normal properties, subjects, objects, or the childMap of a join condition.
     */
    Reference buildLocalReference(String reference, Origin origin);
}

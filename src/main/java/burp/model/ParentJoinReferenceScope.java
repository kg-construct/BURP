package burp.model;

import burp.reporting.Origin;

public interface ParentJoinReferenceScope extends PlanNode {
    /**
     * Builds a reference for a joined parent scope.
     * When a JoinCondition evaluates its parentMap, it asks its scope to build a parent reference.
     */
    Reference buildParentJoinReference(String reference, Origin origin);
}

package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.Nullable;

public interface ParentJoinReferenceScope extends PlanNode {
    /**
     * Builds a reference for a joined parent scope.
     * When a JoinCondition evaluates its parentMap, it asks its scope to build a parent reference.
     */
    Reference buildParentJoinReference(String reference, Origin origin);

    /**
     * Returns the effective parent logical source provided by this scope.
     */
    @Nullable AbstractLogicalSource getParentLogicalSource();

    /**
     * Builds a reference for a joined child scope.
     * When a JoinCondition evaluates its childMap, it asks its scope to build a child reference.
     */
    @Nullable Reference buildChildJoinReference(String reference, Origin origin);

    /**
     * Returns the effective child logical source provided by this scope.
     */
    @Nullable AbstractLogicalSource getChildLogicalSource();
}

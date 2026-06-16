package burp.model;

import java.util.Collections;
import java.util.Set;

public interface LogicalTargetScope extends PlanNode {
    Set<LogicalTarget> getLogicalTargets();

    default Set<LogicalTarget> getEffectiveTargets() {
        if (getLogicalTargets() != null && !getLogicalTargets().isEmpty()) {
            return getLogicalTargets();
        }
        PlanNode p = getParent();
        while (p != null) {
            if (p instanceof LogicalTargetScope) {
                LogicalTargetScope lts = (LogicalTargetScope) p;
                if (lts.getLogicalTargets() != null && !lts.getLogicalTargets().isEmpty()) {
                    return lts.getLogicalTargets();
                }
            }
            p = p.getParent();
        }
        return Collections.emptySet();
    }
}

package burp.dev.rml.service.plan;

import burp.model.*;
import burp.model.gathermap.GatherMap;
import burp.reporting.LiteralPart;
import burp.reporting.PointRange;
import burp.reporting.RDFPointer;
import burp.reporting.StatementParts;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Utility to extract metadata, parametric values, and child references from PlanNodes.
 */
@NullMarked
public final class PlanNodeDescriptor {

    private PlanNodeDescriptor() {
    }

    public static String getAbbreviation(@Nullable PlanNode node) {
        return switch (node) {
            case TriplesMap tm -> "tm";
            case SubjectMap sm -> "sm";
            case PredicateObjectMap pom -> "pom";
            case PredicateMap pm -> "pm";
            case ObjectMap om -> "om";
            case ReferencingObjectMap rom -> "rom";
            case GraphMap gm -> "gm";
            case JoinCondition jc -> "jc";
            case ParentMap pam -> "parentMap";
            case ChildMap cm -> "childMap";
            case GatherMap gam -> "gam";
            case AbstractLogicalSource ls -> "ls";
            case burp.model.Condition cond -> "cond";
            case null -> "";
            default -> node.getClass().getSimpleName().toLowerCase();
        };
    }

    public static List<Range> getRangeForNode(@Nullable PlanNode node) {
        if (node == null) return Collections.emptyList();
        var ranges = node.nodeRanges();
        return ranges.stream().map(PointRange::toLsp).toList();
    }

    public static @Nullable Range getFirstRangeForNode(@Nullable PlanNode node) {
        if (node == null) return null;
        var ranges = node.nodeRanges();
        return ranges.stream().sorted().map(PointRange::toLsp).findFirst().orElse(null);
    }

    public static String getRdfPointerString(@Nullable RDFPointer ptr) {
        return switch (ptr) {
            case null -> "";
            case StatementParts sp -> sp.stmt().toString();
            case LiteralPart lp -> lp.stmt().toString();
            default -> ptr.toString();
        };
    }
}

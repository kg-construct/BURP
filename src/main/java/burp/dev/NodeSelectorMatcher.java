package burp.dev;

import burp.dev.rml.service.selector.*;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.PointRange;
import burp.reporting.RDFPointer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to match a PlanNode against a StructureSelector.
 */
@NullMarked
public final class NodeSelectorMatcher {

    private NodeSelectorMatcher() {}

    public static boolean matches(PlanNode node, StructureSelector selector) {
        return switch (selector) {
            case IdSelector idSel -> !idSel.id().isEmpty() && PlanNodeIdRegistry.getId(node).equals(idSel.id());
            case IriSelector iriSel -> matchesIri(node, iriSel);
            case CursorSelector cursorSel -> matchesCursor(node, cursorSel, false);
            case DocumentSelector docSel -> true;
        };
    }

    public static boolean matchesExact(PlanNode node, StructureSelector selector) {
        if (selector instanceof CursorSelector cursorSel) {
            return matchesCursor(node, cursorSel, true);
        }
        return matches(node, selector);
    }

    private static boolean matchesIri(PlanNode node, IriSelector is) {
        String target = is.iri();
        String iri = PlanNode.resourceToIriOrBNode(node.getIri());
        if (iri != null) {
            if (iri.equals(target)) return true;
            return iri.endsWith("/" + target) || iri.endsWith("#" + target);
        }
        return false;
    }

    private static boolean matchesCursor(PlanNode node, CursorSelector selector, boolean strictColumn) {
        List<PointRange> ranges = new ArrayList<>(node.nodeRanges());
        List<RDFPointer> pointers = node.getSourcePointers();
        if (!pointers.isEmpty()) {
            ranges.addAll(ProvTurtleVisitor.retrieveTurtleLocation(pointers));
        }
        if (ranges.isEmpty()) return false;
        int targetLine = selector.position().getLine();
        int targetCol = selector.position().getCharacter();

        boolean anyLineMatch = false;
        for (PointRange range : ranges) {
            if (range.end() != null) {
                int start = range.start().line();
                int end = range.end().line();
                if (start <= targetLine && targetLine <= end) {
                    anyLineMatch = true;
                    if (start == targetLine && end == targetLine) {
                        int startCol = range.start().column();
                        int endCol = range.end().column();
                        if (startCol <= targetCol && targetCol <= endCol) {
                            return true;
                        }
                    } else {
                        boolean beforeStart = (start == targetLine && targetCol < range.start().column());
                        boolean afterEnd = (end == targetLine && targetCol > range.end().column());
                        if (!beforeStart && !afterEnd) {
                            return true;
                        }
                    }
                }
            }
        }
        return !strictColumn && anyLineMatch;
    }
}

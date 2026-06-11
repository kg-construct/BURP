package burp.reporting;

import java.util.*;

/**
 * Converts a list of Nodes (which may span multiple lines) into a map of
 * LineIndex -> List<ColumnRange>. Overlapping ranges are merged.
 *
 * @return a map from line indexes (0 indexed) to a list of column ranges (also 0-indexed).
 */
public class FileGeometry {

    public record ColumnRange(int first, int last) {
    }

    public static Map<Integer, List<ColumnRange>> getMergedHighlights(List<PointRange> nodes, List<String> lines) {
        Map<Integer, List<ColumnRange>> rawMap = new HashMap<>();

        // 1. Flatten Nodes into raw line ranges
        for (PointRange node : nodes) {
            if (node.getEnd() == null) continue;

            int startLine = Math.max(node.getStart().line(), 0);
            int endLine = Math.min(node.getEnd().line(), lines.size() - 1);

            for (int lineIdx = startLine; lineIdx <= endLine; lineIdx++) {
                int lineLen = lines.get(lineIdx).length();

                int startCol = (lineIdx == startLine) ? node.getStart().column() : 0;
                int endCol = (lineIdx == endLine) ? node.getEnd().column() : lineLen; // Go to end of line if multi-line

                // Ensure we don't go out of bounds
                int safeStart = Math.max(0, Math.min(startCol, lineLen));
                int safeEnd = Math.max(0, Math.min(endCol, lineLen));

                // Add only valid ranges
                if (safeStart <= safeEnd) {
                    rawMap.computeIfAbsent(lineIdx, k -> new ArrayList<>()).add(new ColumnRange(safeStart, safeEnd));
                }
            }
        }

        // 2. Merge overlapping ranges for each line
        Map<Integer, List<ColumnRange>> mergedMap = new HashMap<>();
        for (Map.Entry<Integer, List<ColumnRange>> entry : rawMap.entrySet()) {
            mergedMap.put(entry.getKey(), mergeRanges(entry.getValue()));
        }
        return mergedMap;
    }

    /**
     * Merges a list of overlapping or adjacent ranges.
     * e.g. [0..5, 4..8, 10..12] -> [0..8, 10..12]
     */
    public static List<ColumnRange> mergeRanges(List<ColumnRange> ranges) {
        if (ranges.isEmpty()) {
            return Collections.emptyList();
        }

        List<ColumnRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(ColumnRange::first));

        List<ColumnRange> merged = new ArrayList<>();
        ColumnRange current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            ColumnRange next = sorted.get(i);
            // If next overlaps or is adjacent to current (next.first <= current.last + 1)
            // We use +1 to merge "adjacent" chars (e.g. 0..1 and 2..3 become 0..3)
            // because visually they should be one block.
            if (next.first() <= current.last() + 1) {
                current = new ColumnRange(current.first(), Math.max(current.last(), next.last()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}

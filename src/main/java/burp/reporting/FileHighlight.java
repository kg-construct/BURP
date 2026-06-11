package burp.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileHighlight {

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes) {
        return extractAndHighlight(filePath, nodes, 1);
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes, int contextLines) {
        if (!Files.exists(filePath)) {
            return null;
        }

        List<String> allLines;
        try {
            allLines = Files.readAllLines(filePath);
        } catch (IOException e) {
            return null;
        }

        if (nodes.isEmpty()) {
            return null;
        }

        Map<Integer, List<FileGeometry.ColumnRange>> highlightsByLine = FileGeometry.getMergedHighlights(nodes, allLines);
        if (highlightsByLine.isEmpty()) {
            return null;
        }

        List<Integer> linesToPrint = calculateLinesToPrint(highlightsByLine.keySet(), allLines.size() - 1, contextLines);

        StringBuilder sb = new StringBuilder();
        int previousLineIndex = -1;

        for (int lineIndex : linesToPrint) {
            // Add ellipsis if we skipped lines
            if (previousLineIndex != -1 && lineIndex > previousLineIndex + 1) {
                sb.append(String.format("%6s", "...")).append(System.lineSeparator());
            }
            previousLineIndex = lineIndex;

            String lineContent = allLines.get(lineIndex);
            List<FileGeometry.ColumnRange> rangesOnLine = highlightsByLine.getOrDefault(lineIndex, Collections.emptyList());
            String lineNumberStr = formatLineNumber(lineIndex + 1);

            sb.append(lineNumberStr);
            sb.append(renderLineWithColors(lineContent, rangesOnLine)).append(System.lineSeparator());
        }

        return sb.toString();
    }

    private static List<Integer> calculateLinesToPrint(
        Set<Integer> highlightedLines,
        int maxLineIndex,
        int contextLines
    ) {
        Set<Integer> linesToPrint = new TreeSet<>();
        for (int lineIdx : highlightedLines) {
            int start = Math.max(lineIdx - contextLines, 0);
            int end = Math.min(lineIdx + contextLines, maxLineIndex);
            for (int i = start; i <= end; i++) {
                linesToPrint.add(i);
            }
        }
        return new ArrayList<>(linesToPrint);
    }

    private static String formatLineNumber(int lineNumber) {
        String s = lineNumber + " ";
        return String.format("%5s", s) + "| ";
    }

    /**
     * Splits the line string based on ranges and inserts ANSI codes.
     */
    private static String renderLineWithColors(String line, List<FileGeometry.ColumnRange> ranges) {
        if (ranges.isEmpty()) {
            return line;
        }

        StringBuilder sb = new StringBuilder();
        int currentIndex = 0;

        // Ranges are already sorted and non-overlapping from mergeRanges
        for (FileGeometry.ColumnRange range : ranges) {
            // Append text before the highlight
            if (currentIndex < range.first()) {
                sb.append(line, currentIndex, range.first());
            }

            // Append highlighted text
            // Ensure we don't go out of bounds if the range extends slightly beyond line length (rare but possible)
            int safeEnd = Math.min(range.last() + 1, line.length());
            if (range.first() < safeEnd) {
                String textToHighlight = line.substring(range.first(), safeEnd);
                sb.append(ansiBoldRed(textToHighlight));
            }

            currentIndex = safeEnd;
        }

        // Append the remaining text
        if (currentIndex < line.length()) {
            sb.append(line.substring(currentIndex));
        }

        return sb.toString();
    }

    public static String ansiBoldRed(String text) {
        return "\u001B[1;31m" + text + "\u001B[0m";
    }

    public static String ansiRed(String text) {
        return "\u001B[31m" + text + "\u001B[0m";
    }
}

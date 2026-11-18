package burp.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileHighlight {

    public static boolean hasColorSupport() {
        if (System.getenv("NO_COLOR") != null) return false;
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) return false;
        return System.console() != null || System.getenv("COLORTERM") != null;
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes) {
        return extractAndHighlight(filePath, nodes, 1, Severity.ERROR, hasColorSupport());
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes, Severity severity) {
        return extractAndHighlight(filePath, nodes, 1, severity, hasColorSupport());
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes, int contextLines) {
        return extractAndHighlight(filePath, nodes, contextLines, Severity.ERROR, hasColorSupport());
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes, int contextLines, Severity severity) {
        return extractAndHighlight(filePath, nodes, contextLines, severity, hasColorSupport());
    }

    public static String extractAndHighlight(Path filePath, List<PointRange> nodes, int contextLines, Severity severity, boolean useColors) {
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

            if (useColors) {
                sb.append(lineNumberStr);
                sb.append(renderLineWithColors(lineContent, rangesOnLine, severity)).append(System.lineSeparator());
            } else {
                sb.append(lineNumberStr);
                sb.append(lineContent).append(System.lineSeparator());
                if (!rangesOnLine.isEmpty()) {
                    String underline = renderUnderline(lineContent, rangesOnLine);
                    if (!underline.isEmpty()) {
                        String gutter = String.format("%5s", "") + "| ";
                        sb.append(gutter).append(underline).append(System.lineSeparator());
                    }
                }
            }
        }

        return sb.toString();
    }

    public static String renderUnderline(String line, List<FileGeometry.ColumnRange> ranges) {
        if (ranges.isEmpty()) {
            return "";
        }
        int maxEnd = 0;
        for (FileGeometry.ColumnRange range : ranges) {
            maxEnd = Math.max(maxEnd, Math.min(range.last(), line.length()));
        }
        StringBuilder underline = new StringBuilder();
        for (int i = 0; i < maxEnd; i++) {
            boolean highlighted = false;
            for (FileGeometry.ColumnRange range : ranges) {
                if (i >= range.first() && i < range.last()) {
                    highlighted = true;
                    break;
                }
            }
            if (highlighted) {
                underline.append('^');
            } else {
                char c = i < line.length() ? line.charAt(i) : ' ';
                underline.append(c == '\t' ? '\t' : ' ');
            }
        }
        return underline.toString();
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
    private static String renderLineWithColors(String line, List<FileGeometry.ColumnRange> ranges, Severity severity) {
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
            int safeEnd = Math.min(range.last(), line.length());
            if (range.first() < safeEnd) {
                String textToHighlight = line.substring(range.first(), safeEnd);
                if (severity == Severity.WARNING) {
                    sb.append(ansiBoldYellow(textToHighlight));
                } else if (severity == Severity.INFORMATION) {
                    sb.append(ansiBoldCyan(textToHighlight));
                } else {
                    sb.append(ansiBoldRed(textToHighlight));
                }
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

    public static String ansiYellow(String text) {
        return "\u001B[33m" + text + "\u001B[0m";
    }

    public static String ansiBoldYellow(String text) {
        return "\u001B[1;33m" + text + "\u001B[0m";
    }

    public static String ansiCyan(String text) {
        return "\u001B[36m" + text + "\u001B[0m";
    }

    public static String ansiBoldCyan(String text) {
        return "\u001B[1;36m" + text + "\u001B[0m";
    }
}

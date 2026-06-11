package burp;

import burp.parse.turtleprov.Point;
import burp.reporting.FileGeometry;
import burp.reporting.PointRange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileGeometryTest {

    @Test
    public void testUserReproductionCaseWithMergedRanges() {
        List<String> lines = Arrays.asList(
            "@prefix ex: <http://example.com/> .",
            "@prefix rml: <http://w3id.org/rml/> .",
            "<http://example.com/base/TriplesMap1> a rml:TriplesMap;",
            "",
            "rml:logicalSource [ a rml:LogicalSource;",
            "   rml:iterator \"$.students[*]\";",
            "   rml:referenceFormulation rml:JSONPath;",
            "   rml:source [ a rml:RelativePathSource;",
            "       rml:root rml:MappingDirectory;",
            "       rml:path \"student2.json\" ",
            "   ]",
            "] ."
        );

        List<PointRange> nodes = Arrays.asList(
            new PointRange(new Point(5, 18), new Point(5, 20)),
            new PointRange(new Point(5, 21), new Point(5, 31)),
            new PointRange(new Point(9, 7), new Point(9, 14)),
            new PointRange(new Point(9, 17), new Point(9, 29))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);

        assertTrue(result.containsKey(5));
        assertTrue(result.containsKey(9));

        List<FileGeometry.ColumnRange> line6 = result.get(5);
        assertEquals(1, line6.size());
        assertEquals(new FileGeometry.ColumnRange(18, 31), line6.get(0));

        List<FileGeometry.ColumnRange> line10 = result.get(9);
        assertEquals(2, line10.size());
        assertEquals(new FileGeometry.ColumnRange(7, 14), line10.get(0));
        assertEquals(new FileGeometry.ColumnRange(17, 29), line10.get(1));
    }

    @Test
    public void testOverlappingRangesMerge() {
        List<String> lines = Collections.singletonList("0123456789");
        // Overlapping: 0..4 and 2..6 -> 0..6
        List<PointRange> nodes = Arrays.asList(
            new PointRange(new Point(0, 0), new Point(0, 4)),
            new PointRange(new Point(0, 2), new Point(0, 6))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);
        List<FileGeometry.ColumnRange> ranges = result.get(0);

        assertEquals(1, ranges.size());
        assertEquals(new FileGeometry.ColumnRange(0, 6), ranges.get(0));
    }

    @Test
    public void testAdjacentRangesMerge() {
        List<String> lines = Collections.singletonList("0123456789");
        // Adjacent: 0..4 and 5..9 -> 0..9
        List<PointRange> nodes = Arrays.asList(
            new PointRange(new Point(0, 0), new Point(0, 4)),
            new PointRange(new Point(0, 5), new Point(0, 9))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);
        List<FileGeometry.ColumnRange> ranges = result.get(0);

        assertEquals(1, ranges.size());
        assertEquals(new FileGeometry.ColumnRange(0, 9), ranges.get(0));
    }

    @Test
    public void testNonAdjacentRangesDoNotMerge() {
        List<String> lines = Collections.singletonList("0123456789");
        // Gap: 0..3 and 5..8 (Gap at 4) -> 0..3, 5..8
        List<PointRange> nodes = Arrays.asList(
            new PointRange(new Point(0, 0), new Point(0, 3)),
            new PointRange(new Point(0, 5), new Point(0, 8))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);
        List<FileGeometry.ColumnRange> ranges = result.get(0);

        assertEquals(2, ranges.size());
        assertEquals(new FileGeometry.ColumnRange(0, 3), ranges.get(0));
        assertEquals(new FileGeometry.ColumnRange(5, 8), ranges.get(1));
    }

    @Test
    public void testMultiLineNode() {
        List<String> lines = Arrays.asList("line1", "line2", "line3");
        // Node spans from line 1 col 2 to line 3 col 2
        // Indices: 0, 1, 2
        // Line 0: 2..end (len 5 -> 2..5)
        // Line 1: 0..end (len 5 -> 0..5)
        // Line 2: 0..2

        List<PointRange> nodes = Collections.singletonList(
            new PointRange(new Point(0, 2), new Point(2, 2))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);

        assertEquals(3, result.size());
        assertEquals(Collections.singletonList(new FileGeometry.ColumnRange(2, 5)), result.get(0));
        assertEquals(Collections.singletonList(new FileGeometry.ColumnRange(0, 5)), result.get(1));
        assertEquals(Collections.singletonList(new FileGeometry.ColumnRange(0, 2)), result.get(2));
    }

    @Test
    public void testOutOfBoundsClamping() {
        List<String> lines = Collections.singletonList("abc");
        // Node requests col 10..20 on line 1
        List<PointRange> nodes = Collections.singletonList(
            new PointRange(new Point(0, 10), new Point(0, 20))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);
        List<FileGeometry.ColumnRange> ranges = result.get(0);
        assertEquals(Collections.singletonList(new FileGeometry.ColumnRange(3, 3)), ranges);
    }

    @Test
    public void testInvalidLineNumberIgnored() {
        List<String> lines = Collections.singletonList("abc");
        // Node on line 100
        List<PointRange> nodes = Collections.singletonList(
            new PointRange(new Point(100, 0), new Point(100, 5))
        );

        Map<Integer, List<FileGeometry.ColumnRange>> result = FileGeometry.getMergedHighlights(nodes, lines);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testMergeRangesLogicExplicitly() {
        // Direct test of mergeRanges helper
        List<FileGeometry.ColumnRange> ranges = Arrays.asList(
            new FileGeometry.ColumnRange(0, 5),
            new FileGeometry.ColumnRange(4, 8),
            new FileGeometry.ColumnRange(10, 12)
        );
        List<FileGeometry.ColumnRange> merged = FileGeometry.mergeRanges(ranges);

        // 0..5 and 4..8 overlap -> 0..8
        // 10..12 is separate (gap 9)
        // Result: 0..8, 10..12

        assertEquals(2, merged.size());
        assertEquals(new FileGeometry.ColumnRange(0, 8), merged.get(0));
        assertEquals(new FileGeometry.ColumnRange(10, 12), merged.get(1));
    }

    @Test
    public void testMergeRangesEmpty() {
        List<FileGeometry.ColumnRange> merged = FileGeometry.mergeRanges(Collections.emptyList());
        assertTrue(merged.isEmpty());
    }
}

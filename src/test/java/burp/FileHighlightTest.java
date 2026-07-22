package burp;

import burp.parse.turtleprov.Point;
import burp.reporting.FileHighlight;
import burp.reporting.PointRange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class FileHighlightTest {

    @BeforeAll
    public static void setup() {
        System.setProperty("picocli.ansi", "true");
    }

    @AfterAll
    public static void teardown() {
        System.clearProperty("picocli.ansi");
    }

    @TempDir
    Path tempDir;

    @Test
    public void testExtractAndHighlightBasicUsage() throws IOException {
        Path file = tempDir.resolve("test.ttl");
        List<String> lines = Arrays.asList(
            "@prefix ex: <http://example.com/> .",
            "foo bar baz",
            "qux quux"
        );
        Files.write(file, lines);

        // Highlight "bar" on line 2 (indices 4..6)
        List<PointRange> nodes = Collections.singletonList(
            new PointRange(new Point(1, 4), new Point(1, 6))
        );

        String result = FileHighlight.extractAndHighlight(file, nodes, 0);

        System.out.println("Basic Result:\n" + result);

        assertNotNull(result);
        assertTrue(result.contains("   2 | "), "Should contain formatted line number '   2 | '");
        assertTrue(result.contains("bar"), "Should contain highlighted text 'bar'");
    }

    @Test
    public void testExtractAndHighlightWithContext() throws IOException {
        Path file = tempDir.resolve("context.ttl");
        List<String> lines = Arrays.asList(
            "line 1",
            "line 2 target",
            "line 3"
        );
        Files.write(file, lines);

        // Highlight "target" on line 2 (cols 7..12)
        List<PointRange> nodes = Collections.singletonList(
            new PointRange(new Point(1, 7), new Point(1, 12))
        );

        String result = FileHighlight.extractAndHighlight(file, nodes, 1);

        assertNotNull(result);
        // Check context lines and target line
        assertTrue(result.contains("   1 | line 1"));
        assertTrue(result.contains("   2 | line 2 "));
        assertTrue(result.contains("target"));
        assertTrue(result.contains("   3 | line 3"));
    }

    @Test
    public void testFileNotFound() {
        Path file = tempDir.resolve("nonexistent.ttl");
        List<PointRange> nodes = Collections.singletonList(new PointRange(new Point(1, 0), new Point(1, 5)));

        String result = FileHighlight.extractAndHighlight(file, nodes);
        assertNull(result);
    }

    @Test
    public void testEmptyNodes() throws IOException {
        Path file = tempDir.resolve("empty.ttl");
        Files.write(file, Collections.singletonList("abc"));

        String result = FileHighlight.extractAndHighlight(file, Collections.emptyList());
        assertNull(result);
    }

    @Test
    public void testEllipsisInsertion() throws IOException {
        Path file = tempDir.resolve("ellipsis.ttl");
        List<String> lines = IntStream.rangeClosed(1, 10).mapToObj(i -> "line " + i).collect(Collectors.toList());
        Files.write(file, lines);

        // Highlight line 2 and line 9. Context 0.
        // Should print line 2, then ellipsis, then line 9.
        List<PointRange> nodes = Arrays.asList(
            new PointRange(new Point(1, 0), new Point(1, 5)),
            new PointRange(new Point(8, 0), new Point(8, 5))
        );

        String result = FileHighlight.extractAndHighlight(file, nodes, 0);

        assertNotNull(result);

        assertTrue(result.contains("   2 | "), "Should contain line 2");
        assertTrue(result.contains("   ..."), "Should contain ellipsis");
        assertTrue(result.contains("   9 | "), "Should contain line 9");
    }
}

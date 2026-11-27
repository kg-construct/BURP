package turtleprov;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class Rdf11ManifestTest extends AbstractManifestTest {
    private static final Set<String> EXCLUDED_TESTS = Set.of(
            "turtle-syntax-bad-uri-escape-01",
            "turtle-syntax-bad-uri-escape-02",
            "turtle-syntax-bad-uri-escape-03",
            "turtle-syntax-bad-numeric-escape-01",
            "turtle-syntax-bad-numeric-escape-02",
            "turtle-syntax-bad-numeric-escape-03",
            "turtle-syntax-bad-numeric-escape-04",
            "turtle-syntax-bad-numeric-escape-05",
            "turtle-syntax-bad-numeric-escape-06",
            "turtle-syntax-bad-numeric-escape-07",
            "turtle-syntax-bad-numeric-escape-08",
            "turtle-syntax-bad-numeric-escape-09",
            "turtle-syntax-bad-numeric-escape-10"
    );

    @Override
    public Path rootManifestFile() {
        return Paths.get("src/test/resources/rdf-tests/rdf/rdf11/rdf-turtle/manifest.ttl");
    }

    @Override
    public boolean filterRdf11() {
        return false;
    }

    @Override
    protected boolean isExcluded(TestInfo info) {
        return EXCLUDED_TESTS.contains(info.name);
    }
}

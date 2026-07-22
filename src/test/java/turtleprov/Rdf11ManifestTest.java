package turtleprov;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Rdf11ManifestTest extends AbstractManifestTest {
    @Override
    public Path rootManifestFile() {
        return Paths.get("src/test/resources/rdf-tests/rdf/rdf11/rdf-turtle/manifest.ttl");
    }

    @Override
    public boolean filterRdf11() {
        return false;
    }
}

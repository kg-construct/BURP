package turtleprov;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Rdf12ManifestTest extends AbstractManifestTest {
    @Override
    public Path rootManifestFile() {
        return Paths.get("src/test/resources/rdf-tests/rdf/rdf12/rdf-turtle/manifest.ttl");
    }

    @Override
    public boolean filterRdf11() {
        return true;
    }
}

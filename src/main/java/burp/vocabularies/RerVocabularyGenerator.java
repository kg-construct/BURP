package burp.vocabularies;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;

/// Generates {@link RER} from {@code src/main/resources/vocabularies/rer.ttl}, including
/// compile-time superclass and severity lookup tables.
@NullMarked
public final class RerVocabularyGenerator extends VocabularyGenerator {

    public static void main(String[] args) throws Exception {
        new RerVocabularyGenerator().generate(Path.of("").toAbsolutePath());
    }

    @Override
    protected String namespace() {
        return "https://w3id.org/dre/rer#";
    }

    @Override
    protected String className() {
        return "RER";
    }

    @Override
    protected Path outputFile(Path baseDir) {
        return baseDir.resolve("src/main/java/burp/vocabularies/RER.java");
    }

    @Override
    protected List<Path> ontologyFiles(Path baseDir) {
        return List.of(baseDir.resolve("src/main/resources/vocabularies/rer.ttl"));
    }

    @Override
    protected List<Path> importedFiles(Path baseDir) {
        return List.of(baseDir.resolve("src/main/resources/vocabularies/ptr.ttl"));
    }

    @Override
    protected String modelResourcePath() {
        return "vocabularies/rer.ttl";
    }

    @Override
    protected boolean includeTaxonomyMaps() {
        return true;
    }

    @Override
    protected String classJavadoc() {
        return "Vocabulary definitions from src/main/resources/vocabularies/rer.ttl.";
    }
}

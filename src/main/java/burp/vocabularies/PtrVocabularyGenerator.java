package burp.vocabularies;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;

/// Generates {@link PTR} from {@code src/main/resources/vocabularies/ptr.ttl}.
@NullMarked
public final class PtrVocabularyGenerator extends VocabularyGenerator {

    public static void main(String[] args) throws Exception {
        new PtrVocabularyGenerator().generate(Path.of("").toAbsolutePath());
    }

    @Override
    protected String namespace() {
        return "https://w3id.org/dre/ptr#";
    }

    @Override
    protected String className() {
        return "PTR";
    }

    @Override
    protected Path outputFile(Path baseDir) {
        return baseDir.resolve("src/main/java/burp/vocabularies/PTR.java");
    }

    @Override
    protected List<Path> ontologyFiles(Path baseDir) {
        return List.of(baseDir.resolve("src/main/resources/vocabularies/ptr.ttl"));
    }

    @Override
    protected String classJavadoc() {
        return "Vocabulary definitions from src/main/resources/vocabularies/ptr.ttl.";
    }
}

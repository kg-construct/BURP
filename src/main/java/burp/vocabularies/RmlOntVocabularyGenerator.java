package burp.vocabularies;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

/// Generates {@link RML} containing Jena OntAPI {@code OntModel} constants for RML vocabularies.
@NullMarked
public final class RmlOntVocabularyGenerator extends RmlGenerator {

    public static void main(String[] args) throws Exception {
        new RmlOntVocabularyGenerator().generate(Path.of("").toAbsolutePath());
    }

    @Override
    protected String className() {
        return "RML";
    }

    @Override
    protected Path outputFile(Path baseDir) {
        return baseDir.resolve("src/main/java/burp/vocabularies/RML.java");
    }

    @Override
    protected String classJavadoc() {
        return "Jena OntAPI vocabulary definitions for RML.";
    }
}

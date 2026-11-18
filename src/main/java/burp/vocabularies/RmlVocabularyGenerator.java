package burp.vocabularies;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

/// Generates {@link Rml} containing compile-time {@code String} constants for RML vocabularies.
@NullMarked
public final class RmlVocabularyGenerator extends RmlGenerator {

    public static void main(String[] args) throws Exception {
        new RmlVocabularyGenerator().generate(Path.of("").toAbsolutePath());
    }

    @Override
    protected String className() {
        return "Rml";
    }

    @Override
    protected Path outputFile(Path baseDir) {
        return baseDir.resolve("src/main/java/burp/vocabularies/Rml.java");
    }

    @Override
    protected boolean isStringConstantsOnly() {
        return true;
    }

    @Override
    protected String classJavadoc() {
        return "Compile-time String constants for RML vocabularies (used in annotations and String-based lookups).";
    }
}

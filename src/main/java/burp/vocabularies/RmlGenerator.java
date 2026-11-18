package burp.vocabularies;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;

/// Shared base configuration for RML vocabulary generators ({@link Rml} and {@link RML}).
@NullMarked
abstract class RmlGenerator extends VocabularyGenerator {

    @Override
    protected final String namespace() {
        return "http://w3id.org/rml/";
    }

    @Override
    protected final List<Path> ontologyFiles(Path baseDir) {
        return List.of(
                baseDir.resolve("rml-modules/rml-core/ontology/rml-core.owl"),
                baseDir.resolve("rml-modules/rml-cc/ontology/rml-cc.owl"),
                baseDir.resolve("rml-modules/rml-io/ontology/rml-io.owl"),
                baseDir.resolve("rml-modules/rml-fnml/ontology/rml-fnml.owl"),
                baseDir.resolve("rml-modules/rml-lv/ontology/rml-lv.owl")
        );
    }

    @Override
    protected final List<String> extraKnownProperties() {
        return List.of(
                // RML-CC
                "gatherMap",
                // RML-FNML
                "condition"
        );
    }

    @Override
    protected final List<String> extraKnownResources() {
        return List.of(
                // RML-Core (defaultGraph is referenced in rml-core.owl but not typed as owl:NamedIndividual)
                "defaultGraph", "JoinCondition",
                // RML-CC
                "list",
                // RML-IO
                "RelativePathSource", "noEmpty",
                // RML-FNML
                "InputValueMap"
        );
    }
}

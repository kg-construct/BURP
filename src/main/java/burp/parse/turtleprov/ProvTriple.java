package burp.parse.turtleprov;

import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ProvTriple(
        Statement statement,
        NodeInfo subjectInfo,
        NodeInfo predicateInfo,
        NodeInfo objectInfo,
        @Nullable String documentUri) {

    @Override
    public @NonNull String toString() {
        return "ProvTriple[statement=" + statement + ",\n" +
                "\tsubjectInfo=" + subjectInfo + ",\n" +
                "\tpredicateInfo=" + predicateInfo + ",\n" +
                "\tobjectInfo=" + objectInfo + ",\n" +
                "\tdocumentUri=" + documentUri + "]";
    }
}

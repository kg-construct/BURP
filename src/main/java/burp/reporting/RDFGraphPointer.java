package burp.reporting;

import burp.parse.turtleprov.RDF12Converter;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public interface RDFGraphPointer extends RDFPointer {
    Statement stmt();

    @Override
    default @Nullable Path file() {
        if (stmt() != null && stmt().getModel() != null) {
            RDF12Converter.TripleInfo info = RDF12Converter.fromAnnotations(stmt(), stmt().getModel());
            return info.sourcePath();
        }
        return null;
    }
}

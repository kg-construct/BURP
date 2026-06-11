package burp.reporting;

import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NonNull;

public record LiteralPart(@NonNull Statement stmt, @NonNull PointRange objectRange) implements RDFGraphPointer {
    public LiteralPart {
        if (!stmt.getObject().isLiteral()) {
            throw new IllegalArgumentException("Statement object is not a literal: " + stmt);
        }
    }
}

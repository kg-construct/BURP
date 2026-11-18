package burp.reporting;

import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@NullMarked
public record Origin(@Nullable List<? extends RDFPointer> sourceStatements) {

    public Origin() {
        this(Collections.emptyList());
    }

    public Origin(Statement stmt, StatementPart... stmtParts) {
        this(Collections.singletonList(StatementParts.from(stmt, stmtParts)));
    }

    public @Nullable Path file() {
        if (sourceStatements != null) {
            for (RDFPointer ptr : sourceStatements) {
                if (ptr.file() != null) {
                    return ptr.file();
                }
            }
        }
        return null;
    }

    public List<PointRange> ranges() {
        if (sourceStatements == null || sourceStatements.isEmpty()) {
            return Collections.emptyList();
        }
        return burp.parse.turtleprov.ProvTurtleVisitor.retrieveTurtleLocation(sourceStatements);
    }
}

package burp.reporting;

import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record StatementParts(@NonNull Statement stmt,
                             boolean subject,
                             boolean predicate,
                             boolean object) implements RDFGraphPointer {

    public static StatementParts from(Statement stmt, StatementPart... parts) {
        boolean subj = false;
        boolean pred = false;
        boolean obj = false;
        for (StatementPart part : parts) {
            if (part == StatementPart.Subject) subj = true;
            if (part == StatementPart.Predicate) pred = true;
            if (part == StatementPart.Object) obj = true;
        }
        return new StatementParts(stmt, subj, pred, obj);
    }

    public static StatementParts fromPredicateObject(Statement stmt) {
        return new StatementParts(stmt, false, true, true);
    }

    public @Nullable LiteralPart toLiteralPart() {
        if (object() && stmt.getObject().isLiteral())
            return new LiteralPart(stmt, new PointRange());
        else return null;
    }
}

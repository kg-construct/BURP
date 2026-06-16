package burp.reporting;

import org.apache.jena.rdf.model.Statement;

public class StatementParts implements RDFGraphPointer {
    private final Statement stmt;
    private final boolean subject;
    private final boolean predicate;
    private final boolean object;

    public StatementParts(Statement stmt, boolean subject, boolean predicate, boolean object) {
        this.stmt = stmt;
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
    }

    @Override
    public Statement getStmt() {
        return stmt;
    }

    public boolean isSubject() {
        return subject;
    }

    public boolean isPredicate() {
        return predicate;
    }

    public boolean isObject() {
        return object;
    }

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
}

package burp.reporting;

import org.apache.jena.rdf.model.Statement;

public class LiteralPart implements RDFGraphPointer {
    private final Statement stmt;
    private final PointRange objectRange;

    public LiteralPart(Statement stmt, PointRange objectRange) {
        if (!stmt.getObject().isLiteral()) {
            throw new IllegalArgumentException("Statement object is not a literal: " + stmt);
        }
        this.stmt = stmt;
        this.objectRange = objectRange;
    }

    @Override
    public Statement getStmt() {
        return stmt;
    }

    public PointRange getObjectRange() {
        return objectRange;
    }
}

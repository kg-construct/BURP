package burp.reporting;

import burp.model.PlanNode;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public record Origin(@Nullable PlanNode planNode, @Nullable List<? extends RDFPointer> sourceStatements) {

    public Origin() {
        this((PlanNode) null, null);
    }

    public Origin(Statement stmt, StatementPart... stmtParts) {
        this(null, Collections.singletonList(StatementParts.from(stmt, stmtParts)));
    }

    public Origin(PlanNode planNode, Statement stmt, StatementPart... stmtParts) {
        this(planNode, Collections.singletonList(StatementParts.from(stmt, stmtParts)));
    }
}

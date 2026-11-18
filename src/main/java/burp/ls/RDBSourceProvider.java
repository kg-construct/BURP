package burp.ls;

import burp.reporting.Origin;
import burp.reporting.RDFGraphPointer;
import burp.reporting.StatementPart;
import burp.reporting.StatementParts;
import burp.vocabularies.D2RQ;
import burp.vocabularies.RML;
import com.google.auto.service.AutoService;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import java.nio.file.Path;
import java.util.List;

@AutoService(LogicalSourceProvider.class)
public class RDBSourceProvider implements LogicalSourceProvider {
    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.SQL2008Query.equals(referenceFormulation);
    }

    @Override
    public RDBSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        RDBSource source = new RDBSource();
        source.referenceFormulation = RML.SQL2008Query;

        Resource sourceNode = ls.getPropertyResourceValue(RML.source);
        Statement jdbcDSNStmt = sourceNode.getProperty(D2RQ.jdbcDSN);
        if (jdbcDSNStmt == null) {
            throw new IllegalArgumentException("RDB source must have a d2rq:jdbcDSN property.");
        }
        source.jdbcDSN = jdbcDSNStmt.getLiteral().getString();

        Statement jdbcDriverStmt = sourceNode.getProperty(D2RQ.jdbcDriver);
        source.jdbcDriver = jdbcDriverStmt != null ? jdbcDriverStmt.getLiteral().getString() : null;

        Statement usernameStmt = sourceNode.getProperty(D2RQ.username);
        source.username = usernameStmt != null ? usernameStmt.getLiteral().getString() : null;

        Statement passwordStmt = sourceNode.getProperty(D2RQ.password);
        source.password = passwordStmt != null ? passwordStmt.getLiteral().getString() : null;

        Statement queryStmt = ls.getProperty(RML.iterator);
        source.query = queryStmt.getLiteral().getString(); // TODO: Investigate we were previously replacing "\\" by "" but the reason was lost.
        source.putOrigin("query", new Origin(List.of(StatementParts.from(queryStmt, StatementPart.Object))));

        source.getNulls().addAll(FileBaseSourceProvider.getNullValues(ls));
        source.mappingDirectory = mappingDirectory;
        source.currentWorkingDirectory = currentWorkingDirectory;

        return source;
    }
}

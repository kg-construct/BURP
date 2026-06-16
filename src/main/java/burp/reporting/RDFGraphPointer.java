package burp.reporting;

import org.apache.jena.rdf.model.Statement;

public interface RDFGraphPointer extends RDFPointer {
    Statement getStmt();
}

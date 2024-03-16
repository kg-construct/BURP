package burp.model;

import org.apache.jena.rdf.model.RDFNode;

public class RDFNodeConstant extends Expression {
	
	public RDFNode constant = null;
	
	public RDFNodeConstant(RDFNode constant) {
		this.constant = constant;
	}
	
}
package burp.model;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

import burp.vocabularies.RML;

public class GraphMap extends TermMap {
	
	public GraphMap() {
		termType = RML.IRI;
	}

	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(RML.IRI.equals(termType))
			return generateIRIs(i, baseIRI);
		if(RML.BLANKNODE.equals(termType))
			return generateBlankNodes(i, baseIRI);
				
		throw new RuntimeException("Incorrect term type for graph map.");
	}
	
	@Override
	public boolean isGatherMap() {
		return false;
	}
	
}
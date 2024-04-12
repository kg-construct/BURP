package burp.model;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

import burp.vocabularies.RML;

public class FunctionMap extends TermMap {

	public FunctionMap() {
		termType = RML.IRI;
	}
	
	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI) {
			return generateIRIs(i, baseIRI);			
		}
		
		throw new RuntimeException("Incorrect term type for function map.");
	}

	@Override
	public boolean isGatherMap() {
		return false;
	}
	
}
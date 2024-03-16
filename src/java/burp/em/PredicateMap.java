package burp.em;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

import burp.iteration.Iteration;
import burp.vocabularies.RML;

public class PredicateMap extends TermMap {

	public PredicateMap() {
		termType = RML.IRI;
	}
	
	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI) {
			return generateIRIs(i, baseIRI);			
		}
		
		throw new RuntimeException("Incorrect term type for predicate map.");	
	}

	@Override
	public boolean isGatherMap() {
		return false;
	}
	
}
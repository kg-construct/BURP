package burp.model;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

import burp.vocabularies.RML;

public class InputValueMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;
	
	public InputValueMap() {
		termType = RML.LITERAL;
	}
	
	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
        if(RML.IRI.equals(termType))
            return generateIRIs(i, baseIRI);
        if(RML.URI.equals(termType))
            return generateURIs(i, baseIRI);
        if(RML.BLANKNODE.equals(termType))
			return generateBlankNodes(i, baseIRI);
		if(RML.LITERAL.equals(termType))
			return generateLiterals(i, baseIRI, datatypeMap, languageMap);
					
		throw new RuntimeException("Incorrect term type for input value map.");
	}

	@Override
	public boolean isGatherMap() {
		return false;
	}
	
}
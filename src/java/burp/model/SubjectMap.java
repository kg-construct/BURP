package burp.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import burp.vocabularies.RML;

public class SubjectMap extends TermMap {

	public List<Resource> classes = new ArrayList<Resource>();
	public List<GraphMap> graphMaps = new ArrayList<GraphMap>();
	
	public SubjectMap() {
		termType = RML.IRI;
	}

	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(RML.IRI.equals(termType))
			return generateIRIs(i, baseIRI);
		if(RML.BLANKNODE.equals(termType))
			return generateBlankNodes(i);
		
		throw new RuntimeException("Incorrect term type for subject map.");
	}

	@Override
	public boolean isGatherMap() {
		return gatherMap != null;
	}

}
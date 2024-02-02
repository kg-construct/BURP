package brml;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public abstract class ExpressionMap {

	public Expression expression = null;
		
}

abstract class TermMap extends ExpressionMap {

	public Resource termType;

	public abstract Set<RDFNode> generateTerms(Iteration i, String baseIRI);
	
	protected Set<RDFNode> generateIRIs(Iteration i, String baseIRI) {
		Set<RDFNode> set = new HashSet<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			set.add(((RDFNodeConstant) expression).constant.asResource());
			return set;
		}
		
		if(expression instanceof Template) {
			Set<String> values = ((Template) expression).values(i, true);
			
			for(String v : values) {
				if(isAbsoluteAndValidIRI(v))
					set.add(ResourceFactory.createResource(v));
				else if(isAbsoluteAndValidIRI(baseIRI + v.toString()))
					set.add(ResourceFactory.createResource(v));
				else
					throw new RuntimeException(baseIRI + " and " + v + " do not constitute a valid IRI");
				
			}
			
			return set;
		}
		
		throw new RuntimeException("Error generating IRI in term map");
	}

	protected Set<RDFNode> generateBlankNodes(Iteration i) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private boolean isAbsoluteAndValidIRI(String string) {
		IRI iri = IRIFactory.iriImplementation().create(string.toString());
		return iri.isAbsolute() && !iri.hasViolation(true);
	}
	
}

class SubjectMap extends TermMap {

	public Set<Resource> classes = new HashSet<Resource>();
	public Set<GraphMap> graphMaps = new HashSet<GraphMap>();
	
	public SubjectMap() {
		termType = RML.IRI;
	}

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI)
			return generateIRIs(i, baseIRI);
		if(termType == RML.BLANKNODE)
			return generateBlankNodes(i);
		
		throw new RuntimeException("Incorrect term type for subject map.");
	}

}

class ObjectMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;
	
	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		throw new RuntimeException("Incorrect term type for object map.");
	}

}

class PredicateMap extends TermMap {

	public PredicateMap() {
		termType = RML.IRI;
	}
	
	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI)
			return generateIRIs(i, baseIRI);
		
		throw new RuntimeException("Incorrect term type for predicate map.");	
	}
	
}

class GraphMap extends TermMap {
	
	public GraphMap() {
		termType = RML.IRI;
	}

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI)
			return generateIRIs(i, baseIRI);
		if(termType == RML.BLANKNODE)
			return generateBlankNodes(i);
		
		throw new RuntimeException("Incorrect term type for graph map.");
	}
	
}

class LanguageMap extends ExpressionMap {

}

class DatatypeMap extends ExpressionMap {

}
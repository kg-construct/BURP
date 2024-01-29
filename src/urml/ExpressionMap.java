package urml;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;


public abstract class ExpressionMap {

	public Expression expression = null;
		
}

abstract class TermMap extends ExpressionMap {

	public Resource termType;

	public abstract Set<RDFNode> generateTerms(Iteration i, String baseIRI) throws Exception;
	
}

class SubjectMap extends TermMap {

	public Set<Resource> classes = new HashSet<Resource>();
	public Set<GraphMap> graphMaps = new HashSet<GraphMap>();
	
	public SubjectMap( ) {
		termType = RML.IRI;
	}

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) throws Exception {
		Set<RDFNode> set = new HashSet();
		
		if(termType == RML.IRI) {
			if(expression instanceof TermConstant) {
				if(((TermConstant) expression).constant.isURIResource())
					return (Set<RDFNode>)(Set<?>) expression.values(i);
			}
		}
		
		throw new Exception("Incorrect term type for subject map.");
	}

}

class ObjectMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;
	
	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) throws Exception {

		
		throw new Exception("Incorrect term type for object map.");
	}

}

class PredicateMap extends TermMap {

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) throws Exception {

		
		throw new Exception("Incorrect term type for predicate map.");
	}
	
}

class GraphMap extends TermMap {

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) throws Exception {

		
		throw new Exception("Incorrect term type for graph map.");
	}
	
}

class LanguageMap extends ExpressionMap {

}

class DatatypeMap extends ExpressionMap {

}
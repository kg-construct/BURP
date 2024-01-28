package urml;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Resource;

public abstract class ExpressionMap {

	public Expression expression = null;
		
}

abstract class TermMap extends ExpressionMap {
		
}

class SubjectMap extends TermMap {

	public Set<Resource> classes = new HashSet<Resource>();
	public Set<GraphMap> graphMaps = new HashSet<GraphMap>();
	
	public Resource termType = RML.IRI;
}

class ObjectMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;

}

class PredicateMap extends TermMap {


}

class GraphMap extends TermMap {

}

class LanguageMap extends ExpressionMap {

}

class DatatypeMap extends ExpressionMap {

}
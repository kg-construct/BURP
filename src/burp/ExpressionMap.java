package burp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public abstract class ExpressionMap {

	public Expression expression = null;
	
	protected Set<RDFNode> generateIRIs(Iteration i, String baseIRI) {
		Set<RDFNode> set = new HashSet<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be an IRI, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant);
			return set;
		}
		
		if(expression instanceof Template) {
			for(String v : ((Template) expression).values(i, true)) {
				
				if(isAbsoluteAndValidIRI(v))
					set.add(ResourceFactory.createResource(v));
				else if(isAbsoluteAndValidIRI(baseIRI + v.toString()))
					set.add(ResourceFactory.createResource(baseIRI + v.toString()));
				else
					throw new RuntimeException(baseIRI + " and " + v + " do not constitute a valid IRI");
				
			}
			return set;
		}
		
		if(expression instanceof Reference) {
			for(Object v : ((Reference) expression).values(i)) {
				String s = v.toString();
				
				if(isAbsoluteAndValidIRI(s))
					set.add(ResourceFactory.createResource(s));
				else if(isAbsoluteAndValidIRI(baseIRI + s))
					set.add(ResourceFactory.createResource(baseIRI + s));
				else
					throw new RuntimeException(baseIRI + " and " + s + " do not constitute a valid IRI");
			}
			return set;
		}
		
		throw new RuntimeException("Error generating IRI.");
	}

	static private Map<Object, Resource> map = new HashMap<Object, Resource>();
	protected Set<RDFNode> generateBlankNodes(Iteration i) {
		Set<RDFNode> set = new HashSet<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a BN, otherwise the shapes
			// Would have caught the error.
			RDFNode n = ((RDFNodeConstant) expression).constant;
			set.add(map.computeIfAbsent(n, (x) -> ResourceFactory.createResource()));
			return set;
		}
		
		if(expression instanceof Template) {
			for(String v : ((Template) expression).values(i)) {
				set.add(map.computeIfAbsent(v, (x) -> ResourceFactory.createResource()));
			}
			return set;
		}
		
		if(expression instanceof Reference) {
			for(Object v : ((Reference) expression).values(i)) {
				set.add(map.computeIfAbsent(v, (x) -> ResourceFactory.createResource()));
			}
			return set;
		}
		
		throw new RuntimeException("Error generating blank node.");
	}
	
	protected Set<RDFNode> generateLiterals(Iteration i, String baseIRI, DatatypeMap dm, LanguageMap lm) {
		Set<RDFNode> set = new HashSet<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a literal, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant);
			return set;
		}
		
		Set<RDFNode> datatypes = dm != null ? dm.generateIRIs(i, baseIRI) : null;
		Set<String> languages = lm != null ? lm.generateStrings(i) : null;
		
		if(expression instanceof Template) {
			for(String v : ((Template) expression).values(i)) {
				if(languages != null) {
					for(String l : languages) {
						set.add(ResourceFactory.createLangLiteral(v, l));
					}
				} else if(datatypes != null) {
					for(RDFNode dt : datatypes) {
						String dturi = dt.asResource().getURI();
						set.add(ResourceFactory.createTypedLiteral(v, new BaseDatatype(dturi)));
					}
				} else {
					set.add(ResourceFactory.createTypedLiteral(v));
				}
			}
			return set;
		}
		
		if(expression instanceof Reference) {
			for(Object v : ((Reference) expression).values(i)) {
				if(languages != null) {
					for(String l : languages) {
						set.add(ResourceFactory.createLangLiteral(v.toString(), l));
					}
				} else if(datatypes != null) {
					for(RDFNode dt : datatypes) {
						String dturi = dt.asResource().getURI();
						set.add(ResourceFactory.createTypedLiteral(v.toString(), new BaseDatatype(dturi)));
					}
				} else {
					set.add(ResourceFactory.createTypedLiteral(v));
				}
			}
			return set;
		}
		
		throw new RuntimeException("Error generating literal or value.");
	}
	
	private boolean isAbsoluteAndValidIRI(String string) {
		IRI iri = IRIFactory.iriImplementation().create(string.toString());
		return iri.isAbsolute() && !iri.hasViolation(true);
	}
		
}

abstract class TermMap extends ExpressionMap {

	public Resource termType;

	public abstract Set<RDFNode> generateTerms(Iteration i, String baseIRI);
	
}

class SubjectMap extends TermMap {

	public Set<Resource> classes = new HashSet<Resource>();
	public Set<GraphMap> graphMaps = new HashSet<GraphMap>();
	
	public SubjectMap() {
		termType = RML.IRI;
	}

	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(RML.IRI.equals(termType))
			return generateIRIs(i, baseIRI);
		if(RML.BLANKNODE.equals(termType))
			return generateBlankNodes(i);
		
		throw new RuntimeException("Incorrect term type for subject map.");
	}

}

class ObjectMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;
	
	public ObjectMap() {
		termType = RML.IRI;
	}
	
	@Override
	public Set<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(RML.IRI.equals(termType))
			return generateIRIs(i, baseIRI);
		if(RML.BLANKNODE.equals(termType))
			return generateBlankNodes(i);
		if(RML.LITERAL.equals(termType))
			return generateLiterals(i, baseIRI, datatypeMap, languageMap);
					
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

	public Set<String> generateStrings(Iteration i) {
		Set<String> set = new HashSet<String>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a string, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant.toString());
		}
		else if(expression instanceof Template) {
			set.addAll(((Template) expression).values(i));
		}
		else if(expression instanceof Reference) {
			for(Object o : ((Reference) expression).values(i))
				set.add(o.toString());
		}
		
		set.forEach((l) -> {
			if(!isValidLanguageCode(l))
				throw new RuntimeException("Invalid language code: " + l);
		});
		
		return set;
	}

	// Source of REGEX based on, but reduced: https://www.regextester.com/103066
	private static Pattern p = Pattern.compile("^(((?:([A-Za-z]{2,3}(-(?:[A-Za-z]{3}(-[A-Za-z]{3}){0,2}))?))(-(?:[A-Za-z]{4}))?(-(?:[A-Za-z]{2}|[0-9]{3}))?(-(?:[A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3}))*(-(?:[0-9A-WY-Za-wy-z](-[A-Za-z0-9]{2,8})+))*(-(?:x(-[A-Za-z0-9]{1,8})+))?)|(?:x(-[A-Za-z0-9]{1,8})+))$");
	private boolean isValidLanguageCode(String lang) {
		return p.matcher(lang).find();
	}

}

class DatatypeMap extends ExpressionMap {

}

class ConcreteExpressionMap extends ExpressionMap {

	public List<String> generateValues(Iteration i) {
		List<String> l = new ArrayList<String>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a string, otherwise the shapes
			// Would have caught the error.
			l.add(((RDFNodeConstant) expression).constant.toString());
			return l;
		}
		
		if(expression instanceof Template) {
			l.addAll(((Template) expression).values(i));
			return l;
		}
		
		if(expression instanceof Reference) {
			List<Object> values = ((Reference) expression).values(i);
			for(Object o : values)
				l.add(o.toString());
			return l;
		}
		
		throw new RuntimeException("Error generating language string.");
	}

}
package burp;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public abstract class ExpressionMap {

	public Expression expression = null;
	
	protected List<RDFNode> generateIRIs(Iteration i, String baseIRI) {
		List<RDFNode> set = new ArrayList<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be an IRI, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant.asResource());
			return set;
		}
		
		if(expression instanceof Template) {
			for(String v : ((Template) expression).values(i, true)) {
				
				if(Util.isAbsoluteAndValidIRI(v))
					set.add(ResourceFactory.createResource(v));
				else if(Util.isAbsoluteAndValidIRI(baseIRI + v.toString()))
					set.add(ResourceFactory.createResource(baseIRI + v.toString()));
				else
					throw new RuntimeException(baseIRI + " and " + v + " do not constitute a valid IRI");
				
			}
			return set;
		}
		
		if(expression instanceof Reference) {
			for(Object v : ((Reference) expression).values(i)) {
				String s = v.toString();
				
				if(Util.isAbsoluteAndValidIRI(s))
					set.add(ResourceFactory.createResource(s));
				else if(Util.isAbsoluteAndValidIRI(baseIRI + s))
					set.add(ResourceFactory.createResource(baseIRI + s));
				else
					throw new RuntimeException(baseIRI + " and " + s + " do not constitute a valid IRI");
			}
			return set;
		}
		
		throw new RuntimeException("Error generating IRI.");
	}

	static private Map<Object, RDFNode> map = new HashMap<Object, RDFNode>();
	protected List<RDFNode> generateBlankNodes(Iteration i) {
		List<RDFNode> set = new ArrayList<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a BN, otherwise the shapes
			// Would have caught the error.
			RDFNode n = ((RDFNodeConstant) expression).constant.asResource();
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
		
		if(expression == null) {
			// IF NO REFERENCE, TEMPLATE, OR CONSTANT, 
			// THEN WE GENERATE BLANK NODES (BASED ON THE ITERATION)
			set.add(ResourceFactory.createResource());
			return set;
		}
		
		throw new RuntimeException("Error generating blank node.");
	}
	
	protected List<RDFNode> generateLiterals(Iteration i, String baseIRI, DatatypeMap dm, LanguageMap lm) {
		List<RDFNode> set = new ArrayList<RDFNode>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a literal, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant);
			return set;
		}
		
		List<RDFNode> datatypes = dm != null ? dm.generateIRIs(i, baseIRI) : null;
		List<String> languages = lm != null ? lm.generateStrings(i) : null;
		
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
					set.add(createTypedLiteral(v));
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
					set.add(createTypedLiteral(v));
				}
			}
			return set;
		}
		
		throw new RuntimeException("Error generating literal or value.");
	}
	
	private Literal createTypedLiteral(Object o) {
		if(o instanceof Integer || o instanceof Long)
			return ResourceFactory.createTypedLiteral(o.toString(), XSDDatatype.XSDinteger);
		else if(o instanceof Float) {
			String s = doubleCanonicalMap(Double.valueOf(o.toString()));
			return ResourceFactory.createTypedLiteral(s, XSDDatatype.XSDdouble);
		} else if(o instanceof Double) {
			String s = doubleCanonicalMap(((Double) o));
			return ResourceFactory.createTypedLiteral(s, XSDDatatype.XSDdouble);
		} else if(o instanceof Date) {
			return ResourceFactory.createTypedLiteral(o.toString(), XSDDatatype.XSDdate);
		} else if(o instanceof Timestamp) {
			Timestamp t = (Timestamp) o;
			String s = o.toString().replace(" ", "T");
			
			// Ensure canonical xsd:dateTime by removing the ".0" when no fraction
			if(t.getNanos() == 0)
				s = s.replace(".0", "");
			
			return ResourceFactory.createTypedLiteral(s, XSDDatatype.XSDdateTime);
		}
		
		return ResourceFactory.createTypedLiteral(o);
	}
	
	private static String doubleCanonicalMap(Double d) {
		BigDecimal f = BigDecimal.valueOf(d);
		// The number of digits in the unscaled value
		int p = f.precision();
		// We start from two digits 
		StringBuilder x = new StringBuilder("0.0");
		// Add the remaining digits to the pattern
		for (int i = 2; i < p; i++)
            x.append("#");
		// Let's not forget the e-notation
		x.append("E0");
		
		NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
		DecimalFormat formatter = (DecimalFormat) numberFormat;
		formatter.applyPattern(x.toString());
		
		return formatter.format(d);
	}
		
}

abstract class TermMap extends ExpressionMap implements GatherMap {

	public Resource termType;
	//public abstract List<RDFNode> generateTerms(Iteration i, String baseIRI);
	public GatherMapMixin gatherMap = null;
	
	@Override
	public List<SubGraph> generateGatherMapGraphs(Iteration i, String baseIRI) {
		if(!isGatherMap())
			throw new RuntimeException("Trying to process a non-gathermap as gathermap");
		
		List<SubGraph> g = new ArrayList<SubGraph>();
		
		if(expression == null) {
			for(SubGraph sg : gatherMap.generateGraphs(i, baseIRI)) {
				g.add(sg);
			}
		} else {
			for(RDFNode n : generateTerms(i, baseIRI)) {
				for(SubGraph sg : gatherMap.generateGraphs(i, baseIRI)) {
					sg.updateNode(n);
					g.add(sg);
				}
			}
		}
		
		return g;
	}

}

class SubjectMap extends TermMap {

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

class ObjectMap extends TermMap {
	
	public DatatypeMap datatypeMap = null;
	public LanguageMap languageMap = null;
	
	public ObjectMap() {
		termType = RML.IRI;
	}
	
	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(RML.IRI.equals(termType))
			return new ArrayList<RDFNode>(generateIRIs(i, baseIRI));
		if(RML.BLANKNODE.equals(termType))
			return new ArrayList<RDFNode>(generateBlankNodes(i));
		if(RML.LITERAL.equals(termType))
			return generateLiterals(i, baseIRI, datatypeMap, languageMap);
					
		throw new RuntimeException("Incorrect term type for object map.");
	}

	@Override
	public boolean isGatherMap() {
		return gatherMap != null;
	}
	
}

class PredicateMap extends TermMap {

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

class GraphMap extends TermMap {
	
	public GraphMap() {
		termType = RML.IRI;
	}

	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		if(termType == RML.IRI)
			return generateIRIs(i, baseIRI);
		if(termType == RML.BLANKNODE)
			return generateBlankNodes(i);
		
		throw new RuntimeException("Incorrect term type for graph map.");
	}
	
	@Override
	public boolean isGatherMap() {
		return false;
	}
	
}

class LanguageMap extends ExpressionMap {

	public List<String> generateStrings(Iteration i) {
		List<String> set = new ArrayList<String>();
		
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
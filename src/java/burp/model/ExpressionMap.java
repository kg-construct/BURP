package burp.model;

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

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;

import burp.util.Util;

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
		
		if(expression instanceof FunctionExecution) {
			for(Object v : ((FunctionExecution) expression).values(i, baseIRI)) {
				String s = v.toString();
				
				System.err.println(s);
				System.err.println(Util.isAbsolute(s));
				IRIFactory.iriImplementation().create(s).violations(true).forEachRemaining(x -> System.err.println(x.getLongMessage()));
				
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
	protected List<RDFNode> generateBlankNodes(Iteration i, String baseIRI) {
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
		
		if(expression instanceof FunctionExecution) {
			for(Object v : ((FunctionExecution) expression).values(i, baseIRI)) {
				set.add(map.computeIfAbsent(v, (x) -> ResourceFactory.createResource()));
			}
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
		
		if(expression instanceof FunctionExecution) {
			for(Object v : ((FunctionExecution) expression).values(i, baseIRI)) {
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
		} else if(o instanceof Literal) {
			return (Literal) o;
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
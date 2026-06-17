package burp.model.rdf;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public final class Datardf {

    private Datardf() {
    }

    public static IRITerm iriTerm(Resource resource) {
        return new IRITerm(resource.getURI());
    }

    public static IRITerm rdfUnderscore(int idx) {
        return new IRITerm(RDF.uri + "_" + idx);
    }

    /**
     * Compares two values for semantic equality, particularly useful for Join Conditions.
     * It natively compares objects of the same class (for performance) and otherwise
     * falls back to converting native types to RDF LiteralTerms to ensure semantic
     * equality (e.g., matching a raw String with a LiteralTerm of xsd:string).
     */
    public static boolean semanticEquals(Object a, Object b) {
        if (Objects.equals(a, b)) return true;

        if (a != null && b != null) {
            if (a.getClass().equals(b.getClass()) && !(a instanceof Term)) {
                return false;
            }
        }

        Term termA = toTerm(a);
        Term termB = toTerm(b);

        if (termA instanceof LiteralTerm litA && termB instanceof LiteralTerm litB) {
            return Objects.equals(litA.value(), litB.value());
        }
        if (termA instanceof IRITerm iriA && termB instanceof IRITerm iriB) {
            return Objects.equals(iriA.uri(), iriB.uri());
        }
        if (termA instanceof BlankNodeTerm bnA && termB instanceof BlankNodeTerm bnB) {
            return Objects.equals(bnA.id(), bnB.id());
        }

        return Objects.equals(termA, termB);
    }

    static final IRITerm XSDinteger = new IRITerm("http://www.w3.org/2001/XMLSchema#integer");
    static final IRITerm XSDdouble = new IRITerm("http://www.w3.org/2001/XMLSchema#double");
    static final IRITerm XSDdate = new IRITerm("http://www.w3.org/2001/XMLSchema#date");
    static final IRITerm XSDdateTime = new IRITerm("http://www.w3.org/2001/XMLSchema#dateTime");
    static final IRITerm XSDstring = new IRITerm("http://www.w3.org/2001/XMLSchema#string");

    public static Term toTerm(@Nullable Object o) {
        return toTerm(o, null);
    }

    public static Term toTerm(@Nullable Object o, @Nullable IRITerm datatype) {
        if (o instanceof Term t) return t;
        if (o == null) return null;

        boolean isXsdDouble = datatype != null && XSDdouble.uri().equals(datatype.uri());
        if (o instanceof RDFNode node) {
            if (node.isLiteral()) {
                Literal l = node.asLiteral();
                String lex = l.getLexicalForm();
                String lang = l.getLanguage();
                String dtUri = l.getDatatypeURI();
                if (XSDdouble.uri().equals(dtUri) || isXsdDouble) {
                    try {
                        double val = l.getDouble();
                        return new LiteralTerm(doubleCanonicalMap(val), XSDdouble, null);
                    } catch (Exception e) {
                        // fallback
                    }
                }

                IRITerm finalDt = dtUri != null && !dtUri.equals("http://www.w3.org/2001/XMLSchema#string")
                        ? new IRITerm(dtUri)
                        : (datatype);

                if (lang != null && !lang.isEmpty()) {
                    return new LiteralTerm(lex, null, lang);
                } else {
                    return new LiteralTerm(lex, finalDt, null);
                }
            } else if (node.isURIResource()) {
                return new IRITerm(node.asResource().getURI());
            } else if (node.isAnon()) {
                return new BlankNodeTerm(node.asResource().getId().getLabelString());
            }
        }

        if (datatype != null) {
            if (isXsdDouble) {
                if (o instanceof Number n) {
                    return new LiteralTerm(doubleCanonicalMap(n.doubleValue()), XSDdouble, null);
                }
                try {
                    double val = Double.parseDouble(o.toString());
                    return new LiteralTerm(doubleCanonicalMap(val), XSDdouble, null);
                } catch (NumberFormatException e) {
                    // fallback
                }
            }
            return new LiteralTerm(o.toString(), datatype, null);
        }

        if (o instanceof Integer || o instanceof Long) {
            return new LiteralTerm(o.toString(), XSDinteger, null);
        } else if (o instanceof Float f) {
            return new LiteralTerm(doubleCanonicalMap(f.doubleValue()), XSDdouble, null);
        } else if (o instanceof Double d) {
            return new LiteralTerm(doubleCanonicalMap(d), XSDdouble, null);
        } else if (o instanceof Date) {
            return new LiteralTerm(o.toString(), XSDdate, null);
        } else if (o instanceof Timestamp ts) {
            String s = ts.toString().replace(" ", "T");
            if (ts.getNanos() == 0) s = s.replace(".0", "");
            return new LiteralTerm(s, XSDdateTime, null);
        } else {
            return new LiteralTerm(o.toString(), XSDstring, null);
        }
    }

    static String doubleCanonicalMap(double d) {
        BigDecimal f = BigDecimal.valueOf(d);
        int p = f.precision();
        String x = "0.0" + "#".repeat(Math.max(0, p - 2)) + "E0";
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        DecimalFormat formatter = (DecimalFormat) numberFormat;
        formatter.applyPattern(x);
        return formatter.format(d);
    }
}

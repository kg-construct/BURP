package burp.ls;

import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.Origin;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SPARQLFileSource extends FileBasedLogicalSource {
    private List<Iteration> iterations = null;
    public List<Iteration> getIterations() { return iterations; }
    public void setIterations(List<Iteration> i) { iterations = i; }
    private final boolean isTSV;
    private final Resource referenceFormulation;
    public String iterator;
    public Origin iteratorOrigin;

    public SPARQLFileSource(boolean isTSV, Resource referenceFormulation) {
        this.isTSV = isTSV;
        this.referenceFormulation = referenceFormulation;
    }

    @Override
    public Resource getReferenceFormulation() {
        return referenceFormulation;
    }

    @Override
    public Iterable<Iteration> iterator() {
        try {
            if (getIterations() == null) {
                setIterations(new ArrayList<>());

                String filePath = Objects.requireNonNull(file.getFile(fileOriginStmts)).getPath();
                Dataset ds = RDFDataMgr.loadDataset(filePath);

                try (QueryExecution exec = QueryExecutionFactory.create(iterator, ds)) {
                    ResultSet results = exec.execSelect();
                    while (results.hasNext()) {
                        QuerySolution sol = results.next();
                        if (isTSV) {
                            getIterations().add(new SPARQLTSVIteration(sol, getNulls()));
                        } else {
                            getIterations().add(new SPARQLIteration(sol, getNulls()));
                        }
                    }
                }
            }
            return getIterations();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        if (isTSV) return new SPARQLTSVReference(reference, origin);
        boolean isCSV = burp.vocabularies.RML.SPARQL_Results_CSV.equals(referenceFormulation);
        return new SPARQLReference(reference, origin, isCSV);
    }
}

class SPARQLReference extends Reference {
    private final boolean isCSV;

    public SPARQLReference(String reference, Origin origin, boolean isCSV) {
        super(reference, origin);
        this.isCSV = isCSV;
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof SPARQLIteration sqIteration)) {
            throw new IllegalArgumentException("SPARQLReference can only be used with SPARQLIteration.");
        }
        List<Object> l = new ArrayList<>();
        RDFNode n = sqIteration.sol != null ? sqIteration.sol.get(reference) : null;
        if (n != null && !sqIteration.getNulls().contains(n)) {
            if (isCSV && n.isLiteral()) {
                org.apache.jena.rdf.model.Literal lit = n.asLiteral();
                String dtUri = lit.getDatatypeURI();
                String lang = lit.getLanguage();
                if ((dtUri == null || dtUri.equals("http://www.w3.org/2001/XMLSchema#string")) && (lang == null || lang.isEmpty())) {
                    String lex = lit.getLexicalForm();
                    if (lex.matches("^[+-]?\\d+$")) {
                        l.add(org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral(lex, org.apache.jena.datatypes.xsd.XSDDatatype.XSDinteger));
                    } else if (lex.matches("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$")) {
                        l.add(org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral(lex, org.apache.jena.datatypes.xsd.XSDDatatype.XSDdouble));
                    } else {
                        l.add(n);
                    }
                } else {
                    l.add(n);
                }
            } else {
                l.add(n);
            }
        }
        return l;
    }
}

class SPARQLTSVReference extends Reference {
    public SPARQLTSVReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof SPARQLTSVIteration sqIteration)) {
            throw new IllegalArgumentException("SPARQLTSVReference can only be used with SPARQLTSVIteration.");
        }
        List<Object> l = new ArrayList<>();
        RDFNode n = sqIteration.sol != null ? sqIteration.sol.get(reference != null ? reference.substring(1) : null) : null;
        if (n != null && !sqIteration.getNulls().contains(n)) {
            l.add(n);
        }
        return l;
    }
}

class SPARQLIteration extends Iteration {
    public QuerySolution sol;

    public SPARQLIteration(QuerySolution sol, Set<Object> nulls) {
        super(nulls);
        this.sol = sol;
    }

    @Override
    public String asString() {
        throw new RuntimeException("Not implemented. Does this make sense in the context of LV?");
    }
}

class SPARQLTSVIteration extends Iteration {
    public QuerySolution sol;

    public SPARQLTSVIteration(QuerySolution sol, Set<Object> nulls) {
        super(nulls);
        this.sol = sol;
    }

    @Override
    public String asString() {
        throw new RuntimeException("Not implemented. Does this make sense in the context of LV?");
    }
}
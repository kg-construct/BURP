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
    private Resource referenceFormulation;
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
    public void setReferenceFormulation(Resource value) {
        this.referenceFormulation = value;
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
        return new SPARQLReference(reference, origin);
    }
}

class SPARQLReference extends Reference {
    public SPARQLReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof SPARQLIteration)) {
            throw new IllegalArgumentException("SPARQLReference can only be used with SPARQLIteration.");
        }
        SPARQLIteration sqIteration = (SPARQLIteration) i;
        List<Object> l = new ArrayList<>();
        RDFNode n = sqIteration.sol != null ? sqIteration.sol.get(reference) : null;
        if (n != null && !sqIteration.getNulls().contains(n)) {
            l.add(n);
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
        if (!(i instanceof SPARQLTSVIteration)) {
            throw new IllegalArgumentException("SPARQLTSVReference can only be used with SPARQLTSVIteration.");
        }
        SPARQLTSVIteration sqIteration = (SPARQLTSVIteration) i;
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
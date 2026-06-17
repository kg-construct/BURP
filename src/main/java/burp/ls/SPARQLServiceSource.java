package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;

import java.util.ArrayList;
import java.util.List;

public class SPARQLServiceSource extends LogicalSource {
    private final boolean isTSV;
    private Resource referenceFormulation;
    private List<Iteration> iterations;

    public String iterator;
    public Origin iteratorOrigin;
    public String endpoint;

    public SPARQLServiceSource(boolean isTSV, Resource referenceFormulation) {
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
            if (iterations == null) {
                iterations = new ArrayList<>();

                QueryExecution exec = QueryExecutionHTTP.service(endpoint).query(iterator).build();
                ResultSet results = exec.execSelect();

                while (results.hasNext()) {
                    QuerySolution sol = results.next();

                    if (isTSV) {
                        iterations.add(new SPARQLTSVIteration(sol, getNulls()));
                    } else {
                        iterations.add(new SPARQLIteration(sol, getNulls()));
                    }
                }
            }
            return iterations;
        } catch (QueryParseException e) {
            throw new BurpException(
                new RmlError(
                    "SPARQL Query Parse Error in " + iterator,
                    iteratorOrigin,
                    RER.ReferenceFormulationSyntaxError,
                    e
                )
            );
        } catch (QueryException e) {
            throw new BurpException(
                new RmlError(
                    "SPARQL Query Error",
                    iteratorOrigin,
                    RER.ReferenceFormulationExecutionError,
                    e
                )
            );
        } catch (Exception e) {
            throw new BurpException(
                new RmlError(
                    "SPARQL Source Unexpected Error",
                    iteratorOrigin,
                    RER.LogicalSourceError,
                    e
                )
            );
        }
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        if (isTSV) return new SPARQLTSVReference(reference, origin);
        boolean isCSV = burp.vocabularies.RML.SPARQL_Results_CSV.equals(referenceFormulation);
        return new SPARQLReference(reference, origin, isCSV);
    }
}
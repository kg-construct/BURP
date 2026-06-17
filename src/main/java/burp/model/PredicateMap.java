package burp.model;

import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

public class PredicateMap extends TermMap {

    public PredicateMap() {
        this.termType = RML.IRI;
    }

    @Override
    public String getName() {
        return "predicate map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI);
    }
}
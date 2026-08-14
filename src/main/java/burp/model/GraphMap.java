package burp.model;

import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

public class GraphMap extends TermMap {

    public GraphMap() {
        this.termType = RML.IRI;
    }

    @Override
    public String getName() {
        return "graph map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE);
    }
}
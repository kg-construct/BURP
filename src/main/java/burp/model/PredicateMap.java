package burp.model;

import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

@RdfType(Rml.PredicateMap)
public class PredicateMap extends TermMap {

    @Override
    public String getName() {
        return "predicate map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI);
    }

}
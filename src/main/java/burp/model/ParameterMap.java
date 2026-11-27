package burp.model;

import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import java.util.Set;

@RdfType(Rml.ParameterMap)
public class ParameterMap extends TermMap {

    @Override
    public String getName() {
        return "parameter map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI);
    }
}
package burp.model.fnml;

import burp.model.TermMap;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import java.util.Set;

@RdfType(Rml.ReturnMap)
public class ReturnMap extends TermMap {
    public ReturnMap() {
    }

    @Override
    public String getName() {
        return "return map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI);
    }
}
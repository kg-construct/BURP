package burp.model;

import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import java.util.Set;

@RdfType(Rml.InputValueMap)
public class InputValueMap extends TermMap {
    @Override
    public String getName() {
        return "input value map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, RML.LITERAL, BURP.CollectionOrContainer);
    }

    @Override
    public Resource getDefaultTermType() {
        return RML.LITERAL;
    }
}
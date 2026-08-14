package burp.model;

import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import java.util.Set;

public class InputValueMap extends TermMap {
    public InputValueMap() {
        this.termType = RML.LITERAL;
    }

    @Override
    public String getName() {
        return "input value map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, RML.LITERAL, BURP.CollectionOrContainer);
    }
}
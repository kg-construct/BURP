package burp.model;

import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

public final class ObjectMap extends TermMap implements BaseObjectMap {

    public ObjectMap() {
        this.termType = RML.IRI;
    }

    @Override
    public String getName() {
        return "object map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, RML.LITERAL, BURP.CollectionOrContainer);
    }
}
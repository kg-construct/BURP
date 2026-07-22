package burp.model.fnml;

import burp.model.TermMap;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import java.util.Set;

public class ReturnMap extends TermMap {
    public ReturnMap() {
        this.termType = RML.IRI;
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
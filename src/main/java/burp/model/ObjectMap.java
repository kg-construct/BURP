package burp.model;

import burp.model.fnml.FunctionExecution;
import burp.model.rdf.LiteralTerm;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

@RdfType(Rml.ObjectMap)
public final class ObjectMap extends TermMap implements BaseObjectMap {

    @Override
    public String getName() {
        return "object map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, RML.LITERAL, BURP.CollectionOrContainer);
    }

    @Override
    public Resource getDefaultTermType() {
        if (languageMap != null  || datatypeMap != null
                || expression instanceof Reference
                || expression instanceof FunctionExecution
                || expression instanceof RDFNodeConstant nc && nc.constant instanceof LiteralTerm) {
            return RML.LITERAL;
        }
        return RML.IRI;
    }
}
package burp.reporting;

import burp.vocabularies.RER;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RmlErrorBuilder {
    private final OntClass errorType;
    private String message = "";
    private Origin origin = null;
    private Exception exception = null;
    private final Map<Property, Object> context = new HashMap<>();

    public RmlErrorBuilder(OntClass errorType) {
        this.errorType = errorType;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public void reference(String ref) {
        context.put(RER.reference, ref);
    }

    public void referenceFormulation(Resource refForm) {
        context.put(RER.referenceFormulation, refForm);
    }

    public void allowedTermTypes(List<Resource> termTypes) {
        context.put(RER.allowedTermTypes, termTypes);
    }

    public void withContext(OntProperty property, Object value) {
        context.put(property, value);
    }

    public RmlError build() {
        return new RmlError(message, origin, errorType, exception, context);
    }
}

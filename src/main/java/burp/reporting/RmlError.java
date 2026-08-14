package burp.reporting;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.Property;

import java.util.Collections;
import java.util.Map;

public class RmlError {
    private final String message;
    private final Origin origin;
    private final OntClass errorType;
    private final Exception exception;
    private final Map<Property, Object> context;

    public RmlError(String message, Origin origin, OntClass errorType, Exception exception, Map<Property, Object> context) {
        this.message = message;
        this.origin = origin;
        this.errorType = errorType;
        this.exception = exception;
        this.context = context != null ? context : Collections.emptyMap();
    }

    public RmlError(String message, Origin origin, OntClass errorType) {
        this(message, origin, errorType, null, Collections.emptyMap());
    }

    public RmlError(String message, Origin origin, OntClass errorType, Exception exception) {
        this(message, origin, errorType, exception, Collections.emptyMap());
    }

    public String getMessage() {
        return message;
    }

    public Origin getOrigin() {
        return origin;
    }

    public OntClass getErrorType() {
        return errorType;
    }

    public Exception getException() {
        return exception;
    }

    public Map<Property, Object> getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "RmlError(message=\"" + message + "\", origin=" + origin + ", errorType=" + errorType + ", exception=" + exception + ")";
    }
}

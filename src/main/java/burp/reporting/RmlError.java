package burp.reporting;

import burp.model.PlanNode;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

@NullMarked
public record RmlError(
        String message,
        @Nullable Origin origin,
        OntClass errorType,
        @Nullable Exception exception,
        Map<Property, Object> context,
        @Nullable PlanNode planNode
) {
    public RmlError(
            String message,
            @Nullable Origin origin,
            OntClass errorType,
            @Nullable Exception exception,
            @Nullable Map<Property, Object> context,
            @Nullable PlanNode planNode
    ) {
        this.message = message;
        this.origin = origin;
        this.errorType = errorType;
        this.exception = exception;
        this.context = context != null ? context : Collections.emptyMap();
        this.planNode = planNode;
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception, @Nullable Map<Property, Object> context) {
        this(message, origin, errorType, exception, context, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType) {
        this(message, origin, errorType, null, Collections.emptyMap(), null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception) {
        this(message, origin, errorType, exception, Collections.emptyMap(), null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable PlanNode planNode) {
        this(message, origin, errorType, null, Collections.emptyMap(), planNode);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception, @Nullable PlanNode planNode) {
        this(message, origin, errorType, exception, Collections.emptyMap(), planNode);
    }

    public @Nullable Origin getOrigin() {
        return origin;
    }

    public String getMessage() {
        return message;
    }

    public OntClass getErrorType() {
        return errorType;
    }

    public @Nullable Exception getException() {
        return exception;
    }

    public Map<Property, Object> getContext() {
        return context;
    }

    public @Nullable PlanNode getPlanNode() {
        return planNode;
    }

    @Override
    public String toString() {
        return "RmlError(message=\"" + message + "\", origin=" + origin + ", errorType=" + errorType +
                ", exception=" + exception + ", planNode=" + planNode + ")";
    }
}

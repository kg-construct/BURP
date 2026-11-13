package burp.reporting;

import burp.model.OriginLineage;
import burp.model.PlanNode;
import burp.model.RmlEvaluationResult;
import burp.vocabularies.RER;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.rdf.model.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@NullMarked
public record RmlError(
        String message,
        @Nullable Origin origin,
        OntClass errorType,
        @Nullable Exception exception,
        Map<Property, Object> context,
        @Nullable PlanNode planNode,
        @Nullable OriginLineage originLineage,
        @Nullable RmlEvaluationResult evaluationResult
) {
    public RmlError(
            String message,
            @Nullable Origin origin,
            OntClass errorType,
            @Nullable Exception exception,
            @Nullable Map<Property, Object> context,
            @Nullable PlanNode planNode,
            @Nullable OriginLineage originLineage,
            @Nullable RmlEvaluationResult evaluationResult
    ) {
        this.message = message;
        this.origin = origin;
        this.errorType = errorType;
        this.exception = exception;
        this.context = context != null ? context : Collections.emptyMap();
        this.planNode = planNode;
        this.originLineage = originLineage;
        this.evaluationResult = evaluationResult;
    }

    public RmlError(
            String message,
            @Nullable Origin origin,
            OntClass errorType,
            @Nullable Exception exception,
            @Nullable Map<Property, Object> context,
            @Nullable PlanNode planNode
    ) {
        this(message, origin, errorType, exception, context, planNode, null, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception, @Nullable Map<Property, Object> context) {
        this(message, origin, errorType, exception, context, null, null, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType) {
        this(message, origin, errorType, null, Collections.emptyMap(), null, null, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception) {
        this(message, origin, errorType, exception, Collections.emptyMap(), null, null, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable PlanNode planNode) {
        this(message, origin, errorType, null, Collections.emptyMap(), planNode, null, null);
    }

    public RmlError(String message, @Nullable Origin origin, OntClass errorType, @Nullable Exception exception, @Nullable PlanNode planNode) {
        this(message, origin, errorType, exception, Collections.emptyMap(), planNode, null, null);
    }

    public Severity severity() {
        return RER.severityOf(errorType);
    }

    public List<String> superClassUris() {
        return RER.superClassesOf(errorType);
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

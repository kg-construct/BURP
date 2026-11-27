package burp.dev.rml.service.trace;

import burp.model.*;
import burp.model.fnml.FunctionExecution;
import burp.model.gathermap.GatherMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Categorical dataflow pipeline stages for RML AST PlanNodes.
 */
@NullMarked
public enum PlanNodeStage {
    LOGICAL_SOURCE(0),
    REFERENCE(1),
    INPUT_PARAMETER(2),
    FUNCTION(3),
    TEMPLATE_EXPRESSION(4),
    TERM_MAP(5),
    PREDICATE_OBJECT_MAP(6),
    TRIPLES_MAP(7),
    OTHER(8);

    private final int order;

    PlanNodeStage(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    public static PlanNodeStage determineStage(@Nullable PlanNode node) {
        if (node == null) {
            return OTHER;
        }
        if (node instanceof AbstractLogicalSource) {
            return LOGICAL_SOURCE;
        }
        if (node instanceof Reference || node instanceof Template.ReferenceSegment || node instanceof RDFNodeConstant) {
            return REFERENCE;
        }
        if (node instanceof Input || node instanceof ParameterMap) {
            return INPUT_PARAMETER;
        }
        if (node instanceof FunctionExecution || node instanceof FunctionMap) {
            return FUNCTION;
        }
        if (node instanceof Template || node instanceof Template.LiteralSegment || node instanceof Condition) {
            return TEMPLATE_EXPRESSION;
        }
        if (node instanceof TermMap || node instanceof GatherMap || node instanceof ExpressionMap) {
            return TERM_MAP;
        }
        if (node instanceof PredicateObjectMap) {
            return PREDICATE_OBJECT_MAP;
        }
        if (node instanceof TriplesMap) {
            return TRIPLES_MAP;
        }
        return OTHER;
    }
}

package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.fnml.FunctionExecution;
import burp.model.key.AstKey;
import burp.model.rdf.LiteralTerm;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfMappedFrom;
import burp.parse.loader.annotations.RdfProperty;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public abstract class ExpressionMap extends AbstractPlanNode implements LogicalTargetScope {

    @RdfId
    public Resource subject;

    @Override
    public @Nullable Resource getIri() {
        return subject;
    }

    @RdfMappedFrom
    public Expression expression = null;

    public Origin expressionOrigin() {
        return emitOrigin("expression");
    }

    @RdfProperty(Rml.condition)
    public Condition condition = null;

    public Origin conditionOrigin() {
        return emitOrigin("condition");
    }

    @RdfProperty(Rml.logicalTarget)
    private final Set<LogicalTarget> logicalTargets = new HashSet<>();

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        if (expression != null) {
            children.add(expression);
        }
        if (condition != null) {
            children.add(condition);
        }
        return children;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (expression != null) {
            expression.collectAttributes(sink);
        }
    }

    public boolean isConditionSatisfied(@Nullable Iteration i, EvaluationContext context) {
        if (condition == null) return true;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
        List<Object> values = condition.generateValues(i, tplCtx);
        if (values.isEmpty()) return false;
        for (Object val : values) {
            if (isTruthy(val)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTruthy(Object val) {
        return switch (val) {
            case RawValue rv -> isTruthy(rv.value());
            case Boolean b -> b;
            case LiteralTerm lit -> Boolean.TRUE.equals(lit.booleanOrNull());
            case Number n -> n.doubleValue() != 0.0;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    public List<Object> generateValues(@Nullable Iteration i, EvaluationContext context) {
        if (!isConditionSatisfied(i, context)) {
            return Collections.emptyList();
        }
        switch (expression) {
            case RDFNodeConstant expr -> {
                List<Object> list = new ArrayList<>();
                if (expr.constant != null) {
                    list.add(expr.constant);
                }
                return list;
            }
            case Template template -> {
                return (List<Object>) (List<?>) template.values(i, context);
            }
            case Reference reference -> {
                return reference.values(i);
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i);
            }
            case null, default -> throw new BurpException(
                    new RmlError(
                            "Error generating values, expression is not supported.",
                            expressionOrigin(),
                            RER.UnsupportedMapping,
                            this
                    )
            );
        }
    }

    public List<Object> generateValues(@Nullable Iteration i, TemplateReferenceSafety safe) {
        return generateValues(i, EvaluationContext.createDefault().withTemplateSafety(safe));
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        if (this instanceof TermGenerator tg) {
            return new ArrayList<>(tg.generateTerms(iter, context));
        }
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
        List<RmlEvaluationResult> resList = new ArrayList<>();
        for (Object obj : generateValues(iter, tplCtx)) {
            if (obj instanceof RmlEvaluationResult r) {
                resList.add(r);
            } else if (obj != null) {
                resList.add(new RawValue(obj, Set.of(), iter != null ? iter.getLineage() : null));
            }
        }
        return resList;
    }

    @Override
    public AstKey computeAstKey() {
        String termType = getClass().getSimpleName();
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.ExpressionMapKey(termType, childKeys);
    }
}
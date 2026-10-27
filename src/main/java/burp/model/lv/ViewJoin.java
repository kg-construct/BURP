package burp.model.lv;

import burp.model.*;
import burp.model.rdf.Datardf;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.AbstractPlanNode;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ViewJoin extends AbstractPlanNode implements ParentJoinReferenceScope, LocalReferenceScope, ReferenceHolder {

    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return subject;
    }

    @RdfProperty(Rml.parentLogicalView)
    public LogicalView parentLogicalView;

    @RdfProperty(Rml.joinCondition)
    public List<JoinCondition> joinConditions = new ArrayList<>();

    @RdfProperty(Rml.field)
    public List<Field> fields = new ArrayList<>();

    public JoinType joinType;

    private List<LogicalIteration> iterations = null;

    public List<ExpressionField> getExpressionFields() {
        List<ExpressionField> list = new ArrayList<>();
        for (Field f : fields) {
            if (f instanceof ExpressionField ef) {
                list.add(ef);
            }
        }
        return list;
    }


    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>(joinConditions);
        list.addAll(fields);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        List<PlanNode> list = new ArrayList<>(joinConditions);
        if (parentLogicalView != null) list.add(parentLogicalView);
        return list;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (joinType != null) {
            Origin orig = originOf("joinType");
            sink.put("joinType", joinType.name(), orig);
        }
    }

    @Override
    public List<PlanNodeReference> nodeReferences() {
        if (parentLogicalView != null) {
            return List.of(new PlanNodeReference("parentLogicalView", parentLogicalView, originOf("parentLogicalView")));
        }
        return Collections.emptyList();
    }

    public List<LogicalIteration> expand(List<LogicalIteration> childIterations) {
        return expand(childIterations, null);
    }

    public List<LogicalIteration> expand(List<LogicalIteration> childIterations, @Nullable EvaluationContext context) {
        try {
            if (iterations == null) {
                Iterable<Iteration> parentSource = context != null
                        ? context.getIterations(parentLogicalView)
                        : parentLogicalView.iterator();
                iterations = StreamSupport
                        .stream(parentSource.spliterator(), false)
                        .filter(a -> a instanceof LogicalIteration)
                        .map(a -> (LogicalIteration) a)
                        .collect(Collectors.toList());
            }

            var unsafeChildCtx = (context != null) ? context.withTemplateSafety(TemplateReferenceSafety.UNSAFE) : EvaluationContext.createDefault().withTemplateSafety(TemplateReferenceSafety.UNSAFE);
            var unsafeParentCtx = (context != null) ? context.withTemplateSafety(TemplateReferenceSafety.UNSAFE) : EvaluationContext.createDefault().withTemplateSafety(TemplateReferenceSafety.UNSAFE);

            burp.model.join.ParentJoinIndex joinIndex = (context != null) ? context.getJoinIndex(this) : null;
            if (joinIndex == null) {
                joinIndex = burp.model.join.ParentJoinIndex.build(new ArrayList<>(iterations), joinConditions, unsafeParentCtx);
            }

            List<LogicalIteration> newList = new ArrayList<>();

            for (LogicalIteration childIteration : childIterations) {
                List<Iteration> matches = joinIndex.findMatches(childIteration, joinConditions, unsafeChildCtx);

                if (!matches.isEmpty()) {
                    int count = -1;
                    for (Iteration iter : matches) {
                        if (iter instanceof LogicalIteration parentIteration) {
                            count++;
                            OriginLineage combinedLineage = childIteration.getLineage() != null
                                    ? childIteration.getLineage().merge(parentIteration.getLineage())
                                    : parentIteration.getLineage();
                            List<LogicalIteration> result = new ArrayList<>();
                            result.add(childIteration.copyWithLineage(combinedLineage));
                            for (ExpressionField e : getExpressionFields()) {
                                result = enrichForJoin(e, count, result, parentIteration);
                            }
                            newList.addAll(result);
                        }
                    }
                } else if (joinType == JoinType.LEFT) {
                    LogicalIteration newIteration = childIteration.copy();
                    for (ExpressionField e : getExpressionFields()) {
                        newIteration.put(e.fieldName, null);
                        newIteration.put(e.fieldName + ".#", null);
                    }
                    newList.add(newIteration);
                }
            }

            return newList;
        } catch (BurpException e) {
            throw e;
        } catch (Throwable e) {
            throw new BurpException(new RmlError("Error while expanding ViewJoin: " + e.getMessage(), emitOrigin(), RER.ExecutionError, e instanceof Exception ex ? ex : new RuntimeException(e), this));
        }
    }

    private List<LogicalIteration> enrichForJoin(ExpressionField e, int index, List<LogicalIteration> result, LogicalIteration parentIteration) {
        List<LogicalIteration> nList = new ArrayList<>();

        for (LogicalIteration li : result) {
            OriginLineage combinedLineage = li.getLineage() != null
                    ? li.getLineage().merge(parentIteration.getLineage())
                    : parentIteration.getLineage();
            for (Object o : e.fieldExpressionMap.generateValues(parentIteration, TemplateReferenceSafety.SAFE_IRI)) {
                LogicalIteration newLogicalIteration = li.copyWithLineage(combinedLineage);
                newLogicalIteration.put(e.fieldName, o);
                newLogicalIteration.put(e.fieldName + ".#", index);
                nList.add(newLogicalIteration);
            }
        }

        return nList;
    }

    private boolean matches(LogicalIteration childIteration, LogicalIteration parentIteration) {
        for (JoinCondition jc : joinConditions) {
            Set<Object> values1 = new HashSet<>(jc.childMap.generateValues(childIteration, TemplateReferenceSafety.UNSAFE));
            Set<Object> values2 = new HashSet<>(jc.parentMap.generateValues(parentIteration, TemplateReferenceSafety.UNSAFE));

            boolean match = values1.stream().anyMatch(v1 -> values2.stream().anyMatch(v2 -> Datardf.semanticEquals(v1, v2)));
            if (!match) {
                return false;
            }
        }
        return true;
    }


    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        return parentLogicalView != null ? parentLogicalView.buildExportedReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        return parentLogicalView;
    }

    @Override
    public Reference buildParentJoinReference(String reference, Origin origin) {
        return parentLogicalView != null ? parentLogicalView.buildExportedReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getParentLogicalSource() {
        return parentLogicalView;
    }

    @Override
    public @Nullable Reference buildChildJoinReference(String reference, Origin origin) {
        return parent instanceof LogicalView lv ? lv.buildExportedReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getChildLogicalSource() {
        return parent instanceof LogicalView lv ? lv : null;
    }

    @Override
    public void compileReferences() {
        for (ExpressionField field : getExpressionFields()) {
            if (field.fieldExpressionMap != null) {
                for (RawReference ref : field.fieldExpressionMap.descendants(RawReference.class).toList()) {
                    if (ref.getReference() != null && ref.getCompiledReference() == null) {
                        ref.setCompiledReference(this.buildParentJoinReference(ref.getReference(), ref.originOf("reference")));
                    }
                }
            }
        }
    }

    @Override
    public AstKey computeAstKey() {
        String pIri = parentLogicalView != null ? PlanNode.resourceToIriOrBNode(parentLogicalView.getIri()) : "";
        String jt = (joinType != null) ? joinType.name() : "INNER";
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.ViewJoinKey(pIri, jt, childKeys);
    }
}

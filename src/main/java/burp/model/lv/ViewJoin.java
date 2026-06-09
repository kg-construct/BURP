package burp.model.lv;

import burp.model.*;
import burp.model.rdf.Datardf;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ViewJoin implements PlanNode, ParentJoinReferenceScope, LocalReferenceScope, ReferenceHolder {

    public LogicalView parentLogicalView;
    public List<JoinCondition> joinConditions = new ArrayList<>();
    public List<ExpressionField> expressionFields = new ArrayList<>();
    public JoinType joinType;

    private List<LogicalIteration> iterations = null;

    private PlanNode parent = null;

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        list.add(parentLogicalView);
        list.addAll(joinConditions);
        for (ExpressionField e : expressionFields) {
            if (e.fieldExpressionMap != null) {
                list.add(e.fieldExpressionMap);
            }
        }
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    public List<LogicalIteration> expand(List<LogicalIteration> childIterations) {
        try {
            if (iterations == null) {
                iterations = StreamSupport
                        .stream(parentLogicalView.iterator().spliterator(), false)
                        .filter(a -> a instanceof LogicalIteration)
                        .map(a -> (LogicalIteration) a)
                        .collect(Collectors.toList());
            }

            List<LogicalIteration> newList = new ArrayList<>();

            for (LogicalIteration childIteration : childIterations) {
                boolean hasACorrespondence = false;

                int count = -1;

                for (LogicalIteration parentIteration : iterations) {
                    if (matches(childIteration, parentIteration)) {
                        hasACorrespondence = true;

                        count++;

                        List<LogicalIteration> result = new ArrayList<>();
                        result.add(childIteration);
                        for (ExpressionField e : expressionFields) {
                            result = enrichForJoin(e, count, result, parentIteration);
                        }

                        newList.addAll(result);
                    }
                }

                if (!hasACorrespondence && joinType == JoinType.LEFT) {
                    LogicalIteration newIteration = childIteration.copy();
                    for (ExpressionField e : expressionFields) {
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
            throw new BurpException(new RmlError("Error while expanding ViewJoin: " + e.getMessage(), null, RER.ExecutionError));
        }
    }

    private List<LogicalIteration> enrichForJoin(ExpressionField e, int index, List<LogicalIteration> result, LogicalIteration parentIteration) {
        List<LogicalIteration> nList = new ArrayList<>();

        for (LogicalIteration li : result) {
            for (Object o : e.fieldExpressionMap.generateValues(parentIteration, TemplateReferenceSafety.SAFE_IRI)) {
                LogicalIteration newLogicalIteration = li.copy();
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

    public void addField(Field field) {
        field.parentField = parentLogicalView;
        if (field instanceof ExpressionField) {
            this.expressionFields.add((ExpressionField) field);
        } else {
            throw new RuntimeException("Unknown field type for ViewJoin.");
        }
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        return ((LogicalView) parent).buildExportedReference(reference, origin);
    }

    @Override
    public Reference buildParentJoinReference(String reference, Origin origin) {
        return parentLogicalView.buildExportedReference(reference, origin);
    }

    @Override
    public void compileReferences() {
        for (ExpressionField field : expressionFields) {
            if (field.fieldExpressionMap != null) {
                for (RawReference ref : field.fieldExpressionMap.descendants(RawReference.class).toList()) {
                    if (ref.getReference() != null && ref.getCompiledReference() == null) {
                        ref.setCompiledReference(this.buildParentJoinReference(ref.getReference(), ref.getOrigin()));
                    }
                }
            }
        }
    }
}

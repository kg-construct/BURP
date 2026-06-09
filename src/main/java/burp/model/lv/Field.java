package burp.model.lv;

import burp.model.AbstractLogicalSource;
import burp.model.PlanNode;

import java.util.ArrayList;
import java.util.List;

public abstract class Field implements ContainsFields, FieldParent, PlanNode {

	public String fieldName ;
    public FieldParent parentField;
    private PlanNode planNodeParent;

    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

    public static List<LogicalIteration> expand(List<LogicalIteration> list, List<ExpressionField> expressionFields, List<IterableField> iterableFields) {
        List<LogicalIteration> result = new ArrayList<>(list);

        if (expressionFields != null && !expressionFields.isEmpty()) {
            // Let's process the expression fields
            for (ExpressionField expressionField : expressionFields) {
                List<LogicalIteration> nlist = new ArrayList<>();
                for (LogicalIteration li : result) {
                    nlist.addAll(expressionField.enrich(li));
                }
                result = nlist;
            }
        }

        if (iterableFields != null && !iterableFields.isEmpty()) {
            // Let's process the iterable fields
            List<LogicalIteration> nlist = new ArrayList<>();
            for (LogicalIteration li : result) {
                for (IterableField iterableField : iterableFields) {
                    nlist.addAll(iterableField.enrich(li));
                }
            }
            result = nlist;
        }

        return result;
    }

    @Override
    public String getAbsoluteFieldName() {
        if (parentField instanceof AbstractLogicalSource)
            return fieldName;

        Field parent = (Field) this.parentField;
        return parent.getAbsoluteFieldName() + "." + fieldName;
    }

    @Override
    public PlanNode getParent() {
        return planNodeParent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.planNodeParent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        children.addAll(expressionFields);
        children.addAll(iterableFields);
        return children;
    }

    @Override
    public List<IterableField> getIterableFields() {
        return iterableFields;
    }

    @Override
    public List<ExpressionField> getExpressionFields() {
        return expressionFields;
    }

    @Override
    public void addField(Field field) {
        field.parentField = this;

        if (field instanceof IterableField) {
            getIterableFields().add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            getExpressionFields().add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type.");
    }

}


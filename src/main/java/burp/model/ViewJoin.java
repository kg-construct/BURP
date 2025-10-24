package burp.model;

import java.util.ArrayList;
import java.util.List;

public abstract class ViewJoin implements ContainsFields {

    public LogicalView parentLogicalView;
    public List<JoinCondition> joinConditions = new ArrayList<>();
    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

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
        field.parent = parentLogicalView;

        if (field instanceof IterableField) {
            getIterableFields().add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            getExpressionFields().add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type.");
    }

}

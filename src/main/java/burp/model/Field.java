package burp.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Field implements ContainsFields, FieldParent {

	public String fieldName ;
    public FieldParent parent;

    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

    protected String getAbsoluteFieldName() {
        if (parent instanceof AbstractLogicalSource)
            return fieldName;

        Field parent = (Field) this.parent;
        return parent.getAbsoluteFieldName() + "." + fieldName;
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
        field.parent = this;

        if (field instanceof IterableField) {
            getIterableFields().add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            getExpressionFields().add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type.");
    }

}


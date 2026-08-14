package burp.model.lv;

import java.util.List;

public interface ContainsFields {

    List<IterableField> getIterableFields();
    List<ExpressionField> getExpressionFields();

    void addField(Field field);

}

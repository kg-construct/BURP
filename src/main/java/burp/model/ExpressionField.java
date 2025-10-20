package burp.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExpressionField extends Field {

    public ConcreteExpressionMap fieldExpressionMap;

    public List<LogicalIteration> enrich(LogicalIteration underlying){
        List<LogicalIteration>  result = new ArrayList<>();

        int i = 0;
        for(Object o : fieldExpressionMap.generateValues(underlying.getIteration(getParentIteration()))){
            LogicalIteration e = underlying.copy();
            e.put(getAbsoluteFieldName() + ".#", i++);
            e.put(getAbsoluteFieldName(), o);
            result.add(e);
        }

        return result;
    }

    private String getParentIteration() {
        if(parent instanceof AbstractLogicalSource)
            return  "<i>";

        Field parent = (Field) this.parent;
        return parent.fieldName;
    }

    @Override
    public Iterator<Iteration> iterator() {
        return parent.iterator();
    }
}

package burp.model;

import java.util.ArrayList;
import java.util.List;

public class ExpressionField extends Field {

    public ConcreteExpressionMap fieldExpressionMap;

    public List<LogicalIteration> enRich(String parentIteration, LogicalIteration underlying){
        List<LogicalIteration>  result = new ArrayList<>();

        int i = 0;
        for(Object o : fieldExpressionMap.generateValues(underlying.getIteration(parentIteration))){
            LogicalIteration e = underlying.copy();
            e.put(getPrefix(parentIteration) + this.fieldName + ".#", i++);
            e.put(getPrefix(parentIteration) + this.fieldName, o);

            result.add(e);
        }

        return result;
    }
}

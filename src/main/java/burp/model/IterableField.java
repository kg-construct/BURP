package burp.model;

import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;

public class IterableField extends Field {

    public String iterator;
    public Resource referenceFormulation;

    public List<LogicalIteration> enrich(LogicalIteration underlying) {
        List<LogicalIteration> list = new ArrayList<>();

        if (referenceFormulation == null) {
            // The iterator has chanced
            // We take the iterator from the parent
            Iteration i = underlying.getIteration(parent.getAbsoluteFieldName());
            int index = 0;
            for(Iteration newIteration : i.changeIterator(iterator)) {
                System.out.println(newIteration);
                LogicalIteration e = underlying.copy();
                e.put(getAbsoluteFieldName() + ".#", index++);
                e.put(getAbsoluteFieldName(), newIteration);
                list.add(e);
            }
        } else {
            // The reference formulation (and iterator) have changed.
        }

        return Field.expand(list, expressionFields, iterableFields);
    }

}
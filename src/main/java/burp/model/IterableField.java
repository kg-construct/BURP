package burp.model;

import burp.ls.LogicalSourceFactory;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IterableField extends Field {

    public String iterator;
    public Resource referenceFormulation;

    public List<LogicalIteration> enrich(LogicalIteration underlying) {
        List<LogicalIteration> list = new ArrayList<>();

        if (referenceFormulation == null) {
            // The iterator has chanced
            // We take the iterator from the parent
            String i = underlying.getIterationString(parent.getAbsoluteFieldName());
            int index = 0;
            for(Iteration newIteration : LogicalSourceFactory.changeIterator(i, getAncestorReferenceFormulation(), iterator)) {
                LogicalIteration e = underlying.copy();
                e.put(getAbsoluteFieldName() + ".#", index++);
                e.put(getAbsoluteFieldName(), newIteration);
                list.add(e);
            }
        } else {
            // The reference formulation (and iterator) have changed.
            String i = underlying.getIterationString(parent.getAbsoluteFieldName());

            int index = 0;
            for(Iteration newIteration : LogicalSourceFactory.changeIterator(i, referenceFormulation, iterator)) {
                System.out.println(newIteration);
                LogicalIteration e = underlying.copy();
                e.put(getAbsoluteFieldName() + ".#", index++);
                e.put(getAbsoluteFieldName(), newIteration);
                list.add(e);
            }
        }

        return Field.expand(list, expressionFields, iterableFields);
    }

    private Resource getAncestorReferenceFormulation() {
        // Since we explicitly created an IterableField for the rood. the two lines below should suffice.
        if(referenceFormulation != null) return referenceFormulation;
        if(parent instanceof IterableField) return ((IterableField) parent).getAncestorReferenceFormulation();
        if(parent instanceof LogicalSource) return ((LogicalSource) parent).referenceFormulation;
        return null;
    }

}
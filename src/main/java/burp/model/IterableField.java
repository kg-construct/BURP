package burp.model;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IterableField extends Field {

    public String iterator;
    public Resource referenceFormulation;

    public List<LogicalIteration> enrich() {
        List<LogicalIteration> list = new ArrayList<>();

        if(iterator == null && referenceFormulation == null) {
            if(parent instanceof AbstractLogicalSource) {
                // We have the root
                Iterator<Iteration> iterator = parent.iterator();
                int index = 0;
                while(iterator.hasNext()) {
                    Iteration i = iterator.next();
                    LogicalIteration li = new LogicalIteration(((AbstractLogicalSource) parent).nulls);
                    li.put("#", index++);
                    li.put("<i>", i);
                    list.add(li);
                }

            } else {
                throw new RuntimeException("No iterator or reference formulation for iterable field.");
            }
        } else if (referenceFormulation == null) {
            // The iterator has chanced
            // We take the iterator from the parent
            Iterator<Iteration> iter = parent.iterator();
            int index = 0;
            while(iter.hasNext()) {
                Iteration i = iter.next();
                for(Iteration newIteration : i.changeIterator(iterator)) {
                    LogicalIteration li = new LogicalIteration(((AbstractLogicalSource) parent).nulls);
                    li.put(getAbsoluteFieldName(), index++);
                    li.put(getAbsoluteFieldName() + ".#", newIteration);
                }
            }

        } else {
            // The reference formulation (and iterator) have changed.
        }

        // Let's process the expression fields
        List<LogicalIteration> nlist = new ArrayList<>();
        for(LogicalIteration li : list) {
            for(ExpressionField expressionField : expressionFields) {
                nlist.addAll(expressionField.enrich(li));
            }
        }
        list = nlist;

        // Let's process the iterable fields
        nlist = new ArrayList<>();
        for(LogicalIteration li : list) {
            for(IterableField iterableField : iterableFields) {
                nlist.addAll(iterableField.enrich());
            }
        }
        list = nlist;

        return  list;
    }

    @Override
    public Iterator<Iteration> iterator() {
        return parent.iterator();
    }

}
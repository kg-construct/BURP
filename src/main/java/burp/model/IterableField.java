package burp.model;

import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IterableField extends Field {

    public String iterator;
    public Resource referenceFormulation;

    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

    public void addField(Field field) {
        if (field instanceof IterableField) {
            iterableFields.add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            expressionFields.add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type.");
    }

    public List<LogicalIteration> enrich(Iterator<Iteration> iterations, Set<Object> nulls) {
        List<LogicalIteration> list = new ArrayList<>();

        // TODO TO REMINDER
        // HIER BEGINNEN WE MET DE ROOT
        // DE RECURSIEVE CALLM OET ANDERS

        int index = 0;
        while(iterations.hasNext()) {
            Iteration i = iterations.next();
            LogicalIteration li = new LogicalIteration(nulls);
            li.put(fieldName + ".#", index++);
            li.put(fieldName, i);

            list.add(li);
        }

        // Let's process the expression fields
        List<LogicalIteration> nlist = new ArrayList<>();
        for(LogicalIteration li : list) {
            for(ExpressionField es : expressionFields) {
                nlist.addAll(es.enrich(fieldName, li));
            }
        }
        list = nlist;

        // Let's process the iterable fields
        nlist = new ArrayList<>();
        for(LogicalIteration li : list) {
            for(IterableField iterableField : iterableFields) {
                if(iterableField.referenceFormulation == null && iterableField.iterator == null) {
                    throw new RuntimeException("An Iterable Field has not been set with a new reference formulation or new iterator.");
                }

                if(iterableField.referenceFormulation != null) {
                    // TODO: implement changing of reference formulation (with iterator)
                } else {
                    // The iterator has changed
                    Iteration i = li.getIteration(fieldName);
                    List<Iteration> newiterations = i.changeIterator(iterableField.iterator);
                    nlist.addAll(iterableField.enrich(newiterations.iterator(), nulls));
                }
            }
        }
        list = nlist;

        return  list;
    }

    // TODO

}
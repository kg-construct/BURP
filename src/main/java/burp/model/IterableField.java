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

    public List<LogicalIteration> enrich(Iterator<Iteration> iterations, Resource referenceFormulation, String iterator, Set<Object> nulls) {
        // Inherit if null
        if (this.iterator == null) this.iterator = iterator;
        if (this.referenceFormulation == null) this.referenceFormulation = referenceFormulation;

        List<LogicalIteration> list = new ArrayList<>();

        int index = 0;
        while(iterations.hasNext()) {
            Iteration i = iterations.next();
            LogicalIteration li = new LogicalIteration(nulls);
            li.put(fieldName + ".#", index++);
            li.put(fieldName, i);

            list.add(li);
        }

        List<LogicalIteration> eslist = new ArrayList<>();
        for(LogicalIteration li : list) {
            for(ExpressionField es : expressionFields) {
                eslist.addAll(es.enRich(fieldName, li));
            }
        }
        list = eslist;


        return  list;
    }

    // TODO

}
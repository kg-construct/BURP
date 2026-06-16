package burp.model;

import burp.ls.LogicalSourceFactory;
import burp.model.lv.LogicalIteration;
import burp.reporting.Origin;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;

public class IterableField extends Field implements FormulationIterable {

    public String iterator;
public Resource declaredReferenceFormulation;
public Origin declaredReferenceFormulationOrigin;
public Resource referenceFormulation;
private PlanNode planNodeParent;

@Override
public Resource getReferenceFormulation() { return referenceFormulation; }
@Override
public void setReferenceFormulation(Resource r) { referenceFormulation = r; }

@Override
public PlanNode getParent() { return planNodeParent; }
@Override
public void setParent(PlanNode p) { planNodeParent = p; }

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
package burp.model.lv;

import burp.ls.LogicalSourceFactory;
import burp.model.*;
import burp.reporting.Origin;
import org.apache.jena.rdf.model.Resource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class IterableField extends Field implements FormulationIterable, LocalReferenceScope {
    private PlanNode parentNode;

    public String iterator;
    public Resource declaredReferenceFormulation;
    public Origin declaredReferenceFormulationOrigin;

    @Override
    public Resource getReferenceFormulation() {
        return declaredReferenceFormulation != null ? declaredReferenceFormulation : getAncestorReferenceFormulation();
    }

    @Override
    public PlanNode getParent() {
        return parentNode;
    }

    @Override
    public void setParent(PlanNode p) {
        parentNode = p;
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        if (declaredReferenceFormulation == null) {
            LocalReferenceScope ancestorReferenceScope = ancestor(LocalReferenceScope.class);
            if (ancestorReferenceScope == null) {
                throw new IllegalArgumentException("No ancestor reference formulation scope in " + this);
            }
            return ancestorReferenceScope.buildLocalReference(reference, origin);
        }

        return LogicalSourceFactory.buildReference(
                declaredReferenceFormulation,
                reference,
                origin,
                declaredReferenceFormulationOrigin
        );
    }

    public List<LogicalIteration> enrich(LogicalIteration underlying) {
        var list = new ArrayList<LogicalIteration>();

        // The iterator has changed
        // We take the iterator from the parent
        var iterationContent = underlying.getIterationString(parentField.getAbsoluteFieldName());
        var changedIterator = LogicalSourceFactory.changeIterator(
                iterationContent,
                // The reference formulation has changed.
                declaredReferenceFormulation != null
                        ? declaredReferenceFormulation
                        : getAncestorReferenceFormulation(),
                iterator,
                declaredReferenceFormulationOrigin
        );

        for (int index = 0; index < changedIterator.size(); index++) {
            var iteration = changedIterator.get(index);
            var e = underlying.copy();
            e.put(getAbsoluteFieldName() + ".#", index);
            e.put(getAbsoluteFieldName(), iteration);
            list.add(e);
        }

        return expand(list, expressionFields, iterableFields);
    }

    @Nullable
    private Resource getAncestorReferenceFormulation() {
        // Since we explicitly created an IterableField for the rood. the two lines below should suffice.
        if (declaredReferenceFormulation != null) return declaredReferenceFormulation;
        if (parentField instanceof IterableField)
            return ((IterableField) parentField).getAncestorReferenceFormulation();
        if (parentField instanceof LogicalSource) return ((LogicalSource) parentField).getReferenceFormulation();
        return null;
    }

}
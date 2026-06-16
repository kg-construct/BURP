package burp.model.lv;

import burp.model.*;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.BURP;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogicalView extends AbstractLogicalSource implements ContainsFields, LocalReferenceScope {
    private List<LogicalIteration> iterations = null;

    public AbstractLogicalSource logicalSource;

    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

    public List<ViewJoin> joins = new ArrayList<>();

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        list.add(logicalSource);
        for (ExpressionField e : expressionFields) {
            if (e.fieldExpressionMap != null) list.add(e.fieldExpressionMap);
        }
        list.addAll(joins);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public Iterable<Iteration> iterator() throws BurpException {
        if (iterations == null) {
            iterations = new ArrayList<>();

            List<LogicalIteration> viewOnIterations = new ArrayList<>();
            int index = 0;
            for (Iteration iteration : logicalSource.iterator()) {
                LogicalIteration li = new LogicalIteration(logicalSource.nulls);
                li.put("#", index++);
                li.put("<i>", iteration);
                viewOnIterations.add(li);
            }

            iterations = Field.expand(viewOnIterations, expressionFields, iterableFields);

            for (ViewJoin join : joins) {
                iterations = join.expand(iterations);
            }
        }

        return (List<Iteration>) (List) iterations;
    }

    @Override
    public List<ExpressionField> getExpressionFields() {
        return expressionFields;
    }

    @Override
    public List<IterableField> getIterableFields() {
        return iterableFields;
    }

    public void addField(Field field) {
        field.parent = this.logicalSource;

        if (field instanceof IterableField) {
            iterableFields.add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            expressionFields.add((ExpressionField) field);
        } else {
            throw new RuntimeException("Unknown field type.");
        }
    }

    public void addJoin(ViewJoin join) {
        joins.add(join);
    }

    @Override
    public Resource getReferenceFormulation() {
        return BURP.LogicalView;
    }

    @Override
    public void setReferenceFormulation(Resource referenceFormulation) {
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new LogicalReference(reference, origin);
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        return logicalSource.buildExportedReference(reference, origin);
    }
}

class LogicalReference extends Reference {
    public LogicalReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof LogicalIteration)) {
            throw new IllegalArgumentException("LogicalReference " + reference + " can only be used with LogicalIteration.");
        }

        LogicalIteration li = (LogicalIteration) i;

        if (!li.map.containsKey(reference)) {
            throw new BurpException(new RmlError(
                    "Attribute " + reference + " does not exist.",
                    origin,
                    RER.ReferenceFormulationExecutionError,
                    null,
                    Collections.singletonMap(RER.reference, reference)
            ));
        }

        Object o = li.map.get(reference);

        if (o instanceof Iteration) {
            throw new BurpException(new RmlError(
                    "Attribute " + reference + " refers to a record key.",
                    origin,
                    RER.ReferenceFormulationExecutionError,
                    null,
                    Collections.singletonMap(RER.reference, reference)
            ));
        }

        if (li.nulls != null && li.nulls.contains(o)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(o);
    }
}



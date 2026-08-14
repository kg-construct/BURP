package burp.model.lv;

import burp.model.ConcreteExpressionMap;
import burp.model.PlanNode;
import burp.model.TemplateReferenceSafety;
import burp.reporting.BurpException;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.ArrayList;
import java.util.List;

public class ExpressionField extends Field {

    public ConcreteExpressionMap fieldExpressionMap;

    public List<LogicalIteration> enrich(LogicalIteration underlying) {
        var underlyingIteration = underlying.getIteration(parentField.getAbsoluteFieldName());
        if (underlyingIteration == null)
            throw new BurpException(
                    new RmlError(
                            "Cannot get iterations for ${parentField.absoluteFieldName}, which is required for expression field $absoluteFieldName.",
                            null, // TODO: Add origin to field
                            RER.ReferenceFormulationExecutionError
                    )
            );

        var generatedValues = fieldExpressionMap.generateValues(underlyingIteration, TemplateReferenceSafety.SAFE_IRI);
        var list = new ArrayList<LogicalIteration>();
        if (generatedValues.isEmpty()) {
            //FIXME: How should we register that a field with no result ?
            // So that later on they can request that field an receive nothing (example with RMLLVTC0010b)
            underlying.put(getAbsoluteFieldName() + ".#", null);
            underlying.put(getAbsoluteFieldName(), null);
            list.add(underlying);
        } else {
            for (var index = 0; index < generatedValues.size(); index++) {
                var output = generatedValues.get(index);
                var e = underlying.copy();
                e.put(getAbsoluteFieldName() + ".#", index);
                e.put(getAbsoluteFieldName(), output);
                list.add(e);
            }
        }

        return Field.expand(list, expressionFields, iterableFields);
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        if (fieldExpressionMap != null) children.add(fieldExpressionMap);
        for (PlanNode child : super.children()) children.add(child);
        return children;
    }

}

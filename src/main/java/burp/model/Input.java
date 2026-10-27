package burp.model;

import burp.model.key.AstKey;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@RdfType(Rml.Input)
public class Input extends AbstractPlanNode {
    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return subject;
    }

    @RdfProperty(Rml.parameterMap)
    @RdfShortcutProperty(shortcutUri = Rml.parameter, targetProperty = Rml.constant)
    public ParameterMap parameterMap;

    @RdfProperty(Rml.inputValueMap)
    @RdfShortcutProperty(shortcutUri = Rml.inputValue, targetProperty = Rml.constant)
    public InputValueMap inputValueMap;

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        if (parameterMap != null) list.add(parameterMap);
        if (inputValueMap != null) list.add(inputValueMap);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public AstKey computeAstKey() {
        String name = (parameterMap != null && parameterMap.expression instanceof RDFNodeConstant c && c.constant != null)
                ? c.constant.toString()
                : (parameterMap != null ? String.valueOf(parameterMap.getAstKey().hashCode()) : "");
        AstKey childKey = inputValueMap != null ? inputValueMap.getAstKey() : null;
        return new AstKey.InputKey(name, childKey);
    }
}


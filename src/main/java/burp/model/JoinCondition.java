package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import burp.reporting.Origin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RdfType(Rml.JoinCondition)
public class JoinCondition extends AbstractPlanNode {
    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return subject;
    }
    @RdfProperty(Rml.parentMap)
    @RdfShortcutProperty(shortcutUri = Rml.parent, targetProperty = Rml.reference)
    public ParentMap parentMap;

    @RdfProperty(Rml.childMap)
    @RdfShortcutProperty(shortcutUri = Rml.child, targetProperty = Rml.reference)
    public ChildMap childMap;

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        if (parentMap != null) list.add(parentMap);
        if (childMap != null) list.add(childMap);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (childMap != null && childMap.expression != null) {
            Origin orig = childMap.originOf("reference");
            if (orig == null) orig = originOf("childMap");
            String val = childMap.expression instanceof Reference r && r.getReference() != null
                    ? r.getReference()
                    : childMap.expression.toString();
            sink.put("child", val, orig);
        }
        if (parentMap != null && parentMap.expression != null) {
            Origin orig = parentMap.originOf("reference");
            if (orig == null) orig = originOf("parentMap");
            String val = parentMap.expression instanceof Reference r && r.getReference() != null
                    ? r.getReference()
                    : parentMap.expression.toString();
            sink.put("parent", val, orig);
        }
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        //TODO Good question what is the result of a join condition ?
        //  booleans, pairs of matches, both ?
        //  how to provide the iterations as we need multiple here.
        return Collections.emptyList();
    }

    @Override
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        if (childMap != null) childKeys.add(childMap.getAstKey());
        if (parentMap != null) childKeys.add(parentMap.getAstKey());
        return new AstKey.JoinConditionKey(childKeys);
    }
}

package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.Term;
import burp.parse.loader.annotations.RdfProperty;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RDFNodeConstant extends AbstractPlanNode implements Expression {

    @RdfProperty(Rml.constant)
    public Term constant;

    @Override
    public @Nullable Resource getIri() {
        return null;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (constant != null) {
            sink.put("constant", constant.toString(), originOf("constant"));
        }
    }

    public RDFNodeConstant() {
    }

    public RDFNodeConstant(Term constant) {
        this.constant = constant;
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(Iteration iter, @NonNull EvaluationContext context) {
        if (constant == null) return Collections.emptyList();
        return List.of(constant);
    }

    @Override
    public AstKey computeAstKey() {
        return new AstKey.ConstantKey(constant != null ? constant.toString() : "");
    }
}
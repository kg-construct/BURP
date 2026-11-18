package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.LiteralTerm;
import burp.parse.loader.annotations.RdfProperty;
import burp.reporting.Origin;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Reference extends AbstractPlanNode implements Expression {
    @Override
    public @Nullable Resource getIri() {
        return null;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (reference != null) {
            sink.put("reference", reference, originOf("reference"));
        }
    }

    @RdfProperty(Rml.reference)
    protected String reference;

    public Reference() {
        this.reference = null;
    }

    public Reference(String reference) {
        this.reference = reference;
    }

    public Reference(String reference, @Nullable Origin origin) {
        this.reference = reference;
        if (origin != null) {
            putOrigin("reference", origin);
        }
    }

    public String getReference() {
        return reference;
    }

    @Override
    public AstKey computeAstKey() {
        return new AstKey.ReferenceKey(reference != null ? reference : "");
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        LocalReferenceScope scope = ancestor(LocalReferenceScope.class);
        if (scope != null && scope.getLogicalSource() != null) {
            return List.of(scope.getLogicalSource());
        }
        return Collections.emptyList();
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        if (iter == null) return Collections.emptyList();
        List<RmlEvaluationResult> resList = new ArrayList<>();
        for (String str : getStrings(iter)) {
            resList.add(new LiteralTerm(str));
        }
        return resList;
    }

    public List<Object> values(@Nullable Iteration i) {
        if (i == null) return Collections.emptyList();
        return getValues(i);
    }

    public abstract List<Object> getValues(Iteration i);

    public List<String> getStrings(@Nullable Iteration i) {
        if (i == null) return Collections.emptyList();
        return getValues(i).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }
}
package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.rdf.Term;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface TermGenerator extends PlanNode {
    List<Term> generateTerms(@Nullable Iteration i, EvaluationContext context);

    default List<Term> generateTerms(@Nullable Iteration i) {
        return generateTerms(i, EvaluationContext.createDefault());
    }

    @Override
    default List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        return new ArrayList<>(generateTerms(iter, context));
    }
}

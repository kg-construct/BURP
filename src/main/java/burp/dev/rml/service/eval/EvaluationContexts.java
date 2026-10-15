package burp.dev.rml.service.eval;

import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.lv.IterableField;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static burp.parse.ValidationMode.LENIENT;

/**
 * Shared helper for resolving logical sources, base IRIs, evaluation contexts,
 * and node evaluation scopes across RmlEvaluator and InlineEvaluator.
 */
@NullMarked
public final class EvaluationContexts {

    private EvaluationContexts() {}

    public record NodeEvaluationScope(
            Iteration iteration,
            EvaluationContext context
    ) {}

    /**
     * Resolves the effective logical source for an AST node using its enclosing LocalReferenceScope.
     */
    public static @Nullable AbstractLogicalSource resolveLogicalSource(@Nullable PlanNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof AbstractLogicalSource ls) {
            return ls;
        }
        LocalReferenceScope scope = node instanceof LocalReferenceScope ls ? ls : node.ancestor(LocalReferenceScope.class);
        return scope != null ? scope.getLogicalSource() : null;
    }

    /**
     * Expands base logical source iterations through any enclosing {@link IterableField}s of {@code node}.
     */
    public static List<Iteration> resolveNodeIterations(PlanNode node, List<Iteration> baseIterations) {
        if (baseIterations.isEmpty()) {
            return baseIterations;
        }
        List<IterableField> iterableFields = collectEnclosingIterableFields(node);
        if (iterableFields.isEmpty()) {
            return baseIterations;
        }
        List<Iteration> currentIterations = baseIterations;
        for (IterableField inf : iterableFields) {
            List<Iteration> next = new ArrayList<>();
            for (Iteration iter : currentIterations) {
                next.addAll(inf.extractSubIterations(iter));
            }
            currentIterations = next;
        }
        return currentIterations;
    }

    public static List<IterableField> collectEnclosingIterableFields(PlanNode node) {
        List<IterableField> iterableFields = new ArrayList<>();
        PlanNode current = node;
        while (current != null && !(current instanceof AbstractLogicalSource)) {
            if (current instanceof IterableField inf) {
                iterableFields.addFirst(inf);
            }
            current = current.getParent();
        }
        return iterableFields;
    }

    /**
     * Resolves the effective base IRI for an AST node using its enclosing BaseIRIScope.
     */
    public static @Nullable String resolveBaseIri(PlanNode node, @Nullable String defaultBaseIri) {
        BaseIRIScope baseScope = node instanceof BaseIRIScope bs ? bs : node.ancestor(BaseIRIScope.class);
        if (baseScope == null) {
            AbstractLogicalSource ls = resolveLogicalSource(node);
            if (ls != null) {
                baseScope = ls.ancestor(BaseIRIScope.class);
            }
        }
        return baseScope != null ? baseScope.getBaseIri() : defaultBaseIri;
    }

    /**
     * Resolves the evaluation scope (Iteration and EvaluationContext) for a given AST node.
     */
    public static @Nullable NodeEvaluationScope resolveNodeScope(
            PlanNode node,
            EvaluationDataContext dataContext,
            @Nullable AbstractLogicalSource defaultSource,
            @Nullable Iteration defaultIteration,
            @Nullable EvaluationContext defaultContext,
            RmlEvaluator evaluator
    ) {
        AbstractLogicalSource targetSource = resolveLogicalSource(node);
        boolean hasEnclosingIterableField = !collectEnclosingIterableFields(node).isEmpty();
        if ((targetSource == null || targetSource == defaultSource) && !hasEnclosingIterableField) {
            return (defaultIteration != null && defaultContext != null)
                    ? new NodeEvaluationScope(defaultIteration, defaultContext)
                    : null;
        }

        try {
            var iters = resolveNodeIterations(node, evaluator.resolveIterations(dataContext, targetSource));
            if (!iters.isEmpty()) {
                Iteration iter = iters.getFirst();
                String baseIri = resolveBaseIri(node, defaultContext != null ? defaultContext.baseIri() : null);
                EvaluationContext ctx = (defaultContext != null)
                        ? defaultContext.withBaseIri(baseIri)
                        : EvaluationContext.createDefault().withBaseIri(baseIri).withValidationMode(LENIENT);
                return new NodeEvaluationScope(iter, ctx);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

package burp.model.context;

import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.LogicalTarget;
import burp.model.PlanNode;
import burp.model.RmlEvaluationResult;
import burp.model.TemplateReferenceSafety;
import burp.model.join.ParentJoinIndex;
import burp.parse.ValidationMode;
import burp.reporting.RmlExecutionReport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Unified, immutable evaluation context available to AST nodes during evaluation.
 * Starts from {@link #createDefault()} and customises state via fluent chained {@code with*} methods.
 * Relly why Java doest have optional and named arguments?
 */
@NullMarked
public record EvaluationContext(
        RmlExecutionReport report,
        @Nullable String baseIri,
        BlankNodeScope blankNodeScope,
        Set<LogicalTarget> activeTargets,
        CancellationToken cancellationToken,
        Map<AbstractLogicalSource, List<Iteration>> sourceIterations,
        Map<String, List<RmlEvaluationResult>> nodeOverrides,
        Map<PlanNode, ParentJoinIndex> joinIndexes,
        ValidationMode validationMode,
        TemplateReferenceSafety templateSafety
) {
    public EvaluationContext {
        activeTargets = Set.copyOf(activeTargets);
        sourceIterations = Map.copyOf(sourceIterations);
        nodeOverrides = Map.copyOf(nodeOverrides);
        joinIndexes = Map.copyOf(joinIndexes);
    }

    public static EvaluationContext createDefault() {
        return new EvaluationContext(
                new RmlExecutionReport(),
                null,
                new BlankNodeScope(),
                Collections.emptySet(),
                new CancellationToken(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                ValidationMode.STRICT,
                TemplateReferenceSafety.UNSAFE
        );
    }

    public @Nullable ParentJoinIndex getJoinIndex(PlanNode node) {
        return joinIndexes.get(node);
    }

    public Iterable<Iteration> getIterations(@Nullable AbstractLogicalSource source) {
        if (source == null) return Collections.emptyList();
        List<Iteration> iters = sourceIterations.get(source);
        if (iters != null) {
            return iters;
        }
        var it = source.iterator();
        return it != null ? it : Collections.emptyList();
    }

    public EvaluationContext withReport(RmlExecutionReport newReport) {
        return new EvaluationContext(newReport, baseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withBaseIri(@Nullable String newBaseIri) {
        return new EvaluationContext(report, newBaseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withBlankNodeScope(BlankNodeScope newScope) {
        return new EvaluationContext(report, baseIri, newScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withTargets(Set<LogicalTarget> targets) {
        return new EvaluationContext(report, baseIri, blankNodeScope, targets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withCancellationToken(CancellationToken newToken) {
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, newToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withValidationMode(ValidationMode newValidationMode) {
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, newValidationMode, templateSafety);
    }

    public EvaluationContext withTemplateSafety(TemplateReferenceSafety safety) {
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, joinIndexes, validationMode, safety);
    }

    public EvaluationContext withSourceIterations(Map<AbstractLogicalSource, List<Iteration>> newSourceIterations) {
        Map<AbstractLogicalSource, List<Iteration>> merged = new HashMap<>(sourceIterations);
        merged.putAll(newSourceIterations);
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, cancellationToken, merged, nodeOverrides, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withNodeOverrides(Map<String, List<RmlEvaluationResult>> newOverrides) {
        Map<String, List<RmlEvaluationResult>> merged = new HashMap<>(nodeOverrides);
        merged.putAll(newOverrides);
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, merged, joinIndexes, validationMode, templateSafety);
    }

    public EvaluationContext withJoinIndexes(Map<PlanNode, ParentJoinIndex> newIndexes) {
        Map<PlanNode, ParentJoinIndex> merged = new HashMap<>(joinIndexes);
        merged.putAll(newIndexes);
        return new EvaluationContext(report, baseIri, blankNodeScope, activeTargets, cancellationToken, sourceIterations, nodeOverrides, merged, validationMode, templateSafety);
    }
}

package burp.dev.rml.service.eval;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// Provenance reason explaining why an iteration index was chosen for inline evaluation.
@NullMarked
public sealed interface IterationReason {

    int iterationIndex();

    @Nullable String format(@Nullable Map<String, String> prefixMap);

    @Nullable String formatSummary(@Nullable Map<String, String> prefixMap);

    record Direct(int iterationIndex) implements IterationReason {
        @Override
        public @Nullable String format(@Nullable Map<String, String> prefixMap) {
            return null;
        }

        @Override
        public @Nullable String formatSummary(@Nullable Map<String, String> prefixMap) {
            return null;
        }
    }

    record MatchedJoin(
            String sourceNodeId,
            @Nullable String sourceIri,
            int sourceIterationIndex,
            int iterationIndex,
            @Nullable String joinConditionSummary
    ) implements IterationReason {
        @Override
        public String format(@Nullable Map<String, String> prefixMap) {
            String label = sourceIri != null
                    ? RmlEvaluationResultFormatter.compactIri(sourceIri, prefixMap)
                    : sourceNodeId;
            if (joinConditionSummary != null && !joinConditionSummary.isBlank()) {
                return "Matched via join `" + joinConditionSummary + "` from " + label + " [iteration #" + sourceIterationIndex + "]";
            }
            return "Matched via join from " + label + " [iteration #" + sourceIterationIndex + "]";
        }

        @Override
        public String formatSummary(@Nullable Map<String, String> prefixMap) {
            String label = sourceIri != null
                    ? RmlEvaluationResultFormatter.compactIri(sourceIri, prefixMap)
                    : sourceNodeId;
            if (joinConditionSummary != null && !joinConditionSummary.isBlank()) {
                return "Matched via join " + joinConditionSummary + " from " + label + " Iteration #" + sourceIterationIndex;
            }
            return "Matched via join from " + label + " Iteration #" + sourceIterationIndex;
        }
    }
}

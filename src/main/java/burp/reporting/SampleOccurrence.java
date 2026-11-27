package burp.reporting;

import burp.model.OriginLineage;
import burp.model.RmlEvaluationResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A single sampled occurrence of an {@link AggregatedRmlError}, capturing iteration provenance
/// and any non-fatal evaluation result produced for that occurrence.
@NullMarked
public record SampleOccurrence(
        @Nullable OriginLineage originLineage,
        @Nullable RmlEvaluationResult evaluationResult
) {
}

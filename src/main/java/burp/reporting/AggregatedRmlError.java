package burp.reporting;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// Bounded aggregation of repeated {@link RmlError}s sharing the same error type and mapping origin.
@NullMarked
public record AggregatedRmlError(
        RmlError primary,
        List<SampleOccurrence> samples,
        int additionalCount
) {
}

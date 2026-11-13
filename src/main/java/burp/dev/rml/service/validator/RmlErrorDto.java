package burp.dev.rml.service.validator;

import burp.reporting.AggregatedRmlError;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.SampleOccurrence;
import burp.vocabularies.RER;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/// Jena-free wire DTO projecting an {@link AggregatedRmlError} for JSON-RPC and LSP consumers.
@NullMarked
public record RmlErrorDto(
        String message,
        String errorTypeUri,
        List<String> superClassUris,
        String severity,
        List<PointRange> ranges,
        List<SampleOccurrence> samples,
        int additionalCount,
        @Nullable String source
) {
    public RmlErrorDto {
        superClassUris = superClassUris != null ? List.copyOf(superClassUris) : Collections.emptyList();
        ranges = ranges != null ? List.copyOf(ranges) : Collections.emptyList();
        samples = samples != null ? List.copyOf(samples) : Collections.emptyList();
    }

    public static RmlErrorDto from(AggregatedRmlError agg, @Nullable String source) {
        RmlError primary = agg.primary();
        Origin origin = primary.origin();
        List<PointRange> ranges = origin != null ? origin.ranges() : Collections.emptyList();
        String errorTypeUri = primary.errorType() != null && primary.errorType().getURI() != null
                ? primary.errorType().getURI()
                : RER.Error.getURI();
        return new RmlErrorDto(
                primary.message(),
                errorTypeUri,
                primary.superClassUris(),
                primary.severity().name(),
                ranges,
                agg.samples(),
                agg.additionalCount(),
                source
        );
    }

    public static RmlErrorDto from(RmlError error, @Nullable String source) {
        Origin origin = error.origin();
        List<PointRange> ranges = origin != null ? origin.ranges() : Collections.emptyList();
        String errorTypeUri = error.errorType() != null && error.errorType().getURI() != null
                ? error.errorType().getURI()
                : RER.Error.getURI();
        List<SampleOccurrence> samples = (error.originLineage() != null || error.evaluationResult() != null)
                ? List.of(new SampleOccurrence(error.originLineage(), error.evaluationResult()))
                : Collections.emptyList();
        return new RmlErrorDto(
                error.message(),
                errorTypeUri,
                error.superClassUris(),
                error.severity().name(),
                ranges,
                samples,
                0,
                source
        );
    }
}

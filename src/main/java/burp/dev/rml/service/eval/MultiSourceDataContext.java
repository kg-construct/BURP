package burp.dev.rml.service.eval;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Maps logical source identifiers (relative file path, URI, or TriplesMap IRI)
 * to their specific EvaluationDataContext.
 */
@NullMarked
public record MultiSourceDataContext(
        Map<String, EvaluationDataContext> sources,
        @Nullable EvaluationDataContext defaultContext
) implements EvaluationDataContext {

    public EvaluationDataContext getForSource(@Nullable String sourceId) {
        if (sourceId != null && sources.containsKey(sourceId)) {
            return sources.get(sourceId);
        }
        // If no default context is available, return an empty data range.
        return defaultContext != null ? defaultContext : new RangeDataContext(0, 0);
    }
}

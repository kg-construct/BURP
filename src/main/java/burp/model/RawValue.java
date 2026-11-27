package burp.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Encapsulates an arbitrary non-RDF Java evaluation result (e.g. Boolean, Number, String,
 * or custom object returned from an FNML function execution or expression).
 */
@NullMarked
public record RawValue(
        @Nullable Object value,
        Set<LogicalTarget> targets,
        @Nullable OriginLineage lineage
) implements RmlEvaluationResult {

    public RawValue(@Nullable Object value, Set<LogicalTarget> targets) {
        this(value, targets, null);
    }

    public RawValue(@Nullable Object value) {
        this(value, Set.of(), null);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
package burp.model;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite identifier representing a single iteration offset within a logical source.
 */
@NullMarked
public record IterationId(String logicalSourceId, int index) {
    @Override
    public String toString() {
        return logicalSourceId + "#" + index;
    }
}

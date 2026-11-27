package burp.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the iteration lineage of an evaluation result or record,
 * capturing the primary source iteration and any joined/parent iterations.
 */
@NullMarked
public record OriginLineage(IterationId primary, List<IterationId> joined)  {

    public static OriginLineage of(IterationId primary) {
        return new OriginLineage(primary, List.of());
    }

    public OriginLineage withJoined(@Nullable IterationId joinedId) {
        if (joinedId == null || joinedId.equals(this.primary) || this.joined.contains(joinedId)) {
            return this;
        }
        List<IterationId> newJoined = new ArrayList<>(this.joined);
        newJoined.add(joinedId);
        return new OriginLineage(this.primary, newJoined);
    }

    public OriginLineage merge(@Nullable OriginLineage other) {
        if (other == null) {
            return this;
        }
        OriginLineage result = this;
        if (!other.primary().equals(this.primary)) {
            result = result.withJoined(other.primary());
        }
        for (IterationId j : other.joined()) {
            result = result.withJoined(j);
        }
        return result;
    }
}

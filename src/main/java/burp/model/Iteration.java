package burp.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@NullMarked
public abstract class Iteration {
    public final Set<Object> nulls;
    private final @Nullable OriginLineage lineage;

    public Set<Object> getNulls() {
        return nulls;
    }

    public @Nullable OriginLineage getLineage() {
        return lineage;
    }

    public @Nullable IterationId getId() {
        return lineage != null ? lineage.primary() : null;
    }

    public Iteration(@Nullable OriginLineage lineage, Set<Object> nulls) {
        this.lineage = lineage;
        this.nulls = nulls;
    }

    public Iteration(@Nullable IterationId id, Set<Object> nulls) {
        this(id != null ? OriginLineage.of(id) : null, nulls);
    }

    public Iteration(Set<Object> nulls) {
        this((OriginLineage) null, nulls);
    }

    /// Get the iteration data as a string
    ///
    /// @return String serialized iteration data
    public abstract String asString();
}
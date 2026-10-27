package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public record BlankNodeTerm(
        String id,
        Set<LogicalTarget> targets,
        @Nullable OriginLineage lineage
) implements BlankNodeOrIRI {

    public BlankNodeTerm(String id, Set<LogicalTarget> targets) {
        this(id, targets, null);
    }

    public BlankNodeTerm(String id) {
        this(id, Set.of(), null);
    }

    @Override
    public @NonNull String toString() {
        return "_:" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlankNodeTerm other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

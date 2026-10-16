package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public record IRITerm(
        String uri,
        Set<LogicalTarget> targets,
        @Nullable OriginLineage lineage
) implements BlankNodeOrIRI {

    public IRITerm(String uri, Set<LogicalTarget> targets) {
        this(uri, targets, null);
    }

    public IRITerm(String uri) {
        this(uri, Set.of(), null);
    }

    @Override
    public @NonNull String toString() {
        return "<" + uri + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IRITerm other)) return false;
        return Objects.equals(uri, other.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uri);
    }
}

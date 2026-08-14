package burp.model.rdf;

import burp.model.LogicalTarget;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public record BlankNodeTerm(String id, Set<LogicalTarget> targets) implements BlankNodeOrIRI {
    public BlankNodeTerm(String id) {
        this(id, Set.of());
    }

    @Override
    public @NonNull String toString() {
        return "_:" + id;
    }
}

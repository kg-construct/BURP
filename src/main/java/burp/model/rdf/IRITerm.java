package burp.model.rdf;

import burp.model.LogicalTarget;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public record IRITerm(String uri, Set<LogicalTarget> targets) implements BlankNodeOrIRI {
    public IRITerm(String uri) {
        this(uri, Set.of());
    }

    @Override
    public @NonNull String toString() {
        return "<" + uri + ">";
    }
}

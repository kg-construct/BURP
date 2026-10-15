package burp.model.join;

import burp.model.rdf.Datardf;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Normalized key for join attribute values ensuring semantic equality across
 * RDF Term representations and raw primitive values.
 */
public record CanonicalJoinKey(Object normalizedValue) {

    public static CanonicalJoinKey of(@Nullable Object raw) {
        if (raw == null) {
            return new CanonicalJoinKey(null);
        }
        Term term = Datardf.toTerm(raw);
        if (term instanceof LiteralTerm lt) {
            return new CanonicalJoinKey(lt.value());
        }
        if (term instanceof IRITerm iri) {
            return new CanonicalJoinKey(iri.uri());
        }
        return new CanonicalJoinKey(term != null ? term : raw);
    }

    public boolean isNull() {
        return normalizedValue == null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CanonicalJoinKey other)) return false;
        return Objects.equals(this.normalizedValue, other.normalizedValue);
    }

    @Override
    public int hashCode() {
        return normalizedValue != null ? normalizedValue.hashCode() : 0;
    }
}

package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public record LiteralTerm(
        String value,
        @Nullable IRITerm datatype,
        @Nullable String language,
        Set<LogicalTarget> targets,
        @Nullable OriginLineage lineage
) implements Term {

    public LiteralTerm(String value, @Nullable IRITerm datatype, @Nullable String language, Set<LogicalTarget> targets) {
        this(value, datatype, language, targets, null);
    }

    public LiteralTerm(String value, @Nullable IRITerm datatype, @Nullable String language) {
        this(value, datatype, language, Set.of(), null);
    }

    public LiteralTerm(String value, @Nullable IRITerm datatype) {
        this(value, datatype, null, Set.of(), null);
    }

    public LiteralTerm(String value) {
        this(value, null, null, Set.of(), null);
    }

    @Override
    public @NonNull String toString() {
        if (language != null) {
            return "\"" + value + "\"@" + language;
        } else if (datatype != null) {
            return "\"" + value + "\"^^" + datatype.uri();
        } else {
            return "\"" + value + "\"";
        }
    }

    public @Nullable Integer intOrNull() {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public @Nullable Double doubleOrNull() {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public @Nullable Boolean booleanOrNull() {
        String lower = value.toLowerCase();
        if ("true".equals(lower) || "1".equals(lower)) return true;
        if ("false".equals(lower) || "0".equals(lower)) return false;
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteralTerm other)) return false;
        return Objects.equals(value, other.value) &&
                Objects.equals(datatype, other.datatype) &&
                Objects.equals(language, other.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, datatype, language);
    }
}

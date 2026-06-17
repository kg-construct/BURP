package burp.model.rdf;

import burp.model.LogicalTarget;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public record LiteralTerm(String value, IRITerm datatype, String language, Set<LogicalTarget> targets) implements Term {
    
    public LiteralTerm(String value, IRITerm datatype, String language) {
        this(value, datatype, language, Set.of());
    }

    public LiteralTerm(String value, IRITerm datatype) {
        this(value, datatype, null, Set.of());
    }

    public LiteralTerm(String value) {
        this(value, null, null, Set.of());
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

    public Integer intOrNull() {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double doubleOrNull() {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean booleanOrNull() {
        String lower = value.toLowerCase();
        if ("true".equals(lower) || "1".equals(lower)) return true;
        if ("false".equals(lower) || "0".equals(lower)) return false;
        return null;
    }
}

package burp.parse.turtleprov;

import burp.reporting.PointRange;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ProvStore {
    public final Set<ProvTriple> triples = new HashSet<>();
    public final Map<String, String> prefixes = new HashMap<>();
    public final List<SyntaxError> syntaxErrors = new ArrayList<>();

    public Set<ProvTriple> getTriples() {
        return triples;
    }

    public Map<String, String> getPrefixes() {
        return prefixes;
    }

    @NotNull
    public List<SyntaxError> getSyntaxErrors() {
        return syntaxErrors;
    }

    public static class SyntaxError {
        public final int line;
        public final int charPositionInLine;
        public final String message;
        public final String offendingSymbol;
        public final PointRange range;

        public SyntaxError(int line, int charPositionInLine, String message, String offendingSymbol, PointRange range) {
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.message = message;
            this.offendingSymbol = offendingSymbol;
            this.range = range;
        }

        public SyntaxError(int line, int charPositionInLine, String message, String offendingSymbol) {
            this(line, charPositionInLine, message, offendingSymbol, null);
        }

        @Override
        public String toString() {
            return "line " + line + ":" + charPositionInLine + " " + message;
        }
    }
}

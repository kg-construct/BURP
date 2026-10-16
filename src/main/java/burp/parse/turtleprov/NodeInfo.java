package burp.parse.turtleprov;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// Represents syntactic and positional provenance information for a parsed Turtle AST node.
///
/// This provenance metadata allows semantic RDF statements and terms to be mapped back
/// to exact line and column coordinates in the raw source document, enabling IDE features
/// such as inline hints, code lenses, diagnostic reporting, and navigation.
@NullMarked
public record NodeInfo(
        /// The syntactic kind of this Turtle node (e.g., IRIREF, STRING_LITERAL_QUOTE, BOOLEAN_LITERAL).
        TurtleNodeKind kind,

        /// The starting coordinate (0-based line and column) of the entire token in the raw document.
        Point start,

        /// The ending coordinate (0-based line and column) of the entire token in the raw document.
        Point end,

        /// The starting coordinate of the inner string content (excluding quotes and delimiters).
        /// Present only for string literals; `null` for other node types.
        @Nullable Point rdfLiteralStringStart,

        /// The ending coordinate of the inner string content (excluding quotes and delimiters).
        /// Present only for string literals; `null` for other node types.
        @Nullable Point rdfLiteralStringEnd,

        /// The blank node identifier, if this node is a blank node; `null` otherwise.
        @Nullable String blankNodeId,

        /// An offset mapper to translate decoded string character offsets to raw literal offsets.
        /// Present for string literals that may contain escape sequences; `null` otherwise.
        @Nullable OffsetMapper offsetMapper) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeInfo nodeInfo)) return false;
        return kind == nodeInfo.kind &&
                Objects.equals(start, nodeInfo.start) &&
                Objects.equals(end, nodeInfo.end) &&
                Objects.equals(rdfLiteralStringStart, nodeInfo.rdfLiteralStringStart) &&
                Objects.equals(rdfLiteralStringEnd, nodeInfo.rdfLiteralStringEnd) &&
                Objects.equals(blankNodeId, nodeInfo.blankNodeId) &&
                Objects.equals(offsetMapper, nodeInfo.offsetMapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, start, end, rdfLiteralStringStart, rdfLiteralStringEnd, blankNodeId, offsetMapper);
    }

    @Override
    public @NonNull String toString() {
        return "NodeInfo[kind=" + kind + ", start=" + start + ", end=" + end +
                ", rdfLiteralStringStart=" + rdfLiteralStringStart +
                ", rdfLiteralStringEnd=" + rdfLiteralStringEnd +
                ", blankNodeId=" + blankNodeId +
                ", offsetMapper=" + offsetMapper + "]";
    }
}

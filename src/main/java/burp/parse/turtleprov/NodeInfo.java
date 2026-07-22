package burp.parse.turtleprov;

import org.antlr.v4.runtime.Token;
import java.util.Objects;

public final class NodeInfo {
    public final TurtleNodeKind kind;
    public final Point start;
    public final Point end;
    public final Point rdfLiteralStringStart;
    public final Point rdfLiteralStringEnd;
    public final String blankNodeId;

    public NodeInfo(TurtleNodeKind kind, Point start, Point end, Point rdfLiteralStringStart, Point rdfLiteralStringEnd, String blankNodeId) {
        this.kind = kind;
        this.start = start;
        this.end = end;
        this.rdfLiteralStringStart = rdfLiteralStringStart;
        this.rdfLiteralStringEnd = rdfLiteralStringEnd;
        this.blankNodeId = blankNodeId;
    }

    public NodeInfo(TurtleNodeKind kind, Point start, Point end, String blankNodeId) {
        this(kind, start, end, null, null, blankNodeId);
    }
    
    public NodeInfo(TurtleNodeKind kind, Token start, Token end, String blankNodeId) {
        this(kind, 
             start != null ? new Point(start.getLine() - 1, start.getCharPositionInLine()) : null,
             end != null ? new Point(end.getLine() - 1, end.getCharPositionInLine()) : null,
             blankNodeId);
    }

    public TurtleNodeKind kind() { return kind; }
    public Point start() { return start; }
    public Point end() { return end; }
    public Point rdfLiteralStringStart() { return rdfLiteralStringStart; }
    public Point rdfLiteralStringEnd() { return rdfLiteralStringEnd; }
    public String blankNodeId() { return blankNodeId; }

    public int[] lineIndexRange() {
        return new int[]{start.line, end.line};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeInfo)) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return kind == nodeInfo.kind &&
                Objects.equals(start, nodeInfo.start) &&
                Objects.equals(end, nodeInfo.end) &&
                Objects.equals(rdfLiteralStringStart, nodeInfo.rdfLiteralStringStart) &&
                Objects.equals(rdfLiteralStringEnd, nodeInfo.rdfLiteralStringEnd) &&
                Objects.equals(blankNodeId, nodeInfo.blankNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, start, end, rdfLiteralStringStart, rdfLiteralStringEnd, blankNodeId);
    }

    @Override
    public String toString() {
        return "NodeInfo[kind=" + kind + ", start=" + start + ", end=" + end +
                ", rdfLiteralStringStart=" + rdfLiteralStringStart +
                ", rdfLiteralStringEnd=" + rdfLiteralStringEnd +
                ", blankNodeId=" + blankNodeId + "]";
    }
}

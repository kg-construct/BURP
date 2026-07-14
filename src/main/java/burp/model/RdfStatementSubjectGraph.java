package burp.model;

import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.IRITerm;

import java.util.Objects;
import java.util.Set;

public final class RdfStatementSubjectGraph implements RdfStatementLike {
    private BlankNodeOrIRI subject;
    private IRITerm graph;
    private final Set<LogicalTarget> targets;

    public RdfStatementSubjectGraph(BlankNodeOrIRI subject, IRITerm graph, Set<LogicalTarget> targets) {
        this.subject = subject;
        this.graph = graph;
        this.targets = targets != null ? targets : Set.of();
    }

    public BlankNodeOrIRI getSubject() { return subject; }
    public void setSubject(BlankNodeOrIRI subject) { this.subject = subject; }

    public IRITerm getGraph() { return graph; }
    public void setGraph(IRITerm graph) { this.graph = graph; }

    @Override
    public Set<LogicalTarget> targets() { return targets; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RdfStatementSubjectGraph that)) return false;
        return Objects.equals(subject, that.subject) &&
               Objects.equals(graph, that.graph) &&
               Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, graph, targets);
    }

    @Override
    public String toString() {
        return "RdfStatementSubjectGraph(subject=" + subject + ", graph=" + graph + ", targets=" + targets + ")";
    }
}

package burp.model;

import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;

import java.util.Objects;
import java.util.Set;

public final class RdfStatement implements RdfStatementLike {
    private BlankNodeOrIRI subject;
    private IRITerm predicate;
    private Term object;
    private IRITerm graph;
    private final Set<LogicalTarget> targets;

    public RdfStatement(BlankNodeOrIRI subject, IRITerm predicate, Term object, IRITerm graph, Set<LogicalTarget> targets) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.graph = graph;
        this.targets = targets != null ? targets : Set.of();
    }

    public RdfStatement(BlankNodeOrIRI subject, IRITerm predicate, Term object) {
        this(subject, predicate, object, null, Set.of());
    }

    public BlankNodeOrIRI getSubject() { return subject; }
    public void setSubject(BlankNodeOrIRI subject) { this.subject = subject; }

    public IRITerm getPredicate() { return predicate; }
    public void setPredicate(IRITerm predicate) { this.predicate = predicate; }

    public Term getObject() { return object; }
    public void setObject(Term object) { this.object = object; }

    public IRITerm getGraph() { return graph; }
    public void setGraph(IRITerm graph) { this.graph = graph; }

    @Override
    public Set<LogicalTarget> targets() { return targets; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RdfStatement that)) return false;
        return Objects.equals(subject, that.subject) &&
               Objects.equals(predicate, that.predicate) &&
               Objects.equals(object, that.object) &&
               Objects.equals(graph, that.graph) &&
               Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, predicate, object, graph, targets);
    }

    @Override
    public String toString() {
        return "RdfStatement(subject=" + subject + ", predicate=" + predicate + ", object=" + object + ", graph=" + graph + ", targets=" + targets + ")";
    }
}

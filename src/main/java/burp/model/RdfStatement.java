package burp.model;

import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@NullMarked
public final class RdfStatement implements RdfStatementLike {
    private BlankNodeOrIRI subject;
    private IRITerm predicate;
    private Term object;
    private @Nullable IRITerm graph;
    private final Set<LogicalTarget> targets;
    private final @Nullable OriginLineage lineage;

    public RdfStatement(
            BlankNodeOrIRI subject,
            IRITerm predicate,
            Term object,
            @Nullable IRITerm graph,
            Set<LogicalTarget> targets,
            @Nullable OriginLineage lineage
    ) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.predicate = Objects.requireNonNull(predicate, "predicate must not be null");
        this.object = Objects.requireNonNull(object, "object must not be null");
        this.graph = graph;
        this.targets = targets != null ? targets : Set.of();
        this.lineage = lineage;
    }

    public RdfStatement(BlankNodeOrIRI subject, IRITerm predicate, Term object, @Nullable IRITerm graph, Set<LogicalTarget> targets) {
        this(subject, predicate, object, graph, targets, null);
    }

    public RdfStatement(BlankNodeOrIRI subject, IRITerm predicate, Term object, @Nullable IRITerm graph) {
        this(subject, predicate, object, graph, Set.of(), null);
    }

    public RdfStatement(BlankNodeOrIRI subject, IRITerm predicate, Term object) {
        this(subject, predicate, object, null, Set.of(), null);
    }

    public BlankNodeOrIRI getSubject() {
        return subject;
    }

    public void setSubject(BlankNodeOrIRI subject) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    public IRITerm getPredicate() {
        return predicate;
    }

    public void setPredicate(IRITerm predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate must not be null");
    }

    public Term getObject() {
        return object;
    }

    public void setObject(Term object) {
        this.object = Objects.requireNonNull(object, "object must not be null");
    }

    public @Nullable IRITerm getGraph() {
        return graph;
    }

    public void setGraph(@Nullable IRITerm graph) {
        this.graph = graph;
    }

    @Override
    public Set<LogicalTarget> targets() {
        return targets;
    }

    @Override
    public @Nullable OriginLineage lineage() {
        return lineage;
    }

    public @Nullable OriginLineage getLineage() {
        return lineage;
    }

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
        return "RdfStatement(subject=" + subject + ", predicate=" + predicate + ", object=" + object + ", graph=" + graph + ", targets=" + targets + ", lineage=" + lineage + ")";
    }
}

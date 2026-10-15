package burp.model;

import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.IRITerm;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@NullMarked
public final class RdfStatementSubjectGraph implements RdfStatementLike {
    private BlankNodeOrIRI subject;
    private @Nullable IRITerm graph;
    private final Set<LogicalTarget> targets;
    private final @Nullable OriginLineage lineage;

    public RdfStatementSubjectGraph(
            BlankNodeOrIRI subject,
            @Nullable IRITerm graph,
            Set<LogicalTarget> targets,
            @Nullable OriginLineage lineage
    ) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.graph = graph;
        this.targets = targets != null ? targets : Set.of();
        this.lineage = lineage;
    }

    public RdfStatementSubjectGraph(BlankNodeOrIRI subject, @Nullable IRITerm graph, Set<LogicalTarget> targets) {
        this(subject, graph, targets, null);
    }

    public BlankNodeOrIRI getSubject() {
        return subject;
    }

    public void setSubject(BlankNodeOrIRI subject) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
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
        return "RdfStatementSubjectGraph(subject=" + subject + ", graph=" + graph + ", targets=" + targets + ", lineage=" + lineage + ")";
    }
}

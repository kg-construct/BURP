package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract sealed class CollectionOrContainerTerm implements BlankNodeOrIRI permits RdfListTerm, RdfBagTerm, RdfSeqTerm, RdfAltTerm {
    private final List<Term> elements;
    private BlankNodeOrIRI id;
    private final boolean idGenerated;
    private Set<LogicalTarget> targets;
    private @Nullable OriginLineage lineage;

    protected CollectionOrContainerTerm(
            List<Term> elements,
            BlankNodeOrIRI id,
            boolean idGenerated,
            Set<LogicalTarget> targets,
            @Nullable OriginLineage lineage
    ) {
        this.elements = elements;
        this.id = id;
        this.idGenerated = idGenerated;
        this.targets = targets != null ? new java.util.HashSet<>(targets) : new java.util.HashSet<>();
        this.lineage = lineage;
    }

    protected CollectionOrContainerTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets) {
        this(elements, id, idGenerated, targets, null);
    }

    public void mergeLineage(@Nullable OriginLineage other) {
        if (other == null) return;
        this.lineage = (this.lineage != null) ? this.lineage.merge(other) : other;
    }

    public void addTargets(@Nullable Set<LogicalTarget> additionalTargets) {
        if (additionalTargets == null || additionalTargets.isEmpty()) return;
        this.targets.addAll(additionalTargets);
    }

    public List<Term> getElements() {
        return elements;
    }

    public BlankNodeOrIRI getId() {
        return id;
    }

    public void setId(BlankNodeOrIRI id) {
        this.id = id;
    }

    public boolean isIdGenerated() {
        return idGenerated;
    }

    @Override
    public @NonNull Set<LogicalTarget> targets() {
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
        if (o == null || getClass() != o.getClass()) return false;
        CollectionOrContainerTerm that = (CollectionOrContainerTerm) o;
        return idGenerated == that.idGenerated &&
                Objects.equals(elements, that.elements) &&
                Objects.equals(id, that.id) &&
                Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, id, idGenerated, targets);
    }
}

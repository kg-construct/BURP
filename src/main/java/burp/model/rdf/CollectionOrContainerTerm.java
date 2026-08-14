package burp.model.rdf;

import burp.model.LogicalTarget;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract sealed class CollectionOrContainerTerm implements BlankNodeOrIRI permits RdfListTerm, RdfBagTerm, RdfSeqTerm, RdfAltTerm {
    private final List<Term> elements;
    private BlankNodeOrIRI id;
    private final boolean idGenerated;
    private final Set<LogicalTarget> targets;

    protected CollectionOrContainerTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets) {
        this.elements = elements;
        this.id = id;
        this.idGenerated = idGenerated;
        this.targets = targets != null ? targets : Set.of();
    }

    public List<Term> getElements() { return elements; }
    public BlankNodeOrIRI getId() { return id; }
    public void setId(BlankNodeOrIRI id) { this.id = id; }
    public boolean isIdGenerated() { return idGenerated; }

    @Override
    public Set<LogicalTarget> targets() { return targets; }

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

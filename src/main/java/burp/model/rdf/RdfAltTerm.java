package burp.model.rdf;

import burp.model.LogicalTarget;

import java.util.List;
import java.util.Set;

public final class RdfAltTerm extends CollectionOrContainerTerm {
    public RdfAltTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets) {
        super(elements, id, idGenerated, targets);
    }

    public RdfAltTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated) {
        super(elements, id, idGenerated, Set.of());
    }

    @Override
    public String toString() {
        return "RdfAltTerm(elements=" + getElements() + ", id=" + getId() + ", idGenerated=" + isIdGenerated() + ", targets=" + targets() + ")";
    }
}

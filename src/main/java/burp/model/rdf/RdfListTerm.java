package burp.model.rdf;

import burp.model.LogicalTarget;

import java.util.List;
import java.util.Set;

public final class RdfListTerm extends CollectionOrContainerTerm {
    public RdfListTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets) {
        super(elements, id, idGenerated, targets);
    }

    public RdfListTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated) {
        super(elements, id, idGenerated, Set.of());
    }

    @Override
    public String toString() {
        return "RdfListTerm(elements=" + getElements() + ", id=" + getId() + ", idGenerated=" + isIdGenerated() + ", targets=" + targets() + ")";
    }
}

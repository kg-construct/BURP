package burp.model.rdf;

import burp.model.LogicalTarget;

import java.util.List;
import java.util.Set;

public final class RdfBagTerm extends CollectionOrContainerTerm {
    public RdfBagTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets) {
        super(elements, id, idGenerated, targets);
    }

    public RdfBagTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated) {
        super(elements, id, idGenerated, Set.of());
    }

    @Override
    public String toString() {
        return "RdfBagTerm(elements=" + getElements() + ", id=" + getId() + ", idGenerated=" + isIdGenerated() + ", targets=" + targets() + ")";
    }
}

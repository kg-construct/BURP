package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class RdfBagTerm extends CollectionOrContainerTerm {
    public RdfBagTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        super(elements, id, idGenerated, targets, lineage);
    }

    @Override
    public String toString() {
        return "RdfBagTerm(elements=" + getElements() + ", id=" + getId() + ", idGenerated=" + isIdGenerated() + ", targets=" + targets() + ")";
    }
}

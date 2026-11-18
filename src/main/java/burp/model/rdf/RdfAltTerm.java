package burp.model.rdf;

import burp.model.LogicalTarget;
import burp.model.OriginLineage;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class RdfAltTerm extends CollectionOrContainerTerm {
    public RdfAltTerm(List<Term> elements, BlankNodeOrIRI id, boolean idGenerated, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        super(elements, id, idGenerated, targets, lineage);
    }

    @Override
    public String toString() {
        return "RdfAltTerm(elements=" + getElements() + ", id=" + getId() + ", idGenerated=" + isIdGenerated() + ", targets=" + targets() + ")";
    }
}

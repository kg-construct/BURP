package burp.model;

import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@NullMarked
public record RdfPredicateObject(
        IRITerm predicate,
        Term object,
        @Nullable IRITerm graph,
        Set<LogicalTarget> targets
) implements RmlEvaluationResult {

    @Override
    public @Nullable OriginLineage lineage() {
        return predicate.lineage() != null
                ? predicate.lineage().merge(object.lineage())
                : object.lineage();
    }
}

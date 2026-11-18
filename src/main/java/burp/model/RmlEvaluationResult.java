package burp.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A shared parent interface implemented by all generated RDF evaluation results
 * (both RDF statements and RDF terms).
 */
@NullMarked
public interface RmlEvaluationResult {
    Set<LogicalTarget> targets();

    default @Nullable OriginLineage lineage() {
        return null;
    }
}

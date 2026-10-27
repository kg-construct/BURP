package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.Nullable;

/**
 * Common interface for expression maps on either side of a join condition ({@link ChildMap} and {@link ParentMap}).
 * Exposes the RDF predicate origin representing the join predicate token (e.g. {@code rml:child} or {@code rml:parent}).
 */
public interface JoinMap extends LocalReferenceScope {

    @Nullable Origin getPredicateOrigin();
}

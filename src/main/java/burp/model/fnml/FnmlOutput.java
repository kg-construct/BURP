package burp.model.fnml;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents an FnO output/return definition extracted from FnO ontologies.
 *
 * @param iri          The fno:Output entity IRI (e.g. grel:stringOut or idlab-fn:_stringOut)
 * @param predicateIri The fno:predicate IRI if defined (e.g. grel:stringOutput or idlab-fn:stringOut), or null
 * @param name         Human-readable output name
 * @param typeIri      Expected datatype or class IRI (e.g. xsd:string)
 */
@NullMarked
public record FnmlOutput(
        Resource iri,
        @Nullable Resource predicateIri,
        String name,
        @Nullable Resource typeIri
) {
    /**
     * Checks if this output matches the given resource IRI by either output entity IRI or predicate IRI.
     */
    public boolean matches(Resource r) {
        return (predicateIri != null && (predicateIri.equals(r) || predicateIri.getURI().equals(r.getURI())))
                || iri.equals(r)
                || iri.getURI().equals(r.getURI());
    }

    /**
     * Returns true if r is using the predicate IRI instead of the output entity IRI when distinct.
     */
    public boolean isUsingPredicateWhenOutputDefined(Resource r) {
        if (predicateIri == null || predicateIri.equals(iri) || predicateIri.getURI().equals(iri.getURI())) {
            return false;
        }
        return predicateIri.equals(r) || predicateIri.getURI().equals(r.getURI());
    }
}

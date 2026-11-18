package burp.model.fnml;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents an FnO parameter definition extracted from FnO ontologies.
 *
 * @param iri          The fno:Parameter entity IRI (e.g. idlab-fn:_str or exfn:stringParameter)
 * @param predicateIri The fno:predicate IRI if defined (e.g. idlab-fn:str), or null
 * @param name         Human-readable parameter name
 * @param required     Whether the parameter is required
 * @param typeIri      Expected datatype or class IRI (e.g. xsd:string)
 */
@NullMarked
public record FnmlParameter(
        Resource iri,
        @Nullable Resource predicateIri,
        String name,
        boolean required,
        @Nullable Resource typeIri
) {
    /**
     * Checks if this parameter matches the given resource IRI by either parameter entity IRI or predicate IRI.
     */
    public boolean matches(Resource r) {
        return (predicateIri != null && (predicateIri.equals(r) || predicateIri.getURI().equals(r.getURI())))
                || iri.equals(r)
                || iri.getURI().equals(r.getURI());
    }

    /**
     * Returns true if r is using the predicate IRI instead of the parameter entity IRI when distinct.
     */
    public boolean isUsingPredicateWhenParamDefined(Resource r) {
        if (predicateIri == null || predicateIri.equals(iri) || predicateIri.getURI().equals(iri.getURI())) {
            return false;
        }
        return predicateIri.equals(r) || predicateIri.getURI().equals(r.getURI());
    }
}

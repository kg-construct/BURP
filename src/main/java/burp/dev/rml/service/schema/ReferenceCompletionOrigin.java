package burp.dev.rml.service.schema;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Metadata describing the origin of a schema candidate field (e.g. JSON schema path, CSV header, logical view node).
 *
 * @param type The origin type (e.g. "logicalView", "schema", "header", "data").
 * @param resource The RDF resource (IRI or blank node) of the originating plan node (e.g. Field in LogicalView), if applicable.
 * @param location The file or document location of the origin.
 * @param graphPointer RDF provenance pointer for the declaring statement.
 */
@NullMarked
public record ReferenceCompletionOrigin(
        String type,
        @Nullable Resource resource,
        @Nullable String location,
        @Nullable String graphPointer
) {}

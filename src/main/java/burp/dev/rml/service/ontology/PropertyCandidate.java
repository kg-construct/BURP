package burp.dev.rml.service.ontology;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Represents a candidate ontology property for autocompletion and inspection.
@NullMarked
public record PropertyCandidate(
        Resource property,
        String compactIri,
        @Nullable String label,
        @Nullable String comment,
        @Nullable Resource expectedRange,
        boolean isObjectProperty,
        boolean isDatatypeProperty,
        int inheritanceDistance,
        @Nullable Resource definedOnClass
) {}

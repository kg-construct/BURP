package burp.dev.rml.service.ontology;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/// Represents validation constraints deduced for a property given a subject class context.
@NullMarked
public record PropertyConstraint(
        Resource predicate,
        boolean isKnownProperty,
        boolean isDomainCompatible,
        Set<Resource> declaredDomains,
        @Nullable Resource expectedRange,
        boolean isObjectProperty,
        boolean isDatatypeProperty
) {}

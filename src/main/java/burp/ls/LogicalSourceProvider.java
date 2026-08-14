package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Resource;

import java.nio.file.Path;
import java.util.List;

public interface LogicalSourceProvider {
    boolean supports(Resource referenceFormulation);
    LogicalSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory);

    /**
     * Parse a string payload into a list of nested Iterations (used by RML-LV IterableField).
     * By default, throws an exception for formulations that don't support string payloads,
     * highlighting the origin of the reference formulation that caused the issue.
     */
    default List<Iteration> parseStringPayload(String payload, String iterator, Origin referenceFormulationOrigin) {
        throw new BurpException(
            new RmlError(
                "Nested iterations from string payload are not supported for this reference formulation.",
                referenceFormulationOrigin,
                RER.UnsupportedMapping
            )
        );
    }

    default List<Iteration> parseStringPayload(String payload, String iterator) {
        return parseStringPayload(payload, iterator, null);
    }

    /**
     * Build a Reference extractor based purely on the reference formulation.
     */
    default Reference buildReference(String reference, Origin origin, Origin referenceFormulationOrigin) {
        throw new BurpException(
            new RmlError(
                "Nested references not supported for this formulation.",
                referenceFormulationOrigin,
                RER.UnsupportedMapping
            )
        );
    }

    default Reference buildReference(String reference, Origin origin) {
        return buildReference(reference, origin, null);
    }
}

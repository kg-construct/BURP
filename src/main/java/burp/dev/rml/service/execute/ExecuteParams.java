package burp.dev.rml.service.execute;

import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for executing a mapping with priority given to a specific structure,
 * optional executionId for correlating streaming chunks, and optional baseIri override.
 */
@NullMarked
public record ExecuteParams(
        @Nullable StructureSelector structure,
        @Nullable String executionId,
        @Nullable String baseIri
) {
}

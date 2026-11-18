package burp.dev.rml.service.plan;

import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for requesting the complete mapping execution plan.
 */
public record PlanParams(
    String mappingFileUri,
    @Nullable StructureSelector structure
) {}

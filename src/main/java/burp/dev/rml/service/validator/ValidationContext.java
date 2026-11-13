package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import org.jspecify.annotations.NullMarked;

/**
 * Context payload passed to every MappingValidator rule.
 */
@NullMarked
public record ValidationContext(
        MappingWorkspace.LoadedMapping loaded,
        String mappingFileUri,
        MappingWorkspace workspace
) {}

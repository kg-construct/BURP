package burp.dev.rml.service.trace;

import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.NullMarked;

/**
 * Parameters for requesting a forward execution trace of a single iteration.
 */
@NullMarked
public record TraceIterationParams(
        StructureSelector structure,
        int iterationIndex
) {}

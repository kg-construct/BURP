package burp.dev.rml.service.eval;

import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.NullMarked;

/// Parameters for stepping the active inline evaluation iteration for a mapping target.
@NullMarked
public record StepInlineIterationParams(
        StructureSelector structure,
        int delta
) {
}

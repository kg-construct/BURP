package burp.dev.rml.service.validator;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Common contract for independent mapping validation rules.
 */
@NullMarked
public interface MappingValidator {

    /**
     * Unique stable identifier for this validator (used for granular caching and logging).
     * Examples: "syntax", "fno", "unused-elements", "ontology-constants", "data-references", "runtime-ontology".
     */
    String getId();

    /**
     * Declares whether this validator runs immediately on every keystroke or is debounced in the background.
     */
    ValidationExecutionPolicy getExecutionPolicy();

    /**
     * Executes validation against the parsed mapping context.
     *
     * @param context the loaded mapping, file URI, and workspace coordinator
     * @return list of detected diagnostics (located or unlocated)
     */
    List<RmlDiagnostic> validate(ValidationContext context);
}

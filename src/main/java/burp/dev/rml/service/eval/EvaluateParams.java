package burp.dev.rml.service.eval;

import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for evaluating a structure against a specified data context.
 */
public record EvaluateParams(@Nullable StructureSelector structure, EvaluationDataContext dataContext) {}

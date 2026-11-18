package burp.dev.rml.service.trace;

import burp.dev.rml.service.eval.EvaluationDataContext;
import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.NullMarked;

/**
 * Parameters for requesting an execution trace of a structure and data context.
 */
@NullMarked
public record TraceParams(StructureSelector structure, EvaluationDataContext dataContext) {}

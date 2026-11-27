package burp.dev.rml.service.source;

import burp.dev.rml.service.eval.EvaluationDataContext;
import burp.dev.rml.service.selector.StructureSelector;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for requesting a windowed tabular preview of a Logical View.
 */
public record SourceDataParams(
        StructureSelector structure,
        int offset,
        int limit,
        @Nullable EvaluationDataContext dataContext
) {}

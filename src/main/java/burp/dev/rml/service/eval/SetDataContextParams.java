package burp.dev.rml.service.eval;

import org.jspecify.annotations.NullMarked;

/// Parameters for setting the active inline evaluation data context for a mapping document.
@NullMarked
public record SetDataContextParams(
        String mappingFileUri,
        EvaluationDataContext dataContext
) {
}

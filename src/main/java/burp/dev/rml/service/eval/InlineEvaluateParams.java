package burp.dev.rml.service.eval;

import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

public record InlineEvaluateParams(
        String mappingFileUri,
        EvaluationDataContext dataContext,
        @Nullable Range range
) {
    public InlineEvaluateParams(String mappingFileUri, EvaluationDataContext dataContext) {
        this(mappingFileUri, dataContext, null);
    }
}

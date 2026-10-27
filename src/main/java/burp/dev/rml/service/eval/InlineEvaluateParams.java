package burp.dev.rml.service.eval;

import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record InlineEvaluateParams(
        String mappingFileUri,
        EvaluationDataContext dataContext,
        @Nullable Range range,
        @Nullable Position cursorPosition,
        @Nullable InlineHintsEvaluationMode evaluationMode,
        @Nullable Integer joinIndexLimit
) {
    public InlineEvaluateParams(String mappingFileUri, EvaluationDataContext dataContext) {
        this(mappingFileUri, dataContext, null, null, null, null);
    }

    public InlineEvaluateParams(String mappingFileUri, EvaluationDataContext dataContext, @Nullable Range range) {
        this(mappingFileUri, dataContext, range, null, null, null);
    }

    public InlineEvaluateParams(String mappingFileUri, EvaluationDataContext dataContext, @Nullable Range range, @Nullable Position cursorPosition) {
        this(mappingFileUri, dataContext, range, cursorPosition, null, null);
    }
}

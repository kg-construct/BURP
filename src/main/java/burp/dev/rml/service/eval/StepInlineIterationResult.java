package burp.dev.rml.service.eval;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// Result of stepping the active inline evaluation iteration.
@NullMarked
public record StepInlineIterationResult(
        EvaluationDataContext dataContext,
        int activeIterationIndex,
        List<String> traceSummary
) {
}

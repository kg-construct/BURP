package burp.dev.rml.service.eval;

import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import burp.model.RmlEvaluationResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Result of evaluating a mapping iteration.
 *
 * Link: The iterationId corresponds to the 0-based index of the iteration,
 * which aligns with the startIndex and endIndex parameters in RangeDataContext.
 */
@NullMarked
public record EvaluateResult(
    String iterationId,
    String iterationData,
    List<RmlEvaluationResult> generatedRdf,
    List<String> diagnostics,
    @Nullable String rerDiagnostic,
    Map<String, String> prefixMap,
    @Nullable List<String> traceSummary,
    @Nullable String targetNodeType,
    @Nullable String targetParentTriplesMap,
    @Nullable String targetNodeSummary,
    @Nullable InlineHintsEvaluationMode evaluationMode
) {
    public EvaluateResult(
            String iterationId,
            String iterationData,
            List<RmlEvaluationResult> generatedRdf,
            List<String> diagnostics,
            @Nullable String rerDiagnostic,
            Map<String, String> prefixMap,
            @Nullable List<String> traceSummary
    ) {
        this(iterationId, iterationData, generatedRdf, diagnostics, rerDiagnostic, prefixMap, traceSummary, null, null, null, null);
    }

    public EvaluateResult(
            String iterationId,
            String iterationData,
            List<RmlEvaluationResult> generatedRdf,
            List<String> diagnostics,
            @Nullable String rerDiagnostic,
            Map<String, String> prefixMap
    ) {
        this(iterationId, iterationData, generatedRdf, diagnostics, rerDiagnostic, prefixMap, null, null, null, null, null);
    }
}


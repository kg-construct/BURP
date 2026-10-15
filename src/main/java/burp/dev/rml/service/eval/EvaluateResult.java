package burp.dev.rml.service.eval;

import burp.model.RmlEvaluationResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Result of evaluating a mapping iteration.
 *
 * Link: The iterationId corresponds to the 0-based index of the iteration,
 * which aligns with the startIndex and endIndex parameters in RangeDataContext.
 */
public record EvaluateResult(
    String iterationId,
    String iterationData,
    List<RmlEvaluationResult> generatedRdf,
    List<String> diagnostics,
    @Nullable String rerDiagnostic,
    Map<String, String> prefixMap
) {}


package burp.dev.rml.service.eval;

import burp.model.RmlEvaluationResult;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record InlineEvaluateItem(
    String id,
    String type,
    Range originRange,
    List<RmlEvaluationResult> values,
    List<String> formattedValues,
    List<String> rawFormattedValues,
    List<String> diagnostics
) {}


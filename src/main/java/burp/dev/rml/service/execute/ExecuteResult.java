package burp.dev.rml.service.execute;

import burp.dev.rml.service.validator.RmlErrorDto;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * Result containing per-target execution results, execution report updates, and structured errors.
 */
@NullMarked
public record ExecuteResult(
        Map<String, TargetExecutionResult> targets,
        String reportUpdate,
        Map<String, String> prefixMap,
        Map<String, String> logicalSources,
        List<RmlErrorDto> errors
) {
}

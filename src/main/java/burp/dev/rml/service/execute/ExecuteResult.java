package burp.dev.rml.service.execute;

import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Result containing per-target execution results and execution report updates.
 */
@NullMarked
public record ExecuteResult(
        Map<String, TargetExecutionResult> targets,
        String reportUpdate,
        Map<String, String> prefixMap,
        Map<String, String> logicalSources
) {
}


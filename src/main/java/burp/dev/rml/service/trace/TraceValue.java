package burp.dev.rml.service.trace;

import burp.model.RmlEvaluationResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents an individual value flowing through the mapping execution graph.
 */
public record TraceValue(
        String valueId,
        String iterationId,
        String text,
        @Nullable RmlEvaluationResult term,
        String producerNodeId,
        List<String> derivedFromValueIds
) {}

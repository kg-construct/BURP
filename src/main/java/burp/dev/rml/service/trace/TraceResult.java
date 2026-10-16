package burp.dev.rml.service.trace;

import java.util.List;
import java.util.Map;

/**
 * Result of the trace operation containing trace nodes and graph serialization.
 */
public record TraceResult(
        List<TraceNode> nodes,
        String graphJsonLD,
        Map<String, String> prefixMap
) {
}

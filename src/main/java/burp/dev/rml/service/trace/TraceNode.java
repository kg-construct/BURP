package burp.dev.rml.service.trace;

import burp.dev.rml.service.plan.PlanAttributeItem;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents the execution trace of a single mapping node.
 */
@NullMarked
public record TraceNode(
    String nodeId,
    String nodeType,
    @Nullable String label,
    @Nullable Range originRange,
    List<PlanAttributeItem> attributes,
    List<TraceValue> inputs,
    List<TraceValue> outputs,
    List<String> dependencyIds,
    List<String> futureIds
) {}

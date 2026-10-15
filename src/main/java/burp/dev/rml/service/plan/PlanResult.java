package burp.dev.rml.service.plan;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Result containing the complete mapping plan hierarchy and any selected node ID.
 */
public record PlanResult(
    List<PlanNodeItem> roots,
    @Nullable String selectedNodeId
) {}

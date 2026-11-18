package burp.dev.rml.service.plan;

import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Represents a cross-tree relational reference to another PlanNode in the AST.
///
/// @param role The reference role (e.g. `parentTriplesMap`, `parentLogicalView`).
/// @param targetId Short address identifier of the target node in [burp.model.PlanNodeIdRegistry].
/// @param targetLabel Short human-readable display label (e.g. compacted IRI or short ID).
/// @param targetResource Full IRI or blank node identifier of the target node, or null.
/// @param originRange The LSP source range in the mapping document declaring the reference, or null.
@NullMarked
public record PlanReferenceItem(
    String role,
    String targetId,
    String targetLabel,
    @Nullable String targetResource,
    @Nullable Range originRange
) {}

package burp.dev.rml.service.plan;

import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Represents an individual node in the execution plan tree.
///
/// @param id Unique short addressing identifier for the node assigned and indexed by [burp.model.PlanNodeIdRegistry].
/// @param type The simple class name of the plan node (e.g., `TriplesMap`, `LogicalSource`).
/// @param originRange The source code location ranges in the mapping document (empty if synthetic).
/// @param resource The full IRI or blank node identifier (e.g. `_:[line:col]`), or null if not backed by an RDF entity.
/// @param displayResource A short form of the resource (compacted IRI or blank node identifier) else null.
/// @param attributes Structured configuration attributes of this node with source provenance.
/// @param references Cross-tree relational links to other plan nodes (e.g., `parentTriplesMap`).
/// @param children Direct child plan nodes in the execution tree.
@NullMarked
public record PlanNodeItem(
    String id,
    String type,
    List<Range> originRange,
    @Nullable String resource,
    @Nullable String displayResource,
    List<PlanAttributeItem> attributes,
    List<PlanReferenceItem> references,
    List<PlanNodeItem> children
) {}

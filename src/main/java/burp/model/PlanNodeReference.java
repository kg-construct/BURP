package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Represents a cross-tree relational link from this AST plan node to another [PlanNode].
///
/// Unlike [PlanNode#children()], referenced nodes are not owned tree children; they represent
/// cross-cutting relationships (such as `ReferencingObjectMap.parentTriplesMap` or `ViewJoin.parentLogicalView`).
///
/// @param role The semantic role or property name of the reference (e.g., `parentTriplesMap`, `parentLogicalView`).
/// @param target The referenced [PlanNode] instance.
/// @param origin The Turtle source statement provenance locating where the reference property was declared, or null.
@NullMarked
public record PlanNodeReference(
    String role,
    PlanNode target,
    @Nullable Origin origin
) {
}

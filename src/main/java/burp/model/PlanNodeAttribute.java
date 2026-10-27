package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Represents a configuration attribute of an AST plan node along with its Turtle source provenance and default status.
///
/// @param key    The attribute key/name (e.g. `template`, `constant`, `reference`, `termType`, `source`).
/// @param value  The formatted string value of the attribute.
/// @param origin The Turtle source statement provenance locating where this attribute was declared, or null if synthesized.
@NullMarked
public record PlanNodeAttribute(
    String key,
    String value,
    @Nullable Origin origin
) {

}

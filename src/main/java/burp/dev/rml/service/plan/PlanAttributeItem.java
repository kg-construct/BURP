package burp.dev.rml.service.plan;

import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Represents an individual configuration attribute of a PlanNode along with its LSP source range and default status.
///
/// @param key         The attribute name/key (e.g. `template`, `constant`, `reference`, `termType`, `source`).
/// @param value       The attribute string value.
/// @param originRange The LSP source range in the mapping document (null if default or synthesized).
@NullMarked
public record PlanAttributeItem(
    String key,
    String value,
    @Nullable Range originRange
) {}

package burp.model;

import burp.reporting.Origin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// High-performance accumulator interface for populating node configuration attributes without
/// intermediate map allocations or defensive copies.
@NullMarked
@FunctionalInterface
public interface AttributeCollector {

    /// Adds an attribute with its source provenance.
    ///
    /// @param key The attribute key name.
    /// @param value The attribute string value.
    /// @param origin The Turtle source provenance origin, or null if synthesized.
    void put(String key, String value, @Nullable Origin origin);

    /// Adds an attribute without provenance.
    default void put(String key, String value) {
        put(key, value, null);
    }
}

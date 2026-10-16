package burp.dev.rml.service.eval;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Custom data context providing key-value pairs mapping references to their values.
 */
public record ReferenceValuesCustomDataContext(
        Map<String, @Nullable Object> referenceValues) implements CustomDataContext {
}

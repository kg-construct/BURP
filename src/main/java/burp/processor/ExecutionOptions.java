package burp.processor;

import burp.dev.rml.service.selector.StructureSelector;
import burp.model.context.CancellationToken;
import org.jspecify.annotations.Nullable;

/**
 * Execution options for configuring RmlProcessor runs.
 */
public record ExecutionOptions(
        boolean useCache,
        @Nullable String baseIri,
        @Nullable StructureSelector focusStructure,
        @Nullable CancellationToken cancellationToken
) {
    public static ExecutionOptions defaults() {
        return new ExecutionOptions(true, null, null, null);
    }

    public static ExecutionOptions withoutCache() {
        return new ExecutionOptions(false, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean useCache = true;
        private @Nullable String baseIri;
        private @Nullable StructureSelector focusStructure;
        private @Nullable CancellationToken cancellationToken;

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Builder baseIri(@Nullable String baseIri) {
            this.baseIri = baseIri;
            return this;
        }

        public Builder focusStructure(@Nullable StructureSelector focusStructure) {
            this.focusStructure = focusStructure;
            return this;
        }

        public Builder cancellationToken(@Nullable CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        public ExecutionOptions build() {
            return new ExecutionOptions(useCache, baseIri, focusStructure, cancellationToken);
        }
    }
}

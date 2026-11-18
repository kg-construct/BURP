package burp.dev.rml.service.execute;

import burp.model.FilePathTarget;
import burp.model.LogicalTarget;
import org.jspecify.annotations.Nullable;

/**
 * Clean JSON-RPC serializable metadata for a logical target.
 */
public record LogicalTargetInfo(
        @Nullable String targetType,
        @Nullable String targetPath,
        @Nullable String serialization,
        @Nullable String compression,
        @Nullable String encoding
) {
    public static @Nullable LogicalTargetInfo from(@Nullable LogicalTarget lt) {
        if (lt == null || lt.isDefault()) {
            return null;
        }
        String targetType = null;
        String targetPath = null;
        if (lt.target instanceof FilePathTarget fpt) {
            targetType = "FilePath";
            targetPath = fpt.path();
        } else if (lt.target != null) {
            targetType = lt.target.getClass().getSimpleName();
            targetPath = lt.getId();
        }
        String serialization = lt.serialization != null ? lt.serialization.getURI() : null;
        String compression = lt.compression != null ? lt.compression.getURI() : null;
        String encoding = lt.encoding != null ? lt.encoding.getURI() : null;
        return new LogicalTargetInfo(targetType, targetPath, serialization, compression, encoding);
    }
}

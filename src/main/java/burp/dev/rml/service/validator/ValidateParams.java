package burp.dev.rml.service.validator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ValidateParams(
        @NonNull String mappingFileUri
) {}

package burp.dev.rml.service.validator;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record RmlDiagnostic(
        @Nullable Range range,
        @NonNull DiagnosticSeverity severity,
        @Nullable String rerCodeUri,
        @NonNull String message,
        @Nullable String source
) {}

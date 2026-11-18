package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;

/**
 * Context payload passed to every MappingValidator rule.
 */
@NullMarked
public record ValidationContext(
        MappingWorkspace.LoadedMapping loaded,
        String mappingFileUri,
        MappingWorkspace workspace,
        BooleanSupplier cancellationCheck
) {
    public ValidationContext(
            MappingWorkspace.LoadedMapping loaded,
            String mappingFileUri,
            MappingWorkspace workspace
    ) {
        this(loaded, mappingFileUri, workspace, () -> false);
    }

    public boolean isCancelled() {
        return cancellationCheck.getAsBoolean() || Thread.currentThread().isInterrupted();
    }
}

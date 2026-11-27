package burp.dev.rml.service.execute;

import burp.model.RdfStatement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Execution result for a single target, containing its generated statements and metadata.
 */
@NullMarked
public record TargetExecutionResult(
        String targetId,
        @Nullable LogicalTargetInfo logicalTarget,
        List<RdfStatement> statements,
        long statementCount
) {}

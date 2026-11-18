package burp.dev.rml.service.execute;

import burp.model.RdfStatement;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * Streaming notification payload for chunks of generated RDF statements.
 */
@NullMarked
public record QuadChunkNotification(
        String executionId,
        String targetId,
        List<RdfStatement> statements,
        /// A map of logical source IDs to a corresponding human-friendly name.
        Map<String, String> logicalSources,
        long chunkIndex,
        boolean isLast
) {}


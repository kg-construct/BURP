package burp.dev.rml;

import burp.dev.rml.service.execute.QuadChunkNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.NullMarked;

/**
 * Extended LanguageClient interface providing strongly-typed RML notifications from server to client.
 */
@NullMarked
public interface RmlLanguageClient extends LanguageClient {

    /**
     * Notification sent by the server when a chunk of generated RDF statements is ready during execution.
     */
    @JsonNotification("rml/quadChunk")
    void onQuadChunk(QuadChunkNotification notification);
}

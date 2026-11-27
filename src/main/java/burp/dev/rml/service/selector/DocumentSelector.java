package burp.dev.rml.service.selector;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jspecify.annotations.NullMarked;

/**
 * Selects a mapping document by its LSP text document identifier.
 */
@NullMarked
public record DocumentSelector(TextDocumentIdentifier textDocument) implements StructureSelector {
    public String mappingFileUri() {
        return textDocument.getUri();
    }
}

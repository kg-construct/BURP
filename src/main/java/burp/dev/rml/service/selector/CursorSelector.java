package burp.dev.rml.service.selector;

import org.eclipse.lsp4j.Position;

/**
 * Selects a mapping structure located at a specific editor cursor position.
 */
public record CursorSelector(String mappingFileUri, Position position) implements StructureSelector {}

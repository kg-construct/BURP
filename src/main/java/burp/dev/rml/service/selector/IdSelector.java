package burp.dev.rml.service.selector;

/**
 * Selects a mapping structure by its assigned unique identifier.
 */
public record IdSelector(String mappingFileUri, String id) implements StructureSelector { }

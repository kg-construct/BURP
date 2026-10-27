package burp.dev.rml.service.selector;

/**
 * Selects a mapping structure by its IRI.
 */
public record IriSelector(String mappingFileUri, String iri) implements StructureSelector {
}

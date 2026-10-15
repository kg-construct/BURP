package burp.dev.rml.service.selector;

/**
 * Base interface for selectors identifying a target structure in a mapping document.
 */
public sealed interface StructureSelector permits CursorSelector, IriSelector, IdSelector, DocumentSelector {
}


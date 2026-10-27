package burp.dev.rml.service.plan;

import burp.dev.rml.service.selector.StructureSelector;

/**
 * Parameters for requesting reference formulation dependencies required by a structure.
 */
public record ReferencesDependenciesParams(StructureSelector structure) {}

package burp.dev.rml.service.plan;

import java.util.List;

/**
 * Describes a reference formulation dependency required by a mapping structure.
 */
public record ReferenceDependency(String referenceFormulationType, String referenceId, List<String> placeholders) {}

package burp.model.rdf;

import burp.model.RmlEvaluationResult;

/// An RDF term for generation.
/// Equality and hash code are based of the underlying value, not the object itself or its lineage.
public sealed interface Term extends RmlEvaluationResult permits BlankNodeOrIRI, LiteralTerm {
}

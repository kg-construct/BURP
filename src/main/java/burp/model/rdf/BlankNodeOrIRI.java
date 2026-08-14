package burp.model.rdf;

public sealed interface BlankNodeOrIRI extends Term permits BlankNodeTerm, IRITerm, CollectionOrContainerTerm {
}

package burp.model.rdf;

import burp.model.LogicalTarget;

import java.util.Set;

public sealed interface Term permits BlankNodeOrIRI, LiteralTerm {
    Set<LogicalTarget> targets();
}

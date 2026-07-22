package burp.parse.turtleprov;

import burp.vocabularies.BURP;

public enum TurtleNodeKind {
    IRIREF(BURP.base_uri + "IRIREF"),
    PREFIXED_NAME(BURP.base_uri + "PrefixedName"),
    BLANK_NODE_LABEL(BURP.base_uri + "BlankNodeLabel"),
    ANONYMOUS_BLANK_NODE(BURP.base_uri + "AnonymousBlankNode"),
    BLANK_NODE_PROPERTY_LIST(BURP.base_uri + "BlankNodePropertyList"),
    COLLECTION(BURP.base_uri + "Collection"),
    STRING_LITERAL_QUOTE(BURP.base_uri + "StringLiteralQuote"),
    STRING_LITERAL_SINGLE_QUOTE(BURP.base_uri + "StringLiteralSingleQuote"),
    STRING_LITERAL_LONG_QUOTE(BURP.base_uri + "StringLiteralLongQuote"),
    STRING_LITERAL_LONG_SINGLE_QUOTE(BURP.base_uri + "StringLiteralLongSingleQuote"),
    INTEGER_LITERAL(BURP.base_uri + "IntegerLiteral"),
    DECIMAL_LITERAL(BURP.base_uri + "DecimalLiteral"),
    DOUBLE_LITERAL(BURP.base_uri + "DoubleLiteral"),
    BOOLEAN_LITERAL(BURP.base_uri + "BooleanLiteral"),
    TYPE_VERB(BURP.base_uri + "TypeVerb"),
    REIFIED_TRIPLE(BURP.base_uri + "ReifiedTriple"),
    TRIPLE_TERM(BURP.base_uri + "TripleTerm");

    private final String uri;

    TurtleNodeKind(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}

package burp.parse;

/**
 * Defines the SHACL validation strategy when loading RML mappings.
 */
public enum ValidationMode {
    /**
     * Executes SHACL validation and throws a {@link burp.reporting.BurpException}
     * if the mapping violates any SHACL shapes.
     */
    STRICT,

    /**
     * Executes SHACL validation and records any violations into {@link burp.reporting.RmlExecutionReport}
     * without throwing an exception, allowing partial AST mapping.
     */
    LENIENT,

    /**
     * Completely skips SHACL shape validation and schema inference.
     * Fastest execution mode when mappings are already validated.
     */
    NONE
}

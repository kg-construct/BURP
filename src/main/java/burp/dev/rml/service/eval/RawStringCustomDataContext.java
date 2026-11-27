package burp.dev.rml.service.eval;

/**
 * Custom data context providing raw unparsed string data for an iteration.
 */
public record RawStringCustomDataContext(String rawString) implements CustomDataContext {}

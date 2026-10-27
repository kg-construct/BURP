package burp.dev.rml.service.eval;

/**
 * Marker interface for custom input data provided directly in the request.
 */
public sealed interface CustomDataContext extends EvaluationDataContext permits RawStringCustomDataContext, ReferenceValuesCustomDataContext {
}

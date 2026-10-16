package burp.dev.rml.service.eval;

/**
 * Base interface for data contexts specifying which iteration data to evaluate.
 */
public sealed interface EvaluationDataContext permits AllDataContext, RangeDataContext, CustomDataContext, MultiSourceDataContext {
}

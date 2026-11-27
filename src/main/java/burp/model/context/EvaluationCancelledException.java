package burp.model.context;

/**
 * Exception thrown when an evaluation is interrupted via CancellationToken.
 */
public class EvaluationCancelledException extends RuntimeException {
    public EvaluationCancelledException(String message) {
        super(message);
    }
}

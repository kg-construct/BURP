package burp.model.context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe cancellation token to interrupt evaluation when superseded.
 */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new EvaluationCancelledException("Evaluation was cancelled.");
        }
    }
}

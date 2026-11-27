package burp.dev.rml.service.validator;

public enum ValidationExecutionPolicy {
    /**
     * Fast in-memory check (<50ms). Executed immediately on every keystroke, open, and save.
     */
    IMMEDIATE,

    /**
     * Heavy check (I/O, database query, large sample evaluation).
     * Debounced in background worker; previous results are tagged with "[revalidating...]" during active typing.
     */
    DEBOUNCED_ASYNC
}

package burp.reporting;

public class BurpException extends RuntimeException {
    private final RmlError error;

    public BurpException(RmlError error) {
        super(error.getMessage());
        this.error = error;
    }

    public RmlError getError() {
        return error;
    }
}

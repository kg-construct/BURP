package burp.parse.turtleprov;

import burp.reporting.PointRange;

public class TurtleProvException extends RuntimeException {
    private final PointRange range;

    public TurtleProvException(String message, PointRange range) {
        super(message);
        this.range = range;
    }

    public PointRange getRange() {
        return range;
    }
}

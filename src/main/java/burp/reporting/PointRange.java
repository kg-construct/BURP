package burp.reporting;



import burp.parse.turtleprov.Point;
public class PointRange {
    private final Point start;
    private final Point end;

    public PointRange(Point start, Point end) {
        this.start = start;
        this.end = end;
    }

    public PointRange(Point start) {
        this(start, null);
    }

    public Point getStart() {
        return start;
    }

    public Point getEnd() {
        return end;
    }

    public PointRange plus(PointRange other) {
        Point end1 = this.end != null ? this.end : Point.zero();
        Point end2 = other.end != null ? other.end : Point.zero();
        return new PointRange(
            this.start.plus(other.start),
            end1.plus(end2)
        );
    }
}

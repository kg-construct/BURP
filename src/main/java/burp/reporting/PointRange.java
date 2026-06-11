package burp.reporting;


import burp.parse.turtleprov.Point;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record PointRange(@NonNull Point start, @Nullable Point end) {

    public PointRange() {
        this(new Point(0, 0), null);
    }

    public PointRange(Point start) {
        this(start, null);
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

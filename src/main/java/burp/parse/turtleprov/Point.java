package burp.parse.turtleprov;

import java.util.Objects;

public final class Point implements Comparable<Point> {
    public final int line;
    public final int column;

    public Point(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int line() { return line; }
    public int column() { return column; }
    
    public int getDisplayLine() { return line + 1; }
    public int getDisplayColumn() { return column + 1; }

    @Override
    public int compareTo(Point other) {
        int lineCompare = Integer.compare(this.line, other.line);
        return lineCompare != 0 ? lineCompare : Integer.compare(this.column, other.column);
    }

    public Point plus(Point other) {
        return new Point(this.line + other.line, this.column + other.column);
    }

    public Point minus(Point point) {
        int newLine = this.line - point.line;
        int newColumn = this.column - point.column;
        return (newLine >= 0 && newColumn >= 0) ? new Point(newLine, newColumn) : null;
    }

    public static Point zero() {
        return new Point(0, 0);
    }

    public static Point fromOffset(String text, int offset) {
        int l = 0;
        int c = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                l++;
                c = 0;
            } else {
                c++;
            }
        }
        return new Point(l, c);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point point = (Point) o;
        return line == point.line && column == point.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column);
    }

    @Override
    public String toString() {
        return "Point[line=" + line + ", column=" + column + "]";
    }
}

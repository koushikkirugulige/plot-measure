package com.plotmeasure.measurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Coordinates are ARCore world metres; Y is up. Plot results use the horizontal XZ plane. */
public final class Measurement {
    public enum Mode { DISTANCE, HEIGHT, PLOT }
    public static final int MAX_POINTS = 32;
    private static final double MIN_SEPARATION = 0.02;
    private final List<Point> points = new ArrayList<>();
    private Mode mode = Mode.DISTANCE;
    private boolean closed;

    public static final class Point {
        public final double x, y, z;
        public Point(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Point coordinates must be finite.");
            }
            this.x = x; this.y = y; this.z = z;
        }
    }

    public Mode mode() { return mode; }
    public boolean closed() { return closed; }
    public int size() { return points.size(); }
    public List<Point> points() { return Collections.unmodifiableList(points); }
    public void setMode(Mode next) {
        if (next == null) throw new IllegalArgumentException("Choose a measurement mode.");
        if (mode != next) { clear(); mode = next; }
    }
    public void clear() { points.clear(); closed = false; }
    public void undo() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
        closed = false;
    }
    public void add(Point point) {
        if (point == null) throw new IllegalArgumentException("No surface point selected.");
        if (closed) throw new IllegalArgumentException("Reset or undo to change this measurement.");
        if (mode == Mode.HEIGHT && !points.isEmpty() && point.y - points.get(0).y < MIN_SEPARATION) {
            throw new IllegalArgumentException("The top point must be above the floor point.");
        }
        if (points.size() >= MAX_POINTS) throw new IllegalArgumentException("Use up to 32 corners per plot.");
        for (Point existing : points) {
            if (distance(existing, point, mode == Mode.PLOT) < MIN_SEPARATION) {
                throw new IllegalArgumentException("That point is too close to an existing point.");
            }
        }
        points.add(point);
        if (mode != Mode.PLOT && points.size() == 2) closed = true;
    }
    /** Refresh every anchor together in the same AR frame, never mix old and new coordinates. */
    public void updateTrackedPoints(List<Point> updated) {
        if (updated.size() != points.size() || updated.contains(null)) {
            throw new IllegalArgumentException("Tracked points must match the saved anchors.");
        }
        List<Point> snapshot = new ArrayList<>(updated);
        points.clear(); points.addAll(snapshot);
    }
    public void finishPlot() {
        if (mode != Mode.PLOT) throw new IllegalArgumentException("Choose Plot mode first.");
        validatePolygon(points);
        closed = true;
    }
    public List<Double> sideLengths() {
        List<Double> sides = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) sides.add(mode == Mode.HEIGHT
            ? Math.abs(points.get(i).y - points.get(i - 1).y) : distance(points.get(i - 1), points.get(i), mode == Mode.PLOT));
        if (mode == Mode.PLOT && closed) sides.add(distance(points.get(points.size() - 1), points.get(0), true));
        return sides;
    }
    public double totalLength() {
        double total = 0;
        for (double side : sideLengths()) total += side;
        return total;
    }
    public double area() {
        if (mode != Mode.PLOT || !closed) throw new IllegalStateException("Finish the plot before measuring area.");
        validatePolygon(points);
        return Math.abs(signedArea(points));
    }
    public double heightRange() {
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Point p : points) { low = Math.min(low, p.y); high = Math.max(high, p.y); }
        return points.isEmpty() ? 0 : high - low;
    }
    public double span() {
        double largest = 0;
        for (Point a : points) for (Point b : points) largest = Math.max(largest, distance(a, b, true));
        return largest;
    }
    public static double distance(Point a, Point b, boolean horizontal) {
        return Math.hypot(Math.hypot(a.x - b.x, a.z - b.z), horizontal ? 0 : a.y - b.y);
    }
    public static String lengthLabel(double metres, boolean imperial) {
        return String.format(Locale.getDefault(), "≈ %.2f %s", imperial ? metres / 0.3048 : metres, imperial ? "ft" : "m");
    }
    public static String areaLabel(double squareMetres, boolean imperial) {
        return String.format(Locale.getDefault(), "≈ %.1f %s", imperial ? squareMetres / (0.3048 * 0.3048) : squareMetres, imperial ? "ft²" : "m²");
    }
    private static double cross(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
    }
    private static boolean onSegment(Point a, Point b, Point p) {
        double e = 1e-8;
        return Math.abs(cross(a, b, p)) < e && p.x >= Math.min(a.x, b.x) - e
            && p.x <= Math.max(a.x, b.x) + e && p.z >= Math.min(a.z, b.z) - e && p.z <= Math.max(a.z, b.z) + e;
    }
    private static boolean intersects(Point a, Point b, Point c, Point d) {
        double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
        return (abC * abD < 0 && cdA * cdB < 0) || onSegment(a, b, c) || onSegment(a, b, d)
            || onSegment(c, d, a) || onSegment(c, d, b);
    }
    private static double signedArea(List<Point> p) {
        // Translate to the first point to avoid cancellation far from the world origin.
        Point origin = p.get(0);
        double twiceArea = 0;
        for (int i = 1; i < p.size() - 1; i++) twiceArea += cross(origin, p.get(i), p.get(i + 1));
        return twiceArea / 2;
    }
    private static void validatePolygon(List<Point> p) {
        int n = p.size();
        if (n < 3) throw new IllegalArgumentException("Mark at least 3 corners, in boundary order.");
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (distance(p.get(i), p.get(j), true) < MIN_SEPARATION) {
                    throw new IllegalArgumentException("Corners overlap. Undo or reset and mark them again.");
                }
            }
            Point a = p.get(i), b = p.get((i + 1) % n), previous = p.get((i + n - 1) % n);
            if (onSegment(previous, a, b) || onSegment(a, b, previous)) {
                throw new IllegalArgumentException("An edge doubles back. Mark corners in boundary order.");
            }
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                if (intersects(a, b, p.get(j), p.get((j + 1) % n))) {
                    throw new IllegalArgumentException("Edges cross. Undo and mark corners around the boundary.");
                }
            }
        }
        if (Math.abs(signedArea(p)) < 0.01) throw new IllegalArgumentException("The plot is too narrow or small. Recheck the corners.");
    }
}

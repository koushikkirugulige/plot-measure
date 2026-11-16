package com.plotmeasure.measurement;

/** Converts a saved world point's clip coordinates into an on-screen marker. */
public final class ScreenProjection {
    private ScreenProjection() { }
    public static final class Bounds {
        public final float left, top, right, bottom;
        public Bounds(float left, float top, float right, float bottom) {
            if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right)
                    || !Float.isFinite(bottom) || right <= left || bottom <= top) {
                throw new IllegalArgumentException("The visible camera bounds must have positive area.");
            }
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
    public static final class Marker {
        public final float x, y, imageX, imageY, directionX, directionY;
        public final boolean offScreen, behind;
        private Marker(float x, float y, float imageX, float imageY, float directionX, float directionY,
                       boolean offScreen, boolean behind) {
            this.x = x; this.y = y; this.imageX = imageX; this.imageY = imageY;
            this.directionX = directionX; this.directionY = directionY;
            this.offScreen = offScreen; this.behind = behind;
        }
    }
    public static Marker project(float[] clip, int width, int height, Bounds bounds) {
        if (clip == null || clip.length != 4 || width <= 0 || height <= 0 || bounds == null) {
            throw new IllegalArgumentException("Projection requires clip coordinates and a valid viewport.");
        }
        for (float value : clip) if (!Float.isFinite(value)) throw new IllegalArgumentException("Clip coordinates must be finite.");
        boolean behind = clip[3] < 0;
        boolean projectable = clip[3] > 0.000001f;
        double imageX = projectable ? (clip[0] / (double) clip[3] + 1) * width / 2 : Double.NaN;
        double imageY = projectable ? (1 - clip[1] / (double) clip[3]) * height / 2 : Double.NaN;
        if (projectable && bounds.contains((float) imageX, (float) imageY)) {
            return new Marker((float) imageX, (float) imageY, (float) imageX, (float) imageY, 0, 0, false, false);
        }
        double cx = (bounds.left + bounds.right) / 2.0, cy = (bounds.top + bounds.bottom) / 2.0;
        // Dividing a point behind the camera by negative W would reverse the turn direction.
        double dx = projectable ? imageX - cx : clip[0] * (double) width / 2;
        double dy = projectable ? imageY - cy : -clip[1] * (double) height / 2;
        double magnitude = Math.hypot(dx, dy);
        if (magnitude < 1e-8) { dx = 1; dy = 0; magnitude = 1; }
        dx /= magnitude; dy /= magnitude;
        double reachX = Math.abs(dx) < 1e-8 ? Double.POSITIVE_INFINITY : (bounds.right - bounds.left) / 2.0 / Math.abs(dx);
        double reachY = Math.abs(dy) < 1e-8 ? Double.POSITIVE_INFINITY : (bounds.bottom - bounds.top) / 2.0 / Math.abs(dy);
        double reach = Math.min(reachX, reachY);
        float x = (float) Math.max(bounds.left, Math.min(bounds.right, cx + dx * reach));
        float y = (float) Math.max(bounds.top, Math.min(bounds.bottom, cy + dy * reach));
        return new Marker(x, y, (float) imageX, (float) imageY, (float) dx, (float) dy, true, behind);
    }
    /** Clips the real projected line, never connects the measurement to an edge-indicator location. */
    public static float[] segment(Marker a, Marker b, Bounds bounds) {
        return segmentTo(a, b.imageX, b.imageY, bounds);
    }
    public static float[] segmentTo(Marker a, float endX, float endY, Bounds bounds) {
        if (!Float.isFinite(a.imageX) || !Float.isFinite(a.imageY) || !Float.isFinite(endX) || !Float.isFinite(endY)) return null;
        double dx = (double) endX - a.imageX, dy = (double) endY - a.imageY;
        double[] interval = {0, 1};
        if (!clip(-dx, a.imageX - bounds.left, interval) || !clip(dx, bounds.right - a.imageX, interval)
                || !clip(-dy, a.imageY - bounds.top, interval) || !clip(dy, bounds.bottom - a.imageY, interval)) return null;
        return new float[]{(float) (a.imageX + interval[0] * dx), (float) (a.imageY + interval[0] * dy),
            (float) (a.imageX + interval[1] * dx), (float) (a.imageY + interval[1] * dy)};
    }
    private static boolean clip(double p, double q, double[] interval) {
        if (Math.abs(p) < 1e-8) return q >= 0;
        double t = q / p;
        if (p < 0) interval[0] = Math.max(interval[0], t);
        else interval[1] = Math.min(interval[1], t);
        return interval[0] <= interval[1];
    }
}

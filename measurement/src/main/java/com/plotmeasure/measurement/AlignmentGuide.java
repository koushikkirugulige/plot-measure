package com.plotmeasure.measurement;

import com.plotmeasure.measurement.Measurement.Point;
import java.util.Locale;

/** Advisory gravity-aligned guides. Never change the measured surface points. */
public final class AlignmentGuide {
    public enum Mode { OFF, HORIZONTAL, VERTICAL }
    private static final double MAX_OFFSET = 0.02;
    private static final double MAX_TILT_DEGREES = 2;

    private AlignmentGuide() { }

    public static final class Result {
        public final Mode mode;
        public final Point guidePoint;
        public final double offset, tiltDegrees;
        public final boolean aligned;

        private Result(Mode mode, Point guidePoint, double offset, double tiltDegrees) {
            this.mode = mode; this.guidePoint = guidePoint;
            this.offset = offset; this.tiltDegrees = tiltDegrees;
            aligned = Math.abs(offset) <= MAX_OFFSET && tiltDegrees <= MAX_TILT_DEGREES;
        }

        public String label(boolean imperial, boolean completed) {
            double magnitude = Math.abs(offset) * (imperial ? 1 / 0.0254 : 100);
            String amount = String.format(Locale.getDefault(), "%.1f %s", magnitude, imperial ? "in" : "cm");
            String tilt = String.format(Locale.getDefault(), "%.1f°", tiltDegrees);
            if (mode == Mode.HORIZONTAL) {
                return (aligned ? "Level" : completed ? "Not level" : offset > 0 ? "Aim lower" : "Aim higher")
                    + " · " + amount + (offset >= 0 ? " high" : " low") + " · tilt " + tilt;
            }
            return (aligned ? "Vertical" : completed ? "Not vertical" : "Aim toward the guide")
                + " · " + amount + " sideways · tilt " + tilt;
        }
    }

    /** Re-evaluate using the current frame's tracked points, including a completed distance. */
    public static Result evaluate(Mode guide, Measurement measurement, Point candidate, boolean tracking) {
        if (!tracking || guide == Mode.OFF || measurement.mode() != Measurement.Mode.DISTANCE || measurement.size() == 0) return null;
        Point start = measurement.points().get(0);
        Point end = measurement.closed() ? measurement.points().get(1) : candidate;
        if (end == null || Measurement.distance(start, end, false) < 0.02) return null;
        double horizontal = Math.hypot(end.x - start.x, end.z - start.z);
        double vertical = end.y - start.y;
        if (guide == Mode.HORIZONTAL) {
            return new Result(guide, new Point(end.x, start.y, end.z), vertical,
                Math.toDegrees(Math.atan2(Math.abs(vertical), horizontal)));
        }
        return new Result(guide, new Point(start.x, end.y, start.z), horizontal,
            Math.toDegrees(Math.atan2(horizontal, Math.abs(vertical))));
    }
}

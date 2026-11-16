package com.plotmeasure.measurement;

import com.plotmeasure.measurement.Measurement.Point;

/** Manual height along the gravity-aligned line through a tracked floor point. */
public final class HeightMeasurement {
    private HeightMeasurement() { }
    public static Point top(Point base, Point camera, Point direction) {
        double length = Math.hypot(Math.hypot(direction.x, direction.z), direction.y);
        if (length < 1e-8) throw new IllegalArgumentException("Aim toward the top of the subject.");
        double dx = direction.x / length, dy = direction.y / length, dz = direction.z / length;
        double horizontal = dx * dx + dz * dz;
        if (horizontal < 0.04) throw new IllegalArgumentException("Step back and look toward the subject, not straight up or down.");
        double t = ((base.x - camera.x) * dx + (base.z - camera.z) * dz) / horizontal;
        if (t < 0.2 || t > 5) throw new IllegalArgumentException("Keep the subject in front of you, within 0.2–5 m.");
        double miss = Math.hypot(camera.x + t * dx - base.x, camera.z + t * dz - base.z);
        if (miss > 0.10) throw new IllegalArgumentException("Align the centre dot above the marked floor point.");
        double height = camera.y + t * dy - base.y;
        if (height < 0.02 || height > 5) throw new IllegalArgumentException("Aim above the floor point, at a height below 5 m.");
        return new Point(base.x, base.y + height, base.z);
    }
}

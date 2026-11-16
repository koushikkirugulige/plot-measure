package com.plotmeasure.measurement;

import com.plotmeasure.measurement.Measurement.Point;

/** Requires a continuous half-second of nearby world-space targets, not repeated stale frames. */
public final class PlacementStability {
    private Point reference;
    private long since, last, lastFreshAt;
    private boolean ready;
    public boolean update(Point point, long timestamp, long now) {
        if (point == null || timestamp <= 0) { reset(); return false; }
        if (timestamp != last) lastFreshAt = now;
        if (now - lastFreshAt > 200_000_000L) { reset(); return false; }
        if (reference == null || timestamp < last || timestamp - last > 200_000_000L
                || Measurement.distance(reference, point, false) > 0.03) {
            reference = point; since = timestamp; last = timestamp; ready = false;
            return false;
        }
        if (timestamp == last) return ready;
        last = timestamp;
        ready = timestamp - since >= 500_000_000L;
        return ready;
    }
    public void reset() { reference = null; since = 0; last = 0; lastFreshAt = 0; ready = false; }
}

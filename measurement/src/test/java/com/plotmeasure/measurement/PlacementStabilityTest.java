package com.plotmeasure.measurement;

import static org.junit.Assert.*;
import com.plotmeasure.measurement.Measurement.Point;
import org.junit.Test;

public class PlacementStabilityTest {
    private final PlacementStability gate = new PlacementStability();
    private final Point point = new Point(0, 0, -2);
    private boolean sample(int ms) { return gate.update(point, 1 + ms * 1_000_000L, 1 + ms * 1_000_000L); }
    private void ready() { for (int ms = 0; ms <= 500; ms += 100) sample(ms); }
    @Test public void waitsForHalfSecondOfContinuousStableFrames() {
        for (int ms = 0; ms < 500; ms += 100) assertFalse(sample(ms));
        assertTrue(sample(500)); assertTrue(sample(600));
    }
    @Test public void jitterWithinThreeCentimetresIsAllowed() {
        for (int ms = 0; ms <= 500; ms += 100) gate.update(new Point(ms % 200 == 0 ? 0.01 : -0.01, 0, -2), 1 + ms * 1_000_000L, 1 + ms * 1_000_000L);
        assertTrue(sample(600));
    }
    @Test public void changingTargetRestartsStabilization() {
        ready(); assertFalse(gate.update(new Point(0.1, 0, -2), 600_000_001L, 600_000_001L));
    }
    @Test public void trackingLossAndPauseResetReadiness() {
        ready(); assertFalse(gate.update(null, 600_000_001L, 600_000_001L)); assertFalse(sample(700));
        ready(); gate.reset(); assertFalse(sample(600));
    }
    @Test public void gapOrNewSessionTimestampRequiresFreshFrames() {
        ready(); assertFalse(sample(1000));
        ready(); assertFalse(gate.update(point, 1, 1));
    }
    @Test public void staleFrameExpiresEvenAfterTargetWasReady() {
        ready();
        assertFalse(gate.update(point, 500_000_001L, 800_000_001L));
        assertFalse(gate.update(point, 500_000_001L, 900_000_001L));
    }
    @Test public void repeatedFrameCannotBecomeReady() {
        for (int i = 0; i < 100; i++) assertFalse(sample(0));
        assertFalse(gate.update(point, 0, 0));
    }
}

package com.plotmeasure.measurement;

import static org.junit.Assert.*;
import com.plotmeasure.measurement.Measurement.Point;
import org.junit.Test;

public class HeightMeasurementTest {
    private final Point base = new Point(0, 0, 0);
    @Test public void measuresHeadAboveFloorWithoutHeadSurface() {
        Point top = HeightMeasurement.top(base, new Point(0, 1.5, 2), new Point(0, 0.3, -2));
        assertEquals(1.8, top.y, 1e-9); assertEquals(0, top.x, 0); assertEquals(0, top.z, 0);
    }
    @Test public void movingToAnotherViewpointKeepsSameHeight() {
        Point top = HeightMeasurement.top(base, new Point(2, 1, 2), new Point(-2, 0.8, -2));
        assertEquals(1.8, top.y, 1e-9);
    }
    @Test public void floorElevationAndRayScaleDoNotChangeHeight() {
        Point floor = new Point(5, 10, 5);
        Point top = HeightMeasurement.top(floor, new Point(5, 11.5, 7), new Point(0, 3, -20));
        assertEquals(1.8, top.y - floor.y, 1e-9);
    }
    @Test public void sidewaysAimMustStayNearVerticalGuide() {
        assertThrows(IllegalArgumentException.class, () -> HeightMeasurement.top(base, new Point(0, 1.5, 2), new Point(1, 0.3, -2)));
    }
    @Test public void nearbyAimSnapsToBaseVerticalLine() {
        Point top = HeightMeasurement.top(base, new Point(0, 1.5, 2), new Point(0.05, 0.3, -2));
        assertEquals(0, top.x, 0); assertEquals(0, top.z, 0); assertEquals(1.8, top.y, 0.001);
    }
    @Test public void rejectsBehindDistantBelowFloorAndVerticalRays() {
        Point camera = new Point(0, 1.5, 2);
        for (Point direction : new Point[]{new Point(0, 0.3, 2), new Point(0, -2, -2), new Point(0, 1, 0), new Point(0, 0, 0)}) {
            assertThrows(IllegalArgumentException.class, () -> HeightMeasurement.top(base, camera, direction));
        }
        assertThrows(IllegalArgumentException.class, () -> HeightMeasurement.top(base, new Point(0, 1.5, 8), new Point(0, 0.3, -8)));
    }
    @Test public void heightModeMeasuresVerticalComponentAndClosesAfterTwoPoints() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.HEIGHT);
        m.add(base); m.add(new Point(0.1, 1.8, 0.1));
        assertTrue(m.closed()); assertEquals(1.8, m.totalLength(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> m.add(new Point(0, 2, 0)));
    }
    @Test public void heightUndoAndResetAllowNewFloorAndTop() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.HEIGHT);
        m.add(base); m.add(new Point(0, 1.8, 0)); m.undo();
        assertEquals(1, m.size()); assertFalse(m.closed());
        m.add(new Point(0, 2, 0)); assertEquals(2, m.totalLength(), 0);
        m.clear(); assertEquals(0, m.size()); assertEquals(Measurement.Mode.HEIGHT, m.mode());
        m.add(base); assertThrows(IllegalArgumentException.class, () -> m.add(new Point(0, -1, 0)));
    }
    @Test public void baseAndTopCanMoveTogetherWithoutChangingHeight() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.HEIGHT);
        m.add(base); m.add(new Point(0, 1.8, 0));
        m.updateTrackedPoints(java.util.Arrays.asList(new Point(2, 0.2, 3), new Point(2, 2, 3)));
        assertEquals(1.8, m.totalLength(), 1e-9);
    }
}

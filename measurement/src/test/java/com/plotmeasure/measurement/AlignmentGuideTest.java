package com.plotmeasure.measurement;

import static org.junit.Assert.*;
import com.plotmeasure.measurement.AlignmentGuide.Mode;
import com.plotmeasure.measurement.AlignmentGuide.Result;
import com.plotmeasure.measurement.Measurement.Point;
import java.util.Arrays;
import org.junit.Test;

public class AlignmentGuideTest {
    private final Measurement measurement = new Measurement();
    private final Point start = new Point(3, 2, 4);

    private Result guide(Mode mode, Point end) {
        if (measurement.size() == 0) measurement.add(start);
        return AlignmentGuide.evaluate(mode, measurement, end, true);
    }

    @Test public void horizontalAllowsAnyGroundDirectionButKeepsHeight() {
        Result result = guide(Mode.HORIZONTAL, new Point(6, 2, 8));
        assertTrue(result.aligned); assertEquals(0, result.offset, 0); assertEquals(0, result.tiltDegrees, 0);
        assertEquals(6, result.guidePoint.x, 0); assertEquals(2, result.guidePoint.y, 0); assertEquals(8, result.guidePoint.z, 0);
    }
    @Test public void highAndLowTargetsHaveSignedHeightError() {
        Result high = guide(Mode.HORIZONTAL, new Point(4, 3, 4));
        Result low = guide(Mode.HORIZONTAL, new Point(4, 1, 4));
        assertFalse(high.aligned); assertEquals(1, high.offset, 0); assertEquals(45, high.tiltDegrees, 1e-9);
        assertFalse(low.aligned); assertEquals(-1, low.offset, 0); assertEquals(45, low.tiltDegrees, 1e-9);
        assertEquals(2, high.guidePoint.y, 0); assertEquals(2, low.guidePoint.y, 0);
        assertTrue(high.label(false, false).startsWith("Aim lower")); assertTrue(low.label(false, false).startsWith("Aim higher"));
    }
    @Test public void verticalWorksAboveAndBelowStart() {
        for (double y : new double[]{0, 4}) {
            Result result = guide(Mode.VERTICAL, new Point(3, y, 4));
            assertTrue(result.aligned); assertEquals(0, result.tiltDegrees, 0);
            assertEquals(3, result.guidePoint.x, 0); assertEquals(y, result.guidePoint.y, 0); assertEquals(4, result.guidePoint.z, 0);
        }
    }
    @Test public void verticalIncludesBothHorizontalAxes() {
        Result result = guide(Mode.VERTICAL, new Point(3.3, 4, 4.4));
        assertFalse(result.aligned); assertEquals(0.5, result.offset, 1e-9);
        assertEquals(Math.toDegrees(Math.atan2(0.5, 2)), result.tiltDegrees, 1e-9);
        assertTrue(result.label(false, false).startsWith("Aim toward the guide"));
    }
    @Test public void nearlyAlignedTargetsAreAcceptedWithinBothLimits() {
        assertTrue(guide(Mode.HORIZONTAL, new Point(4, 2.01, 4)).aligned);
        assertTrue(guide(Mode.VERTICAL, new Point(3.01, 3, 4)).aligned);
    }
    @Test public void longLinesStillRequireSmallAbsoluteOffset() {
        assertFalse(guide(Mode.HORIZONTAL, new Point(13, 2.03, 4)).aligned);
        assertFalse(guide(Mode.VERTICAL, new Point(3.03, 12, 4)).aligned);
    }
    @Test public void shortLinesStillRequireSmallAngle() {
        assertFalse(guide(Mode.HORIZONTAL, new Point(3.1, 2.01, 4)).aligned);
        assertFalse(guide(Mode.VERTICAL, new Point(3.01, 2.1, 4)).aligned);
    }
    @Test public void oppositeDirectionsHaveNinetyDegreeError() {
        Result horizontal = guide(Mode.HORIZONTAL, new Point(3, 3, 4));
        Result vertical = guide(Mode.VERTICAL, new Point(4, 2, 4));
        assertEquals(90, horizontal.tiltDegrees, 0); assertFalse(horizontal.aligned);
        assertEquals(90, vertical.tiltDegrees, 0); assertFalse(vertical.aligned);
    }
    @Test public void missingFirstPointTargetTrackingOrDisabledGuideShowsNothing() {
        Point target = new Point(4, 2, 4);
        assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, target, true));
        measurement.add(start);
        assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, null, true));
        assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, target, false));
        assertNull(AlignmentGuide.evaluate(Mode.OFF, measurement, target, true));
        assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, start, true));
    }
    @Test public void guidesDoNotChangeSurfacePointsOrDistance() {
        Point end = new Point(6, 3, 8);
        Result result = guide(Mode.HORIZONTAL, end);
        assertEquals(1, end.y - result.guidePoint.y, 0);
        measurement.add(end);
        assertSame(start, measurement.points().get(0)); assertSame(end, measurement.points().get(1));
        assertEquals(Math.sqrt(26), measurement.totalLength(), 1e-9);
        assertFalse(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, null, true).aligned);
    }
    @Test public void completedLineUsesTrackedEndpointsAndUndoUsesNewTarget() {
        measurement.add(start); measurement.add(new Point(4, 2, 4));
        assertTrue(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, null, true).aligned);
        measurement.updateTrackedPoints(Arrays.asList(new Point(13, 5, 14), new Point(14, 5.1, 14)));
        assertEquals(0.1, AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, null, true).offset, 1e-9);
        assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, null, false));
        measurement.undo();
        assertTrue(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, new Point(14, 5, 14), true).aligned);
        measurement.clear(); assertNull(AlignmentGuide.evaluate(Mode.HORIZONTAL, measurement, start, true));
    }
    @Test public void heightAndPlotKeepTheirExistingSemantics() {
        for (Measurement.Mode mode : new Measurement.Mode[]{Measurement.Mode.HEIGHT, Measurement.Mode.PLOT}) {
            measurement.setMode(mode); measurement.add(start);
            assertNull(AlignmentGuide.evaluate(Mode.VERTICAL, measurement, new Point(3, 4, 4), true));
        }
    }
    @Test public void offsetLabelsFollowUnitSelection() {
        Result result = guide(Mode.VERTICAL, new Point(3.254, 4, 4));
        assertTrue(result.label(true, false).contains(String.format(java.util.Locale.getDefault(), "%.1f in", 10.0)));
        assertTrue(result.label(false, false).contains(String.format(java.util.Locale.getDefault(), "%.1f cm", 25.4)));
    }
    @Test public void completedReadoutDoesNotTellUserToMoveTheTarget() {
        Result horizontal = guide(Mode.HORIZONTAL, new Point(4, 3, 4));
        Result vertical = guide(Mode.VERTICAL, new Point(4, 3, 4));
        assertTrue(horizontal.label(false, true).startsWith("Not level"));
        assertTrue(vertical.label(false, true).startsWith("Not vertical"));
    }
}

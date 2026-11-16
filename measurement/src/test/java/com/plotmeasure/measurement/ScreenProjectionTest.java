package com.plotmeasure.measurement;

import static org.junit.Assert.*;
import com.plotmeasure.measurement.ScreenProjection.Bounds;
import com.plotmeasure.measurement.ScreenProjection.Marker;
import org.junit.Test;

public class ScreenProjectionTest {
    private static final Bounds CAMERA = new Bounds(30, 120, 970, 700);
    private static final float EPS = 0.001f;
    private Marker marker(float x, float y, float w) {
        return ScreenProjection.project(new float[]{x, y, 0, w}, 1000, 1000, CAMERA);
    }
    @Test public void visiblePointStaysAtItsActualImagePosition() {
        Marker p = marker(0, 0, 1);
        assertNotNull(p); assertFalse(p.offScreen); assertFalse(p.behind);
        assertEquals(500, p.x, EPS); assertEquals(500, p.y, EPS);
    }
    @Test public void pointOutsideRightEdgeRemainsRepresented() {
        Marker p = marker(3, 0, 1);
        assertNotNull("A saved point must have an indicator after leaving the frame", p);
        assertTrue(p.offScreen); assertFalse(p.behind); assertEquals(CAMERA.right, p.x, EPS);
        assertTrue(p.directionX > 0); assertEquals(2000, p.imageX, EPS);
    }
    @Test public void pointOutsideLeftEdgeRemainsRepresented() {
        Marker p = marker(-3, 0, 1);
        assertNotNull(p); assertTrue(p.offScreen); assertEquals(CAMERA.left, p.x, EPS); assertTrue(p.directionX < 0);
    }
    @Test public void pointCoveredByBottomControlsGetsAnIndicator() {
        Marker p = marker(0, -0.8f, 1);
        assertTrue(p.offScreen); assertEquals(CAMERA.bottom, p.y, EPS); assertTrue(p.directionY > 0);
    }
    @Test public void pointCoveredByTopControlsGetsAnIndicator() {
        Marker p = marker(0, 0.9f, 1);
        assertTrue(p.offScreen); assertEquals(CAMERA.top, p.y, EPS); assertTrue(p.directionY < 0);
    }
    @Test public void turningPastPointDoesNotMirrorItsDirection() {
        Marker p = marker(1, 0, -1);
        assertNotNull(p); assertTrue(p.offScreen); assertTrue(p.behind);
        assertEquals(CAMERA.right, p.x, EPS); assertTrue(p.directionX > 0);
    }
    @Test public void pointDirectlyBehindHasFiniteIndicator() {
        Marker p = marker(0, 0, -1);
        assertNotNull(p); assertTrue(p.behind); assertTrue(p.offScreen);
        assertTrue(Float.isFinite(p.x)); assertTrue(Float.isFinite(p.y));
        assertEquals(CAMERA.right, p.x, EPS);
    }
    @Test public void cameraPlaneDoesNotDivideByZero() {
        Marker p = marker(-1, 0, 0);
        assertNotNull(p); assertTrue(p.offScreen); assertEquals(CAMERA.left, p.x, EPS);
        assertTrue(Float.isFinite(p.y));
    }
    @Test public void pointReturnsToSameLocationAfterCameraTurnsBack() {
        Marker before = marker(0.2f, 0.1f, 1);
        Marker outside = marker(2, 0.1f, 1);
        Marker after = marker(0.2f, 0.1f, 1);
        assertNotNull(outside); assertTrue(outside.offScreen);
        assertFalse(after.offScreen); assertEquals(before.x, after.x, EPS); assertEquals(before.y, after.y, EPS);
    }
    @Test public void turningCameraDoesNotRemoveSavedPointOrBlockSecondEndpoint() {
        Measurement m = new Measurement(); m.add(new Measurement.Point(0, 0, -2));
        assertNotNull(marker(-4, 0, 1));
        assertEquals(1, m.size());
        m.add(new Measurement.Point(3, 0, -2));
        assertEquals(3, m.totalLength(), EPS); assertTrue(m.closed());
    }
    @Test public void diagonalIndicatorStaysInsideUnobscuredCameraBounds() {
        Marker p = marker(20, 30, 1);
        assertNotNull(p); assertTrue(p.offScreen);
        assertTrue(p.x >= CAMERA.left && p.x <= CAMERA.right);
        assertTrue(p.y >= CAMERA.top && p.y <= CAMERA.bottom);
        assertTrue(p.directionX > 0); assertTrue(p.directionY < 0);
    }
    @Test public void lineFromOffScreenEndpointIsClippedToCameraArea() {
        float[] line = ScreenProjection.segment(marker(3, 0, 1), marker(0, 0, 1), CAMERA);
        assertArrayEquals(new float[]{970, 500, 500, 500}, line, EPS);
    }
    @Test public void lineWithBothEndpointsOutsideStillCrossesTheScreen() {
        float[] line = ScreenProjection.segment(marker(-3, 0, 1), marker(3, 0, 1), CAMERA);
        assertArrayEquals(new float[]{30, 500, 970, 500}, line, EPS);
    }
    @Test public void lineOutsideTheCameraAreaIsNotDrawnAlongIndicatorLocations() {
        assertNull(ScreenProjection.segment(marker(3, 0, 1), marker(4, 0.5f, 1), CAMERA));
    }
    @Test public void behindCameraPointDoesNotDrawMirroredMeasurementLine() {
        assertNull(ScreenProjection.segment(marker(1, 0, -1), marker(0, 0, 1), CAMERA));
    }
    @Test public void livePreviewFromOffScreenStartReachesTarget() {
        assertArrayEquals(new float[]{30, 450, 500, 450},
            ScreenProjection.segmentTo(marker(-3, 0.1f, 1), 500, 450, CAMERA), EPS);
    }
    @Test public void diagonalLineIsClippedOnCorrectEdges() {
        float[] line = ScreenProjection.segment(marker(-2, 2, 1), marker(2, -2, 1), CAMERA);
        assertArrayEquals(new float[]{120, 120, 700, 700}, line, EPS);
    }
    @Test public void verticalLineAndZeroLengthLineDoNotDivideByZero() {
        assertArrayEquals(new float[]{500, 120, 500, 700},
            ScreenProjection.segment(marker(0, 2, 1), marker(0, -2, 1), CAMERA), EPS);
        assertArrayEquals(new float[]{500, 500, 500, 500},
            ScreenProjection.segment(marker(0, 0, 1), marker(0, 0, 1), CAMERA), EPS);
    }
    @Test public void invalidViewportAndNonFiniteInputAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, 0, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, Float.NaN, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> marker(Float.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ScreenProjection.project(new float[]{0, 0, 0, 1}, 0, 1000, CAMERA));
    }
    @Test public void drawingOffScreenPlotDoesNotChangeAreaPerimeterOrPointOrder() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT);
        m.add(new Measurement.Point(0, 0, 0)); m.add(new Measurement.Point(3, 0, 0));
        m.add(new Measurement.Point(3, 0, 4)); m.add(new Measurement.Point(0, 0, 4)); m.finishPlot();
        for (float x : new float[]{-3, 0, 3, 10}) assertNotNull(marker(x, 0, 1));
        assertEquals(4, m.size()); assertEquals(12, m.area(), EPS); assertEquals(14, m.totalLength(), EPS);
        assertEquals(3, m.points().get(1).x, EPS); assertEquals(0, m.points().get(1).z, EPS);
    }
}

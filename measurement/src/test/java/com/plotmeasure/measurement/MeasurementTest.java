package com.plotmeasure.measurement;

import static org.junit.Assert.*;
import com.plotmeasure.measurement.Measurement.Point;
import java.util.Arrays;
import java.util.Locale;
import org.junit.Test;

public class MeasurementTest {
    private static final double EPS = 1e-8;
    private static Point p(double x, double z) { return new Point(x, 0, z); }
    private static Measurement polygon(Point... points) {
        Measurement model = new Measurement(); model.setMode(Measurement.Mode.PLOT);
        for (Point point : points) model.add(point);
        model.finishPlot(); return model;
    }
    @Test public void distanceMeasuresAllThreeDimensionsAndClosesAtTwoPoints() {
        Measurement m = new Measurement(); m.add(new Point(0, 0, 0)); m.add(new Point(3, 4, 12));
        assertEquals(13, m.totalLength(), EPS); assertTrue(m.closed());
        assertThrows(IllegalArgumentException.class, () -> m.add(p(4, 0)));
        assertEquals(2, m.size());
    }
    @Test public void rectangleHasCorrectAreaPerimeterAndSides() {
        Measurement m = polygon(p(0, 0), p(30, 0), p(30, 40), p(0, 40));
        assertEquals(1200, m.area(), EPS); assertEquals(140, m.totalLength(), EPS);
        assertEquals(Arrays.asList(30.0, 40.0, 30.0, 40.0), m.sideLengths());
    }
    @Test public void clockwiseAndCounterclockwiseAreasAgree() {
        assertEquals(polygon(p(0, 0), p(6, 0), p(0, 4)).area(), polygon(p(0, 4), p(6, 0), p(0, 0)).area(), EPS);
    }
    @Test public void concavePlotIsSupported() {
        Measurement m = polygon(p(0, 0), p(4, 0), p(4, 2), p(2, 2), p(2, 4), p(0, 4));
        assertEquals(12, m.area(), EPS); assertEquals(16, m.totalLength(), EPS);
    }
    @Test public void groundMeasurementIsHorizontalProjection() {
        Measurement m = polygon(new Point(0, 0, 0), new Point(3, 4, 0), new Point(3, 4, 4), new Point(0, 0, 4));
        assertEquals(12, m.area(), EPS); assertEquals(14, m.totalLength(), EPS); assertEquals(4, m.heightRange(), EPS);
    }
    @Test public void translationAndRotationPreserveAreaAndPerimeter() {
        double angle = 0.823; Point[] points = new Point[4];
        Point[] original = {p(0, 0), p(10, 0), p(10, 20), p(0, 20)};
        for (int i = 0; i < 4; i++) {
            Point p = original[i]; points[i] = p(1000 + p.x * Math.cos(angle) - p.z * Math.sin(angle), -2000 + p.x * Math.sin(angle) + p.z * Math.cos(angle));
        }
        Measurement m = polygon(points); assertEquals(200, m.area(), EPS); assertEquals(60, m.totalLength(), EPS);
    }
    @Test public void openPlotDoesNotIncludeClosingSide() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT);
        m.add(p(0, 0)); m.add(p(3, 0)); m.add(p(3, 4));
        assertEquals(7, m.totalLength(), EPS); assertFalse(m.closed());
        assertThrows(IllegalStateException.class, m::area);
        m.finishPlot(); assertEquals(12, m.totalLength(), EPS); assertEquals(6, m.area(), EPS);
    }
    @Test public void crossingEdgesCannotBeFinished() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT);
        for (Point p : new Point[]{p(0, 0), p(5, 4), p(0, 4), p(4, 0)}) m.add(p);
        assertThrows(IllegalArgumentException.class, m::finishPlot); assertFalse(m.closed());
    }
    @Test public void repeatedAndNearbyPointsAreRejectedWithoutMutation() {
        Measurement m = new Measurement(); m.add(p(1, 1));
        assertThrows(IllegalArgumentException.class, () -> m.add(p(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> m.add(p(1.001, 1)));
        assertEquals(1, m.size());
    }
    @Test public void plotRejectsCornersDirectlyAboveEachOther() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT); m.add(p(0, 0));
        assertThrows(IllegalArgumentException.class, () -> m.add(new Point(0, 4, 0)));
    }
    @Test public void lineCannotBecomeAnArea() {
        assertThrows(IllegalArgumentException.class, () -> polygon(p(0, 0), p(1, 0), p(2, 0)));
    }
    @Test public void straightIntermediateBoundaryPointIsAllowed() {
        assertEquals(16, polygon(p(0, 0), p(2, 0), p(4, 0), p(4, 4), p(0, 4)).area(), EPS);
    }
    @Test public void doublingBackAlongAnEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> polygon(p(0, 0), p(4, 0), p(2, 0), p(4, 4), p(0, 4)));
    }
    @Test public void closingEdgeTouchingAnotherCornerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> polygon(p(0, 0), p(4, 0), p(2, 2), p(4, 4), p(0, 4), p(2, 0)));
    }
    @Test public void undoReopensPlotAndRemovesOneCorner() {
        Measurement m = polygon(p(0, 0), p(4, 0), p(4, 4), p(0, 4)); m.undo();
        assertFalse(m.closed()); assertEquals(3, m.size()); m.add(p(0, 3)); m.finishPlot(); assertEquals(14, m.area(), EPS);
    }
    @Test public void undoDistanceAllowsReplacingEndpoint() {
        Measurement m = new Measurement(); m.add(p(0, 0)); m.add(p(1, 0)); m.undo(); m.add(p(2, 0));
        assertEquals(2, m.totalLength(), EPS); assertTrue(m.closed());
    }
    @Test public void changingModeClearsPointsButSelectingCurrentModePreservesThem() {
        Measurement m = new Measurement(); m.add(p(0, 0)); m.setMode(Measurement.Mode.DISTANCE); assertEquals(1, m.size());
        m.setMode(Measurement.Mode.PLOT); assertEquals(0, m.size()); assertFalse(m.closed());
    }
    @Test public void latestAnchorPosesUpdateMeasurements() {
        Measurement m = polygon(p(0, 0), p(4, 0), p(4, 4), p(0, 4));
        m.updateTrackedPoints(Arrays.asList(p(0, 0), p(5, 0), p(5, 4), p(0, 4)));
        assertEquals(20, m.area(), EPS); assertEquals(18, m.totalLength(), EPS);
    }
    @Test public void driftThatCrossesEdgesInvalidatesArea() {
        Measurement m = polygon(p(0, 0), p(4, 0), p(4, 4), p(0, 4));
        m.updateTrackedPoints(Arrays.asList(p(0, 0), p(4, 4), p(4, 0), p(0, 4)));
        assertThrows(IllegalArgumentException.class, m::area);
    }
    @Test public void anchorCountMismatchIsRejectedWithoutDiscardingPoints() {
        Measurement m = new Measurement(); m.add(p(0, 0));
        assertThrows(IllegalArgumentException.class, () -> m.updateTrackedPoints(Arrays.asList(p(1, 0), p(2, 0))));
        assertEquals(1, m.size()); assertEquals(0, m.points().get(0).x, EPS);
    }
    @Test public void refreshingWithCurrentPointsDoesNotClearMeasurement() {
        Measurement m = new Measurement(); m.add(p(0, 0)); m.add(p(3, 4));
        m.updateTrackedPoints(m.points()); assertEquals(5, m.totalLength(), EPS); assertEquals(2, m.size());
    }
    @Test public void tooFewCornersCannotBeFinished() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT); m.add(p(0, 0)); m.add(p(2, 0));
        assertThrows(IllegalArgumentException.class, m::finishPlot); assertFalse(m.closed());
    }
    @Test public void finiteCoordinateValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, Double.POSITIVE_INFINITY, 0));
    }
    @Test public void markerCountIsBounded() {
        Measurement m = new Measurement(); m.setMode(Measurement.Mode.PLOT);
        for (int i = 0; i < Measurement.MAX_POINTS; i++) m.add(p(i, i * i));
        assertThrows(IllegalArgumentException.class, () -> m.add(p(100, 100))); assertEquals(32, m.size());
    }
    @Test public void imperialConversionsUseExactInternationalFoot() {
        Locale original = Locale.getDefault();
        try { Locale.setDefault(Locale.US); assertEquals("≈ 1.00 ft", Measurement.lengthLabel(0.3048, true)); assertEquals("≈ 1.0 ft²", Measurement.areaLabel(0.3048 * 0.3048, true)); }
        finally { Locale.setDefault(original); }
    }
    @Test public void clearingMeasurementKeepsModeAndResetsResults() {
        Measurement m = polygon(p(0, 0), p(3, 0), p(0, 4)); m.clear(); m.undo();
        assertEquals(0, m.size()); assertFalse(m.closed()); assertEquals(0, m.totalLength(), EPS); assertEquals(Measurement.Mode.PLOT, m.mode());
    }
}

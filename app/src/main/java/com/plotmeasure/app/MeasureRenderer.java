package com.plotmeasure.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotTrackingException;
import com.google.ar.core.exceptions.ResourceExhaustedException;
import com.plotmeasure.measurement.AlignmentGuide;
import com.plotmeasure.measurement.Measurement;
import com.plotmeasure.measurement.HeightMeasurement;
import com.plotmeasure.measurement.PlacementStability;
import com.plotmeasure.measurement.Measurement.Point;
import com.plotmeasure.measurement.ScreenProjection;
import com.plotmeasure.measurement.ScreenProjection.Bounds;
import com.plotmeasure.measurement.ScreenProjection.Marker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class MeasureRenderer implements GLSurfaceView.Renderer {
    enum Action { ADD, UNDO, CLEAR, FINISH, DISTANCE, HEIGHT, PLOT, GUIDE }
    interface Listener { void onFrame(State state); void onFailure(String message); }
    static final class State {
        final Measurement.Mode mode;
        final boolean closed, tracking, canAdd, valid;
        final int count, offScreenCount;
        final Marker[] screen;
        final Marker previewScreen, heightGuide, alignmentScreen;
        final AlignmentGuide.Mode alignmentMode;
        final AlignmentGuide.Result alignment;
        final Bounds bounds;
        final List<Double> sides;
        final double total, area, preview;
        final float aimX, aimY;
        final String status, hint, warning;
        State(Measurement model, boolean tracking, boolean canAdd, boolean valid, Marker[] screen,
              double area, double preview, String status, String hint, String warning, float aimX, float aimY, Bounds bounds, Marker previewScreen, Marker heightGuide,
              AlignmentGuide.Mode alignmentMode, AlignmentGuide.Result alignment, Marker alignmentScreen) {
            mode = model.mode(); closed = model.closed(); count = model.size();
            this.tracking = tracking; this.canAdd = canAdd; this.valid = valid; this.screen = screen;
            sides = Collections.unmodifiableList(model.sideLengths()); total = model.totalLength();
            this.area = area; this.preview = preview; this.status = status; this.hint = hint; this.warning = warning;
            this.aimX = aimX; this.aimY = aimY;
            this.bounds = bounds; this.previewScreen = previewScreen; this.heightGuide = heightGuide;
            this.alignmentMode = alignmentMode; this.alignment = alignment; this.alignmentScreen = alignmentScreen;
            int outside = 0;
            for (Marker marker : screen) if (marker != null && marker.offScreen) outside++;
            offScreenCount = outside;
        }
        boolean canSave() { return tracking && closed && valid; }
    }
    private final Display display;
    private final Listener listener;
    private final Measurement model = new Measurement();
    private final PlacementStability stability = new PlacementStability();
    private AlignmentGuide.Mode alignmentMode = AlignmentGuide.Mode.OFF;
    private double heightOffset;
    private final List<Anchor> anchors = new ArrayList<>();
    private final ArrayBlockingQueue<Action> actions = new ArrayBlockingQueue<>(32);
    private final CameraBackground background = new CameraBackground();
    private final float[] view = new float[16], projection = new float[16], combined = new float[16];
    private volatile Session session;
    private Session textureSession;
    private int width, height;
    private volatile float[] requestedAim;
    private volatile Bounds requestedBounds;
    private float aimX, aimY;
    private boolean failed;
    private String feedback = "";
    private long feedbackUntil;

    MeasureRenderer(Display display, Listener listener) { this.display = display; this.listener = listener; }
    void setSession(Session session) { this.session = session; }
    void action(Action action) { actions.offer(action); }
    // Called only after GLSurfaceView.onPause has stopped frame processing.
    void pausePlacement() { actions.clear(); stability.reset(); }
    void setAim(float x, float y) { requestedAim = new float[]{x, y}; }
    void setVisibleBounds(Bounds bounds) { requestedBounds = bounds; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try { background.create(); textureSession = null; }
        catch (IllegalStateException e) { fail("Unable to display the AR camera.", e); }
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width; this.height = height; GLES20.glViewport(0, 0, width, height);
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.04f, 0.06f, 0.05f, 1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session current = session;
        if (current == null || width == 0 || height == 0 || failed) return;
        float[] aim = requestedAim;
        aimX = aim == null ? width / 2f : aim[0]; aimY = aim == null ? height / 2f : aim[1];
        Bounds bounds = requestedBounds;
        if (bounds == null || bounds.right > width || bounds.bottom > height) {
            bounds = new Bounds(width * 0.04f, height * 0.15f, width * 0.96f, height * 0.6f);
        }
        try {
            if (textureSession != current) { current.setCameraTextureName(background.texture); textureSession = current; }
            current.setDisplayGeometry(display.getRotation(), width, height);
            Frame frame = current.update();
            background.draw(frame);
            Camera camera = frame.getCamera();
            boolean tracking = updatePoints(camera);
            camera.getViewMatrix(view, 0); camera.getProjectionMatrix(projection, 0, 0.05f, 500f);
            Matrix.multiplyMM(combined, 0, projection, 0, view, 0);
            Candidate target = candidate(frame, camera, tracking);
            boolean stable = stability.update(target.point, frame.getTimestamp(), SystemClock.elapsedRealtimeNanos());
            Action action;
            while ((action = actions.poll()) != null) {
                try {
                    switch (action) {
                        case CLEAR: clearAnchors(); model.clear(); feedback = ""; break;
                        case DISTANCE: changeMode(Measurement.Mode.DISTANCE); break;
                        case PLOT: changeMode(Measurement.Mode.PLOT); break;
                        case HEIGHT: changeMode(Measurement.Mode.HEIGHT); break;
                        case GUIDE:
                            alignmentMode = alignmentMode == AlignmentGuide.Mode.OFF ? AlignmentGuide.Mode.HORIZONTAL
                                : alignmentMode == AlignmentGuide.Mode.HORIZONTAL ? AlignmentGuide.Mode.VERTICAL : AlignmentGuide.Mode.OFF;
                            feedback = ""; break;
                        case UNDO:
                            if (model.mode() == Measurement.Mode.HEIGHT && model.size() == 2) heightOffset = 0;
                            else if (!anchors.isEmpty()) anchors.remove(anchors.size() - 1).detach();
                            model.undo(); feedback = ""; break;
                        case ADD:
                            if (!tracking) throw new IllegalArgumentException("Wait for tracking to recover before marking a point.");
                            if (target.point == null) throw new IllegalArgumentException(target.reason);
                            if (!stable) throw new IllegalArgumentException("Hold the target steady briefly before marking.");
                            if (model.mode() == Measurement.Mode.HEIGHT && model.size() == 1) {
                                heightOffset = target.point.y - model.points().get(0).y;
                                model.add(target.point);
                            } else {
                                Anchor anchor = target.hit.createAnchor();
                                try { model.add(point(anchor.getPose())); anchors.add(anchor); }
                                catch (IllegalArgumentException e) { anchor.detach(); throw e; }
                            }
                            feedback = ""; break;
                        case FINISH:
                            if (!tracking) throw new IllegalArgumentException("Wait for all corners to be tracked before finishing.");
                            model.finishPlot(); feedback = ""; break;
                    }
                } catch (IllegalArgumentException e) { feedback(e.getMessage()); }
                catch (NotTrackingException | ResourceExhaustedException e) {
                    Log.w("PlotMeasure", "Anchor creation unavailable", e);
                    feedback("Point could not be anchored. Scan the surface and try again.");
                }
                stability.reset();
                tracking = updatePoints(camera);
                target = candidate(frame, camera, tracking);
                stable = stability.update(target.point, frame.getTimestamp(), SystemClock.elapsedRealtimeNanos());
            }
            Marker[] screen = new Marker[model.size()];
            if (tracking) for (int i = 0; i < model.size(); i++) screen[i] = project(model.points().get(i), bounds);
            double area = 0, preview = -1;
            boolean valid = true;
            String warning = "Approximate measurement · Recheck against a known distance";
            if (model.mode() == Measurement.Mode.PLOT) {
                warning = "Horizontal ground area · Uneven terrain can reduce accuracy";
                if (model.heightRange() > 0.5) warning = "Corners differ in height. Area is a horizontal projection.";
                if (model.closed()) {
                    try { area = model.area(); }
                    catch (IllegalArgumentException e) { valid = false; warning = e.getMessage(); }
                }
            }
            if (model.mode() == Measurement.Mode.HEIGHT) warning = "Vertical estimate · Stand still directly above the floor mark";
            if (valid && model.span() > 10) warning = "Long span: AR drift may be significant. Recheck the corners.";
            if (target.point != null && model.size() > 0) {
                preview = model.mode() == Measurement.Mode.HEIGHT ? target.point.y - model.points().get(0).y
                    : Measurement.distance(model.points().get(model.size() - 1), target.point, model.mode() == Measurement.Mode.PLOT);
            }
            String status = !tracking ? "TRACKING PAUSED" : model.closed() ? "MEASUREMENT READY"
                : target.point == null ? "POINT UNAVAILABLE" : stable ? "READY TO MARK" : "HOLD STEADY";
            String hint = !tracking ? trackingHint(camera) : target.point == null ? target.reason
                : !stable ? "Keep the dot on the same point for half a second." : placementHint();
            if (!feedback.isEmpty() && SystemClock.uptimeMillis() < feedbackUntil) hint = feedback;
            Marker heightGuide = null;
            if (tracking && model.mode() == Measurement.Mode.HEIGHT && model.size() == 1) {
                Point base = model.points().get(0);
                heightGuide = project(new Point(base.x, base.y + 5, base.z), bounds);
            }
            AlignmentGuide.Result alignment = AlignmentGuide.evaluate(alignmentMode, model, target.point, tracking);
            listener.onFrame(new State(model, tracking, target.point != null && stable, valid, screen, area, preview, status, hint, warning, aimX, aimY, bounds,
                target.point == null ? null : project(target.point, bounds), heightGuide, alignmentMode, alignment,
                alignment == null ? null : project(alignment.guidePoint, bounds)));
        } catch (CameraNotAvailableException e) { fail("Camera unavailable. Close other camera apps and reopen Plot Measure.", e); }
        catch (RuntimeException e) { fail("The AR session stopped. Reopen Plot Measure to start a new measurement.", e); }
    }
    private String placementHint() {
        if (model.mode() == Measurement.Mode.HEIGHT) return model.size() == 0
            ? "Mark the floor directly beneath the top of the head." : "Aim at the top of the head, above the floor mark. Tap +.";
        if (model.mode() == Measurement.Mode.DISTANCE) return model.size() == 0 ? "Aim at the start point and tap +." : "Aim at the end point and tap +.";
        return model.size() == 0 ? "Aim at the first ground corner and tap +." : "Walk to the next corner, aim, and tap +. Follow the boundary.";
    }
    private String trackingHint(Camera camera) {
        if (camera.getTrackingState() == TrackingState.TRACKING) return "A saved corner lost tracking. Return to it, or reset and start again.";
        switch (camera.getTrackingFailureReason()) {
            case INSUFFICIENT_LIGHT: return "More light is needed. Point toward a well-lit surface.";
            case EXCESSIVE_MOTION: return "Slow down and hold the phone steady.";
            case INSUFFICIENT_FEATURES: return "Aim at textured ground or nearby objects. Avoid blank surfaces.";
            case BAD_STATE: return "Tracking needs to recover. Look back toward the starting area.";
            case CAMERA_UNAVAILABLE: return "The camera is unavailable. Close other camera apps.";
            default: return "Move the phone slowly to scan your surroundings.";
        }
    }
    private boolean updatePoints(Camera camera) {
        if (camera.getTrackingState() != TrackingState.TRACKING) return false;
        List<Point> points = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.getTrackingState() != TrackingState.TRACKING) return false;
            points.add(point(anchor.getPose()));
        }
        if (model.mode() == Measurement.Mode.HEIGHT && model.size() == 2) {
            Point base = points.get(0); points.add(new Point(base.x, base.y + heightOffset, base.z));
        }
        model.updateTrackedPoints(points); return true;
    }
    private static final class Candidate {
        final HitResult hit; final Point point; final String reason;
        Candidate(HitResult hit, Point point, String reason) { this.hit = hit; this.point = point; this.reason = reason; }
    }
    private Candidate candidate(Frame frame, Camera camera, boolean tracking) {
        if (!tracking) return new Candidate(null, null, trackingHint(camera));
        if (model.closed()) return new Candidate(null, null, "Save a photo, undo, or reset to measure again.");
        if (model.size() >= Measurement.MAX_POINTS) return new Candidate(null, null, "32 corners marked. Finish the plot or undo a corner.");
        if (model.mode() == Measurement.Mode.HEIGHT && model.size() == 1) {
            float[] inverse = new float[16], world = new float[4];
            if (!Matrix.invertM(inverse, 0, combined, 0)) throw new IllegalStateException("Camera projection is not invertible");
            Matrix.multiplyMV(world, 0, inverse, 0, new float[]{2 * aimX / width - 1, 1 - 2 * aimY / height, 1, 1}, 0);
            Point origin = point(camera.getPose());
            Point direction = new Point(world[0] / world[3] - origin.x, world[1] / world[3] - origin.y, world[2] / world[3] - origin.z);
            try { return new Candidate(null, HeightMeasurement.top(model.points().get(0), origin, direction), ""); }
            catch (IllegalArgumentException e) { return new Candidate(null, null, e.getMessage()); }
        }
        String reason = model.mode() == Measurement.Mode.HEIGHT ? "Scan the floor, then aim directly beneath the subject's head."
            : model.mode() == Measurement.Mode.PLOT ? "Scan textured ground, then aim at a ground corner."
            : "No surface at the dot. Slowly scan textured ground or a wall.";
        for (HitResult hit : frame.hitTest(aimX, aimY)) {
            if (hit.getDistance() < 0.2f) { reason = "Move back — keep at least 20 cm from the point."; continue; }
            if (hit.getDistance() > 5f) { reason = "Move closer — aim within 5 metres."; continue; }
            Trackable trackable = hit.getTrackable();
            if (trackable.getTrackingState() != TrackingState.TRACKING) continue;
            if (trackable instanceof Plane) {
                Plane plane = (Plane) trackable;
                if (!plane.isPoseInPolygon(hit.getHitPose())) continue;
                if (model.mode() != Measurement.Mode.DISTANCE && plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    reason = "This mode starts on the ground. Aim at a flat floor or ground surface."; continue;
                }
                return new Candidate(hit, point(hit.getHitPose()), "");
            }
            if (model.mode() == Measurement.Mode.DISTANCE && trackable instanceof DepthPoint) return new Candidate(hit, point(hit.getHitPose()), "");
        }
        return new Candidate(null, null, reason);
    }
    private void changeMode(Measurement.Mode next) {
        if (model.mode() != next) { clearAnchors(); model.setMode(next); feedback = ""; }
    }
    private void clearAnchors() { for (Anchor anchor : anchors) anchor.detach(); anchors.clear(); heightOffset = 0; }
    private void feedback(String text) { feedback = text; feedbackUntil = SystemClock.uptimeMillis() + 5000; }
    private static Point point(Pose pose) { return new Point(pose.tx(), pose.ty(), pose.tz()); }
    private Marker project(Point point, Bounds bounds) {
        float[] world = {(float) point.x, (float) point.y, (float) point.z, 1}, clip = new float[4];
        Matrix.multiplyMV(clip, 0, combined, 0, world, 0);
        return ScreenProjection.project(clip, width, height, bounds);
    }
    private void fail(String message, Exception e) {
        if (failed) return;
        failed = true; Log.e("PlotMeasure", message, e); listener.onFailure(message);
    }
}

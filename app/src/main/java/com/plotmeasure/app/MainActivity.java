package com.plotmeasure.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.plotmeasure.measurement.AlignmentGuide;
import com.plotmeasure.measurement.Measurement;
import com.plotmeasure.measurement.ScreenProjection.Bounds;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements MeasureRenderer.Listener {
    private static final int CAMERA_REQUEST = 10, SAVE_REQUEST = 20;
    private static final int GOLD = MeasurementOverlay.GOLD;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean framePosted = new AtomicBoolean();
    private GLSurfaceView surface;
    private MeasurementOverlay overlay;
    private MeasureRenderer renderer;
    private Session session;
    private volatile MeasureRenderer.State latest;
    private TextView status, savedPoints, hint, result, detail, warning;
    private Button add, finishPlot, save, distanceMode, heightMode, plotMode, units, undo, reset, pause, guide;
    private boolean installRequested, cameraAsked, imperial;
    private final PendingPhoto pendingPhoto = new PendingPhoto();
    private final ArLifecycle lifecycle = new ArLifecycle(new ArLifecycle.Driver() {
        @Override public boolean start() { return startAr(); }
        @Override public void stop() { stopAr(); }
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        imperial = getPreferences(MODE_PRIVATE).getBoolean("imperial", false);
        if (state != null) installRequested = state.getBoolean("installRequested");
        createUi();
        if (state != null && state.getBoolean("userPaused")) {
            lifecycle.paused(true); pause.setText(R.string.resume_measurement); showStopped();
        }
        File legacyPhoto = new File(getCacheDir(), "measurement.png");
        if (legacyPhoto.exists() && !legacyPhoto.delete()) {
            Log.w("PlotMeasure", "Legacy temporary photo could not be deleted");
            toast("Clear this app's cache in Settings to remove an old temporary photo.");
        }
        if (state != null) toast("A new AR session has started. Mark your points again.");
    }
    private void createUi() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xFF101513);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        surface = new GLSurfaceView(this); surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        renderer = new MeasureRenderer(getWindowManager().getDefaultDisplay(), this);
        surface.setRenderer(renderer); surface.onPause();
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        overlay = new MeasurementOverlay(this); root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = column(); top.setPadding(dp(20), dp(12), dp(20), dp(20));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xEE101513, 0x00101513}));
        LinearLayout titleRow = row(); TextView title = text("Plot Measure", 24, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        units = button(imperial ? "ft" : "m", v -> {
            imperial = !imperial; getPreferences(MODE_PRIVATE).edit().putBoolean("imperial", imperial).apply();
            units.setText(imperial ? "ft" : "m"); if (latest != null) render(latest);
        });
        units.setContentDescription("Change measurement units"); titleRow.addView(units, new LinearLayout.LayoutParams(dp(52), dp(44)));
        Button help = button("?", v -> showHelp()); help.setContentDescription("Instructions and privacy information");
        titleRow.addView(help, new LinearLayout.LayoutParams(dp(44), dp(44))); top.addView(titleRow);
        TextView subtitle = text("A little perspective. A clearer estimate.", 13, 0xFFD0D9D3); top.addView(subtitle);
        status = text("STARTING CAMERA", 11, GOLD); status.setLetterSpacing(0.12f); status.setPadding(0, dp(16), 0, 0); top.addView(status);
        savedPoints = text("", 12, Color.WHITE); savedPoints.setPadding(0, dp(6), 0, 0);
        savedPoints.setVisibility(View.GONE); top.addView(savedPoints);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); root.addView(top, topParams);

        LinearLayout bottom = column(); bottom.setPadding(dp(16), dp(12), dp(16), dp(12));
        bottom.setBackground(rounded(0xE9101513, 22));
        LinearLayout modes = row();
        distanceMode = button("Distance", v -> selectMode(Measurement.Mode.DISTANCE));
        heightMode = button("Height", v -> selectMode(Measurement.Mode.HEIGHT));
        plotMode = button("Plot area", v -> selectMode(Measurement.Mode.PLOT));
        modes.addView(distanceMode, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(heightMode, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(plotMode, new LinearLayout.LayoutParams(0, dp(44), 1)); bottom.addView(modes);
        hint = text("Move the phone slowly to scan nearby surfaces.", 13, 0xFFE4E8E5);
        hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(10), 0, dp(4)); hint.setMinHeight(dp(42)); hint.setMaxLines(2); bottom.addView(hint);
        result = text("Ready to measure", 29, Color.WHITE); result.setGravity(Gravity.CENTER); bottom.addView(result);
        detail = text("Mark two points on a nearby object", 12, 0xFFAFBDB3); detail.setGravity(Gravity.CENTER); bottom.addView(detail);
        LinearLayout actions = row(); actions.setGravity(Gravity.CENTER); actions.setPadding(0, dp(8), 0, dp(4));
        undo = button("Undo", v -> renderer.action(MeasureRenderer.Action.UNDO));
        reset = button("Reset", v -> renderer.action(MeasureRenderer.Action.CLEAR));
        add = button("+", v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } else renderer.action(MeasureRenderer.Action.ADD);
        });
        add.setTextSize(32); add.setBackground(rounded(GOLD, 32)); add.setTextColor(0xFF182019); add.setContentDescription("Mark the point at the centre dot");
        save = button("Photo", v -> capture()); save.setContentDescription("Save a photo with the measurements");
        actions.addView(undo, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(add, new LinearLayout.LayoutParams(dp(64), dp(64)));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1.4f)); bottom.addView(actions);
        finishPlot = button("Finish plot", v -> renderer.action(MeasureRenderer.Action.FINISH));
        LinearLayout sessionActions = row();
        finishPlot.setVisibility(View.GONE); sessionActions.addView(finishPlot, new LinearLayout.LayoutParams(0, dp(44), 1));
        guide = button("Guide: Off", v -> renderer.action(MeasureRenderer.Action.GUIDE));
        guide.setContentDescription("Alignment guide off. Tap for horizontal alignment.");
        sessionActions.addView(guide, new LinearLayout.LayoutParams(0, dp(44), 1));
        pause = button("Pause", v -> {
            lifecycle.paused(!lifecycle.paused());
            pause.setText(lifecycle.paused() ? R.string.resume_measurement : R.string.pause_measurement);
            if (lifecycle.paused()) showStopped();
        });
        pause.setContentDescription("Pause or resume camera measurement");
        sessionActions.addView(pause, new LinearLayout.LayoutParams(0, dp(44), 1)); bottom.addView(sessionActions);
        warning = text("Approximate measurement · Check a known distance", 10, 0xFFAFBDB3);
        warning.setGravity(Gravity.CENTER); warning.setPadding(0, dp(5), 0, 0); bottom.addView(warning);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomParams.setMargins(dp(10), 0, dp(10), dp(8)); root.addView(bottom, bottomParams);
        View.OnLayoutChangeListener updateTarget = (v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            float aimY = (top.getBottom() + bottom.getTop()) / 2f - surface.getTop();
            renderer.setAim(surface.getWidth() / 2f, Math.max(dp(80), aimY));
            float visibleTop = top.getBottom() - surface.getTop() + dp(24);
            float visibleBottom = bottom.getTop() - surface.getTop() - dp(24);
            if (surface.getWidth() > dp(60) && visibleBottom > visibleTop) {
                renderer.setVisibleBounds(new Bounds(dp(30), visibleTop, surface.getWidth() - dp(30), visibleBottom));
            }
        };
        top.addOnLayoutChangeListener(updateTarget);
        bottom.addOnLayoutChangeListener(updateTarget);
        surface.addOnLayoutChangeListener(updateTarget);
        setContentView(root); setControlsEnabled(false);
    }
    private void selectMode(Measurement.Mode next) {
        if (latest != null && latest.mode == next) return;
        Runnable change = () -> renderer.action(next == Measurement.Mode.PLOT ? MeasureRenderer.Action.PLOT
            : next == Measurement.Mode.HEIGHT ? MeasureRenderer.Action.HEIGHT : MeasureRenderer.Action.DISTANCE);
        if (latest != null && latest.count > 0) {
            new AlertDialog.Builder(this).setTitle("Switch measurement mode?")
                .setMessage("The current points will be cleared.").setNegativeButton("Keep points", null)
                .setPositiveButton("Switch", (dialog, which) -> change.run()).show();
        } else change.run();
    }
    @Override protected void onResume() { super.onResume(); lifecycle.foreground(true); }
    private boolean startAr() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!cameraAsked) { cameraAsked = true; requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST); }
            else { status.setText(R.string.camera_access); hint.setText(R.string.permission_hint); add.setEnabled(true); }
            return false;
        }
        try {
            if (session == null) {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true; status.setText(R.string.installing_ar); return false;
                }
                session = new Session(this);
                Config config = new Config(session);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                config.setFocusMode(Config.FocusMode.AUTO);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.setDepthMode(Config.DepthMode.AUTOMATIC);
                session.configure(config);
            }
            session.resume(); renderer.setSession(session); latest = null; overlay.update(null, imperial); surface.onResume();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            overlay.setPaused(false);
            distanceMode.setEnabled(true); heightMode.setEnabled(true); plotMode.setEnabled(true);
            status.setText(R.string.scanning); hint.setText(R.string.resume_scanning);
            return true;
        } catch (UnavailableException e) {
            Log.w("PlotMeasure", "AR services unavailable", e);
            onFailure("This phone needs ARCore support and an up-to-date Google Play Services for AR installation.");
        } catch (CameraNotAvailableException e) {
            Log.w("PlotMeasure", "Camera resume failed", e);
            onFailure("Camera unavailable. Close other camera apps and reopen Plot Measure.");
        } catch (SecurityException e) {
            Log.w("PlotMeasure", "Camera access denied", e); onFailure("Camera permission is required to measure with AR.");
        }
        return false;
    }
    private void stopAr() {
        surface.onPause(); renderer.pausePlacement(); session.pause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showStopped();
    }
    private void showStopped() {
        setControlsEnabled(false); overlay.setPaused(true);
        status.setText(lifecycle.busy() ? "PREPARING PHOTO" : "PAUSED");
        hint.setText(lifecycle.busy() ? "Camera paused while saving your photo." : "Camera stopped. Tap Resume when ready.");
        if (latest != null && latest.count > 0) savedPoints.setText(getString(R.string.saved_points_manual_pause, latest.count));
    }
    @Override protected void onPause() {
        lifecycle.foreground(false);
        super.onPause();
    }
    @Override protected void onDestroy() {
        lifecycle.destroy(); pendingPhoto.discard();
        if (session != null) { Session closing = session; renderer.setSession(null); io.execute(closing::close); session = null; }
        io.shutdown(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putBoolean("installRequested", installRequested);
        state.putBoolean("userPaused", lifecycle.paused());
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != CAMERA_REQUEST) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) lifecycle.retry();
        else {
            status.setText(R.string.camera_access);
            new AlertDialog.Builder(this).setTitle("Allow camera access")
                .setMessage("Plot Measure uses the camera to track surfaces and measure distances. Enable Camera in the app’s settings.")
                .setNegativeButton("Close", (dialog, which) -> finish())
                .setPositiveButton("Open settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setCancelable(false).show();
        }
    }
    @Override public void onFrame(MeasureRenderer.State state) {
        latest = state;
        if (framePosted.compareAndSet(false, true)) main.post(() -> {
            framePosted.set(false); if (!isDestroyed() && lifecycle.foreground() && lifecycle.running()) render(latest);
        });
    }
    private void render(MeasureRenderer.State s) {
        if (s == null || !lifecycle.running()) return;
        overlay.update(s, imperial); status.setText(s.status); hint.setText(s.hint); warning.setText(s.warning);
        savedPoints.setVisibility(s.count > 0 ? View.VISIBLE : View.GONE);
        savedPoints.setText(s.tracking ? getString(R.string.saved_points, s.count, s.offScreenCount)
            : getString(R.string.saved_points_paused, s.count));
        distanceMode.setTextColor(s.mode == Measurement.Mode.DISTANCE ? GOLD : Color.WHITE);
        plotMode.setTextColor(s.mode == Measurement.Mode.PLOT ? GOLD : Color.WHITE);
        heightMode.setTextColor(s.mode == Measurement.Mode.HEIGHT ? GOLD : Color.WHITE);
        add.setEnabled(s.canAdd && !lifecycle.busy()); add.setAlpha(add.isEnabled() ? 1 : 0.35f);
        undo.setEnabled(s.count > 0 && !lifecycle.busy()); reset.setEnabled(s.count > 0 && !lifecycle.busy());
        save.setEnabled(s.canSave() && !lifecycle.busy());
        finishPlot.setVisibility(s.mode == Measurement.Mode.PLOT && !s.closed ? View.VISIBLE : View.GONE);
        finishPlot.setEnabled(s.tracking && s.count >= 3 && !lifecycle.busy());
        guide.setVisibility(s.mode == Measurement.Mode.DISTANCE ? View.VISIBLE : View.GONE);
        guide.setEnabled(!lifecycle.busy());
        guide.setText(s.alignmentMode == AlignmentGuide.Mode.HORIZONTAL ? "Guide: Horizontal"
            : s.alignmentMode == AlignmentGuide.Mode.VERTICAL ? "Guide: Vertical" : "Guide: Off");
        guide.setTextColor(s.alignmentMode == AlignmentGuide.Mode.OFF ? Color.WHITE : MeasurementOverlay.GUIDE);
        guide.setContentDescription(s.alignmentMode == AlignmentGuide.Mode.HORIZONTAL ? "Horizontal guide. Tap for vertical alignment."
            : s.alignmentMode == AlignmentGuide.Mode.VERTICAL ? "Vertical guide. Tap to turn guides off."
            : "Alignment guide off. Tap for horizontal alignment.");
        detail.setTextColor(0xFFAFBDB3);
        if (!s.tracking && s.count > 0) { result.setText(getString(R.string.points_retained, s.count)); detail.setText(R.string.tracking_resume_hint); }
        else if (!s.valid) { result.setText(R.string.recheck_corners); detail.setText(R.string.correct_boundary); }
        else if (s.mode == Measurement.Mode.PLOT && s.closed) {
            result.setText(Measurement.areaLabel(s.area, imperial));
            detail.setText(getString(R.string.perimeter_summary, s.count, Measurement.lengthLabel(s.total, imperial)));
        } else if (s.closed) { result.setText(Measurement.lengthLabel(s.total, imperial)); detail.setText(s.mode == Measurement.Mode.HEIGHT ? R.string.height_summary : R.string.distance_summary); }
        else if (s.count > 0) {
            result.setText(s.preview >= 0 ? Measurement.lengthLabel(s.preview, imperial) : getString(R.string.points_marked, s.count));
            detail.setText(s.mode == Measurement.Mode.PLOT ? getString(R.string.open_boundary, s.count, Measurement.lengthLabel(s.total, imperial)) : getString(s.mode == Measurement.Mode.HEIGHT ? R.string.aim_height : R.string.aim_endpoint));
        } else { result.setText(R.string.ready); detail.setText(s.mode == Measurement.Mode.PLOT ? R.string.mark_ground : s.mode == Measurement.Mode.HEIGHT ? R.string.mark_height_base : R.string.mark_object); }
        if (s.alignment != null) {
            detail.setText(s.alignment.label(imperial, s.closed));
            detail.setTextColor(s.alignment.aligned ? MeasurementOverlay.ALIGNED : MeasurementOverlay.GUIDE);
        } else if (s.tracking && s.mode == Measurement.Mode.DISTANCE && s.alignmentMode != AlignmentGuide.Mode.OFF && s.count == 0) {
            detail.setText(R.string.start_alignment);
        }
    }
    @Override public void onFailure(String message) {
        main.post(() -> {
            if (isDestroyed() || isFinishing() || lifecycle.failed()) return;
            lifecycle.fail(); pause.setEnabled(false); pendingPhoto.discard(); setControlsEnabled(false); status.setText(R.string.ar_unavailable);
            new AlertDialog.Builder(this).setTitle("AR measurement unavailable").setMessage(message)
                .setPositiveButton("Close", (dialog, which) -> finish()).setCancelable(false).show();
        });
    }
    private void setControlsEnabled(boolean enabled) {
        for (Button button : new Button[]{add, undo, reset, save, finishPlot, distanceMode, heightMode, plotMode, guide}) button.setEnabled(enabled);
    }
    private void capture() {
        if (lifecycle.busy() || !lifecycle.running() || latest == null || !latest.canSave()
                || surface.getWidth() == 0 || surface.getHeight() == 0) return;
        lifecycle.busy(true); pause.setEnabled(false);
        MeasureRenderer.State snapshot = latest;
        if (!snapshot.canSave()) { finishPhoto(); toast("Tracking changed. Resume and try again."); return; }
        boolean captureImperial = imperial;
        Bitmap bitmap = Bitmap.createBitmap(surface.getWidth(), surface.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(surface, bitmap, code -> {
            if (isDestroyed()) { bitmap.recycle(); return; }
            if (code != PixelCopy.SUCCESS || !lifecycle.foreground()) {
                bitmap.recycle(); finishPhoto(); toast("Photo cancelled. Return to the app and try again."); return;
            }
            Canvas canvas = new Canvas(bitmap);
            overlay.drawMeasurement(canvas, snapshot, captureImperial, false);
            overlay.drawPhotoSummary(canvas, snapshot, captureImperial);
            io.execute(() -> {
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG encoding failed");
                    byte[] bytes = output.toByteArray();
                    main.post(() -> {
                        if (isDestroyed()) { Arrays.fill(bytes, (byte) 0); return; }
                        if (!lifecycle.foreground() || lifecycle.failed()) {
                            Arrays.fill(bytes, (byte) 0); finishPhoto(); return;
                        }
                        pendingPhoto.prepare(bytes);
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png");
                        intent.putExtra(Intent.EXTRA_TITLE, "PlotMeasure-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png");
                        try { startActivityForResult(intent, SAVE_REQUEST); }
                        catch (ActivityNotFoundException e) { finishPhoto(); toast("No file-saving app is available on this device."); }
                    });
                } catch (IOException e) {
                    Log.w("PlotMeasure", "Photo encoding failed", e);
                    main.post(() -> { if (!isDestroyed()) { finishPhoto(); toast("Photo could not be prepared."); } });
                } finally { bitmap.recycle(); }
            });
        }, main);
    }
    @Override protected void onActivityResult(int request, int resultCode, Intent data) {
        super.onActivityResult(request, resultCode, data);
        if (request != SAVE_REQUEST) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { finishPhoto(); return; }
        byte[] bytes = pendingPhoto.take();
        if (bytes == null) { finishPhoto(); toast("Photo was cleared when the app restarted. Capture it again."); return; }
        Uri destination = data.getData();
        if (!"content".equals(destination.getScheme())) {
            Arrays.fill(bytes, (byte) 0); finishPhoto(); toast("Choose a location in the Android file picker."); return;
        }
        io.execute(() -> {
            try {
                try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("No destination stream");
                    PendingPhoto.writeAndErase(bytes, output);
                }
                main.post(() -> toast("Measurement photo saved."));
            } catch (IOException | SecurityException e) {
                Log.w("PlotMeasure", "Photo export failed", e); main.post(() -> toast("Could not save the photo. Please try again."));
            } finally {
                Arrays.fill(bytes, (byte) 0);
                main.post(() -> { if (!isDestroyed()) finishPhoto(); });
            }
        });
    }
    private void finishPhoto() {
        pendingPhoto.discard(); lifecycle.busy(false); pause.setEnabled(!lifecycle.failed());
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Measure with your camera")
            .setMessage("1. Move your phone slowly to scan textured surfaces.\n\n2. Aim the centre dot at a point. Hold it steady for half a second; when it turns gold, tap +. Stay within 5 m of each point.\n\n3. Distance: mark two endpoints. Tap Guide to cycle Off, Horizontal and Vertical. After the first point, aim toward the blue guide ring. The offset and tilt appear below the distance; green indicates alignment within 2 cm and 2 degrees. This is guidance, not an accuracy guarantee, and does not move your measured points. Height: mark the floor directly beneath the head, then aim at the top of the head along the vertical guide. Keep the person still. Plot area: mark ground corners in order, then tap Finish plot.\n\n4. Saved points stay anchored when you turn away. Numbered edge arrows point toward off-screen points; turn back to see their markers again. You can keep marking while tracking is active.\n\n5. Pause stops the camera and keeps your points in this session. Tap Resume to recover tracking. Photo saves the camera view with measurements; no temporary photo is written to app storage.\n\nPlot area is a horizontal projection. Large plots, grass, low light and uneven ground can reduce accuracy. Check a known distance first.\n\nMeasurements are kept for this AR session. A restarted app starts fresh.\n\n" + getString(R.string.ar_notice))
            .setPositiveButton("Got it", null)
            .setNeutralButton("Privacy policy", (dialog, which) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))); }
                catch (ActivityNotFoundException e) { toast("Read the policy at policies.google.com/privacy"); }
            }).show();
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String value, float size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private Button button(String value, View.OnClickListener click) {
        Button button = new Button(this); button.setText(value); button.setTextSize(13); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(6), 0, dp(6), 0);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{0xFF667269, Color.WHITE}));
        button.setBackground(rounded(0x552C3931, 12)); button.setOnClickListener(click); return button;
    }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}

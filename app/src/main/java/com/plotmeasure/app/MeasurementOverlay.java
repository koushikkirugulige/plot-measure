package com.plotmeasure.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import com.plotmeasure.measurement.Measurement;
import com.plotmeasure.measurement.ScreenProjection;
import com.plotmeasure.measurement.ScreenProjection.Bounds;
import com.plotmeasure.measurement.ScreenProjection.Marker;

/** Transparent, screen-space annotation layer above the camera surface. */
final class MeasurementOverlay extends View {
    static final int GOLD = Color.rgb(242, 181, 68);
    static final int ALIGNED = Color.rgb(112, 232, 164), GUIDE = Color.rgb(114, 213, 244);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private MeasureRenderer.State state;
    private boolean imperial;
    private boolean paused;

    void setPaused(boolean value) { paused = value; invalidate(); }

    MeasurementOverlay(Context context) {
        super(context); density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void update(MeasureRenderer.State next, boolean imperial) {
        state = next; this.imperial = imperial; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paused) {
            canvas.drawColor(0xE6101513);
            label(canvas, "Camera paused", getWidth() / 2f, getHeight() / 2f);
            return;
        }
        drawMeasurement(canvas, state, imperial, true);
    }
    void drawMeasurement(Canvas canvas, MeasureRenderer.State s, boolean imperial, boolean reticle) {
        if (s == null) return;
        paint.setPathEffect(null);
        Bounds bounds = reticle ? s.bounds : new Bounds(0, 0, canvas.getWidth(), canvas.getHeight());
        if (s.tracking) {
            if (reticle && s.heightGuide != null && s.count == 1 && s.screen[0] != null) {
                float[] guide = ScreenProjection.segment(s.screen[0], s.heightGuide, bounds);
                if (guide != null) line(canvas, guide[0], guide[1], guide[2], guide[3], true);
            }
            boolean allVisible = s.screen.length >= 3;
            for (Marker point : s.screen) if (point == null || !bounds.contains(point.imageX, point.imageY)) allVisible = false;
            if (s.closed && s.mode == Measurement.Mode.PLOT && s.valid && allVisible) {
                Path path = new Path(); path.moveTo(s.screen[0].imageX, s.screen[0].imageY);
                for (int i = 1; i < s.screen.length; i++) path.lineTo(s.screen[i].imageX, s.screen[i].imageY);
                path.close(); paint.setStyle(Paint.Style.FILL); paint.setColor(0x25F2B544); canvas.drawPath(path, paint);
            }
            for (int i = 0; i < s.sides.size(); i++) {
                Marker a = s.screen[i], b = s.screen[(i + 1) % s.count];
                if (a == null || b == null) continue;
                float[] segment = ScreenProjection.segment(a, b, bounds);
                if (segment == null) continue;
                line(canvas, segment[0], segment[1], segment[2], segment[3], false);
                label(canvas, Measurement.lengthLabel(s.sides.get(i), imperial), (segment[0] + segment[2]) / 2,
                    Math.max(bounds.top + dp(16), (segment[1] + segment[3]) / 2 - dp(14)));
            }
            for (int i = 0; i < s.screen.length; i++) {
                Marker p = s.screen[i]; if (p == null) continue;
                if (!bounds.contains(p.imageX, p.imageY)) {
                    if (reticle) edgeIndicator(canvas, p, i + 1);
                    continue;
                }
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); canvas.drawCircle(p.imageX, p.imageY, dp(7), paint);
                paint.setColor(GOLD); canvas.drawCircle(p.imageX, p.imageY, dp(4), paint);
                label(canvas, Integer.toString(i + 1), p.imageX, Math.min(bounds.bottom, p.imageY + dp(27)));
            }
            if (reticle && s.preview >= 0 && s.count > 0 && s.screen[s.count - 1] != null) {
                Marker last = s.screen[s.count - 1];
                float[] segment = s.previewScreen == null ? null : ScreenProjection.segment(last, s.previewScreen, bounds);
                if (segment != null) line(canvas, segment[0], segment[1], segment[2], segment[3], true);
                label(canvas, Measurement.lengthLabel(s.preview, imperial), s.aimX, s.aimY - dp(46));
            }
            if (reticle && !s.closed && s.alignment != null && s.alignmentScreen != null && s.screen[0] != null) {
                int color = s.alignment.aligned ? ALIGNED : GUIDE;
                float[] guide = ScreenProjection.segment(s.screen[0], s.alignmentScreen, bounds);
                if (guide != null) line(canvas, guide[0], guide[1], guide[2], guide[3], true, color);
                if (s.previewScreen != null) {
                    float[] correction = ScreenProjection.segment(s.previewScreen, s.alignmentScreen, bounds);
                    if (correction != null) line(canvas, correction[0], correction[1], correction[2], correction[3], true, color);
                }
                if (bounds.contains(s.alignmentScreen.imageX, s.alignmentScreen.imageY)) {
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(dp(2));
                    canvas.drawCircle(s.alignmentScreen.imageX, s.alignmentScreen.imageY, dp(9), paint);
                }
            }
        }
        if (reticle && !s.closed) {
            paint.setPathEffect(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
            paint.setColor(s.canAdd ? s.alignment != null && s.alignment.aligned ? ALIGNED : GOLD : 0xAAFFFFFF);
            canvas.drawCircle(s.aimX, s.aimY, dp(20), paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(s.aimX, s.aimY, dp(3), paint);
        }
    }
    private void edgeIndicator(Canvas canvas, Marker marker, int number) {
        float dx = marker.directionX, dy = marker.directionY;
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xE617231D);
        canvas.drawCircle(marker.x, marker.y, dp(14), paint);
        Path arrow = new Path();
        arrow.moveTo(marker.x + dx * dp(8), marker.y + dy * dp(8));
        arrow.lineTo(marker.x - dx * dp(5) - dy * dp(6), marker.y - dy * dp(5) + dx * dp(6));
        arrow.lineTo(marker.x - dx * dp(5) + dy * dp(6), marker.y - dy * dp(5) - dx * dp(6));
        arrow.close(); paint.setColor(GOLD); canvas.drawPath(arrow, paint);
        label(canvas, Integer.toString(number) + (marker.behind ? " · behind" : ""),
            marker.x - dx * dp(28), marker.y - dy * dp(28));
    }
    void drawPhotoSummary(Canvas canvas, MeasureRenderer.State s, boolean imperial) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xD9101513);
        canvas.drawRect(0, 0, canvas.getWidth(), dp(94), paint);
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(dp(19));
        canvas.drawText("Plot Measure · Approximate", dp(20), dp(30), paint);
        paint.setTextSize(dp(16));
        String result = s.mode == Measurement.Mode.PLOT
            ? "Area " + Measurement.areaLabel(s.area, imperial) + "   Perimeter " + Measurement.lengthLabel(s.total, imperial)
            : (s.mode == Measurement.Mode.HEIGHT ? "Height " : "Distance ") + Measurement.lengthLabel(s.total, imperial);
        canvas.drawText(result, dp(20), dp(57), paint);
        paint.setTextSize(dp(11)); paint.setColor(0xFFDDDDDD);
        canvas.drawText(s.mode == Measurement.Mode.PLOT ? "Horizontal projection · Check against known distances" : "AR estimate · Check against a known distance", dp(20), dp(79), paint);
    }
    private void line(Canvas canvas, float x1, float y1, float x2, float y2, boolean dashed) {
        line(canvas, x1, y1, x2, y2, dashed, GOLD);
    }
    private void line(Canvas canvas, float x1, float y1, float x2, float y2, boolean dashed, int color) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(dp(2.5f));
        paint.setPathEffect(dashed ? new DashPathEffect(new float[]{dp(6), dp(5)}, 0) : null);
        canvas.drawLine(x1, y1, x2, y2, paint); paint.setPathEffect(null);
    }
    private void label(Canvas canvas, String text, float x, float y) {
        paint.setTextSize(dp(13)); paint.setTextAlign(Paint.Align.CENTER);
        float half = paint.measureText(text) / 2 + dp(9);
        x = Math.max(half, Math.min(getWidth() - half, x));
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xDF17231D);
        canvas.drawRoundRect(new RectF(x - half, y - dp(16), x + half, y + dp(8)), dp(7), dp(7), paint);
        paint.setColor(Color.WHITE); canvas.drawText(text, x, y, paint);
    }
    private float dp(float value) { return density * value; }
}

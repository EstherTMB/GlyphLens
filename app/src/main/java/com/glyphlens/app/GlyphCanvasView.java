package com.glyphlens.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public class GlyphCanvasView extends View {

    public interface OnStrokeRecordedListener {
        void onStrokeRecorded(GlyphStroke stroke);
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private boolean isRecordMode = true;
    private boolean isCalibrationPreview = false;
    private boolean showGuide = true;
    private float gridScale = 1.0f;
    private float gridSquash = 1.0f; // Коэффициент сплющивания по горизонтали
    private GlyphStroke currentDrawingStroke = null;
    private GlyphStroke displayedStroke = null;
    private OnStrokeRecordedListener listener;

    public GlyphCanvasView(Context context) {
        super(context);
        bgPaint.setStyle(Paint.Style.FILL);
        setBgAlphaPercent(55);

        nodePaint.setStyle(Paint.Style.STROKE);
        nodePaint.setStrokeWidth(dpToPx(2.5f));
        nodePaint.setColor(Color.parseColor("#7799A8B8"));

        nodeHighlightPaint.setStyle(Paint.Style.STROKE);
        nodeHighlightPaint.setStrokeWidth(dpToPx(2.8f));
        nodeHighlightPaint.setColor(Color.parseColor("#00E676"));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeWidth(dpToPx(9f));
        strokePaint.setColor(Color.parseColor("#00E676"));

        statusTextPaint.setColor(Color.parseColor("#88B0C4DE"));
        statusTextPaint.setTextSize(dpToPx(13f));
    }

    public void setOnStrokeRecordedListener(OnStrokeRecordedListener listener) { this.listener = listener; }

    public void setRecordMode(boolean recordMode) {
        this.isRecordMode = recordMode;
        if (recordMode) displayedStroke = null;
        invalidate();
    }

    public void setCalibrationPreview(boolean calibrationPreview) { this.isCalibrationPreview = calibrationPreview; invalidate(); }
    public void setDisplayedStroke(GlyphStroke stroke) { this.displayedStroke = stroke; invalidate(); }
    public void setShowGuide(boolean show) { this.showGuide = show; invalidate(); }
    public void setLineColor(int color) { strokePaint.setColor(color); invalidate(); }
    
    public void setGridGeometry(float scale, float squash) { 
        this.gridScale = scale; 
        this.gridSquash = squash;
        invalidate(); 
    }

    public void setBgAlphaPercent(int percent) {
        int alpha = (int) (255 * (percent / 100f));
        bgPaint.setColor(Color.argb(alpha, 24, 42, 68));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        bgRect.set(0, 0, w, h);
        float cornerRadius = dpToPx(10f);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        if (isCalibrationPreview) {
            canvas.drawText("CALIBRATION PREVIEW", dpToPx(10f), dpToPx(20f), statusTextPaint);
        } else if (isRecordMode) {
            canvas.drawText("TRACE THE GLYPH...", dpToPx(10f), dpToPx(20f), statusTextPaint);
        }

        if (showGuide || isCalibrationPreview) {
            drawElevenNodes(canvas, w, h, isCalibrationPreview ? nodeHighlightPaint : nodePaint);
        }

        GlyphStroke active = isRecordMode ? currentDrawingStroke : displayedStroke;
        if (active != null && !active.isEmpty()) {
            Path path = active.toPath(w, h, 0f);
            canvas.drawPath(path, strokePaint);
        }
    }

    private void drawElevenNodes(Canvas canvas, float w, float h, Paint paintToUse) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        // Базовый радиус зависит от масштаба (gridScale)
        float baseRadius = Math.min(w, h) * 0.46f * gridScale;
        
        float nodeCircleRadius = dpToPx(7.5f);

        canvas.drawCircle(cx, cy, nodeCircleRadius, paintToUse);

        int[] outerAnglesDeg = {-90, -30, 30, 90, 150, -150};
        for (int angleDeg : outerAnglesDeg) {
            double rad = Math.toRadians(angleDeg);
            float nx = cx + (float) (baseRadius * Math.cos(rad) * gridSquash);
            float ny = cy + (float) (baseRadius * Math.sin(rad));
            canvas.drawCircle(nx, ny, nodeCircleRadius, paintToUse);
        }

        int[] innerAnglesDeg = {-30, 30, 150, -150};
        for (int angleDeg : innerAnglesDeg) {
            double rad = Math.toRadians(angleDeg);
            float nx = cx + (float) (baseRadius * 0.5f * Math.cos(rad) * gridSquash);
            float ny = cy + (float) (baseRadius * 0.5f * Math.sin(rad));
            canvas.drawCircle(nx, ny, nodeCircleRadius, paintToUse);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isRecordMode || isCalibrationPreview) return false;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return false;

        float normX = Math.max(0f, Math.min(1f, event.getX() / w));
        float normY = Math.max(0f, Math.min(1f, event.getY() / h));

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                currentDrawingStroke = new GlyphStroke();
                currentDrawingStroke.addPoint(normX, normY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentDrawingStroke != null) {
                    currentDrawingStroke.addPoint(normX, normY);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentDrawingStroke != null) {
                    float minStrokeLengthPx = dpToPx(14f);
                    if (currentDrawingStroke.getTotalLengthPx(w, h) >= minStrokeLengthPx) {
                        GlyphStroke finished = currentDrawingStroke;
                        currentDrawingStroke = null;
                        invalidate();
                        if (listener != null) listener.onStrokeRecorded(finished);
                    } else {
                        currentDrawingStroke = null;
                        invalidate();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }
}

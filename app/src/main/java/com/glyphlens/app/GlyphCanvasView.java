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

/**
 * Основное квадратное поле-линза с разметкой из 11 точек гексагона.
 * Включает защиту от случайных коротких тычков пальцем и увеличенный радиус гексагона (0.455f).
 */
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

    public void setOnStrokeRecordedListener(OnStrokeRecordedListener listener) {
        this.listener = listener;
    }

    public void setRecordMode(boolean recordMode) {
        this.isRecordMode = recordMode;
        if (recordMode) {
            displayedStroke = null;
        }
        invalidate();
    }

    public void setCalibrationPreview(boolean calibrationPreview) {
        this.isCalibrationPreview = calibrationPreview;
        invalidate();
    }

    public void setDisplayedStroke(GlyphStroke stroke) {
        this.displayedStroke = stroke;
        invalidate();
    }

    public void setShowGuide(boolean show) {
        this.showGuide = show;
        invalidate();
    }

    public void setLineColor(int color) {
        strokePaint.setColor(color);
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

        // 1. Полупрозрачная скруглённая подложка
        bgRect.set(0, 0, w, h);
        float cornerRadius = dpToPx(10f);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        // 2. Надпись статуса сверху слева
        if (isCalibrationPreview) {
            canvas.drawText("CALIBRATION PREVIEW", dpToPx(10f), dpToPx(20f), statusTextPaint);
        } else if (isRecordMode) {
            canvas.drawText("TRACE THE GLYPH...", dpToPx(10f), dpToPx(20f), statusTextPaint);
        }

        // 3. Отрисовка 11 точек гексагона (в режиме калибровки точки подсвечиваются ярче)
        if (showGuide || isCalibrationPreview) {
            drawElevenNodes(canvas, w, h, isCalibrationPreview ? nodeHighlightPaint : nodePaint);
        }

        // 4. Отрисовка линии глифа
        GlyphStroke active = isRecordMode ? currentDrawingStroke : displayedStroke;
        if (active != null && !active.isEmpty()) {
            Path path = active.toPath(w, h, 0f);
            canvas.drawPath(path, strokePaint);
        }
    }

    /**
     * Геометрия 11 точек гексагона Ingress с увеличенным базовым радиусом (0.455f):
     * - 1 центральная точка
     * - 6 внешних вершин правильного шестиугольника (-90, -30, +30, +90, +150, -150 градусов)
     * - 4 внутренние точки ровно посередине диагоналей (-30, +30, +150, -150 градусов)
     */
    private void drawElevenNodes(Canvas canvas, float w, float h, Paint paintToUse) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float outerRadius = Math.min(w, h) * 0.455f;
        float innerRadius = outerRadius * 0.5f;
        float nodeCircleRadius = dpToPx(7.5f);

        // Центральная точка (1)
        canvas.drawCircle(cx, cy, nodeCircleRadius, paintToUse);

        // 6 внешних точек шестиугольника (2..7)
        int[] outerAnglesDeg = {-90, -30, 30, 90, 150, -150};
        for (int angleDeg : outerAnglesDeg) {
            double rad = Math.toRadians(angleDeg);
            float nx = cx + (float) (outerRadius * Math.cos(rad));
            float ny = cy + (float) (outerRadius * Math.sin(rad));
            canvas.drawCircle(nx, ny, nodeCircleRadius, paintToUse);
        }

        // 4 внутренние точки (8..11)
        int[] innerAnglesDeg = {-30, 30, 150, -150};
        for (int angleDeg : innerAnglesDeg) {
            double rad = Math.toRadians(angleDeg);
            float nx = cx + (float) (innerRadius * Math.cos(rad));
            float ny = cy + (float) (innerRadius * Math.sin(rad));
            canvas.drawCircle(nx, ny, nodeCircleRadius, paintToUse);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isRecordMode || isCalibrationPreview) {
            return false;
        }
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
                    // Фильтр случайных тычков: засчитываем росчерк только если его длина >= 14 dp
                    float minStrokeLengthPx = dpToPx(14f);
                    if (currentDrawingStroke.getTotalLengthPx(w, h) >= minStrokeLengthPx) {
                        GlyphStroke finished = currentDrawingStroke;
                        currentDrawingStroke = null;
                        invalidate();
                        if (listener != null) {
                            listener.onStrokeRecorded(finished);
                        }
                    } else {
                        // Слишком короткое касание (случайная точка) — просто игнорируем!
                        currentDrawingStroke = null;
                        invalidate();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}

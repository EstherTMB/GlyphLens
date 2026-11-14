package com.glyphlens.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

public class SlotButtonView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String labelText = null;
    private GlyphStroke stroke = null;
    private boolean isSelected = false;
    private boolean isCloseStyle = false;
    private int lineColor = Color.parseColor("#00E676");

    public SlotButtonView(Context context) {
        super(context);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2.5f));
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeJoin(Paint.Join.ROUND);
        glyphPaint.setStrokeCap(Paint.Cap.ROUND);
        glyphPaint.setStrokeWidth(dpToPx(2.5f));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(dpToPx(16f));
    }

    public void setLabelText(String text) { this.labelText = text; invalidate(); }
    public String getLabelText() { return labelText; }
    public void setStroke(GlyphStroke stroke) { this.stroke = stroke; invalidate(); }
    public void setSlotSelected(boolean selected) { this.isSelected = selected; invalidate(); }
    public void setCloseButtonStyle(boolean closeStyle) { this.isCloseStyle = closeStyle; updateShader(getHeight()); invalidate(); }
    public void setLineColor(int color) { this.lineColor = color; glyphPaint.setColor(color); invalidate(); }

    private void updateShader(int h) {
        if (h <= 0) return;
        if (isCloseStyle) {
            bgPaint.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#6D3242"), Color.parseColor("#A84E64"), Shader.TileMode.CLAMP));
        } else {
            bgPaint.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#4F74A8"), Color.parseColor("#8FB4E8"), Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader(h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = Math.min(w, h) * 0.5f - dpToPx(2f);

        canvas.drawCircle(cx, cy, radius, bgPaint);

        if (stroke != null && !stroke.isEmpty()) {
            glyphPaint.setColor(lineColor);
            Path miniPath = stroke.toPath(w, h, dpToPx(6f));
            canvas.drawPath(miniPath, glyphPaint);
        }
        if (labelText != null && !labelText.isEmpty()) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(labelText, cx, textY, textPaint);
        }
        if (isSelected) {
            borderPaint.setColor(Color.parseColor("#FF7043"));
            borderPaint.setStrokeWidth(dpToPx(2.8f));
        } else {
            borderPaint.setColor(Color.parseColor("#66000000"));
            borderPaint.setStrokeWidth(dpToPx(1.5f));
        }
        canvas.drawCircle(cx, cy, radius, borderPaint);
    }

    private float dpToPx(float dp) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }
}

package com.glyphlens.app;

import android.graphics.Path;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

public class GlyphStroke {
    private final List<PointF> points = new ArrayList<>();

    public void addPoint(float normX, float normY) { points.add(new PointF(normX, normY)); }
    public boolean isEmpty() { return points.size() < 2; }
    public void clear() { points.clear(); }

    public float getTotalLengthPx(float width, float height) {
        if (points.size() < 2) return 0f;
        float total = 0f;
        PointF prev = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            PointF curr = points.get(i);
            float dx = (curr.x - prev.x) * width;
            float dy = (curr.y - prev.y) * height;
            total += (float) Math.hypot(dx, dy);
            prev = curr;
        }
        return total;
    }

    public Path toPath(float width, float height, float paddingPx) {
        Path path = new Path();
        if (points.isEmpty()) return path;
        float drawW = width - paddingPx * 2f;
        float drawH = height - paddingPx * 2f;
        PointF first = points.get(0);
        float prevX = paddingPx + first.x * drawW;
        float prevY = paddingPx + first.y * drawH;
        path.moveTo(prevX, prevY);
        for (int i = 1; i < points.size(); i++) {
            PointF pt = points.get(i);
            float currX = paddingPx + pt.x * drawW;
            float currY = paddingPx + pt.y * drawH;
            path.quadTo(prevX, prevY, (prevX + currX) * 0.5f, (prevY + currY) * 0.5f);
            prevX = currX;
            prevY = currY;
        }
        path.lineTo(prevX, prevY);
        return path;
    }
}

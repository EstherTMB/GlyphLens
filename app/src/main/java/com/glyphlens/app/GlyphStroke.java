package com.glyphlens.app;

import android.graphics.Path;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит один нарисованный глиф в виде нормализованных координат (от 0.0 до 1.0).
 * Благодаря этому один и тот же глиф можно идеально отрисовать и на большом холсте,
 * и внутри маленького кружка-превью на верхней панели!
 */
public class GlyphStroke {
    private final List<PointF> points = new ArrayList<>();

    public void addPoint(float normX, float normY) {
        points.add(new PointF(normX, normY));
    }

    public boolean isEmpty() {
        return points.size() < 2;
    }

    public void clear() {
        points.clear();
    }

    /**
     * Генерирует сглаженный векторный Path под любой размер окна (width x height)
     * с учётом внутреннего отступа paddingPx.
     */
    public Path toPath(float width, float height, float paddingPx) {
        Path path = new Path();
        if (points.isEmpty()) {
            return path;
        }
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

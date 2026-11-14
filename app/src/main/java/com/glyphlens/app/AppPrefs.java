package com.glyphlens.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AppPrefs {
    private static final String PREFS_NAME = "glyphlens_prefs";

    private static final String KEY_ASK_BEFORE_HACK = "ask_before_hack";
    private static final String KEY_FIXED_MAX_GLYPHS = "fixed_max_glyphs";
    private static final String KEY_SHOW_GUIDE = "show_guide";
    private static final String KEY_LINE_COLOR = "line_color";
    private static final String KEY_BG_ALPHA = "bg_alpha";
    private static final String KEY_GRID_SCALE = "grid_scale"; // 50..120 (%)
    private static final String KEY_CANVAS_OFFSET_Y = "canvas_offset_y";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAskBeforeHack(Context context) { return prefs(context).getBoolean(KEY_ASK_BEFORE_HACK, false); }
    public static void setAskBeforeHack(Context context, boolean ask) { prefs(context).edit().putBoolean(KEY_ASK_BEFORE_HACK, ask).apply(); }

    public static int getFixedMaxGlyphs(Context context) { return prefs(context).getInt(KEY_FIXED_MAX_GLYPHS, 5); }
    public static void setFixedMaxGlyphs(Context context, int count) { prefs(context).edit().putInt(KEY_FIXED_MAX_GLYPHS, Math.max(1, Math.min(5, count))).apply(); }

    public static boolean isShowGuide(Context context) { return prefs(context).getBoolean(KEY_SHOW_GUIDE, true); }
    public static void setShowGuide(Context context, boolean show) { prefs(context).edit().putBoolean(KEY_SHOW_GUIDE, show).apply(); }

    public static int getLineColor(Context context) { return prefs(context).getInt(KEY_LINE_COLOR, Color.parseColor("#00E676")); }
    public static void setLineColor(Context context, int color) { prefs(context).edit().putInt(KEY_LINE_COLOR, color).apply(); }

    public static int getBgAlphaPercent(Context context) { return prefs(context).getInt(KEY_BG_ALPHA, 55); }
    public static void setBgAlphaPercent(Context context, int percent) { prefs(context).edit().putInt(KEY_BG_ALPHA, Math.max(15, Math.min(75, percent))).apply(); }

    // Теперь масштаб меняет только размер точек, а не окна
    public static int getGridScalePercent(Context context) { return prefs(context).getInt(KEY_GRID_SCALE, 94); }
    public static void setGridScalePercent(Context context, int percent) { prefs(context).edit().putInt(KEY_GRID_SCALE, Math.max(50, Math.min(120, percent))).apply(); }

    public static int getCanvasOffsetYDp(Context context) { return prefs(context).getInt(KEY_CANVAS_OFFSET_Y, 20); }
    public static void setCanvasOffsetYDp(Context context, int offsetDp) { prefs(context).edit().putInt(KEY_CANVAS_OFFSET_Y, Math.max(-150, Math.min(150, offsetDp))).apply(); }

    public static int getBubbleX(Context context, int defaultVal) { return prefs(context).getInt(KEY_BUBBLE_X, defaultVal); }
    public static int getBubbleY(Context context, int defaultVal) { return prefs(context).getInt(KEY_BUBBLE_Y, defaultVal); }
    public static void saveBubblePos(Context context, int x, int y) { prefs(context).edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply(); }
}

package com.glyphlens.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Хранилище всех настроек приложения GlyphLens (SharedPreferences).
 */
public class AppPrefs {
    private static final String PREFS_NAME = "glyphlens_prefs";

    // Режим выбора количества глифов:
    // false = Жёстко заданное количество (1..5), на кружке пишется цифра
    // true  = Спрашивать при нажатии на кружок, на кружке пишется "?"
    private static final String KEY_ASK_BEFORE_HACK = "ask_before_hack";
    private static final String KEY_FIXED_MAX_GLYPHS = "fixed_max_glyphs";
    private static final String KEY_SHOW_GUIDE = "show_guide";
    private static final String KEY_LINE_COLOR = "line_color";
    private static final String KEY_BG_ALPHA = "bg_alpha"; // 15..75 (%)
    private static final String KEY_CANVAS_SCALE = "canvas_scale"; // 65..100 (%)
    private static final String KEY_CANVAS_OFFSET_Y = "canvas_offset_y"; // -150..+150 (dp)
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAskBeforeHack(Context context) {
        return prefs(context).getBoolean(KEY_ASK_BEFORE_HACK, false);
    }

    public static void setAskBeforeHack(Context context, boolean ask) {
        prefs(context).edit().putBoolean(KEY_ASK_BEFORE_HACK, ask).apply();
    }

    public static int getFixedMaxGlyphs(Context context) {
        return prefs(context).getInt(KEY_FIXED_MAX_GLYPHS, 5);
    }

    public static void setFixedMaxGlyphs(Context context, int count) {
        int clamped = Math.max(1, Math.min(5, count));
        prefs(context).edit().putInt(KEY_FIXED_MAX_GLYPHS, clamped).apply();
    }

    public static boolean isShowGuide(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_GUIDE, true);
    }

    public static void setShowGuide(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_GUIDE, show).apply();
    }

    public static int getLineColor(Context context) {
        // По умолчанию — классический зелёный цвет глифа (#00E676)
        return prefs(context).getInt(KEY_LINE_COLOR, Color.parseColor("#00E676"));
    }

    public static void setLineColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_LINE_COLOR, color).apply();
    }

    /**
     * Непрозрачность фона в процентах (от 15% до 75%).
     * Важно: потолок в 75% гарантирует, что на Android 12+ защита от Tapjacking
     * никогда не заблокирует сквозные нажатия в игру!
     */
    public static int getBgAlphaPercent(Context context) {
        return prefs(context).getInt(KEY_BG_ALPHA, 55);
    }

    public static void setBgAlphaPercent(Context context, int percent) {
        int clamped = Math.max(15, Math.min(75, percent));
        prefs(context).edit().putInt(KEY_BG_ALPHA, clamped).apply();
    }

    public static int getCanvasScalePercent(Context context) {
        return prefs(context).getInt(KEY_CANVAS_SCALE, 94);
    }

    public static void setCanvasScalePercent(Context context, int percent) {
        int clamped = Math.max(65, Math.min(100, percent));
        prefs(context).edit().putInt(KEY_CANVAS_SCALE, clamped).apply();
    }

    public static int getCanvasOffsetYDp(Context context) {
        return prefs(context).getInt(KEY_CANVAS_OFFSET_Y, 20);
    }

    public static void setCanvasOffsetYDp(Context context, int offsetDp) {
        int clamped = Math.max(-150, Math.min(150, offsetDp));
        prefs(context).edit().putInt(KEY_CANVAS_OFFSET_Y, clamped).apply();
    }

    public static int getBubbleX(Context context, int defaultVal) {
        return prefs(context).getInt(KEY_BUBBLE_X, defaultVal);
    }

    public static int getBubbleY(Context context, int defaultVal) {
        return prefs(context).getInt(KEY_BUBBLE_Y, defaultVal);
    }

    public static void saveBubblePos(Context context, int x, int y) {
        prefs(context).edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply();
    }
}

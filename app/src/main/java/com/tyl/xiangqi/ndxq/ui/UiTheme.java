package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Color;

/** V19.6：全局底色/透明度、高亮色、首页按钮色与可读文字颜色统一入口。 */
public final class UiTheme {
    public static final String PREFS = "human_vs_engine_v1";
    public static final String KEY_BACKGROUND_COLOR = "global_background_color";
    public static final String KEY_BACKGROUND_ALPHA = "global_background_alpha";
    public static final String KEY_HIGHLIGHT_COLOR = "global_highlight_color";
    public static final String KEY_HIGHLIGHT_ALPHA = "global_highlight_alpha";
    public static final String KEY_HOME_BUTTON_COLOR = "home_button_color";
    public static final String KEY_HOME_BUTTON_ALPHA = "home_button_alpha";

    /** 保留 V19.5 已有视觉值；新增透明度默认均为 100%。 */
    public static final int DEFAULT_BACKGROUND_COLOR = Color.rgb(246, 244, 238);
    public static final int DEFAULT_BACKGROUND_ALPHA = 100;
    public static final int DEFAULT_HIGHLIGHT_COLOR = Color.rgb(42, 92, 70);
    public static final int DEFAULT_HIGHLIGHT_ALPHA = 100;
    public static final int DEFAULT_HOME_BUTTON_COLOR = Color.rgb(239, 239, 233);
    public static final int DEFAULT_HOME_BUTTON_ALPHA = 100;

    private UiTheme() {}

    /** 不带透明度的全局底色，供色盘、hex 显示和无 back 时的根背景兜底使用。 */
    public static int backgroundRgb(Context context) {
        if (context == null) return DEFAULT_BACKGROUND_COLOR;
        return opaque(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR));
    }

    public static int backgroundAlphaPercent(Context context) {
        if (context == null) return DEFAULT_BACKGROUND_ALPHA;
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BACKGROUND_ALPHA, DEFAULT_BACKGROUND_ALPHA);
        return Math.max(0, Math.min(100, value));
    }

    /** 实际面板/按钮绘制用底色；根页面无 back 时仍由 backgroundRgb() 提供不透明兜底。 */
    public static int background(Context context) {
        return withAlpha(backgroundRgb(context), backgroundAlphaPercent(context));
    }

    /** 返回不带透明度的高亮色，供色盘编辑与 hex 显示使用。 */
    public static int highlightRgb(Context context) {
        if (context == null) return DEFAULT_HIGHLIGHT_COLOR;
        return opaque(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR));
    }

    public static int highlightAlphaPercent(Context context) {
        if (context == null) return DEFAULT_HIGHLIGHT_ALPHA;
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HIGHLIGHT_ALPHA, DEFAULT_HIGHLIGHT_ALPHA);
        return Math.max(0, Math.min(100, value));
    }

    /** 实际绘制用高亮色，0% 时 alpha=0，100% 时完全不透明。 */
    public static int highlight(Context context) {
        return withAlpha(highlightRgb(context), highlightAlphaPercent(context));
    }

    public static int homeButtonRgb(Context context) {
        if (context == null) return DEFAULT_HOME_BUTTON_COLOR;
        return opaque(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HOME_BUTTON_COLOR, DEFAULT_HOME_BUTTON_COLOR));
    }

    public static int homeButtonAlphaPercent(Context context) {
        if (context == null) return DEFAULT_HOME_BUTTON_ALPHA;
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HOME_BUTTON_ALPHA, DEFAULT_HOME_BUTTON_ALPHA);
        return Math.max(0, Math.min(100, value));
    }

    public static int homeButton(Context context) {
        return withAlpha(homeButtonRgb(context), homeButtonAlphaPercent(context));
    }

    public static int textOnBackground(Context context) {
        return textForOpaqueColor(backgroundRgb(context));
    }

    /** 次要说明文字也跟随底色明暗，但与底色略微混合，避免过分抢眼。 */
    public static int secondaryTextOnBackground(Context context) {
        int bg = backgroundRgb(context);
        int fg = textOnBackground(context);
        return Color.rgb(
                Math.round(Color.red(fg) * 0.68f + Color.red(bg) * 0.32f),
                Math.round(Color.green(fg) * 0.68f + Color.green(bg) * 0.32f),
                Math.round(Color.blue(fg) * 0.68f + Color.blue(bg) * 0.32f));
    }

    public static int textOnHighlight(Context context) {
        return textOnHighlight(context, highlight(context));
    }

    /** 根据高亮色叠加到底色后的实际亮度选黑/白字，透明高亮也不会造成白字“隐身”。 */
    public static int textOnHighlight(Context context, int color) {
        int base = backgroundRgb(context);
        return textForOpaqueColor(compositeOver(opaque(base), color));
    }

    /** 兼容旧调用；无 Context 时按默认底色估算。 */
    public static int textOnHighlight(int color) {
        return textForOpaqueColor(compositeOver(DEFAULT_BACKGROUND_COLOR, color));
    }

    /** V19.7：首页按实际取样背景，在深色字/白字中选择 WCAG 对比度更高的一侧。 */
    public static int textForColor(int color) {
        int background = opaque(color);
        int dark = Color.rgb(28, 31, 29);
        return contrastRatio(background, dark) >= contrastRatio(background, Color.WHITE)
                ? dark : Color.WHITE;
    }

    private static double contrastRatio(int a, int b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relativeLuminance(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static int compositeOver(int background, int foreground) {
        int a = Color.alpha(foreground);
        if (a <= 0) return opaque(background);
        if (a >= 255) return opaque(foreground);
        float f = a / 255f;
        int r = Math.round(Color.red(foreground) * f + Color.red(background) * (1f - f));
        int g = Math.round(Color.green(foreground) * f + Color.green(background) * (1f - f));
        int b = Math.round(Color.blue(foreground) * f + Color.blue(background) * (1f - f));
        return Color.rgb(r, g, b);
    }

    private static int withAlpha(int rgb, int percent) {
        int alpha = Math.round(Math.max(0, Math.min(100, percent)) * 255f / 100f);
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private static int textForOpaqueColor(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance > 0.48 ? Color.rgb(28, 31, 29) : Color.WHITE;
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}

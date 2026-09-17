package com.tyl.xiangqi.ndxq.ui;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * 棋子阴影统一绘制器。
 *
 * <p>阴影参数全部以棋子直径为基准，避免不同屏幕、棋盘缩放或导入皮肤下出现
 * 阴影大小和方向漂移。参考样图采用右下方柔和半透明投影：外层为宽而淡的环境阴影，
 * 内层为略靠下的接触阴影。阴影先离屏栅格化并缓存，同一棋盘尺寸下所有棋子复用，
 * 既保证 Android 6+ 的兼容性，也避免逐帧为每枚棋子执行模糊运算。</p>
 */
public final class PieceShadowRenderer {
    /** 阴影缓存相对棋子四周预留范围。 */
    public static final float PADDING_RATIO = 0.20f;

    /** 外层环境阴影：固定向右、向下偏移。 */
    public static final float AMBIENT_OFFSET_X_RATIO = 0.055f;
    public static final float AMBIENT_OFFSET_Y_RATIO = 0.085f;
    public static final float AMBIENT_WIDTH_RATIO = 0.98f;
    public static final float AMBIENT_HEIGHT_RATIO = 0.94f;
    public static final float AMBIENT_BLUR_RATIO = 0.075f;
    public static final int AMBIENT_ALPHA = 72;

    /** 内层接触阴影：范围更小、更靠近棋子右下缘。 */
    public static final float CONTACT_OFFSET_X_RATIO = 0.070f;
    public static final float CONTACT_OFFSET_Y_RATIO = 0.110f;
    public static final float CONTACT_WIDTH_RATIO = 0.86f;
    public static final float CONTACT_HEIGHT_RATIO = 0.82f;
    public static final float CONTACT_BLUR_RATIO = 0.040f;
    public static final int CONTACT_ALPHA = 48;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap cachedShadow;
    private int cachedPieceSizePx = -1;
    private int cachedPaddingPx = 0;

    /**
     * 在棋子位图之前绘制阴影。pieceRect 必须是棋子的最终显示矩形。
     */
    public void draw(Canvas canvas, RectF pieceRect) {
        if (canvas == null || pieceRect == null) return;
        float diameter = Math.min(pieceRect.width(), pieceRect.height());
        if (!(diameter > 0f) || Float.isNaN(diameter) || Float.isInfinite(diameter)) return;

        int pieceSizePx = Math.max(2, Math.round(diameter));
        ensureShadowBitmap(pieceSizePx);
        if (cachedShadow == null || cachedShadow.isRecycled()) return;

        float padding = diameter * PADDING_RATIO;
        RectF shadowDst = new RectF(
                pieceRect.centerX() - diameter / 2f - padding,
                pieceRect.centerY() - diameter / 2f - padding,
                pieceRect.centerX() + diameter / 2f + padding,
                pieceRect.centerY() + diameter / 2f + padding
        );
        canvas.drawBitmap(cachedShadow, null, shadowDst, bitmapPaint);
    }

    /** 清理缓存；View 销毁或极端低内存场景可调用。 */
    public void clear() {
        if (cachedShadow != null && !cachedShadow.isRecycled()) cachedShadow.recycle();
        cachedShadow = null;
        cachedPieceSizePx = -1;
        cachedPaddingPx = 0;
    }

    int getCachedPieceSizePxForTest() { return cachedPieceSizePx; }
    int getCachedPaddingPxForTest() { return cachedPaddingPx; }

    private void ensureShadowBitmap(int pieceSizePx) {
        if (cachedShadow != null && !cachedShadow.isRecycled() && cachedPieceSizePx == pieceSizePx) return;
        clear();

        cachedPieceSizePx = pieceSizePx;
        cachedPaddingPx = Math.max(2, (int) Math.ceil(pieceSizePx * PADDING_RATIO));
        int side = Math.max(4, pieceSizePx + cachedPaddingPx * 2);
        try {
            cachedShadow = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            cachedShadow.eraseColor(Color.TRANSPARENT);
            Canvas shadowCanvas = new Canvas(cachedShadow);
            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            shadowPaint.setStyle(Paint.Style.FILL);

            drawBlurredOval(shadowCanvas, shadowPaint, pieceSizePx, cachedPaddingPx,
                    AMBIENT_OFFSET_X_RATIO, AMBIENT_OFFSET_Y_RATIO,
                    AMBIENT_WIDTH_RATIO, AMBIENT_HEIGHT_RATIO,
                    AMBIENT_BLUR_RATIO, AMBIENT_ALPHA);
            drawBlurredOval(shadowCanvas, shadowPaint, pieceSizePx, cachedPaddingPx,
                    CONTACT_OFFSET_X_RATIO, CONTACT_OFFSET_Y_RATIO,
                    CONTACT_WIDTH_RATIO, CONTACT_HEIGHT_RATIO,
                    CONTACT_BLUR_RATIO, CONTACT_ALPHA);
            shadowPaint.setMaskFilter(null);
        } catch (OutOfMemoryError error) {
            clear();
        }
    }

    private static void drawBlurredOval(Canvas canvas,
                                        Paint paint,
                                        int size,
                                        int padding,
                                        float offsetXRatio,
                                        float offsetYRatio,
                                        float widthRatio,
                                        float heightRatio,
                                        float blurRatio,
                                        int alpha) {
        float width = size * widthRatio;
        float height = size * heightRatio;
        float cx = padding + size * (0.5f + offsetXRatio);
        float cy = padding + size * (0.5f + offsetYRatio);
        RectF oval = new RectF(cx - width / 2f, cy - height / 2f,
                cx + width / 2f, cy + height / 2f);
        paint.setColor(Color.argb(Math.max(0, Math.min(255, alpha)), 0, 0, 0));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(1f, size * blurRatio), BlurMaskFilter.Blur.NORMAL));
        canvas.drawOval(oval, paint);
    }
}

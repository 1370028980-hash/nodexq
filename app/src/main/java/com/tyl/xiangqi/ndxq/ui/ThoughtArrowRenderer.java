package com.tyl.xiangqi.ndxq.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * 思考箭头统一绘制器。
 *
 * 视觉约束：
 * 1. 箭杆使用圆头，避免从棋子中心伸出时出现生硬方角；
 * 2. 箭头为实心“导航图标”轮廓，而不是两条折线组成的空心三角；
 * 3. 箭头后缘使用向前收拢的圆弧过渡，形成参考图中的圆弧底；
 * 4. 箭头头部纵向长度严格取一个棋盘单位格的 0.382。
 */
public final class ThoughtArrowRenderer {
    /** 参考图要求：箭头头部长度占一个单位格长度的 0.382。 */
    public static final float HEAD_LENGTH_CELL_RATIO = 0.382f;
    /** 旧版 10.8 / 115 的基准箭杆比例，100% 粗细时保持原有视觉量级。 */
    public static final float BASE_STROKE_CELL_RATIO = 10.8f / 115f;

    private ThoughtArrowRenderer() {}

    public static int clampThicknessPercent(int percent) {
        return Math.max(50, Math.min(200, percent));
    }

    public static float strokeWidth(float cellSize, int thicknessPercent, float minPx) {
        float base = Math.max(minPx, Math.max(1f, cellSize) * BASE_STROKE_CELL_RATIO);
        return base * clampThicknessPercent(thicknessPercent) / 100f;
    }

    public static void draw(Canvas canvas, Paint paint,
                            float startX, float startY, float endX, float endY,
                            float cellSize, float strokeWidth, int color) {
        if (canvas == null || paint == null) return;
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1f) return;

        float ux = dx / length;
        float uy = dy / length;
        float nx = -uy;
        float ny = ux;

        // 正常象棋着法至少跨一个单位格。保留极短线段保护，避免头部反向越过起点。
        float requestedHeadLength = Math.max(1f, cellSize) * HEAD_LENGTH_CELL_RATIO;
        float headLength = Math.min(requestedHeadLength, length * 0.72f);
        float headHalfWidth = Math.max(strokeWidth * 1.45f, headLength * 0.46f);
        float neckDistance = headLength * 0.80f;
        float neckHalfWidth = Math.max(strokeWidth * 0.54f, headLength * 0.12f);

        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        // 箭杆先画到箭头内部，使圆头与实心箭头自然融合且不会出现接缝。
        float shaftEndDistance = headLength * 0.50f;
        float shaftEndX = endX - ux * shaftEndDistance;
        float shaftEndY = endY - uy * shaftEndDistance;
        canvas.drawLine(startX, startY, shaftEndX, shaftEndY, paint);

        float upperWingX = endX - ux * headLength + nx * headHalfWidth;
        float upperWingY = endY - uy * headLength + ny * headHalfWidth;
        float lowerWingX = endX - ux * headLength - nx * headHalfWidth;
        float lowerWingY = endY - uy * headLength - ny * headHalfWidth;

        float upperNeckX = endX - ux * neckDistance + nx * neckHalfWidth;
        float upperNeckY = endY - uy * neckDistance + ny * neckHalfWidth;
        float lowerNeckX = endX - ux * neckDistance - nx * neckHalfWidth;
        float lowerNeckY = endY - uy * neckDistance - ny * neckHalfWidth;

        // 两侧肩部略带圆润，中央后缘向箭尖方向拱起，形成“导航图标”式圆弧底。
        float upperControl1X = upperWingX + ux * headLength * 0.08f;
        float upperControl1Y = upperWingY + uy * headLength * 0.08f;
        float upperControl2X = upperNeckX - ux * headLength * 0.05f;
        float upperControl2Y = upperNeckY - uy * headLength * 0.05f;
        float arcControlX = endX - ux * headLength * 0.53f;
        float arcControlY = endY - uy * headLength * 0.53f;
        float lowerControl1X = lowerNeckX - ux * headLength * 0.05f;
        float lowerControl1Y = lowerNeckY - uy * headLength * 0.05f;
        float lowerControl2X = lowerWingX + ux * headLength * 0.08f;
        float lowerControl2Y = lowerWingY + uy * headLength * 0.08f;

        Path head = new Path();
        head.moveTo(endX, endY);
        head.lineTo(upperWingX, upperWingY);
        head.cubicTo(upperControl1X, upperControl1Y,
                upperControl2X, upperControl2Y,
                upperNeckX, upperNeckY);
        head.quadTo(arcControlX, arcControlY, lowerNeckX, lowerNeckY);
        head.cubicTo(lowerControl1X, lowerControl1Y,
                lowerControl2X, lowerControl2Y,
                lowerWingX, lowerWingY);
        head.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(head, paint);
    }
}

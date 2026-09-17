package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * 节点象棋顶部工具栏的小型自绘图标按钮。
 * 不依赖系统 emoji 字体，确保不同 Android 版本上的图标尺寸和位置一致。
 */
public final class ToolbarIconButton extends View {
    public enum Icon {
        PAPER, MENU, PEN, HOURGLASS, MAGNIFY, LIGHTNING, ALTERNATIVE,
        COPY, PASTE, OPEN, SAVE, UNDO, WRENCH, GEAR, PUSH, COMPUTER_RED, COMPUTER_BLACK
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Icon icon;
    private boolean active;

    public ToolbarIconButton(Context context, Icon icon, String description) {
        super(context);
        this.icon = icon;
        setClickable(true);
        setFocusable(true);
        setContentDescription(description);
        paint.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        invalidate();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float radius = 6f * d;

        boolean dark = active || isPressed();
        int highlight = UiTheme.highlight(getContext());
        paint.setColor(dark ? highlight : UiTheme.background(getContext()));
        canvas.drawRoundRect(new RectF(1f * d, 1f * d, w - d, h - d), radius, radius, paint);
        float size = Math.min(w, h) * 0.53f;
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        stroke.setStrokeWidth(Math.max(1.5f * d, size * 0.105f));
        int iconColor = dark ? UiTheme.textOnHighlight(getContext(), highlight) : UiTheme.textOnBackground(getContext());
        stroke.setColor(iconColor);
        paint.setColor(iconColor);

        switch (icon) {
            case PAPER:
                drawPaper(canvas, cx, cy, size);
                break;
            case MENU:
                drawMenu(canvas, cx, cy, size);
                break;
            case PEN:
                drawPen(canvas, cx, cy, size);
                break;
            case HOURGLASS:
                drawHourglass(canvas, cx, cy, size);
                break;
            case MAGNIFY:
                drawMagnify(canvas, cx, cy, size);
                break;
            case LIGHTNING:
                drawLightning(canvas, cx, cy, size);
                break;
            case ALTERNATIVE:
                drawTextIcon(canvas, "b", cx, cy, size, Color.rgb(244, 67, 54));
                break;
            case COPY:
                drawCopy(canvas, cx, cy, size);
                break;
            case PASTE:
                drawPaste(canvas, cx, cy, size);
                break;
            case OPEN:
                drawOpen(canvas, cx, cy, size);
                break;
            case SAVE:
                drawSave(canvas, cx, cy, size);
                break;
            case UNDO:
                drawTextIcon(canvas, "悔", cx, cy, size * 0.88f, Color.WHITE);
                break;
            case WRENCH:
                drawWrench(canvas, cx, cy, size);
                break;
            case GEAR:
                drawGear(canvas, cx, cy, size);
                break;
            case PUSH:
                drawTextIcon(canvas, "推", cx, cy, size * 0.86f, iconColor);
                break;
            case COMPUTER_RED:
                drawComputer(canvas, cx, cy, size, Color.rgb(210, 48, 45));
                break;
            case COMPUTER_BLACK:
                drawComputer(canvas, cx, cy, size, Color.rgb(45, 48, 50));
                break;
        }
    }


    /** 三横线加左侧圆点，作为紧凑功能菜单图标。 */
    private void drawMenu(Canvas canvas, float cx, float cy, float s) {
        float left = cx - s * 0.30f;
        float right = cx + s * 0.34f;
        float dot = s * 0.055f;
        for (int i = -1; i <= 1; i++) {
            float y = cy + i * s * 0.25f;
            canvas.drawCircle(left, y, dot, paint);
            canvas.drawLine(left + s * 0.16f, y, right, y, stroke);
        }
    }
    private void drawPaper(Canvas canvas, float cx, float cy, float s) {
        float l = cx - s * 0.32f, t = cy - s * 0.42f;
        float r = cx + s * 0.32f, b = cy + s * 0.42f;
        float fold = s * 0.20f;
        path.reset();
        path.moveTo(l, t);
        path.lineTo(r - fold, t);
        path.lineTo(r, t + fold);
        path.lineTo(r, b);
        path.lineTo(l, b);
        path.close();
        canvas.drawPath(path, stroke);
        canvas.drawLine(r - fold, t, r - fold, t + fold, stroke);
        canvas.drawLine(r - fold, t + fold, r, t + fold, stroke);
    }

    private void drawPen(Canvas canvas, float cx, float cy, float s) {
        canvas.save();
        canvas.rotate(-42f, cx, cy);
        RectF body = new RectF(cx - s * 0.11f, cy - s * 0.36f,
                cx + s * 0.11f, cy + s * 0.25f);
        canvas.drawRoundRect(body, s * 0.07f, s * 0.07f, stroke);
        path.reset();
        path.moveTo(cx - s * 0.11f, cy + s * 0.25f);
        path.lineTo(cx, cy + s * 0.45f);
        path.lineTo(cx + s * 0.11f, cy + s * 0.25f);
        canvas.drawPath(path, stroke);
        canvas.drawLine(cx - s * 0.11f, cy - s * 0.22f,
                cx + s * 0.11f, cy - s * 0.22f, stroke);
        canvas.restore();
    }

    private void drawHourglass(Canvas canvas, float cx, float cy, float s) {
        float l = cx - s * 0.30f, r = cx + s * 0.30f;
        float t = cy - s * 0.40f, b = cy + s * 0.40f;
        canvas.drawLine(l, t, r, t, stroke);
        canvas.drawLine(l, b, r, b, stroke);
        path.reset();
        path.moveTo(l + s * 0.04f, t + s * 0.03f);
        path.lineTo(r - s * 0.04f, t + s * 0.03f);
        path.lineTo(cx, cy);
        path.lineTo(r - s * 0.04f, b - s * 0.03f);
        path.lineTo(l + s * 0.04f, b - s * 0.03f);
        path.lineTo(cx, cy);
        path.close();
        canvas.drawPath(path, stroke);
    }

    private void drawMagnify(Canvas canvas, float cx, float cy, float s) {
        float rr = s * 0.27f;
        canvas.drawCircle(cx - s * 0.08f, cy - s * 0.08f, rr, stroke);
        canvas.drawLine(cx + s * 0.12f, cy + s * 0.12f,
                cx + s * 0.37f, cy + s * 0.37f, stroke);
    }

    private void drawLightning(Canvas canvas, float cx, float cy, float s) {
        path.reset();
        path.moveTo(cx + s * 0.06f, cy - s * 0.48f);
        path.lineTo(cx - s * 0.30f, cy + s * 0.04f);
        path.lineTo(cx - s * 0.02f, cy + s * 0.04f);
        path.lineTo(cx - s * 0.12f, cy + s * 0.48f);
        path.lineTo(cx + s * 0.34f, cy - s * 0.10f);
        path.lineTo(cx + s * 0.06f, cy - s * 0.10f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawCopy(Canvas canvas, float cx, float cy, float s) {
        RectF back = new RectF(cx - s * 0.34f, cy - s * 0.34f,
                cx + s * 0.12f, cy + s * 0.18f);
        RectF front = new RectF(cx - s * 0.12f, cy - s * 0.14f,
                cx + s * 0.34f, cy + s * 0.38f);
        canvas.drawRoundRect(back, s * 0.04f, s * 0.04f, stroke);
        canvas.drawRoundRect(front, s * 0.04f, s * 0.04f, stroke);
    }

    private void drawPaste(Canvas canvas, float cx, float cy, float s) {
        RectF sheet = new RectF(cx - s * 0.31f, cy - s * 0.29f,
                cx + s * 0.31f, cy + s * 0.40f);
        canvas.drawRoundRect(sheet, s * 0.05f, s * 0.05f, stroke);
        RectF clip = new RectF(cx - s * 0.16f, cy - s * 0.43f,
                cx + s * 0.16f, cy - s * 0.19f);
        canvas.drawRoundRect(clip, s * 0.06f, s * 0.06f, stroke);
        canvas.drawLine(cx - s * 0.16f, cy, cx + s * 0.16f, cy, stroke);
        canvas.drawLine(cx - s * 0.16f, cy + s * 0.16f,
                cx + s * 0.10f, cy + s * 0.16f, stroke);
    }

    private void drawOpen(Canvas canvas, float cx, float cy, float s) {
        float l = cx - s * 0.40f;
        float r = cx + s * 0.40f;
        float t = cy - s * 0.26f;
        float b = cy + s * 0.34f;
        path.reset();
        path.moveTo(l, t + s * 0.10f);
        path.lineTo(l + s * 0.18f, t + s * 0.10f);
        path.lineTo(l + s * 0.27f, t - s * 0.04f);
        path.lineTo(cx + s * 0.02f, t - s * 0.04f);
        path.lineTo(cx + s * 0.12f, t + s * 0.10f);
        path.lineTo(r, t + s * 0.10f);
        path.lineTo(r - s * 0.08f, b);
        path.lineTo(l + s * 0.08f, b);
        path.close();
        canvas.drawPath(path, stroke);
        canvas.drawLine(l + s * 0.08f, cy, r - s * 0.08f, cy, stroke);
    }

    private void drawSave(Canvas canvas, float cx, float cy, float s) {
        RectF body = new RectF(cx - s * 0.36f, cy - s * 0.40f,
                cx + s * 0.36f, cy + s * 0.40f);
        canvas.drawRoundRect(body, s * 0.05f, s * 0.05f, stroke);
        RectF slot = new RectF(cx - s * 0.20f, cy - s * 0.34f,
                cx + s * 0.18f, cy - s * 0.08f);
        canvas.drawRect(slot, stroke);
        RectF label = new RectF(cx - s * 0.22f, cy + s * 0.06f,
                cx + s * 0.22f, cy + s * 0.31f);
        canvas.drawRoundRect(label, s * 0.03f, s * 0.03f, stroke);
    }

    private void drawGear(Canvas canvas, float cx, float cy, float s) {
        path.reset();
        for (int i = 0; i < 24; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 12.0;
            float radius;
            int phase = i % 3;
            if (phase == 0) radius = s * 0.43f;
            else if (phase == 1) radius = s * 0.34f;
            else radius = s * 0.36f;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, stroke);
        canvas.drawCircle(cx, cy, s * 0.14f, stroke);
    }

    private void drawWrench(Canvas canvas, float cx, float cy, float s) {
        canvas.save();
        canvas.rotate(-42f, cx, cy);
        canvas.drawLine(cx, cy - s * 0.18f, cx, cy + s * 0.34f, stroke);
        canvas.drawCircle(cx, cy + s * 0.34f, s * 0.11f, stroke);
        path.reset();
        path.moveTo(cx - s * 0.23f, cy - s * 0.42f);
        path.lineTo(cx - s * 0.07f, cy - s * 0.27f);
        path.lineTo(cx + s * 0.07f, cy - s * 0.27f);
        path.lineTo(cx + s * 0.23f, cy - s * 0.42f);
        canvas.drawPath(path, stroke);
        canvas.restore();
    }

    private void drawTextIcon(Canvas canvas, String text, float cx, float cy, float size, int color) {
        paint.setColor(color);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(text, cx, baseline, paint);
    }

    private void drawComputer(Canvas canvas, float cx, float cy, float s, int color) {
        Paint p = stroke;
        p.setColor(color);
        p.setStrokeWidth(Math.max(1.4f * getResources().getDisplayMetrics().density, s * 0.09f));
        canvas.drawRoundRect(new RectF(cx - s * .38f, cy - s * .28f, cx + s * .38f, cy + s * .20f), s * .05f, s * .05f, p);
        canvas.drawLine(cx, cy + s * .20f, cx, cy + s * .36f, p);
        canvas.drawLine(cx - s * .20f, cy + s * .36f, cx + s * .20f, cy + s * .36f, p);
    }
}

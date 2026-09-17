package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** 简单 HSV 色彩盘：角度=色相，半径=饱和度，亮度由外部拖动条控制。 */
public final class ColorWheelView extends View {
    public interface Listener { void onColorChanged(int color); }

    private static final int SAMPLE_SIZE = 240;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private Bitmap wheelBitmap;
    private float hue = 135f;
    private float saturation = 0.54f;
    private float value = 0.36f;
    private Listener listener;

    public ColorWheelView(Context context) { super(context); init(); }
    public ColorWheelView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setClickable(true);
        setFocusable(true);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(dp(2));
    }

    public void setListener(Listener value) { listener = value; }

    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        rebuildBitmap();
        invalidate();
    }

    public int getColor() { return Color.HSVToColor(new float[]{hue, saturation, value}); }
    public float getValue() { return value; }

    public void setValue(float newValue) {
        value = Math.max(0f, Math.min(1f, newValue));
        rebuildBitmap();
        invalidate();
        notifyChanged();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(250);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheelBitmap == null) rebuildBitmap();
        float inset = dp(4);
        dst.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawBitmap(wheelBitmap, null, dst, paint);

        float radius = dst.width() / 2f;
        double angle = Math.toRadians(hue);
        float cx = dst.centerX() + (float) Math.cos(angle) * saturation * radius;
        float cy = dst.centerY() + (float) Math.sin(angle) * saturation * radius;
        markerPaint.setColor(UiTheme.textOnHighlight(getColor()));
        markerPaint.setStrokeWidth(dp(2));
        canvas.drawCircle(cx, cy, dp(8), markerPaint);
        markerPaint.setColor(Color.argb(190, 0, 0, 0));
        markerPaint.setStrokeWidth(dp(1));
        canvas.drawCircle(cx, cy, dp(10), markerPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                updateFromPoint(event.getX(), event.getY());
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromPoint(float x, float y) {
        float radius = Math.max(1f, Math.min(getWidth(), getHeight()) / 2f - dp(4));
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        saturation = Math.max(0f, Math.min(1f, distance / radius));
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (degrees < 0f) degrees += 360f;
        hue = degrees;
        invalidate();
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) listener.onColorChanged(getColor());
    }

    private void rebuildBitmap() {
        if (wheelBitmap == null) {
            wheelBitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888);
        }
        int[] pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
        float center = (SAMPLE_SIZE - 1) / 2f;
        float radius = center;
        float[] hsv = new float[3];
        hsv[2] = value;
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            float dy = y - center;
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                float dx = x - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                int index = y * SAMPLE_SIZE + x;
                if (distance > radius) {
                    pixels[index] = Color.TRANSPARENT;
                    continue;
                }
                float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (degrees < 0f) degrees += 360f;
                hsv[0] = degrees;
                hsv[1] = Math.max(0f, Math.min(1f, distance / radius));
                pixels[index] = Color.HSVToColor(hsv);
            }
        }
        wheelBitmap.setPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

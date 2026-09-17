package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** V19.9：两行分段式难度条；第一行 7 档，第二行显示其余档位（县镇好手起）。 */
public final class SegmentedDifficultyView extends View {
    public interface Listener {
        void onSegmentChanged(int index);
    }

    private static final int FIRST_ROW_COUNT = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private String[] labels = new String[0];
    private int[] displayNumbers = new int[0];
    private int selectedIndex;
    private Listener listener;
    /** 首页根据控件所在背景动态传入高对比文字色。 */
    private int labelTextColor = Color.BLACK;

    public SegmentedDifficultyView(Context context) {
        super(context);
        init();
    }

    public SegmentedDifficultyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setPadding(dp(4), dp(4), dp(4), dp(4));
    }

    public void setLabels(String[] values) {
        labels = values == null ? new String[0] : values.clone();
        selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, labels.length - 1)));
        invalidate();
    }

    public void setDisplayNumbers(int[] values) {
        displayNumbers = values == null ? new int[0] : values.clone();
        invalidate();
    }

    public void setSelectedIndex(int index) {
        int next = Math.max(0, Math.min(index, Math.max(0, labels.length - 1)));
        if (next == selectedIndex) return;
        selectedIndex = next;
        invalidate();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void setLabelTextColor(int color) {
        if (labelTextColor == color) return;
        labelTextColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(116);
        int height = resolveSize(desired, heightMeasureSpec);
        int width = resolveSize(dp(280), widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = labels.length;
        if (count == 0) return;

        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float row1Top = dp(32);
        float row1Bottom = dp(55);
        float row2Top = dp(62);
        float row2Bottom = dp(85);

        drawSelectedName(canvas, left, right);
        drawSegmentRow(canvas, 0, Math.min(FIRST_ROW_COUNT, count),
                left, right, row1Top, row1Bottom);
        if (count > FIRST_ROW_COUNT) {
            drawSegmentRow(canvas, FIRST_ROW_COUNT, count,
                    left, right, row2Top, row2Bottom);
        }

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(sp(11));
        paint.setColor(labelTextColor);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("低", left, dp(108), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("高", right, dp(108), paint);
    }

    private void drawSelectedName(Canvas canvas, float left, float right) {
        String text = labels[selectedIndex] == null ? "" : labels[selectedIndex];
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(labelTextColor);
        float size = sp(17);
        paint.setTextSize(size);
        float available = Math.max(1f, right - left - dp(8));
        while (size > sp(11) && paint.measureText(text) > available) {
            size -= sp(0.5f);
            paint.setTextSize(size);
        }
        canvas.drawText(text, getWidth() / 2f, dp(22), paint);
    }

    private void drawSegmentRow(Canvas canvas, int start, int end,
                                float left, float right, float top, float bottom) {
        int rowCount = Math.max(0, end - start);
        if (rowCount <= 0) return;
        float gap = dp(3);
        float segmentWidth = (right - left - gap * (rowCount - 1)) / rowCount;
        for (int offset = 0; offset < rowCount; offset++) {
            int index = start + offset;
            float x1 = left + offset * (segmentWidth + gap);
            float x2 = x1 + segmentWidth;
            rect.set(x1, top, x2, bottom);
            int background = index <= selectedIndex
                    ? UiTheme.highlight(getContext()) : UiTheme.homeButton(getContext());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);

            if (index == selectedIndex) {
                float cx = (x1 + x2) / 2f;
                float cy = (top + bottom) / 2f;
                int markerColor = UiTheme.textOnHighlight(getContext(), background);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.2f));
                paint.setColor(markerColor);
                canvas.drawCircle(cx, cy, dp(8.4f), paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(sp(index + 1 >= 10 ? 8.5f : 9.5f));
                Paint.FontMetrics fm = paint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                int number = index < displayNumbers.length && displayNumbers[index] > 0
                        ? displayNumbers[index] : index + 1;
                canvas.drawText(String.valueOf(number), cx, baseline, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (labels.length == 0) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_MOVE
                || event.getAction() == MotionEvent.ACTION_UP) {
            int next = indexForPoint(event.getX(), event.getY());
            if (next >= 0 && next != selectedIndex) {
                selectedIndex = next;
                invalidate();
                if (listener != null) listener.onSegmentChanged(next);
            }
            if (event.getAction() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int indexForPoint(float x, float y) {
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float row1Top = dp(28);
        float row1Bottom = dp(59);
        float row2Top = dp(58);
        float row2Bottom = dp(90);
        int start;
        int rowCount;
        if (y >= row1Top && y <= row1Bottom) {
            start = 0;
            rowCount = Math.min(FIRST_ROW_COUNT, labels.length);
        } else if (labels.length > FIRST_ROW_COUNT && y >= row2Top && y <= row2Bottom) {
            start = FIRST_ROW_COUNT;
            rowCount = labels.length - FIRST_ROW_COUNT;
        } else {
            return -1;
        }
        if (rowCount <= 0) return -1;
        float width = Math.max(1f, right - left);
        float normalized = Math.max(0f, Math.min(0.9999f, (x - left) / width));
        int offset = Math.max(0, Math.min(rowCount - 1, (int) (normalized * rowCount)));
        return start + offset;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

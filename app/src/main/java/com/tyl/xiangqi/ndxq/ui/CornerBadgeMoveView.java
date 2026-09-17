package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.TextView;

/** 在棋谱文字右上角绘制分支角标，不参与布局测量。 */
public final class CornerBadgeMoveView extends TextView {
    public interface AccentColorProvider {
        int getAccentColor();
    }

    private String cornerBadge;
    private final AccentColorProvider accentColorProvider;
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CornerBadgeMoveView(Context context, AccentColorProvider accentColorProvider) {
        super(context);
        this.accentColorProvider = accentColorProvider;
        badgePaint.setTypeface(Typeface.DEFAULT_BOLD);
        badgePaint.setTextSize(8f * getResources().getDisplayMetrics().scaledDensity);
        badgePaint.setColor(currentAccentColor());
    }

    public void setCornerBadge(String text, boolean selected) {
        cornerBadge = text == null || text.isEmpty() ? null : text;
        int highlightColor = currentAccentColor();
        badgePaint.setColor(selected
                ? UiTheme.textOnHighlight(getContext(), highlightColor) : highlightColor);
        invalidate();
    }

    private int currentAccentColor() {
        return accentColorProvider == null ? 0 : accentColorProvider.getAccentColor();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cornerBadge == null) return;
        float density = getResources().getDisplayMetrics().density;
        float badgeWidth = badgePaint.measureText(cornerBadge);
        float x = getWidth() - badgeWidth - 4f * density;
        Paint.FontMetrics fm = badgePaint.getFontMetrics();
        float y = density - fm.ascent;
        canvas.drawText(cornerBadge, x, y, badgePaint);
    }
}

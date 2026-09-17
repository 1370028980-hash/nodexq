package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.CornerBadgeMoveView;
import com.tyl.xiangqi.ndxq.ui.ToolbarIconButton;

import java.util.List;

/** 通用界面组件、按钮样式和棋谱步视图的创建。 */
final class UiComponentController {
    private final MainActivity host;

    UiComponentController(MainActivity host) {
        this.host = host;
    }

    TextView manualNavButton(String text, boolean enabled, View.OnClickListener listener) {
        TextView button = new TextView(host);
        button.setText(text);
        button.setTextSize(text.length() > 1 ? 10 : 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setEnabled(enabled);
        button.setClickable(enabled);
        button.setTextColor(enabled ? host.globalBackgroundTextColor() : Color.rgb(145, 150, 147));
        setRoundedBackground(button, host.globalSurfaceFillColor(), 5, Color.TRANSPARENT);
        if (enabled) button.setOnClickListener(listener);
        return button;
    }

    TextView pushNavButton(String text, boolean enabled, View.OnClickListener listener) {
        TextView button = manualNavButton(text, enabled, listener);
        button.setTextSize(text.length() > 1 ? 13 : 15);
        return button;
    }

    LinearLayout.LayoutParams pushNavLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, host.dp(42), 1f);
        lp.leftMargin = host.dp(3);
        lp.rightMargin = host.dp(3);
        return lp;
    }

    LinearLayout toolbarRow() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    TextView sectionTitle(String text) {
        TextView title = new TextView(host);
        title.setText(text);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        host.markLauncherTextBackground(title, MainActivity.LAUNCHER_TEXT_BG_GLOBAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(host.dp(2), 0, 0, host.dp(7));
        return title;
    }

    TextView selectorOption(String text) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        return view;
    }

    void styleSelectorOption(TextView view, boolean selected) {
        // 未选中项保留首页底色，选中项只叠加全局高亮色。
        host.markLauncherTextBackground(view, selected
                ? MainActivity.LAUNCHER_TEXT_BG_HOME_HIGHLIGHT
                : MainActivity.LAUNCHER_TEXT_BG_HOME);
        setRoundedBackground(view, selected ? host.highlightColor() : Color.TRANSPARENT,
                11, Color.TRANSPARENT);
        host.skinRuntimeController.refreshLauncherTextContrastFor(view);
    }

    Button largeButton(CharSequence text, boolean primary) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? host.highlightTextColor() : Color.rgb(42, 65, 54));
        button.setAllCaps(false);
        setRoundedBackground(button,
                primary ? host.highlightColor() : Color.rgb(239, 239, 233),
                14, primary ? Color.TRANSPARENT : Color.rgb(193, 197, 191));
        return button;
    }

    ToolbarIconButton toolbarIconButton(ToolbarIconButton.Icon icon,
                                        String description,
                                        View.OnClickListener listener) {
        ToolbarIconButton button = new ToolbarIconButton(host, icon, description);
        button.setOnClickListener(listener);
        return button;
    }

    private Button toolbarTextButton(String text, View.OnClickListener listener) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        setRoundedBackground(button, Color.rgb(35, 46, 41), 6, Color.TRANSPARENT);
        button.setOnClickListener(listener);
        return button;
    }

    Button evaluationToolbarButton(String text, View.OnClickListener listener) {
        Button button = toolbarTextButton(text, listener);
        styleEvaluationToolbarButton(button, false, true);
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && button.isEnabled()) {
                styleEvaluationToolbarButton(button, true, true);
                host.handler.postDelayed(() -> styleEvaluationToolbarButton(
                        button, false, button.isEnabled()), 120L);
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                styleEvaluationToolbarButton(button, false, button.isEnabled());
            }
            return false;
        });
        return button;
    }

    void styleEvaluationToolbarButton(Button button, boolean highlighted, boolean enabled) {
        if (button == null) return;
        int background = highlighted ? host.highlightColor() : host.globalSurfaceFillColor();
        int text = highlighted ? host.highlightTextColor() : host.globalBackgroundTextColor();
        setRoundedBackground(button, background, 6, Color.TRANSPARENT);
        button.setTextColor(text);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    Button surfaceActionButton(String text) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        setRoundedBackground(button, host.globalSurfaceFillColor(), 7, Color.TRANSPARENT);
        button.setTextColor(host.globalBackgroundTextColor());
        return button;
    }

    Button compactButton(String text) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        setRoundedBackground(button, host.highlightColor(), 7, Color.TRANSPARENT);
        button.setTextColor(host.highlightTextColor());
        return button;
    }

    TextView playerLabel() {
        TextView label = new TextView(host);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        return label;
    }

    TextView tabText(String text) {
        TextView tab = new TextView(host);
        tab.setText(text);
        tab.setTextSize(12);
        tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        return tab;
    }

    void styleTab(TextView tab, boolean selected) {
        if (tab == null) return;
        tab.setTextColor(host.globalBackgroundTextColor());
        final boolean showIndicator = selected;
        tab.setBackground(new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override public void draw(android.graphics.Canvas canvas) {
                if (!showIndicator) return;
                android.graphics.Rect b = getBounds();
                float lineWidth = Math.min(host.dp(26), b.width() * 0.42f);
                float lineHeight = host.dp(2);
                float cx = b.exactCenterX();
                float bottom = b.bottom - host.dp(1);
                paint.setColor(Color.BLACK);
                canvas.drawRoundRect(cx - lineWidth / 2f, bottom - lineHeight,
                        cx + lineWidth / 2f, bottom, lineHeight, lineHeight, paint);
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

            @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        });
    }

    TextView manualMoveRow(String text, boolean selected) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextColor(selected ? host.highlightTextColor() : host.globalBackgroundTextColor());
        view.setPadding(host.dp(7), 0, host.dp(5), 0);
        view.setMinWidth(host.dp(74));
        setRoundedBackground(view, selected ? host.highlightColor() : Color.TRANSPARENT,
                6, selected ? Color.TRANSPARENT : Color.rgb(226, 228, 224));
        return view;
    }

    View manualMoveCell(String text, boolean selected, int node, boolean firstInRound) {
        CornerBadgeMoveView move = new CornerBadgeMoveView(host,
                new CornerBadgeMoveView.AccentColorProvider() {
                    @Override public int getAccentColor() {
                        return host.highlightColor();
                    }
                });
        move.setText(text);
        move.setTextSize(14);
        move.setSingleLine(true);
        move.setGravity(Gravity.CENTER_VERTICAL);
        move.setTextColor(selected ? host.highlightTextColor() : host.globalBackgroundTextColor());
        move.setPadding(host.dp(7), 0, host.dp(5), 0);
        setRoundedBackground(move, selected ? host.highlightColor() : Color.TRANSPARENT,
                6, selected ? Color.TRANSPARENT : Color.rgb(226, 228, 224));
        move.setCornerBadge(branchIndicator(node), selected);
        return move;
    }

    private String branchIndicator(int node) {
        List<ManualVariation> vars = host.manualVariations.get(node);
        if (vars == null || vars.isEmpty()) return "";
        int count = 1;
        for (ManualVariation variation : vars) {
            if (variation != null && !variation.engineSteps.isEmpty()) count++;
        }
        if (count <= 1) return "";
        return count + host.activeBranchLabel(node);
    }

    LinearLayout.LayoutParams toolbarLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = host.dp(1);
        lp.rightMargin = host.dp(1);
        return lp;
    }

    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void setRoundedBackground(View view, int fill, int radiusDp, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(host.dp(radiusDp));
        if (stroke != Color.TRANSPARENT) bg.setStroke(host.dp(1), stroke);
        view.setBackground(bg);
    }
}

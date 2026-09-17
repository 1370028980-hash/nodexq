package com.tyl.xiangqi.ndxq.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 引擎分析结果视图，MultiPV 使用“按深度分组 + PV 等宽分列”的布局。 */
public final class EngineAnalysisPanel {
    private static final int SCORE_BLUE = Color.rgb(0, 90, 210);
    private static final int SCORE_RED = Color.rgb(210, 0, 0);
    public static final int COLOR_AUTO = Integer.MIN_VALUE;
    public static final String KEY_ADVANTAGE_COLOR = "engine_advantage_color";
    public static final String KEY_DISADVANTAGE_COLOR = "engine_disadvantage_color";
    public static final String KEY_RED_MOVE_COLOR = "engine_red_move_color";
    public static final String KEY_BLACK_MOVE_COLOR = "engine_black_move_color";
    private static final int TEXT = Color.rgb(38, 43, 40);
    private static final int DIVIDER = Color.rgb(170, 176, 172);
    private static final char WORD_JOINER = '\u2060';

    private EngineAnalysisPanel() {}

    public static final class DepthModule {
        public final int depth;
        public final String timeText;
        public final String npsText;
        public final String nodesText;
        public final String hashFullText;
        public final List<PvItem> pvs = new ArrayList<PvItem>();

        public DepthModule(int depth, String timeText, String npsText,
                           String nodesText, String hashFullText) {
            this.depth = depth;
            this.timeText = safe(timeText);
            this.npsText = safe(npsText);
            this.nodesText = safe(nodesText);
            this.hashFullText = safe(hashFullText);
        }
    }

    public static final class PvItem {
        public final int index;
        public final int ownScoreValue;
        public final String scoreText;
        public final String wdlText;
        public final String moveText;
        public final boolean redToMoveAtRoot;

        public PvItem(int index, int ownScoreValue, String scoreText,
                      String wdlText, String moveText, boolean redToMoveAtRoot) {
            this.index = Math.max(1, index);
            this.ownScoreValue = ownScoreValue;
            this.scoreText = safe(scoreText);
            this.wdlText = safe(wdlText);
            this.moveText = safe(moveText);
            this.redToMoveAtRoot = redToMoveAtRoot;
        }
    }

    public static void render(Activity activity, LinearLayout host, CharSequence fallback,
                              List<DepthModule> modules) {
        if (activity == null || host == null) return;
        host.removeAllViews();
        if (modules == null || modules.isEmpty()) {
            TextView text = baseText(activity, 13);
            text.setTypeface(android.graphics.Typeface.MONOSPACE);
            text.setText(fallback == null ? "" : fallback);
            host.addView(text, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (int i = 0; i < modules.size(); i++) {
            DepthModule module = modules.get(i);
            TextView meta = baseText(activity, 12);
            StringBuilder line = new StringBuilder();
            line.append("深度").append(module.depth)
                    .append("  时间:").append(module.timeText)
                    .append("  NPS:").append(module.npsText)
                    .append("  节点数:").append(module.nodesText);
            if (!module.hashFullText.isEmpty()) line.append("  HF:").append(module.hashFullText);
            meta.setText(line.toString());
            meta.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            meta.setPadding(0, dp(activity, 2), 0, dp(activity, 4));
            host.addView(meta, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // MultiPV 永远在当前屏幕宽度内等分：N 个 PV 各占剩余宽度的 1/N，
            // 不再使用固定 108dp 列宽和 HorizontalScrollView。
            LinearLayout pvRow = new LinearLayout(activity);
            pvRow.setOrientation(LinearLayout.HORIZONTAL);
            pvRow.setBaselineAligned(false);
            for (int p = 0; p < module.pvs.size(); p++) {
                pvRow.addView(pvColumn(activity, module.pvs.get(p), p > 0),
                        new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            host.addView(pvRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i + 1 < modules.size()) {
                host.addView(new DashedDivider(activity, false),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(activity, 9)));
            }
        }
    }

    /** 二合一布局专用：只保留深度、己分、时间、NPS，走法与 MultiPV 逻辑不变。 */
    public static void renderCompact(Activity activity, LinearLayout host, CharSequence fallback,
                                     List<DepthModule> modules) {
        if (activity == null || host == null) return;
        host.removeAllViews();
        if (modules == null || modules.isEmpty()) {
            TextView text = baseText(activity, 12);
            text.setText(fallback == null ? "" : fallback);
            host.addView(text, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (int i = 0; i < modules.size(); i++) {
            DepthModule module = modules.get(i);
            TextView meta = baseText(activity, 11);
            meta.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            meta.setText("深度:" + module.depth + "  时间:" + module.timeText
                    + "  NPS:" + module.npsText);
            host.addView(meta, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout pvRow = new LinearLayout(activity);
            pvRow.setOrientation(LinearLayout.HORIZONTAL);
            pvRow.setBaselineAligned(false);
            for (int p = 0; p < module.pvs.size(); p++) {
                PvItem item = module.pvs.get(p);
                LinearLayout column = new PvColumnLayout(activity, p > 0);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setPadding(dp(activity, p > 0 ? 3 : 1), 0, dp(activity, 1), dp(activity, 3));
                TextView header = baseText(activity, 10);
                SpannableStringBuilder label = new SpannableStringBuilder();
                label.append(String.valueOf(item.index)).append(" 己分:");
                int scoreStart = label.length();
                label.append(item.scoreText);
                label.setSpan(new ForegroundColorSpan(item.ownScoreValue < 0 ? disadvantageColor(activity) : advantageColor(activity)),
                        scoreStart, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                header.setText(label);
                column.addView(header, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                MoveSequenceView moves = new MoveSequenceView(activity);
                moves.setTextSize(11);
                moves.setMoveText(item.moveText.isEmpty() ? "-" : item.moveText,
                        item.redToMoveAtRoot);
                column.addView(moves, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                pvRow.addView(column, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            host.addView(pvRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i + 1 < modules.size()) {
                host.addView(new DashedDivider(activity, false),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(activity, 7)));
            }
        }
    }

    /** 红方招法红字、黑方招法黑字；仍用 WORD JOINER 保持每着四字模块。 */
    public static CharSequence colorMoveUnits(Context context, String moveText, boolean redToMoveAtRoot) {
        String joined = keepMoveUnitsTogether(moveText);
        return colorFormattedMoveText(context, joined, redToMoveAtRoot);
    }

    private static CharSequence colorFormattedMoveText(Context context, String text, boolean redToMoveAtRoot) {
        SpannableStringBuilder out = new SpannableStringBuilder(safe(text));
        boolean red = redToMoveAtRoot;
        int i = 0;
        while (i < out.length()) {
            while (i < out.length() && Character.isWhitespace(out.charAt(i))) i++;
            if (i >= out.length()) break;
            int start = i;
            while (i < out.length() && !Character.isWhitespace(out.charAt(i))) i++;
            if (red) {
                out.setSpan(new ForegroundColorSpan(redMoveColor(context)), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.setSpan(new ForegroundColorSpan(blackMoveColor(context)), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            red = !red;
        }
        return out;
    }

    private static LinearLayout pvColumn(Activity activity, PvItem item, boolean leftDivider) {
        LinearLayout column = new PvColumnLayout(activity, leftDivider);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, leftDivider ? 4 : 2), 0,
                dp(activity, 2), dp(activity, 4));

        TextView header = baseText(activity, 11);
        header.setGravity(Gravity.START);
        SpannableStringBuilder label = new SpannableStringBuilder();
        int numberStart = label.length();
        label.append(String.valueOf(item.index));
        label.setSpan(new ForegroundColorSpan(scoreBlue(activity)), numberStart, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.append(" ");
        int scoreStart = label.length();
        label.append("己分:").append(item.scoreText)
                .append(" WDL:").append(item.wdlText);
        label.setSpan(new ForegroundColorSpan(item.ownScoreValue < 0 ? disadvantageColor(activity) : advantageColor(activity)),
                scoreStart, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        header.setText(label);
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MoveSequenceView moves = new MoveSequenceView(activity);
        moves.setMoveText(item.moveText.isEmpty() ? "-" : item.moveText, item.redToMoveAtRoot);
        moves.setPadding(0, dp(activity, 3), 0, 0);
        column.addView(moves, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    /**
     * 单 PV 的普通 TextView 没有实际列宽信息，因此用 WORD JOINER 把每一着内部连成不可断行单位，
     * 着与着之间只留一个空格，让系统优先在完整着法之间紧凑换行。
     */
    public static String keepMoveUnitsTogether(String moveText) {
        List<String> units = splitMoveUnits(moveText);
        if (units.isEmpty()) return safe(moveText);
        StringBuilder out = new StringBuilder();
        for (String unit : units) {
            if (out.length() > 0) out.append(' ');
            out.append(joinUnit(unit));
        }
        return out.toString();
    }

    public static String formatTime(long timeMs) {
        if (timeMs < 0) return "-";
        if (timeMs < 1000L) return timeMs + "ms";
        return String.format(java.util.Locale.CHINA, "%.1fs", timeMs / 1000.0d);
    }

    public static String formatCount(long value) {
        if (value < 0) return "-";
        if (value >= 1000000L) return String.format(java.util.Locale.CHINA, "%.1fM", value / 1000000.0d);
        if (value >= 1000L) return (value / 1000L) + "K";
        return String.valueOf(value);
    }

    public static String formatHashFull(int value) {
        return value < 0 ? "" : value + "‰";
    }

    public static int automaticDisadvantageColor(Context context) {
        return context != null && UiTheme.textOnBackground(context) == Color.WHITE
                ? Color.rgb(255, 145, 135) : SCORE_RED;
    }

    public static int automaticAdvantageColor(Context context) {
        return context != null && UiTheme.textOnBackground(context) == Color.WHITE
                ? Color.rgb(125, 185, 255) : SCORE_BLUE;
    }

    public static int automaticRedMoveColor(Context context) {
        return automaticDisadvantageColor(context);
    }

    public static int automaticBlackMoveColor(Context context) {
        return context == null ? Color.BLACK : UiTheme.textOnBackground(context);
    }

    public static int advantageColor(Context context) {
        return configuredColor(context, KEY_ADVANTAGE_COLOR, automaticAdvantageColor(context));
    }

    public static int disadvantageColor(Context context) {
        return configuredColor(context, KEY_DISADVANTAGE_COLOR, automaticDisadvantageColor(context));
    }

    public static int redMoveColor(Context context) {
        return configuredColor(context, KEY_RED_MOVE_COLOR, automaticRedMoveColor(context));
    }

    public static int blackMoveColor(Context context) {
        return configuredColor(context, KEY_BLACK_MOVE_COLOR, automaticBlackMoveColor(context));
    }

    private static int configuredColor(Context context, String key, int fallback) {
        if (context == null) return fallback;
        int value = context.getSharedPreferences(UiTheme.PREFS, Context.MODE_PRIVATE)
                .getInt(key, COLOR_AUTO);
        return value == COLOR_AUTO ? fallback : value;
    }

    private static int scoreRed(Context context) {
        return automaticDisadvantageColor(context);
    }

    private static int scoreBlue(Context context) {
        return automaticAdvantageColor(context);
    }

    private static TextView baseText(Activity activity, int sp) {
        TextView view = new TextView(activity);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.textOnBackground(activity));
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private static List<String> splitMoveUnits(String text) {
        ArrayList<String> units = new ArrayList<String>();
        String value = safe(text).replace(String.valueOf(WORD_JOINER), "").trim();
        if (value.length() == 0) return units;
        String[] parts = value.split("\\s+");
        for (String part : parts) {
            if (part != null && part.length() > 0) units.add(part);
        }
        return units;
    }

    private static String joinUnit(String unit) {
        if (unit == null || unit.length() <= 1) return safe(unit);
        StringBuilder out = new StringBuilder(unit.length() * 2);
        for (int i = 0; i < unit.length(); i++) {
            if (i > 0) out.append(WORD_JOINER);
            out.append(unit.charAt(i));
        }
        return out.toString();
    }

    private static String compactPlainUnits(List<String> units) {
        StringBuilder out = new StringBuilder();
        for (String unit : units) {
            if (out.length() > 0) out.append(' ');
            out.append(unit);
        }
        return out.toString();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /**
     * MultiPV 走法视图：按实际分到的 1/N 列宽贪心排版。
     * 能容纳至少一着时绝不拆开“炮八平五/马２进３”这类单位；若列宽连一着都放不下，
     * 则回退为普通文本，让 Android 按字符换行，避免极端 MultiPV 下文字被截断或横向滚动。
     */
    private static final class MoveSequenceView extends TextView {
        private String source = "";
        private boolean redToMoveAtRoot = true;
        private int lastAvailableWidth = -1;

        MoveSequenceView(Activity activity) {
            super(activity);
            setTextSize(12);
            setTextColor(UiTheme.textOnBackground(activity));
            setLineSpacing(0f, 1.08f);
            setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }

        void setMoveText(String text, boolean redToMoveAtRoot) {
            source = safe(text);
            this.redToMoveAtRoot = redToMoveAtRoot;
            lastAvailableWidth = -1;
            setText(colorFormattedMoveText(getContext(), source, redToMoveAtRoot));
            post(this::applyForCurrentWidth);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w != oldw) applyForCurrentWidth();
        }

        private void applyForCurrentWidth() {
            int available = getWidth() - getPaddingLeft() - getPaddingRight();
            if (available <= 0 || available == lastAvailableWidth) return;
            lastAvailableWidth = available;
            List<String> units = splitMoveUnits(source);
            if (units.isEmpty()) {
                setText(colorFormattedMoveText(getContext(), source, redToMoveAtRoot));
                return;
            }

            float widest = 0f;
            for (String unit : units) widest = Math.max(widest, getPaint().measureText(unit));
            if (widest > available) {
                // 列窄到放不下一整着时，按用户要求取消“四字一单位”的限制。
                setText(colorFormattedMoveText(getContext(), compactPlainUnits(units), redToMoveAtRoot));
                return;
            }

            float space = getPaint().measureText(" ");
            float lineWidth = 0f;
            StringBuilder out = new StringBuilder();
            for (String unit : units) {
                float unitWidth = getPaint().measureText(unit);
                if (lineWidth > 0f && lineWidth + space + unitWidth > available) {
                    out.append('\n');
                    lineWidth = 0f;
                } else if (lineWidth > 0f) {
                    out.append(' ');
                    lineWidth += space;
                }
                out.append(joinUnit(unit));
                lineWidth += unitWidth;
            }
            setText(colorFormattedMoveText(getContext(), out.toString(), redToMoveAtRoot));
        }
    }


    /** 在 PV 列自身内部绘制左侧虚线，不额外占布局宽度，保证 N 列严格等分可用宽度。 */
    private static final class PvColumnLayout extends LinearLayout {
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean drawLeftDivider;

        PvColumnLayout(Activity activity, boolean drawLeftDivider) {
            super(activity);
            this.drawLeftDivider = drawLeftDivider;
            setWillNotDraw(false);
            dividerPaint.setColor(DIVIDER);
            dividerPaint.setStyle(Paint.Style.STROKE);
            dividerPaint.setStrokeWidth(Math.max(1f,
                    activity.getResources().getDisplayMetrics().density));
            dividerPaint.setPathEffect(new DashPathEffect(
                    new float[]{dp(activity, 4), dp(activity, 3)}, 0));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (drawLeftDivider) canvas.drawLine(0, 0, 0, getHeight(), dividerPaint);
        }
    }

    private static final class DashedDivider extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean vertical;

        DashedDivider(Activity activity, boolean vertical) {
            super(activity);
            this.vertical = vertical;
            paint.setColor(DIVIDER);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, activity.getResources().getDisplayMetrics().density));
            paint.setPathEffect(new DashPathEffect(new float[]{dp(activity, 4), dp(activity, 3)}, 0));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (vertical) {
                float x = getWidth() / 2f;
                canvas.drawLine(x, 0, x, getHeight(), paint);
            } else {
                float y = getHeight() / 2f;
                canvas.drawLine(0, y, getWidth(), y, paint);
            }
        }
    }
}

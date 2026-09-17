package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.GameReportCalculator;

import java.util.List;
import java.util.Locale;

/** 对局报告及错招列表的展示控制器，不参与评分计算和局面导航的实现。 */
final class GameReportDialogController {
    interface Source {
        GameReportCalculator.Report buildReport(double softMinTau);
        List<String> readableMoves();
        boolean redToMoveAtRoot();
        void navigateToPly(int target);
        void refreshReportButton(GameReportCalculator.Report report);
    }

    private final MainActivity activity;
    private final Source source;
    private double softMinTau = GameReportCalculator.PHASE_SOFTMIN_TAU;
    private AlertDialog dialog;
    private TextView redHeading;
    private TextView redDetail;
    private TextView blackHeading;
    private TextView blackDetail;

    GameReportDialogController(MainActivity activity, Source source) {
        this.activity = activity;
        this.source = source;
    }

    double softMinTau() {
        return softMinTau;
    }

    void show() {
        dismiss();
        GameReportCalculator.Report report = source.buildReport(softMinTau);
        if (!report.complete) {
            Toast.makeText(activity, "对局评估不完整，请先补全所有局面评分", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(8));

        final TextView hint = new TextView(activity);
        hint.setText(reportHint());
        hint.setTextSize(11);
        hint.setTextColor(Color.rgb(90, 98, 94));
        hint.setGravity(Gravity.CENTER);
        body.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tauRow = optionRow();
        TextView tauLabel = new TextView(activity);
        tauLabel.setText("SoftMin τ：");
        tauLabel.setTextSize(13);
        tauLabel.setTextColor(Color.rgb(45, 52, 48));
        tauLabel.setGravity(Gravity.CENTER_VERTICAL);
        tauRow.addView(tauLabel, new LinearLayout.LayoutParams(
                activity.dp(96), ViewGroup.LayoutParams.MATCH_PARENT));
        final EditText tauInput = new EditText(activity);
        tauInput.setText(String.valueOf(softMinTau));
        tauInput.setSingleLine(true);
        tauInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tauInput.setTextSize(14);
        tauInput.setSelectAllOnFocus(true);
        tauInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        tauRow.addView(tauInput, new LinearLayout.LayoutParams(0, activity.dp(40), 1f));
        TextView tauApply = activity.compactButton("重算");
        tauApply.setOnClickListener(v -> applyTau(tauInput, hint));
        tauRow.addView(tauApply, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(38)));
        LinearLayout.LayoutParams tauRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42));
        tauRowLp.topMargin = activity.dp(6);
        body.addView(tauRow, tauRowLp);

        TextView tauExplain = new TextView(activity);
        tauExplain.setText("参数越大打分越宽松，越小打分越严格，默认值15");
        tauExplain.setTextSize(11);
        tauExplain.setTextColor(Color.rgb(90, 98, 94));
        tauExplain.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tauExplainLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tauExplainLp.topMargin = activity.dp(2);
        body.addView(tauExplain, tauExplainLp);

        TextView timeHint = new TextView(activity);
        timeHint.setText("每步打分时间越久越准确");
        timeHint.setTextSize(11);
        timeHint.setTextColor(Color.rgb(90, 98, 94));
        timeHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeHintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeHintLp.topMargin = activity.dp(4);
        body.addView(timeHint, timeHintLp);

        LinearLayout sides = new LinearLayout(activity);
        sides.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sidesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sidesLp.topMargin = activity.dp(10);
        body.addView(sides, sidesLp);

        LinearLayout.LayoutParams redLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        redLp.rightMargin = activity.dp(4);
        sides.addView(buildSideCard("红方", report.red, true), redLp);
        LinearLayout.LayoutParams blackLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blackLp.leftMargin = activity.dp(4);
        sides.addView(buildSideCard("黑方", report.black, false), blackLp);

        dialog = new AlertDialog.Builder(activity)
                .setTitle("对局报告")
                .setView(body)
                .setPositiveButton("关闭", null)
                .create();
        dialog.setOnDismissListener(ignored -> clearDialogReferences());
        dialog.show();
    }

    void refreshIfOpen(GameReportCalculator.Report report) {
        if (dialog == null || !dialog.isShowing() || report == null || !report.complete) return;
        bindSide(redHeading, redDetail, "红方", report.red);
        bindSide(blackHeading, blackDetail, "黑方", report.black);
    }

    void dismiss() {
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        clearDialogReferences();
    }

    private void applyTau(EditText tauInput, TextView hint) {
        try {
            double value = Double.parseDouble(tauInput.getText().toString().trim());
            if (value <= 0) throw new IllegalArgumentException("τ 需大于 0");
            GameReportCalculator.Report recalculated = source.buildReport(value);
            if (!recalculated.complete) {
                Toast.makeText(activity, "对局评估不完整，请先补全所有局面评分", Toast.LENGTH_SHORT).show();
                return;
            }
            softMinTau = value;
            bindSide(redHeading, redDetail, "红方", recalculated.red);
            bindSide(blackHeading, blackDetail, "黑方", recalculated.black);
            source.refreshReportButton(recalculated);
            hint.setText(reportHint());
            Toast.makeText(activity, "已按 τ=" + value + " 重新计算", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, "请输入有效的 τ 值（大于 0）", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindSide(TextView heading, TextView detail, String title,
                          GameReportCalculator.SideReport side) {
        if (heading != null) heading.setText(title + "  总评 " + formatScore(side.total));
        if (detail != null) bindDetail(detail, side, "红方".equals(title));
    }

    private void bindDetail(TextView detail, GameReportCalculator.SideReport side, boolean redSide) {
        String text = "开局  " + formatScore(side.opening) + "\n中局  " + formatScore(side.middlegame)
                + "\n残局  " + formatScore(side.endgame) + "\n错误招法  " + side.errorCount;
        SpannableStringBuilder content = new SpannableStringBuilder(text);
        int start = text.lastIndexOf("错误招法");
        if (side.errorCount >= 1 && start >= 0) {
            content.setSpan(new UnderlineSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            content.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    showErrorMoves(redSide);
                }
            }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            detail.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            detail.setMovementMethod(null);
        }
        detail.setText(content);
    }

    private void showErrorMoves(boolean redSide) {
        GameReportCalculator.Report report = source.buildReport(softMinTau);
        if (!report.complete) return;
        List<String> moves = source.readableMoves();

        LinearLayout table = new LinearLayout(activity);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        int roundWidth = activity.dp(82);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(errorCell("回合数", true, Gravity.CENTER),
                new LinearLayout.LayoutParams(roundWidth, activity.dp(30)));
        header.addView(errorCell("错误招法", true, Gravity.START),
                new LinearLayout.LayoutParams(0, activity.dp(30), 1f));
        table.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(30)));
        View headerDivider = new View(activity);
        headerDivider.setBackgroundColor(Color.rgb(192, 197, 193));
        table.addView(headerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1)));

        final AlertDialog[] errorDialog = new AlertDialog[1];
        int count = 0;
        for (Integer plyValue : report.errorPlies) {
            if (plyValue == null) continue;
            int ply = plyValue;
            boolean moverRed = source.redToMoveAtRoot()
                    ? ((ply - 1) % 2 == 0) : ((ply - 1) % 2 != 0);
            if (moverRed != redSide || ply < 1 || ply > moves.size()) continue;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.addView(errorCell(String.valueOf((ply + 1) / 2), false, Gravity.CENTER),
                    new LinearLayout.LayoutParams(roundWidth, activity.dp(32)));
            row.addView(errorCell(moves.get(ply - 1), false, Gravity.CENTER_VERTICAL | Gravity.START),
                    new LinearLayout.LayoutParams(0, activity.dp(32), 1f));
            final int target = ply;
            row.setOnClickListener(v -> {
                if (errorDialog[0] != null) errorDialog[0].dismiss();
                dismiss();
                source.navigateToPly(target);
            });
            table.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)));
            View divider = new View(activity);
            divider.setBackgroundColor(Color.rgb(225, 227, 224));
            table.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1)));
            count++;
        }
        if (count == 0) {
            TextView empty = new TextView(activity);
            empty.setText("暂无错招");
            empty.setTextSize(14);
            empty.setTextColor(Color.rgb(95, 101, 98));
            empty.setGravity(Gravity.CENTER);
            table.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(60)));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(table, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        errorDialog[0] = new AlertDialog.Builder(activity)
                .setTitle(redSide ? "红方错招" : "黑方错招")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();
        errorDialog[0].show();
    }

    private View buildSideCard(String title, GameReportCalculator.SideReport side, boolean redSide) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        activity.setRoundedBackground(card,
                redSide ? Color.rgb(250, 239, 235) : Color.rgb(239, 241, 240),
                10, redSide ? Color.rgb(222, 181, 170) : Color.rgb(198, 203, 200));
        TextView heading = new TextView(activity);
        heading.setText(title + "  总评 " + formatScore(side.total));
        if (redSide) redHeading = heading;
        else blackHeading = heading;
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(redSide ? Color.rgb(154, 42, 36) : Color.rgb(48, 53, 51));
        card.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView detail = new TextView(activity);
        bindDetail(detail, side, redSide);
        if (redSide) redDetail = detail;
        else blackDetail = detail;
        detail.setTextSize(13);
        detail.setTextColor(Color.rgb(58, 67, 63));
        detail.setLineSpacing(activity.dp(3), 1.05f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = activity.dp(8);
        card.addView(detail, detailLp);
        return card;
    }

    private TextView errorCell(String text, boolean header, int gravity) {
        TextView cell = new TextView(activity);
        cell.setText(text == null ? "" : text);
        cell.setTextSize(header ? 13 : 14);
        cell.setTypeface(Typeface.DEFAULT, header ? Typeface.BOLD : Typeface.NORMAL);
        cell.setTextColor(header ? Color.rgb(42, 65, 54) : Color.rgb(45, 50, 48));
        cell.setGravity(gravity);
        cell.setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4));
        if (header) cell.setBackgroundColor(Color.rgb(235, 238, 233));
        return cell;
    }

    private LinearLayout optionRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private String reportHint() {
        return "百分制招法质量报告 · 阶段 SoftMin τ=" + softMinTau + " · 错招阈值 L > 1000";
    }

    private static String formatScore(double value) {
        return Double.isNaN(value) ? "--" : String.format(Locale.CHINA, "%.1f", value);
    }

    private void clearDialogReferences() {
        dialog = null;
        redHeading = null;
        redDetail = null;
        blackHeading = null;
        blackDetail = null;
    }
}

package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.V68SituationChartView;

import java.util.Collections;
import java.util.List;

/** 局势图页的 View 生命周期与显示刷新；评分、重打分和导航实现由调用方持有。 */
final class SituationPanelController {
    interface Actions {
        void stopRescore();
        void showRescoreOptions();
        void showGameReport();
        void openCorrectionBook();
        void previewPly(int target);
        void navigateToPly(int target);
    }

    static final class Data {
        final boolean rescoring;
        final boolean correctionEnabled;
        final boolean reportComplete;
        final int redAdvantageColor;
        final int blackAdvantageColor;
        final List<Integer> scores;
        final List<Integer> matePlies;
        final int initialScoreRed;
        final boolean redToMoveAtRoot;
        final int totalPly;
        final int currentPly;
        final int endgameRound;
        final List<Integer> errorPlies;
        final String rescoreProgressText;

        Data(boolean rescoring, boolean correctionEnabled, boolean reportComplete,
             int redAdvantageColor, int blackAdvantageColor,
             List<Integer> scores, List<Integer> matePlies, int initialScoreRed,
             boolean redToMoveAtRoot, int totalPly, int currentPly, int endgameRound,
             List<Integer> errorPlies, String rescoreProgressText) {
            this.rescoring = rescoring;
            this.correctionEnabled = correctionEnabled;
            this.reportComplete = reportComplete;
            this.redAdvantageColor = redAdvantageColor;
            this.blackAdvantageColor = blackAdvantageColor;
            this.scores = scores == null ? Collections.<Integer>emptyList() : scores;
            this.matePlies = matePlies == null ? Collections.<Integer>emptyList() : matePlies;
            this.initialScoreRed = initialScoreRed;
            this.redToMoveAtRoot = redToMoveAtRoot;
            this.totalPly = totalPly;
            this.currentPly = currentPly;
            this.endgameRound = endgameRound;
            this.errorPlies = errorPlies == null ? Collections.<Integer>emptyList() : errorPlies;
            this.rescoreProgressText = rescoreProgressText == null ? "" : rescoreProgressText;
        }
    }

    private final MainActivity activity;
    private V68SituationChartView chart;
    private TextView rescoreProgress;
    private Button reportButton;

    SituationPanelController(MainActivity activity) {
        this.activity = activity;
    }

    View build(View navigationBar, Data data, Actions actions) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(navigationBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)));

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        if (data.rescoring) {
            Button stop = activity.compactButton("停止");
            stop.setOnClickListener(v -> actions.stopRescore());
            actionRow.addView(stop, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(28)));
            rescoreProgress = new TextView(activity);
            rescoreProgress.setTextSize(10);
            rescoreProgress.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            rescoreProgress.setTextColor(Color.rgb(55, 70, 62));
            rescoreProgress.setGravity(Gravity.CENTER);
            rescoreProgress.setSingleLine(true);
            rescoreProgress.setText(data.rescoreProgressText);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            progressLp.leftMargin = activity.dp(4);
            progressLp.rightMargin = activity.dp(4);
            actionRow.addView(rescoreProgress, progressLp);
        } else {
            rescoreProgress = null;
            Button rescore = activity.surfaceActionButton("重新打分");
            rescore.setOnClickListener(v -> actions.showRescoreOptions());
            actionRow.addView(rescore, new LinearLayout.LayoutParams(activity.dp(66), activity.dp(28)));
            reportButton = activity.surfaceActionButton("报告");
            reportButton.setOnClickListener(v -> actions.showGameReport());
            setReportAvailable(data.reportComplete);
            LinearLayout.LayoutParams reportLp = new LinearLayout.LayoutParams(activity.dp(48), activity.dp(28));
            reportLp.leftMargin = activity.dp(4);
            actionRow.addView(reportButton, reportLp);
            Button correction = activity.surfaceActionButton("错");
            correction.setEnabled(data.correctionEnabled);
            correction.setOnClickListener(v -> actions.openCorrectionBook());
            LinearLayout.LayoutParams correctionLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(28));
            correctionLp.leftMargin = activity.dp(4);
            actionRow.addView(correction, correctionLp);
            actionRow.addView(new View(activity), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)));

        chart = new V68SituationChartView(activity);
        chart.setAdvantageColors(data.redAdvantageColor, data.blackAdvantageColor);
        activity.keepNestedScrollGestures(chart);
        chart.setListener(new V68SituationChartView.Listener() {
            @Override public void onPreviewPly(int target) {
                actions.previewPly(target);
            }

            @Override public void onNavigateToPly(int target) {
                actions.navigateToPly(target);
            }
        });
        applyChartData(data);
        root.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    void refresh(Data data) {
        if (chart == null) return;
        setReportAvailable(data.reportComplete);
        applyChartData(data);
    }

    boolean hasChart() {
        return chart != null;
    }

    void updateRescoreProgress(String text) {
        if (rescoreProgress != null) rescoreProgress.setText(text == null ? "" : text);
    }

    void updateAdvantageColors(int redColor, int blackColor) {
        if (chart != null) chart.setAdvantageColors(redColor, blackColor);
    }

    void clearViewReferences() {
        chart = null;
        rescoreProgress = null;
        reportButton = null;
    }

    void setReportAvailable(boolean complete) {
        if (reportButton == null) return;
        reportButton.setEnabled(complete);
        activity.setRoundedBackground(reportButton, activity.globalSurfaceFillColor(), 7,
                Color.TRANSPARENT);
        reportButton.setTextColor(complete ? activity.globalBackgroundTextColor()
                : Color.rgb(145, 150, 147));
    }

    private void applyChartData(Data data) {
        if (chart == null) return;
        chart.setAdvantageColors(data.redAdvantageColor, data.blackAdvantageColor);
        chart.setData(data.scores, data.matePlies, data.initialScoreRed,
                data.redToMoveAtRoot, data.totalPly, data.currentPly,
                data.endgameRound, data.errorPlies);
    }
}

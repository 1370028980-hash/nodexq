package com.tyl.xiangqi.ndxq;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.SegmentedDifficultyView;

/** 首页与评测入口的 View 构建；模式切换、留存恢复仍由 MainActivity 编排。 */
final class LauncherController {
    private LauncherController() {}
    private static final int HOMEPAGE_BUTTON_STROKE =
            android.graphics.Color.rgb(220, 222, 217);

    private static LinearLayout.LayoutParams launcherEntryLp(MainActivity activity,
                                                               int height, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(height));
        lp.topMargin = activity.dp(topMargin);
        return lp;
    }

    private static Button launcherButton(MainActivity activity, CharSequence text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        activity.markLauncherTextBackground(button, MainActivity.LAUNCHER_TEXT_BG_HOME);
        button.setAllCaps(false);
        // Theme.Material supplies a tinted/inset drawable. Clear it so the
        // homepage shape is drawn edge-to-edge by our own GradientDrawable.
        button.setBackgroundTintList(null);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        activity.setRoundedBackground(button, activity.homepageButtonColor(), 14,
                HOMEPAGE_BUTTON_STROKE);
        return button;
    }

    private static Button launcherSmallButton(MainActivity activity, String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(10);
        activity.markLauncherTextBackground(button,
                MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE);
        button.setAllCaps(false);
        button.setBackgroundTintList(null);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        activity.setRoundedBackground(button, activity.homepageButtonColor(), 7,
                HOMEPAGE_BUTTON_STROKE);
        return button;
    }

    static void showHome(MainActivity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(activity.dp(24), activity.dp(30), activity.dp(24), activity.dp(28));
        activity.launcherScreenRoot = root;

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView version = new TextView(activity);
        version.setText("节点象棋");
        version.setTextSize(34);
        version.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        version.setGravity(Gravity.CENTER);
        activity.markLauncherTextBackground(version, MainActivity.LAUNCHER_TEXT_BG_GLOBAL);
        root.addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(110)));

        Button evaluation = launcherButton(activity, "评测模式");
        root.addView(evaluation, launcherEntryLp(activity, 54, 0));
        evaluation.setOnClickListener(v -> activity.showEvaluationLauncher());
        Button custom = launcherButton(activity, "自选难度");
        root.addView(custom, launcherEntryLp(activity, 54, 10));
        custom.setOnClickListener(v -> activity.showCustomDifficultyLauncher());
        Button analysisEntry = launcherButton(activity, "分析模式");
        root.addView(analysisEntry, launcherEntryLp(activity, 50, 10));
        analysisEntry.setOnClickListener(v -> activity.handleLauncherAnalysisEntry());
        Button recent = launcherButton(activity, "最近对局");
        root.addView(recent, launcherEntryLp(activity, 50, 10));
        recent.setOnClickListener(v -> activity.showRecentGamesScreen());
        Button settings = launcherButton(activity, "设置");
        root.addView(settings, launcherEntryLp(activity, 50, 10));
        settings.setOnClickListener(v -> activity.showLauncherSettingsMenu());

        activity.appRoot.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.scheduleLauncherTextContrastRefresh(scroll);
        activity.handler.postDelayed(activity::prepareAboutDialog, 80L);
    }

    static void showEvaluation(MainActivity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(24));

        TextView title = activity.sectionTitle("评测模式");
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));
        TextView rating = new TextView(activity);
        rating.setText("我的等级分：" + activity.playerRating);
        rating.setTextSize(22);
        rating.setGravity(Gravity.CENTER);
        rating.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(rating, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(70)));
        TextView note = new TextView(activity);
        note.setText("仅供参考的等级分：特大2550，大师2450，省冠2300，市冠2200，县冠2100，镇冠1950，业93 1900，业91 1750，业7 1500，再低不清楚了。");
        note.setTextSize(13);
        note.setTextColor(com.tyl.xiangqi.ndxq.ui.UiTheme.secondaryTextOnBackground(activity));
        note.setLineSpacing(0f, 1.15f);
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button start = launcherButton(activity,
                activity.evaluationResumeAvailable() ? "继续评测" : "开始评测");
        root.addView(start, launcherEntryLp(activity, MainActivity.LAUNCHER_ACTION_HEIGHT_DP, 14));
        start.setOnClickListener(v -> activity.handleEvaluationLauncherStart());
        Button history = launcherButton(activity, "等级分变动历史");
        root.addView(history, launcherEntryLp(activity, MainActivity.LAUNCHER_ACTION_HEIGHT_DP, 10));
        history.setOnClickListener(v -> activity.showEvaluationRatingHistory());
        Button back = launcherButton(activity, "返回首页");
        root.addView(back, launcherEntryLp(activity, MainActivity.LAUNCHER_ACTION_HEIGHT_DP, 10));
        back.setOnClickListener(v -> activity.showLauncherScreen());

        activity.appRoot.addView(root, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    static void showEvaluationRatingHistory(MainActivity activity) {
        // 历史页是独立页面，先移除评测二级页，避免两个 MATCH_PARENT 页面叠放。
        activity.appRoot.removeAllViews();
        activity.launcherScreenRoot = null;
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(activity.dp(18), activity.dp(24), activity.dp(18), activity.dp(24));
        root.addView(activity.sectionTitle("等级分变动历史"), activity.matchWrap());
        java.util.List<EvaluationRatingHistory.Entry> entries = EvaluationRatingHistory.read(activity);
        if (entries.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("暂无等级分变动记录");
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(120)));
        } else {
            for (int i = entries.size() - 1; i >= 0; i--) {
                EvaluationRatingHistory.Entry e = entries.get(i);
                TextView row = new TextView(activity);
                int delta = e.after - e.before;
                String sign = delta > 0 ? "+" : "";
                row.setText(EvaluationRatingHistory.formatDate(e.time) + "  " + e.result
                        + "\n" + e.before + " → " + e.after + "（" + sign + delta + "）"
                        + "   对手 " + e.opponent + "   K=" + e.k + "   连胜 " + e.streak);
                row.setTextSize(14);
                row.setTextColor(activity.globalBackgroundTextColor());
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
                activity.setRoundedBackground(row, activity.globalSurfaceFillColor(), 8,
                        android.graphics.Color.TRANSPARENT);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(66));
                lp.bottomMargin = activity.dp(8);
                root.addView(row, lp);
            }
        }
        Button back = launcherButton(activity, "返回评测");
        root.addView(back, launcherEntryLp(activity, MainActivity.LAUNCHER_ACTION_HEIGHT_DP, 10));
        back.setOnClickListener(v -> activity.showEvaluationLauncher());
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        activity.appRoot.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    static void showCustomDifficulty(MainActivity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(activity.dp(24), activity.dp(30), activity.dp(24), activity.dp(28));
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        activity.launcherScreenRoot = root;
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                activity.scheduleLauncherTextContrastRefresh(v));

        root.addView(activity.sectionTitle("引擎执子"), activity.matchWrap());
        LinearLayout sideSwitch = new LinearLayout(activity);
        sideSwitch.setOrientation(LinearLayout.HORIZONTAL);
        sideSwitch.setPadding(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2));
        activity.setRoundedBackground(sideSwitch, activity.homepageButtonColor(), 13,
                android.graphics.Color.TRANSPARENT);
        TextView red = activity.selectorOption("引擎执红");
        TextView black = activity.selectorOption("引擎执黑");
        sideSwitch.addView(red, new LinearLayout.LayoutParams(0, activity.dp(48), 1f));
        sideSwitch.addView(black, new LinearLayout.LayoutParams(0, activity.dp(48), 1f));
        root.addView(sideSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(52)));
        Runnable refreshSide = () -> {
            activity.styleSelectorOption(red, activity.enginePlaysRed);
            activity.styleSelectorOption(black, !activity.enginePlaysRed);
        };
        red.setOnClickListener(v -> {
            activity.setCustomEnginePlaysRed(true);
            refreshSide.run();
        });
        black.setOnClickListener(v -> {
            activity.setCustomEnginePlaysRed(false);
            refreshSide.run();
        });
        refreshSide.run();

        LinearLayout difficultyTitleRow = new LinearLayout(activity);
        difficultyTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        difficultyTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView difficultyTitle = activity.sectionTitle("引擎难度");
        difficultyTitleRow.addView(difficultyTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        activity.launcherRatingText = new TextView(activity);
        activity.launcherRatingText.setTextSize(13);
        activity.launcherRatingText.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        activity.launcherRatingText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        activity.markLauncherTextBackground(activity.launcherRatingText,
                MainActivity.LAUNCHER_TEXT_BG_GLOBAL);
        difficultyTitleRow.addView(activity.launcherRatingText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams difficultyTitleLp = activity.matchWrap();
        difficultyTitleLp.topMargin = activity.dp(22);
        root.addView(difficultyTitleRow, difficultyTitleLp);
        activity.updateLauncherRatingText();
        activity.launcherRatingText.setVisibility(android.view.View.GONE);

        SegmentedDifficultyView difficulty = new SegmentedDifficultyView(activity);
        activity.launcherDifficultyView = difficulty;
        difficulty.setLabels(activity.customDifficultyDisplayLabels());
        difficulty.setDisplayNumbers(activity.customDifficultyDisplayNumbers());
        difficulty.setSelectedIndex(activity.customDifficultyPosition(activity.selectedDifficultyIndex));
        difficulty.setListener(activity::selectCustomDifficultyPosition);
        root.addView(difficulty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(120)));

        root.addView(buildLauncherStatsRow(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));
        activity.updateLauncherStatsText();

        Button start = launcherButton(activity, "开始对弈");
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(MainActivity.LAUNCHER_ACTION_HEIGHT_DP));
        startLp.topMargin = activity.dp(10);
        root.addView(start, startLp);
        start.setOnClickListener(v -> activity.handleLauncherStartGame());

        Button random = launcherButton(activity, "随机分配");
        LinearLayout.LayoutParams randomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(MainActivity.LAUNCHER_ACTION_HEIGHT_DP));
        randomLp.topMargin = activity.dp(10);
        root.addView(random, randomLp);
        random.setOnClickListener(v -> {
            activity.randomizeCustomDifficulty();
            refreshSide.run();
        });

        Button names = launcherButton(activity, "难度名自定义");
        LinearLayout.LayoutParams namesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(MainActivity.LAUNCHER_ACTION_HEIGHT_DP));
        namesLp.topMargin = activity.dp(10);
        root.addView(names, namesLp);
        names.setOnClickListener(v -> activity.showDifficultyNameSettingsDialog());

        Button back = launcherButton(activity, "返回首页");
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(MainActivity.LAUNCHER_ACTION_HEIGHT_DP));
        backLp.topMargin = activity.dp(10);
        root.addView(back, backLp);
        back.setOnClickListener(v -> activity.showLauncherScreen());

        activity.appRoot.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.scheduleLauncherTextContrastRefresh(scroll);
        activity.handler.postDelayed(activity::prepareAboutDialog, 80L);
    }

    private static android.view.View buildLauncherStatsRow(MainActivity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(5), activity.dp(4), activity.dp(5), activity.dp(4));
        activity.setRoundedBackground(row, activity.homepageButtonColor(), 10,
                android.graphics.Color.TRANSPARENT);

        TextView title = new TextView(activity);
        title.setText("历史战绩");
        title.setTextSize(11);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        activity.markLauncherTextBackground(title, MainActivity.LAUNCHER_TEXT_BG_HOME);
        title.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(activity.dp(62),
                ViewGroup.LayoutParams.MATCH_PARENT));

        activity.launcherStatsText = new TextView(activity);
        activity.launcherStatsText.setTextSize(15);
        activity.launcherStatsText.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        activity.markLauncherTextBackground(activity.launcherStatsText,
                MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE);
        activity.launcherStatsText.setGravity(Gravity.CENTER);
        activity.setRoundedBackground(activity.launcherStatsText, activity.homepageButtonColor(), 7,
                android.graphics.Color.TRANSPARENT);
        row.addView(activity.launcherStatsText, new LinearLayout.LayoutParams(0, activity.dp(32), 1f));

        Button reset = launcherSmallButton(activity, "重置战绩");
        reset.setTextSize(10);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(activity.dp(72), activity.dp(32));
        resetLp.leftMargin = activity.dp(6);
        row.addView(reset, resetLp);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(activity)
                .setMessage("确定重置战绩吗？")
                .setPositiveButton("确认", (d, w) -> activity.resetAllDifficultyStats())
                .setNegativeButton("取消", null)
                .show());
        return row;
    }
}

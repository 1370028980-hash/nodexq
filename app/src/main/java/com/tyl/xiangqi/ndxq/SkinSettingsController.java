package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;
import com.tyl.xiangqi.ndxq.ui.UiTheme;

import java.util.List;

/** 皮肤、透明度与分析配色设置页面。 */
final class SkinSettingsController {
    private SkinSettingsController() {}

    static void show(MainActivity activity) {
        final boolean storageReady = activity.ensureNodeStorageReady(false);
        final List<String> skins = activity.listAvailableSkinNames();
        int initialIndex = skins.indexOf(activity.currentSkinName);
        if (initialIndex < 0) initialIndex = 0;
        final String[] selectedSkin = new String[]{skins.get(initialIndex)};
        final int[] selectedSize = new int[]{activity.clamp(activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(activity.skinPieceSizeKey(selectedSkin[0]), 96), 55, 125)};
        final float[][] selectedGrid = new float[][]{activity.loadSkinGrid(selectedSkin[0])};
        final int[] selectedBackground = new int[]{UiTheme.backgroundRgb(activity)};
        final int[] selectedBackgroundAlpha = new int[]{UiTheme.backgroundAlphaPercent(activity)};
        final int[] selectedHighlight = new int[]{UiTheme.highlightRgb(activity)};
        final int[] selectedHighlightAlpha = new int[]{UiTheme.highlightAlphaPercent(activity)};
        final int[] selectedHomeButton = new int[]{UiTheme.homeButtonRgb(activity)};
        final int[] selectedHomeButtonAlpha = new int[]{UiTheme.homeButtonAlphaPercent(activity)};
        final int[] selectedRedArrow = new int[]{activity.redArrowColor};
        final int[] selectedBlackArrow = new int[]{activity.blackArrowColor};
        final int[] selectedSituationRed = new int[]{activity.situationRedAdvantageColor};
        final int[] selectedSituationBlack = new int[]{activity.situationBlackAdvantageColor};
        SharedPreferences colorPrefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        final int[] selectedEngineAdvantage = new int[]{colorPrefs.getInt(
                EngineAnalysisPanel.KEY_ADVANTAGE_COLOR, EngineAnalysisPanel.COLOR_AUTO)};
        final int[] selectedEngineDisadvantage = new int[]{colorPrefs.getInt(
                EngineAnalysisPanel.KEY_DISADVANTAGE_COLOR, EngineAnalysisPanel.COLOR_AUTO)};
        final int[] selectedEngineRedMove = new int[]{colorPrefs.getInt(
                EngineAnalysisPanel.KEY_RED_MOVE_COLOR, EngineAnalysisPanel.COLOR_AUTO)};
        final int[] selectedEngineBlackMove = new int[]{colorPrefs.getInt(
                EngineAnalysisPanel.KEY_BLACK_MOVE_COLOR, EngineAnalysisPanel.COLOR_AUTO)};

        ScrollView scroll = new ScrollView(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8));
        scroll.addView(panel);

        TextView skinExplain = new TextView(activity);
        skinExplain.setText("default 为内置皮肤。新增皮肤请放到 /storage/emulated/0/nodexq/pic/<皮肤名>/，棋盘文件名为 board，棋子图片使用 br、bn、bb、ba、bk、bc、bp、rr、rn、rb、ra、rk、rc、rp，支持 PNG、WEBP、JPG；可选 back 图片作为全局背景。");
        skinExplain.setTextSize(10);
        skinExplain.setTextColor(Color.rgb(105, 110, 106));
        skinExplain.setPadding(0, 0, 0, activity.dp(5));
        panel.addView(skinExplain, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout chooseRow = new LinearLayout(activity);
        chooseRow.setOrientation(LinearLayout.HORIZONTAL);
        chooseRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView chooseLabel = new TextView(activity);
        chooseLabel.setText("选择皮肤");
        chooseLabel.setTextSize(13);
        chooseRow.addView(chooseLabel, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(40)));
        Spinner skinSpinner = new Spinner(activity);
        skinSpinner.setAdapter(new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_dropdown_item, skins));
        skinSpinner.setSelection(initialIndex);
        chooseRow.addView(skinSpinner, new LinearLayout.LayoutParams(0, activity.dp(40), 1f));
        panel.addView(chooseRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        if (!storageReady) {
            TextView permissionHint = new TextView(activity);
            permissionHint.setText("默认皮肤已内置并可直接使用。当前未授予文件访问权限，因此暂不显示 /nodexq/pic 中的用户皮肤；授权后重新打开本页面即可选择。");
            permissionHint.setTextSize(11);
            permissionHint.setTextColor(Color.rgb(132, 88, 36));
            permissionHint.setPadding(0, activity.dp(2), 0, activity.dp(5));
            panel.addView(permissionHint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Button grant = activity.compactButton("授权读取外部皮肤");
            grant.setOnClickListener(v -> activity.requestNodeStorageAccess());
            panel.addView(grant, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(34)));
        }

        // V19.9：皮肤设置本体只保留入口；网格、恢复、方向键、步长和说明全部移到临时校准页。
        Button calibrationEntry = activity.compactButton("开始动态校准");
        LinearLayout.LayoutParams calibrationEntryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38));
        calibrationEntryLp.topMargin = activity.dp(5);
        calibrationEntryLp.bottomMargin = activity.dp(8);
        panel.addView(calibrationEntry, calibrationEntryLp);
        calibrationEntry.setOnClickListener(v -> activity.showDynamicCalibrationPage(
                selectedSkin[0], selectedSize[0], selectedGrid[0],
                (corners, size) -> {
                    selectedGrid[0] = corners;
                    selectedSize[0] = size;
                }));

        LinearLayout backgroundRow = new LinearLayout(activity);
        backgroundRow.setOrientation(LinearLayout.HORIZONTAL);
        backgroundRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView backgroundLabel = new TextView(activity);
        backgroundLabel.setText("全局底色");
        backgroundLabel.setTextSize(13);
        backgroundRow.addView(backgroundLabel, new LinearLayout.LayoutParams(
                activity.dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button backgroundButton = new Button(activity);
        backgroundButton.setAllCaps(false);
        backgroundButton.setTextSize(12);
        backgroundButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(backgroundButton, selectedBackground[0]);
        backgroundButton.setOnClickListener(v -> activity.showColorPicker(
                "选择全局底色", selectedBackground[0], color -> {
                    selectedBackground[0] = color;
                    activity.styleHighlightColorButton(backgroundButton, color);
                }));
        backgroundRow.addView(backgroundButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(backgroundRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView backgroundAlphaLabel = new TextView(activity);
        backgroundAlphaLabel.setText("全局底色透明度: " + selectedBackgroundAlpha[0] + "%");
        backgroundAlphaLabel.setTextSize(12);
        backgroundAlphaLabel.setPadding(0, activity.dp(3), 0, 0);
        panel.addView(backgroundAlphaLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar backgroundAlphaSlider = new SeekBar(activity);
        backgroundAlphaSlider.setMax(100);
        backgroundAlphaSlider.setProgress(selectedBackgroundAlpha[0]);
        backgroundAlphaSlider.setContentDescription("全局底色透明度");
        backgroundAlphaSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedBackgroundAlpha[0] = activity.clamp(progress, 0, 100);
                backgroundAlphaLabel.setText("全局底色透明度: "
                        + selectedBackgroundAlpha[0] + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(backgroundAlphaSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38)));

        LinearLayout colorRow = new LinearLayout(activity);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView colorLabel = new TextView(activity);
        colorLabel.setText("全局高亮色");
        colorLabel.setTextSize(13);
        colorRow.addView(colorLabel, new LinearLayout.LayoutParams(
                activity.dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button colorButton = new Button(activity);
        colorButton.setAllCaps(false);
        colorButton.setTextSize(12);
        colorButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(colorButton, selectedHighlight[0]);
        colorButton.setOnClickListener(v -> activity.showColorPicker(
                "选择全局高亮色", selectedHighlight[0], color -> {
                    selectedHighlight[0] = color;
                    activity.styleHighlightColorButton(colorButton, color);
                }));
        colorRow.addView(colorButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(colorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView alphaLabel = new TextView(activity);
        alphaLabel.setText("全局高亮透明度: " + selectedHighlightAlpha[0] + "%");
        alphaLabel.setTextSize(12);
        alphaLabel.setPadding(0, activity.dp(3), 0, 0);
        panel.addView(alphaLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar highlightAlphaSlider = new SeekBar(activity);
        highlightAlphaSlider.setMax(100);
        highlightAlphaSlider.setProgress(selectedHighlightAlpha[0]);
        highlightAlphaSlider.setContentDescription("全局高亮透明度");
        highlightAlphaSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedHighlightAlpha[0] = activity.clamp(progress, 0, 100);
                alphaLabel.setText("全局高亮透明度: " + selectedHighlightAlpha[0] + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(highlightAlphaSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38)));

        LinearLayout homeButtonRow = new LinearLayout(activity);
        homeButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        homeButtonRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView homeButtonLabel = new TextView(activity);
        homeButtonLabel.setText("首页按钮色");
        homeButtonLabel.setTextSize(13);
        homeButtonRow.addView(homeButtonLabel, new LinearLayout.LayoutParams(
                activity.dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button homeButtonColorButton = new Button(activity);
        homeButtonColorButton.setAllCaps(false);
        homeButtonColorButton.setTextSize(12);
        homeButtonColorButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(homeButtonColorButton, selectedHomeButton[0]);
        homeButtonColorButton.setOnClickListener(v -> activity.showColorPicker(
                "选择首页按钮色", selectedHomeButton[0], color -> {
                    selectedHomeButton[0] = color;
                    activity.styleHighlightColorButton(homeButtonColorButton, color);
                }));
        homeButtonRow.addView(homeButtonColorButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(homeButtonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView homeButtonAlphaLabel = new TextView(activity);
        homeButtonAlphaLabel.setText("首页按钮透明度: " + selectedHomeButtonAlpha[0] + "%");
        homeButtonAlphaLabel.setTextSize(12);
        homeButtonAlphaLabel.setPadding(0, activity.dp(3), 0, 0);
        panel.addView(homeButtonAlphaLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar homeButtonAlphaSlider = new SeekBar(activity);
        homeButtonAlphaSlider.setMax(100);
        homeButtonAlphaSlider.setProgress(selectedHomeButtonAlpha[0]);
        homeButtonAlphaSlider.setContentDescription("首页按钮透明度");
        homeButtonAlphaSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedHomeButtonAlpha[0] = activity.clamp(progress, 0, 100);
                homeButtonAlphaLabel.setText("首页按钮透明度: "
                        + selectedHomeButtonAlpha[0] + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(homeButtonAlphaSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38)));

        TextView arrowTitle = new TextView(activity);
        arrowTitle.setText("红黑双方箭头颜色");
        arrowTitle.setTextSize(13);
        arrowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(arrowTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(28)));

        LinearLayout redArrowRow = new LinearLayout(activity);
        redArrowRow.setOrientation(LinearLayout.HORIZONTAL);
        redArrowRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView redArrowLabel = new TextView(activity);
        redArrowLabel.setText("红方箭头");
        redArrowLabel.setTextSize(13);
        redArrowRow.addView(redArrowLabel, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(40)));
        Button redArrowButton = new Button(activity);
        redArrowButton.setAllCaps(false);
        redArrowButton.setTextSize(12);
        redArrowButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(redArrowButton, selectedRedArrow[0]);
        redArrowButton.setOnClickListener(v -> activity.showColorPicker(
                "选择红方箭头颜色", selectedRedArrow[0], color -> {
                    selectedRedArrow[0] = color;
                    activity.styleHighlightColorButton(redArrowButton, color);
                }));
        redArrowRow.addView(redArrowButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(redArrowRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout blackArrowRow = new LinearLayout(activity);
        blackArrowRow.setOrientation(LinearLayout.HORIZONTAL);
        blackArrowRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView blackArrowLabel = new TextView(activity);
        blackArrowLabel.setText("黑方箭头");
        blackArrowLabel.setTextSize(13);
        blackArrowRow.addView(blackArrowLabel, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(40)));
        Button blackArrowButton = new Button(activity);
        blackArrowButton.setAllCaps(false);
        blackArrowButton.setTextSize(12);
        blackArrowButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(blackArrowButton, selectedBlackArrow[0]);
        blackArrowButton.setOnClickListener(v -> activity.showColorPicker(
                "选择黑方箭头颜色", selectedBlackArrow[0], color -> {
                    selectedBlackArrow[0] = color;
                    activity.styleHighlightColorButton(blackArrowButton, color);
                }));
        blackArrowRow.addView(blackArrowButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(blackArrowRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        TextView engineTextTitle = new TextView(activity);
        engineTextTitle.setText("引擎分析文字颜色");
        engineTextTitle.setTextSize(13);
        engineTextTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(engineTextTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(28)));

        LinearLayout advantageRow = new LinearLayout(activity);
        advantageRow.setOrientation(LinearLayout.HORIZONTAL);
        advantageRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView advantageLabel = new TextView(activity);
        advantageLabel.setText("己方优势信息");
        advantageLabel.setTextSize(13);
        advantageRow.addView(advantageLabel, new LinearLayout.LayoutParams(activity.dp(92), activity.dp(40)));
        Button advantageButton = new Button(activity);
        advantageButton.setAllCaps(false);
        advantageButton.setTextSize(11);
        advantageButton.setPadding(0, 0, 0, 0);
        activity.styleEngineColorButton(advantageButton, selectedEngineAdvantage[0],
                EngineAnalysisPanel.automaticAdvantageColor(activity));
        advantageButton.setOnClickListener(v -> {
            int initial = selectedEngineAdvantage[0] == EngineAnalysisPanel.COLOR_AUTO
                    ? EngineAnalysisPanel.automaticAdvantageColor(activity)
                    : selectedEngineAdvantage[0];
            activity.showColorPicker("选择己方优势信息颜色", initial, color -> {
                selectedEngineAdvantage[0] = color;
                activity.styleEngineColorButton(advantageButton, color,
                        EngineAnalysisPanel.automaticAdvantageColor(activity));
            });
        });
        advantageRow.addView(advantageButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(advantageRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout disadvantageRow = new LinearLayout(activity);
        disadvantageRow.setOrientation(LinearLayout.HORIZONTAL);
        disadvantageRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView disadvantageLabel = new TextView(activity);
        disadvantageLabel.setText("己方劣势信息");
        disadvantageLabel.setTextSize(13);
        disadvantageRow.addView(disadvantageLabel, new LinearLayout.LayoutParams(activity.dp(92), activity.dp(40)));
        Button disadvantageButton = new Button(activity);
        disadvantageButton.setAllCaps(false);
        disadvantageButton.setTextSize(11);
        disadvantageButton.setPadding(0, 0, 0, 0);
        activity.styleEngineColorButton(disadvantageButton, selectedEngineDisadvantage[0],
                EngineAnalysisPanel.automaticDisadvantageColor(activity));
        disadvantageButton.setOnClickListener(v -> {
            int initial = selectedEngineDisadvantage[0] == EngineAnalysisPanel.COLOR_AUTO
                    ? EngineAnalysisPanel.automaticDisadvantageColor(activity)
                    : selectedEngineDisadvantage[0];
            activity.showColorPicker("选择己方劣势信息颜色", initial, color -> {
                selectedEngineDisadvantage[0] = color;
                activity.styleEngineColorButton(disadvantageButton, color,
                        EngineAnalysisPanel.automaticDisadvantageColor(activity));
            });
        });
        disadvantageRow.addView(disadvantageButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(disadvantageRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout redMoveRow = new LinearLayout(activity);
        redMoveRow.setOrientation(LinearLayout.HORIZONTAL);
        redMoveRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView redMoveLabel = new TextView(activity);
        redMoveLabel.setText("红方实际着法");
        redMoveLabel.setTextSize(13);
        redMoveRow.addView(redMoveLabel, new LinearLayout.LayoutParams(activity.dp(92), activity.dp(40)));
        Button redMoveButton = new Button(activity);
        redMoveButton.setAllCaps(false);
        redMoveButton.setTextSize(11);
        redMoveButton.setPadding(0, 0, 0, 0);
        activity.styleEngineColorButton(redMoveButton, selectedEngineRedMove[0],
                EngineAnalysisPanel.automaticRedMoveColor(activity));
        redMoveButton.setOnClickListener(v -> {
            int initial = selectedEngineRedMove[0] == EngineAnalysisPanel.COLOR_AUTO
                    ? EngineAnalysisPanel.automaticRedMoveColor(activity)
                    : selectedEngineRedMove[0];
            activity.showColorPicker("选择红方实际着法颜色", initial, color -> {
                selectedEngineRedMove[0] = color;
                activity.styleEngineColorButton(redMoveButton, color,
                        EngineAnalysisPanel.automaticRedMoveColor(activity));
            });
        });
        redMoveRow.addView(redMoveButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(redMoveRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout blackMoveRow = new LinearLayout(activity);
        blackMoveRow.setOrientation(LinearLayout.HORIZONTAL);
        blackMoveRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView blackMoveLabel = new TextView(activity);
        blackMoveLabel.setText("黑方实际着法");
        blackMoveLabel.setTextSize(13);
        blackMoveRow.addView(blackMoveLabel, new LinearLayout.LayoutParams(activity.dp(92), activity.dp(40)));
        Button blackMoveButton = new Button(activity);
        blackMoveButton.setAllCaps(false);
        blackMoveButton.setTextSize(11);
        blackMoveButton.setPadding(0, 0, 0, 0);
        activity.styleEngineColorButton(blackMoveButton, selectedEngineBlackMove[0],
                EngineAnalysisPanel.automaticBlackMoveColor(activity));
        blackMoveButton.setOnClickListener(v -> {
            int initial = selectedEngineBlackMove[0] == EngineAnalysisPanel.COLOR_AUTO
                    ? EngineAnalysisPanel.automaticBlackMoveColor(activity)
                    : selectedEngineBlackMove[0];
            activity.showColorPicker("选择黑方实际着法颜色", initial, color -> {
                selectedEngineBlackMove[0] = color;
                activity.styleEngineColorButton(blackMoveButton, color,
                        EngineAnalysisPanel.automaticBlackMoveColor(activity));
            });
        });
        blackMoveRow.addView(blackMoveButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(blackMoveRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        TextView situationTitle = new TextView(activity);
        situationTitle.setText("局势图优势线条颜色");
        situationTitle.setTextSize(13);
        situationTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(situationTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(28)));

        LinearLayout situationRedRow = new LinearLayout(activity);
        situationRedRow.setOrientation(LinearLayout.HORIZONTAL);
        situationRedRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView situationRedLabel = new TextView(activity);
        situationRedLabel.setText("红优线条");
        situationRedLabel.setTextSize(13);
        situationRedRow.addView(situationRedLabel, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(40)));
        Button situationRedButton = new Button(activity);
        situationRedButton.setAllCaps(false);
        situationRedButton.setTextSize(12);
        situationRedButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(situationRedButton, selectedSituationRed[0]);
        situationRedButton.setOnClickListener(v -> activity.showColorPicker(
                "选择局势图红优线条颜色", selectedSituationRed[0], color -> {
                    selectedSituationRed[0] = color;
                    activity.styleHighlightColorButton(situationRedButton, color);
                }));
        situationRedRow.addView(situationRedButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(situationRedRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout situationBlackRow = new LinearLayout(activity);
        situationBlackRow.setOrientation(LinearLayout.HORIZONTAL);
        situationBlackRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView situationBlackLabel = new TextView(activity);
        situationBlackLabel.setText("黑优线条");
        situationBlackLabel.setTextSize(13);
        situationBlackRow.addView(situationBlackLabel, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(40)));
        Button situationBlackButton = new Button(activity);
        situationBlackButton.setAllCaps(false);
        situationBlackButton.setTextSize(12);
        situationBlackButton.setPadding(0, 0, 0, 0);
        activity.styleHighlightColorButton(situationBlackButton, selectedSituationBlack[0]);
        situationBlackButton.setOnClickListener(v -> activity.showColorPicker(
                "选择局势图黑优线条颜色", selectedSituationBlack[0], color -> {
                    selectedSituationBlack[0] = color;
                    activity.styleHighlightColorButton(situationBlackButton, color);
                }));
        situationBlackRow.addView(situationBlackButton, new LinearLayout.LayoutParams(0, activity.dp(36), 1f));
        panel.addView(situationBlackRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        Button restoreDefaultColors = activity.compactButton("恢复默认色值与透明度");
        restoreDefaultColors.setOnClickListener(v -> {
            selectedBackground[0] = UiTheme.DEFAULT_BACKGROUND_COLOR;
            selectedBackgroundAlpha[0] = UiTheme.DEFAULT_BACKGROUND_ALPHA;
            selectedHighlight[0] = UiTheme.DEFAULT_HIGHLIGHT_COLOR;
            selectedHighlightAlpha[0] = UiTheme.DEFAULT_HIGHLIGHT_ALPHA;
            selectedHomeButton[0] = UiTheme.DEFAULT_HOME_BUTTON_COLOR;
            selectedHomeButtonAlpha[0] = UiTheme.DEFAULT_HOME_BUTTON_ALPHA;
            selectedRedArrow[0] = MainActivity.DEFAULT_RED_ARROW_COLOR;
            selectedBlackArrow[0] = MainActivity.DEFAULT_BLACK_ARROW_COLOR;
            selectedSituationRed[0] = MainActivity.DEFAULT_SITUATION_RED_COLOR;
            selectedSituationBlack[0] = MainActivity.DEFAULT_SITUATION_BLACK_COLOR;
            selectedEngineAdvantage[0] = EngineAnalysisPanel.COLOR_AUTO;
            selectedEngineDisadvantage[0] = EngineAnalysisPanel.COLOR_AUTO;
            selectedEngineRedMove[0] = EngineAnalysisPanel.COLOR_AUTO;
            selectedEngineBlackMove[0] = EngineAnalysisPanel.COLOR_AUTO;

            activity.styleHighlightColorButton(backgroundButton, selectedBackground[0]);
            backgroundAlphaSlider.setProgress(selectedBackgroundAlpha[0]);
            backgroundAlphaLabel.setText("全局底色透明度: "
                    + selectedBackgroundAlpha[0] + "%");
            activity.styleHighlightColorButton(colorButton, selectedHighlight[0]);
            highlightAlphaSlider.setProgress(selectedHighlightAlpha[0]);
            alphaLabel.setText("全局高亮透明度: "
                    + selectedHighlightAlpha[0] + "%");
            activity.styleHighlightColorButton(homeButtonColorButton, selectedHomeButton[0]);
            homeButtonAlphaSlider.setProgress(selectedHomeButtonAlpha[0]);
            homeButtonAlphaLabel.setText("首页按钮透明度: "
                    + selectedHomeButtonAlpha[0] + "%");
            activity.styleHighlightColorButton(redArrowButton, selectedRedArrow[0]);
            activity.styleHighlightColorButton(blackArrowButton, selectedBlackArrow[0]);
            activity.styleHighlightColorButton(situationRedButton, selectedSituationRed[0]);
            activity.styleHighlightColorButton(situationBlackButton, selectedSituationBlack[0]);
            activity.styleEngineColorButton(advantageButton, selectedEngineAdvantage[0],
                    EngineAnalysisPanel.automaticAdvantageColor(activity));
            activity.styleEngineColorButton(disadvantageButton, selectedEngineDisadvantage[0],
                    EngineAnalysisPanel.automaticDisadvantageColor(activity));
            activity.styleEngineColorButton(redMoveButton, selectedEngineRedMove[0],
                    EngineAnalysisPanel.automaticRedMoveColor(activity));
            activity.styleEngineColorButton(blackMoveButton, selectedEngineBlackMove[0],
                    EngineAnalysisPanel.automaticBlackMoveColor(activity));
        });
        LinearLayout.LayoutParams restoreColorsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(36));
        restoreColorsLp.topMargin = activity.dp(5);
        restoreColorsLp.bottomMargin = activity.dp(3);
        panel.addView(restoreDefaultColors, restoreColorsLp);

        TextView defaultColorHint = new TextView(activity);
        defaultColorHint.setText("默认：底色 #F6F4EE / 100%；高亮 #2A5C46 / 100%；首页按钮 #EFEFE9 / 100%；红箭头 #D62828；黑箭头 #2864D6；红优 #EE2A2A；黑优 #1966EB。引擎优势/劣势信息行与红黑实际着法恢复后使用原有自适应配色逻辑。");
        defaultColorHint.setTextSize(10);
        defaultColorHint.setTextColor(Color.rgb(105, 110, 106));
        defaultColorHint.setPadding(0, 0, 0, activity.dp(4));
        panel.addView(defaultColorHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        skinSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                if (position < 0 || position >= skins.size()) return;
                selectedSkin[0] = skins.get(position);
                selectedSize[0] = activity.clamp(activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .getInt(activity.skinPieceSizeKey(selectedSkin[0]), 96), 55, 125);
                selectedGrid[0] = activity.loadSkinGrid(selectedSkin[0]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(activity)
                .setTitle("皮肤设置")
                .setView(scroll)
                .setPositiveButton("保存", (d, which) -> {
                    activity.currentSkinName = activity.sanitizeSkinName(selectedSkin[0]);
                    activity.currentSkinPieceSizePercent = activity.clamp(selectedSize[0], 55, 125);
                    activity.redArrowColor = selectedRedArrow[0];
                    activity.blackArrowColor = selectedBlackArrow[0];
                    activity.situationRedAdvantageColor = selectedSituationRed[0];
                    activity.situationBlackAdvantageColor = selectedSituationBlack[0];

                    SharedPreferences.Editor editor = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                            .putString("skin_name", activity.currentSkinName)
                            .putInt(activity.skinPieceSizeKey(activity.currentSkinName),
                                    activity.currentSkinPieceSizePercent)
                            .putInt(UiTheme.KEY_BACKGROUND_COLOR, selectedBackground[0])
                            .putInt(UiTheme.KEY_BACKGROUND_ALPHA, selectedBackgroundAlpha[0])
                            .putInt(UiTheme.KEY_HIGHLIGHT_COLOR, selectedHighlight[0])
                            .putInt(UiTheme.KEY_HIGHLIGHT_ALPHA, selectedHighlightAlpha[0])
                            .putInt(UiTheme.KEY_HOME_BUTTON_COLOR, selectedHomeButton[0])
                            .putInt(UiTheme.KEY_HOME_BUTTON_ALPHA, selectedHomeButtonAlpha[0])
                            .putInt(MainActivity.PREF_RED_ARROW_COLOR, activity.redArrowColor)
                            .putInt(MainActivity.PREF_BLACK_ARROW_COLOR, activity.blackArrowColor)
                            .putInt(MainActivity.PREF_SITUATION_RED_COLOR,
                                    activity.situationRedAdvantageColor)
                            .putInt(MainActivity.PREF_SITUATION_BLACK_COLOR,
                                    activity.situationBlackAdvantageColor);
                    activity.putOptionalColor(editor, EngineAnalysisPanel.KEY_ADVANTAGE_COLOR,
                            selectedEngineAdvantage[0]);
                    activity.putOptionalColor(editor, EngineAnalysisPanel.KEY_DISADVANTAGE_COLOR,
                            selectedEngineDisadvantage[0]);
                    activity.putOptionalColor(editor, EngineAnalysisPanel.KEY_RED_MOVE_COLOR,
                            selectedEngineRedMove[0]);
                    activity.putOptionalColor(editor, EngineAnalysisPanel.KEY_BLACK_MOVE_COLOR,
                            selectedEngineBlackMove[0]);
                    activity.saveSkinGrid(editor, activity.currentSkinName, selectedGrid[0]);
                    editor.apply();

                    if (activity.boardView != null) activity.applyCurrentSkinToBoard(activity.boardView, true);
                    activity.refreshSituationChartColors();
                    if (activity.analysisMode) activity.updateAnalysisArrows();
                    else activity.updateRescoreReviewArrows();
                    activity.refreshEngineAnalysisColorsAfterSkinSave();
                    activity.requestGlobalBackgroundRefresh();
                    activity.refreshGlobalSurfaceTheme();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

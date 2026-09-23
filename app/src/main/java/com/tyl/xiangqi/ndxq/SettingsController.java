package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.ui.UiTheme;

import java.util.Locale;

/** 首页、棋盘页的设置入口，以及日志/关于/偏好类弹窗。 */
final class SettingsController {
    private static final int SECRET_RATING_RESET_TAPS = 10;
    private final MainActivity host;
    private AlertDialog aboutDialog;

    SettingsController(MainActivity host) {
        this.host = host;
    }

    void showLauncherSettingsMenu() {
        TextView title = new TextView(host);
        title.setText("设置");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(42, 65, 54));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(host.dp(20), host.dp(8), host.dp(20), host.dp(8));
        final int[] titleTapCount = new int[]{0};
        title.setOnClickListener(v -> {
            titleTapCount[0]++;
            if (titleTapCount[0] < SECRET_RATING_RESET_TAPS) return;
            titleTapCount[0] = 0;
            showHiddenRatingResetDialog();
        });
        String[] items = new String[]{"日志信息", "皮肤设置", "关于应用"};
        new AlertDialog.Builder(host)
                .setCustomTitle(title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showLogDialog();
                    else if (which == 1) host.showSkinSettingsDialog();
                    else showAboutDialog();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showHiddenRatingResetDialog() {
        new AlertDialog.Builder(host)
                .setMessage("是否重置评测等级分？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialog, which) -> host.resetPlayerRatingToMinimum())
                .show();
    }

    void showPlayerRatingSettingsDialog() {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(4));
        TextView note = new TextView(host);
        note.setText("仅供参考的等级分：特大2550，大师2450，省冠2300，市冠2200，县冠2100，镇冠1950，业93 1900，业91 1750，业7 1500，再低不清楚了。");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(90, 98, 93));
        note.setLineSpacing(0f, 1.12f);
        panel.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setHint("500～4000");
        input.setText(String.valueOf(host.playerRating));
        panel.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(42)));
        CheckBox show = new CheckBox(host);
        show.setText("在首页显示等级分");
        show.setChecked(host.showPlayerRating);
        panel.addView(show, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(42)));
        new AlertDialog.Builder(host).setTitle("我的等级分设置").setView(panel)
                .setPositiveButton("保存", (d, w) -> {
                    int value = 200;
                    try {
                        value = Integer.parseInt(input.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    host.playerRating = Math.max(MainActivity.MIN_PLAYER_RATING,
                            host.clamp(value, MainActivity.MIN_PLAYER_RATING, 4000));
                    host.showPlayerRating = show.isChecked();
                    host.saveLauncherPreferences();
                    host.updateLauncherRatingText();
                }).setNegativeButton("取消", null).show();
    }

    void showGameSettingsMenu() {
        String[] items = new String[]{"日志信息", "引擎设置", "调整棋盘大小"};
        new AlertDialog.Builder(host)
                .setTitle("设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) host.showLogDialog();
                    else if (which == 1) host.showEngineOptionsDialog();
                    else host.showBoardScaleDialog();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    void showDifficultyNameSettingsDialog() {
        ScrollView scroll = new ScrollView(host);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(8));
        scroll.addView(panel);

        TextView hint = new TextView(host);
        hint.setText("“青云”固定不变，留空则为默认名称");
        hint.setTextSize(12);
        hint.setTextColor(UiTheme.secondaryTextOnBackground(host));
        hint.setPadding(0, 0, 0, host.dp(8));
        panel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int[] customIndices = host.customDifficultyIndices();
        final EditText[] inputs = new EditText[customIndices.length - 1];
        for (int i = 0; i < inputs.length; i++) {
            int difficultyIndex = customIndices[i];
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(host);
            label.setText((difficultyIndex + 1) + ". " + host.difficultyDefaultName(difficultyIndex));
            label.setTextSize(12);
            row.addView(label, new LinearLayout.LayoutParams(host.dp(112), host.dp(42)));

            EditText input = new EditText(host);
            input.setSingleLine(true);
            input.setTextSize(13);
            input.setText(host.difficultyDisplayName(difficultyIndex));
            input.setSelectAllOnFocus(true);
            inputs[i] = input;
            row.addView(input, new LinearLayout.LayoutParams(0, host.dp(42), 1f));
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44)));
        }

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("难度名自定义")
                .setView(scroll)
                .setPositiveButton("保存", (d, which) -> {
                    SharedPreferences.Editor editor = host.getSharedPreferences(
                            MainActivity.PREFS, Context.MODE_PRIVATE).edit();
                    for (int i = 0; i < inputs.length; i++) {
                        int difficultyIndex = customIndices[i];
                        String value = inputs[i].getText().toString().trim();
                        if (value.isEmpty() || value.equals(host.difficultyDefaultName(difficultyIndex))) {
                            editor.remove(host.difficultyNameKey(difficultyIndex));
                        } else {
                            editor.putString(host.difficultyNameKey(difficultyIndex), value);
                        }
                    }
                    editor.apply();
                    host.refreshLauncherDifficultyLabels();
                    android.widget.Toast.makeText(host, "难度名称已保存",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("全部恢复默认", (d, which) -> {
                    SharedPreferences.Editor editor = host.getSharedPreferences(
                            MainActivity.PREFS, Context.MODE_PRIVATE).edit();
                    for (int difficultyIndex : customIndices) {
                        editor.remove(host.difficultyNameKey(difficultyIndex));
                    }
                    editor.apply();
                    host.refreshLauncherDifficultyLabels();
                    android.widget.Toast.makeText(host, "难度名称已恢复默认",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    void showManualPlaySettingsDialog() {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(4));
        CheckBox time = new CheckBox(host);
        styleManualLimitCheck(time, "每步时间（秒）");
        CheckBox depth = new CheckBox(host);
        styleManualLimitCheck(depth, "每步深度（层数）");
        CheckBox nodes = new CheckBox(host);
        styleManualLimitCheck(nodes, "每步节点（节点数）");
        time.setChecked(host.manualPlayLimit.moveTimeMs > 0);
        depth.setChecked(host.manualPlayLimit.depth > 0);
        nodes.setChecked(host.manualPlayLimit.nodes > 0);
        EditText timeValue = manualTimeLimitEdit(formatManualSeconds(
                host.manualPlayLimit.moveTimeMs), "秒");
        EditText depthValue = manualLimitEdit(String.valueOf(
                Math.max(1, host.manualPlayLimit.depth)), "层");
        EditText nodesValue = manualLimitEdit(String.valueOf(
                Math.max(1, host.manualPlayLimit.nodes)), "节点");
        addLimitRow(panel, time, timeValue);
        addLimitRow(panel, depth, depthValue);
        addLimitRow(panel, nodes, nodesValue);
        new AlertDialog.Builder(host).setTitle("行棋设置").setView(panel)
                .setPositiveButton("保存", (d, w) -> {
                    boolean any = time.isChecked() || depth.isChecked() || nodes.isChecked();
                    int ms = time.isChecked() ? parsePositiveMilliseconds(timeValue, 2000) : 0;
                    int dep = depth.isChecked() ? Math.max(1, parsePositive(depthValue, 20)) : 0;
                    int nod = nodes.isChecked() ? Math.max(1, parsePositive(nodesValue, 500000)) : 0;
                    if (!any) ms = 100;
                    host.manualPlayLimit = PikafishEngine.SearchLimit.combined(dep, nod, ms);
                    host.setManualPlayLimitConfigured(true);
                    host.saveLauncherPreferences();
                }).setNegativeButton("取消", null).show();
    }

    private EditText manualLimitEdit(String value, String hint) {
        EditText edit = new EditText(host);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(value);
        edit.setHint(hint);
        edit.setSelectAllOnFocus(true);
        return edit;
    }

    private EditText manualTimeLimitEdit(String value, String hint) {
        EditText edit = manualLimitEdit(value, hint);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return edit;
    }

    private String formatManualSeconds(int millis) {
        if (millis <= 0) return "1";
        if (millis % 1000 == 0) return String.valueOf(millis / 1000);
        return String.format(Locale.US, "%.3f", millis / 1000d)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private int parsePositiveMilliseconds(EditText input, int fallbackMs) {
        try {
            double seconds = Double.parseDouble(input.getText().toString().trim());
            if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0d) {
                throw new NumberFormatException();
            }
            return host.clamp((int) Math.round(seconds * 1000d), 1, Integer.MAX_VALUE);
        } catch (Exception ignored) {
            return Math.max(1, fallbackMs);
        }
    }

    private void styleManualLimitCheck(CheckBox check, String label) {
        RadioButton radioStyle = new RadioButton(host);
        check.setButtonDrawable(radioStyle.getButtonDrawable());
        check.setText(label);
        check.setTextSize(13);
        check.setSingleLine(true);
        check.setMinWidth(0);
    }

    private void addLimitRow(LinearLayout panel, CheckBox check, EditText value) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(check, new LinearLayout.LayoutParams(0, host.dp(42), 1f));
        row.addView(value, new LinearLayout.LayoutParams(host.dp(100), host.dp(42)));
        panel.addView(row);
    }

    private int parsePositive(EditText input, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(input.getText().toString().trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    void showLogDialog() {
        ScrollView scroll = new ScrollView(host);
        TextView log = new TextView(host);
        String currentLog = host.logText();
        log.setText(currentLog.length() == 0 ? "暂无日志" : currentLog);
        log.setTextSize(12);
        log.setTextColor(Color.rgb(38, 43, 40));
        log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12));
        scroll.addView(log, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("日志信息")
                .setView(scroll)
                .setPositiveButton("复制", null)
                .setNeutralButton("清空", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v ->
                    host.copyToClipboard("对弈日志", host.logText()));
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                host.clearLogText();
                log.setText("暂无日志");
            });
        });
        dialog.show();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    void showAboutDialog() {
        prepareAboutDialog();
        if (aboutDialog != null && !aboutDialog.isShowing()) aboutDialog.show();
    }

    void prepareAboutDialog() {
        if (aboutDialog != null) return;
        final AlertDialog dialog = new AlertDialog.Builder(host).create();

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        host.setRoundedBackground(card, Color.rgb(250, 249, 245), 18,
                Color.rgb(179, 188, 181));

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(14));
        host.setRoundedBackground(header, Color.rgb(35, 55, 47), 18, Color.TRANSPARENT);

        TextView title = new TextView(host);
        title.setText("节点象棋");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(host.dp(18), host.dp(16), host.dp(18), host.dp(16));

        TextView intro = new TextView(host);
        intro.setText("本应用主要用于人机对弈，同时也提供棋局分析功能。");
        intro.setTextSize(14);
        intro.setTextColor(Color.rgb(50, 61, 55));
        intro.setLineSpacing(0f, 1.18f);
        intro.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        host.setRoundedBackground(intro, Color.rgb(232, 239, 234), 10,
                Color.rgb(201, 215, 205));
        body.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout infoBox = new LinearLayout(host);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
        host.setRoundedBackground(infoBox, Color.WHITE, 10, Color.rgb(218, 221, 217));
        LinearLayout.LayoutParams infoBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoBoxLp.topMargin = host.dp(12);
        body.addView(infoBox, infoBoxLp);
        infoBox.addView(aboutInfoRow("版本号：" + MainActivity.VERSION_NAME),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));
        infoBox.addView(aboutInfoRow("作者：赵芮（rui）"),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));
        infoBox.addView(aboutInfoRow("QQ交流群：511645431"),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));

        TextView thanks = aboutParagraph(
                "感谢Qsll项目以及Pikafish、Duffish、PikafishHCE、Tchess这几个开源项目，为本项目提供了重要的参考和帮助。");
        LinearLayout.LayoutParams thanksLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thanksLp.topMargin = host.dp(12);
        body.addView(thanks, thanksLp);

        TextView specialThanks = aboutParagraph("特别鸣谢：GPT5.6，DeepSeekV4。");
        LinearLayout.LayoutParams specialLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        specialLp.topMargin = host.dp(8);
        body.addView(specialThanks, specialLp);

        Button close = new Button(host);
        close.setAllCaps(false);
        close.setText("关闭");
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setTextColor(host.highlightTextColor());
        close.setPadding(0, 0, 0, 0);
        host.setRoundedBackground(close, host.highlightColor(), 10, Color.TRANSPARENT);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44));
        closeLp.topMargin = host.dp(14);
        body.addView(close, closeLp);

        card.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setView(card);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() == null) return;
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = host.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(host.dp(390), screenWidth - host.dp(32)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        });
        aboutDialog = dialog;
    }

    private View aboutInfoRow(String text) {
        TextView row = new TextView(host);
        row.setText(text);
        row.setTextSize(14);
        row.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.setTextColor(Color.rgb(35, 55, 47));
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView aboutParagraph(String text) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(Color.rgb(76, 84, 79));
        view.setLineSpacing(0f, 1.2f);
        view.setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(2));
        return view;
    }

    void dismiss() {
        if (aboutDialog == null) return;
        try {
            aboutDialog.dismiss();
        } catch (Exception ignored) {
        }
        aboutDialog = null;
    }
}

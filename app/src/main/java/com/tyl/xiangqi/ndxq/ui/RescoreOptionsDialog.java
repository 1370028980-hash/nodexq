package com.tyl.xiangqi.ndxq.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** “重新打分”参数弹窗。把纯 UI/参数校验从 MainActivity 中移出。 */
public final class RescoreOptionsDialog {
    private static final int ROW_HEIGHT_DP = 46;
    private static final int CONTROL_HEIGHT_DP = 40;
    private static final int LABEL_WIDTH_DP = 124;
    private static final String PREFS = "human_vs_engine_v1";
    private static final String PREF_LAST_RESCORE_SECONDS = "rescore_last_seconds";

    public interface Listener {
        void onConfirm(int timeMs, int startPlyInclusive, int endPlyExclusive,
                       boolean forward, boolean allBranches, boolean appendScoresToComments);
    }

    private RescoreOptionsDialog() {}

    public static void show(Activity activity, int moveCount, int allBranchMaxMoveCount,
                            Listener listener) {
        if (activity == null || moveCount <= 0 || listener == null) return;
        final int mainMaxRounds = Math.max(1, (moveCount + 1) / 2);
        final int allMaxRounds = Math.max(mainMaxRounds,
                (Math.max(moveCount, allBranchMaxMoveCount) + 1) / 2);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 4));
        panel.setFocusableInTouchMode(true);

        LinearLayout timeRow = optionRow(activity);
        timeRow.addView(rowLabel(activity, "每步用时（秒）："), labelParams(activity));
        final EditText timeInput = numberInput(activity, true);
        String lastSeconds = activity.getSharedPreferences(PREFS, 0)
                .getString(PREF_LAST_RESCORE_SECONDS, "0.1");
        if (lastSeconds == null || lastSeconds.trim().length() == 0) lastSeconds = "0.1";
        timeInput.setText(lastSeconds);
        // 进入弹窗后点击输入框立即全选，直接输入即可覆盖，与引擎设置输入框行为一致。
        timeInput.setSelectAllOnFocus(true);
        timeRow.addView(timeInput, fillControlParams(activity));
        panel.addView(timeRow, rowParams(activity));

        LinearLayout rangeRow = optionRow(activity);
        rangeRow.addView(rowLabel(activity, "范围(回合)："), labelParams(activity));
        LinearLayout rangeControls = new LinearLayout(activity);
        rangeControls.setOrientation(LinearLayout.HORIZONTAL);
        rangeControls.setGravity(Gravity.CENTER_VERTICAL);
        final EditText fromInput = numberInput(activity, false);
        final EditText toInput = numberInput(activity, false);
        fromInput.setHint("起");
        toInput.setHint("止");
        fromInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        toInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        fromInput.setSelectAllOnFocus(true);
        toInput.setSelectAllOnFocus(true);
        rangeControls.addView(fromInput, new LinearLayout.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        TextView to = new TextView(activity);
        to.setText("至");
        to.setTextSize(14);
        to.setTextColor(Color.rgb(45, 52, 48));
        to.setGravity(Gravity.CENTER);
        rangeControls.addView(to, new LinearLayout.LayoutParams(
                dp(activity, 34), dp(activity, CONTROL_HEIGHT_DP)));
        rangeControls.addView(toInput, new LinearLayout.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        rangeRow.addView(rangeControls, fillControlParams(activity));
        panel.addView(rangeRow, rowParams(activity));

        LinearLayout directionRow = optionRow(activity);
        directionRow.addView(rowLabel(activity, "打分方向："), labelParams(activity));
        final RadioGroup directionGroup = new RadioGroup(activity);
        directionGroup.setOrientation(RadioGroup.HORIZONTAL);
        directionGroup.setGravity(Gravity.CENTER_VERTICAL);
        final int ID_FORWARD = 0x1001;
        final int ID_BACKWARD = 0x1002;
        final RadioButton forward = new RadioButton(activity);
        forward.setId(ID_FORWARD);
        forward.setText("从前往后");
        forward.setTextSize(13);
        forward.setSingleLine(true);
        forward.setMinWidth(0);
        final RadioButton backward = new RadioButton(activity);
        backward.setId(ID_BACKWARD);
        backward.setText("从后往前");
        backward.setTextSize(13);
        backward.setSingleLine(true);
        backward.setMinWidth(0);
        // 必须先 addView 再 setChecked：若在加入 RadioGroup 前 setChecked(true)，
        // 组内互斥状态机不认它是“初始选中”，会导致两个选项同时可勾选、方向判定失效。
        directionGroup.addView(forward, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        directionGroup.addView(backward, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        directionGroup.check(ID_FORWARD);
        directionRow.addView(directionGroup, fillControlParams(activity));
        panel.addView(directionRow, rowParams(activity));

        LinearLayout allBranchesRow = optionRow(activity);
        allBranchesRow.addView(rowLabel(activity, "给所有分支打分："), labelParams(activity));
        final RadioGroup allBranchesGroup = new RadioGroup(activity);
        allBranchesGroup.setOrientation(RadioGroup.HORIZONTAL);
        allBranchesGroup.setGravity(Gravity.CENTER_VERTICAL);
        final int ID_ALL_BRANCHES_NO = 0x1011;
        final int ID_ALL_BRANCHES_YES = 0x1012;
        final RadioButton allBranchesNo = new RadioButton(activity);
        allBranchesNo.setId(ID_ALL_BRANCHES_NO);
        allBranchesNo.setText("否");
        allBranchesNo.setTextSize(13);
        allBranchesNo.setSingleLine(true);
        allBranchesNo.setMinWidth(0);
        final RadioButton allBranchesYes = new RadioButton(activity);
        allBranchesYes.setId(ID_ALL_BRANCHES_YES);
        allBranchesYes.setText("是");
        allBranchesYes.setTextSize(13);
        allBranchesYes.setSingleLine(true);
        allBranchesYes.setMinWidth(0);
        // 与“打分方向”相同：先加入同一个 RadioGroup 再设置默认值，确保“否/是”严格互斥。
        allBranchesGroup.addView(allBranchesNo, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        allBranchesGroup.addView(allBranchesYes, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        allBranchesGroup.check(ID_ALL_BRANCHES_NO);
        allBranchesRow.addView(allBranchesGroup, fillControlParams(activity));
        panel.addView(allBranchesRow, rowParams(activity));

        LinearLayout appendScoresRow = optionRow(activity);
        appendScoresRow.addView(rowLabel(activity, "打分结果追加到注释："), labelParams(activity));
        final RadioGroup appendScoresGroup = new RadioGroup(activity);
        appendScoresGroup.setOrientation(RadioGroup.HORIZONTAL);
        appendScoresGroup.setGravity(Gravity.CENTER_VERTICAL);
        final int ID_APPEND_SCORES_NO = 0x1013;
        final int ID_APPEND_SCORES_YES = 0x1014;
        final RadioButton appendScoresNo = new RadioButton(activity);
        appendScoresNo.setId(ID_APPEND_SCORES_NO);
        appendScoresNo.setText("否");
        appendScoresNo.setTextSize(13);
        appendScoresNo.setSingleLine(true);
        appendScoresNo.setMinWidth(0);
        final RadioButton appendScoresYes = new RadioButton(activity);
        appendScoresYes.setId(ID_APPEND_SCORES_YES);
        appendScoresYes.setText("是");
        appendScoresYes.setTextSize(13);
        appendScoresYes.setSingleLine(true);
        appendScoresYes.setMinWidth(0);
        appendScoresGroup.addView(appendScoresNo, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        appendScoresGroup.addView(appendScoresYes, new RadioGroup.LayoutParams(
                0, dp(activity, CONTROL_HEIGHT_DP), 1f));
        appendScoresGroup.check(ID_APPEND_SCORES_NO);
        appendScoresRow.addView(appendScoresGroup, fillControlParams(activity));
        panel.addView(appendScoresRow, rowParams(activity));

        TextView appendScoresHint = new TextView(activity);
        appendScoresHint.setText("若原注释内容重要，请务必备份。");
        appendScoresHint.setTextSize(11);
        appendScoresHint.setTextColor(Color.rgb(100, 106, 102));
        appendScoresHint.setPadding(0, 0, 0, dp(activity, 2));
        panel.addView(appendScoresHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText("留空表示全部回合；只填起点则到最后，只填终点则从开局开始。主线共 "
                + mainMaxRounds + " 回合"
                + (allMaxRounds > mainMaxRounds ? "，含分支最长 " + allMaxRounds + " 回合。" : "。"));
        hint.setTextSize(11);
        hint.setTextColor(Color.rgb(100, 106, 102));
        hint.setPadding(0, dp(activity, 4), 0, dp(activity, 2));
        panel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("重新打分")
                .setView(panel)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            // 把初始焦点留给面板，避免刚打开弹窗时 0.1 就处于全选状态。
            panel.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String secondsText = timeInput.getText().toString().trim();
                    double seconds = Double.parseDouble(secondsText);
                    if (seconds <= 0) throw new IllegalArgumentException("请输入大于 0 的有效时间");
                    boolean scoreAllBranches = allBranchesGroup.getCheckedRadioButtonId()
                            == ID_ALL_BRANCHES_YES;
                    int selectedMaxRounds = scoreAllBranches ? allMaxRounds : mainMaxRounds;
                    int selectedMaxMoves = scoreAllBranches
                            ? Math.max(moveCount, allBranchMaxMoveCount) : moveCount;
                    int fromRound = parseRound(fromInput.getText().toString(), 1);
                    int toRound = parseRound(toInput.getText().toString(), selectedMaxRounds);
                    if (fromRound < 1 || fromRound > selectedMaxRounds
                            || toRound < 1 || toRound > selectedMaxRounds) {
                        throw new IllegalArgumentException("回合范围应在 1～" + selectedMaxRounds + " 之间");
                    }
                    if (fromRound > toRound) throw new IllegalArgumentException("起始回合不能大于结束回合");
                    int startPly = Math.max(0, (fromRound - 1) * 2);
                    int endPly = Math.min(selectedMaxMoves, toRound * 2);
                    int timeMs = Math.max(50, (int) Math.round(seconds * 1000));
                    boolean isForward = directionGroup.getCheckedRadioButtonId() == ID_FORWARD;
                    boolean appendScoresToComments = appendScoresGroup.getCheckedRadioButtonId()
                            == ID_APPEND_SCORES_YES;
                    // 仅在参数校验通过并确认后记住本次填写值；首次使用仍默认 0.1 秒。
                    activity.getSharedPreferences(PREFS, 0).edit()
                            .putString(PREF_LAST_RESCORE_SECONDS, secondsText)
                            .apply();
                    dialog.dismiss();
                    listener.onConfirm(timeMs, startPly, endPly, isForward, scoreAllBranches,
                            appendScoresToComments);
                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message == null || message.length() == 0) message = "请输入有效的重新打分参数";
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private static LinearLayout optionRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static LinearLayout.LayoutParams rowParams(Activity activity) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, ROW_HEIGHT_DP));
    }

    private static LinearLayout.LayoutParams labelParams(Activity activity) {
        return new LinearLayout.LayoutParams(
                dp(activity, LABEL_WIDTH_DP), dp(activity, CONTROL_HEIGHT_DP));
    }

    private static LinearLayout.LayoutParams fillControlParams(Activity activity) {
        return new LinearLayout.LayoutParams(0, dp(activity, CONTROL_HEIGHT_DP), 1f);
    }

    private static int parseRound(String raw, int fallback) {
        String value = raw == null ? "" : raw.trim();
        return value.length() == 0 ? fallback : Integer.parseInt(value);
    }

    private static TextView rowLabel(Activity activity, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(45, 52, 48));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setSingleLine(true);
        return view;
    }

    private static EditText numberInput(Activity activity, boolean decimal) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        return input;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

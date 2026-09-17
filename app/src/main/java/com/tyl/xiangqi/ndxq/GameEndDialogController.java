package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 正式对局结束弹窗及其页面流转。 */
final class GameEndDialogController {
    private final MainActivity host;

    GameEndDialogController(MainActivity host) {
        this.host = host;
    }

    void show(String message, GameEndType type) {
        final AlertDialog dialog = new AlertDialog.Builder(host).create();
        dialog.setCancelable(false);

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14));
        host.setRoundedBackground(card, Color.rgb(250, 249, 245), 16,
                Color.rgb(178, 188, 180));

        TextView title = new TextView(host);
        title.setText("对局结束");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        host.setRoundedBackground(title, Color.rgb(35, 55, 47), 11, Color.TRANSPARENT);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(48)));

        TextView result = new TextView(host);
        String resultMessage = type == GameEndType.RED_WIN ? "红方胜！"
                : (type == GameEndType.BLACK_WIN ? "黑方胜！" : message);
        result.setText(resultMessage);
        result.setTextSize(17);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setTextColor(Color.rgb(42, 52, 47));
        result.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(46));
        resultLp.topMargin = host.dp(16);
        card.addView(result, resultLp);

        boolean evaluation = host.evaluationMode;
        LinearLayout buttons = new LinearLayout(host);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button returnBoard = dialogButton(evaluation ? "返回棋盘" : "确定", false);
        Button again = dialogButton("再来一局", true);
        Button exit = dialogButton(evaluation ? "退出" : "新的一局", false);
        buttons.addView(returnBoard, buttonLayoutParams());
        buttons.addView(again, buttonLayoutParams());
        buttons.addView(exit, buttonLayoutParams());
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44));
        buttonsLp.topMargin = host.dp(8);
        card.addView(buttons, buttonsLp);

        returnBoard.setOnClickListener(v -> dialog.dismiss());
        again.setOnClickListener(v -> {
            dialog.dismiss();
            if (host.selfAnalysisMode) {
                host.startSelfAnalysisSession();
            } else if (evaluation) {
                // 完整评测入口会重新按当前等级分匹配难度，并重新随机引擎执子。
                host.gameScreenController.discardFinishedEvaluationScreen();
                host.startEvaluationSession();
            } else {
                host.startNewGame();
            }
        });
        exit.setOnClickListener(v -> {
            dialog.dismiss();
            if (evaluation) {
                host.discardFinishedEvaluationState();
                host.showEvaluationLauncher();
            } else {
                if (host.selfAnalysisMode) host.clearSavedSession(true);
                host.showLauncherScreen();
            }
        });

        dialog.setView(card);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() == null) return;
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = host.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(host.dp(390), width - host.dp(28)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        });
        dialog.show();
    }

    private Button dialogButton(String text, boolean primary) {
        Button button = new Button(host);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setTextColor(primary ? host.highlightTextColor() : Color.rgb(42, 74, 59));
        host.setRoundedBackground(button,
                primary ? host.highlightColor() : Color.rgb(236, 239, 234),
                8, Color.rgb(178, 188, 180));
        return button;
    }

    private LinearLayout.LayoutParams buttonLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = host.dp(2);
        lp.rightMargin = host.dp(2);
        return lp;
    }
}

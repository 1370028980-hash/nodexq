package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.ChessBoardView;

import java.util.Collections;

/** 棋盘页菜单、菜单按钮状态和箭头显示选项控制器。 */
final class GameActionMenuController {
    private final MainActivity host;
    private Button drawButton;
    private Button resignButton;
    private Button undoButton;

    GameActionMenuController(MainActivity host) {
        this.host = host;
    }

    void show() {
        final AlertDialog dialog = new AlertDialog.Builder(host).create();
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        panel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(host);
        title.setText("菜单");
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.rgb(42, 65, 54));
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(36)));

        if (host.evaluationMode && !host.completedDuelGame) {
            LinearLayout[] evalRows = new LinearLayout[3];
            for (int i = 0; i < evalRows.length; i++) {
                evalRows[i] = new LinearLayout(host);
                evalRows[i].setOrientation(LinearLayout.HORIZONTAL);
                evalRows[i].setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44));
                if (i > 0) rowLp.topMargin = host.dp(4);
                panel.addView(evalRows[i], rowLp);
            }
            Button saveEval = menuActionButton("保存棋谱", host::showStoreManualDialog, dialog);
            Button logEval = menuActionButton("日志", host::showLogDialog, dialog);
            Button boardEval = menuActionButton("棋盘大小", host::showBoardScaleDialog, dialog);
            Button skinEval = menuActionButton("皮肤设置", host::showSkinSettingsDialog, dialog);
            Button soundEval = menuStayButton(host.soundEnabled ? "当前声音:开" : "当前声音:关");
            soundEval.setOnClickListener(v -> {
                host.soundEnabled = !host.soundEnabled;
                soundEval.setText(host.soundEnabled ? "当前声音:开" : "当前声音:关");
                host.saveLauncherPreferences();
            });
            evalRows[0].addView(saveEval, menuActionLp());
            evalRows[0].addView(logEval, menuActionLp());
            evalRows[1].addView(boardEval, menuActionLp());
            evalRows[1].addView(skinEval, menuActionLp());
            evalRows[2].addView(soundEval, menuActionLp());
            dialog.setView(panel);
            dialog.setOnShowListener(ignored -> configureDialogWindow(dialog));
            dialog.show();
            return;
        }

        LinearLayout[] rows = new LinearLayout[host.selfAnalysisMode ? 8 : 7];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new LinearLayout(host);
            rows[i].setOrientation(LinearLayout.HORIZONTAL);
            rows[i].setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44));
            if (i > 0) rowLp.topMargin = host.dp(4);
            panel.addView(rows[i], rowLp);
        }

        drawButton = menuActionButton("提和", host::offerDraw, dialog);
        resignButton = menuActionButton("认输", host::confirmResign, dialog);
        undoButton = menuActionButton("悔棋", host::undoToPreviousHumanTurn, dialog);
        Button edit = menuActionButton("编辑", host::toggleEditMode, dialog);
        Button open = menuActionButton("打开文件", host::openManualFilePicker, dialog);
        boolean openEnabled = host.selfAnalysisMode;
        open.setEnabled(openEnabled);
        open.setAlpha(openEnabled ? 1f : 0.4f);
        open.setTextColor(openEnabled ? Color.rgb(42, 65, 54) : Color.rgb(150, 150, 150));
        Button save = menuActionButton("保存棋谱", host::showStoreManualDialog, dialog);
        Button logBtn = menuActionButton("日志", host::showLogDialog, dialog);
        Button engineBtn = menuActionButton("引擎设置", host::showEngineOptionsDialog, dialog);
        Button boardBtn = menuActionButton("棋盘大小", host::showBoardScaleDialog, dialog);
        Button skinBtn = menuActionButton("皮肤设置", host::showSkinSettingsDialog, dialog);
        Button moveSettingsBtn = menuActionButton("行棋设置",
                host::showManualPlaySettingsDialog, dialog);
        Button modeBtn = menuActionButton(host.selfAnalysisMode ? "进入对弈模式" : "进入分析模式",
                host::toggleSelfAnalysisMode, dialog);
        final Button soundBtn = menuStayButton(host.soundEnabled ? "当前声音:开" : "当前声音:关");
        soundBtn.setOnClickListener(v -> {
            host.soundEnabled = !host.soundEnabled;
            soundBtn.setText(host.soundEnabled ? "当前声音:开" : "当前声音:关");
            host.saveLauncherPreferences();
        });
        final Button combinedBtn = menuStayButton(host.combinedManualEngineMode
                ? "棋谱引擎二合一:开" : "棋谱引擎二合一:关");
        combinedBtn.setOnClickListener(v -> requestCombinedManualEngineToggle(dialog, combinedBtn));

        rows[0].addView(drawButton, menuActionLp());
        rows[0].addView(resignButton, menuActionLp());
        rows[1].addView(undoButton, menuActionLp());
        rows[1].addView(edit, menuActionLp());
        rows[2].addView(open, menuActionLp());
        rows[2].addView(save, menuActionLp());
        rows[3].addView(logBtn, menuActionLp());
        rows[3].addView(engineBtn, menuActionLp());
        rows[4].addView(boardBtn, menuActionLp());
        rows[4].addView(skinBtn, menuActionLp());
        rows[5].addView(modeBtn, menuActionLp());
        rows[5].addView(soundBtn, menuActionLp());
        rows[6].addView(combinedBtn, menuActionLp());
        if (host.selfAnalysisMode) rows[6].addView(moveSettingsBtn, menuActionLp());
        final TextView arrowMode = buildArrowModeButton();
        Button arrowBtn = menuStayButton(arrowMode.getText().toString());
        arrowBtn.setOnClickListener(v -> {
            if (isMultiPvArrowMode()) {
                host.arrowStepCount = host.arrowStepCount == 0 ? 2 : 0;
            } else {
                host.arrowStepCount = host.arrowStepCount >= 4 ? 0 : host.arrowStepCount + 1;
            }
            host.showEngineArrows = host.arrowStepCount > 0;
            if (host.boardView != null) {
                host.boardView.setShowArrow(host.showEngineArrows);
                if (host.showEngineArrows) host.updateAnalysisArrows();
                else host.boardView.setAnalysisArrows(
                        Collections.<ChessBoardView.AnalysisArrow>emptyList());
            }
            host.saveLauncherPreferences();
            arrowBtn.setText(host.arrowStepCount <= 0 ? "不显示箭头"
                    : (host.analysisConfiguredMultiPv > 1 ? "显示箭头"
                    : "箭头显示" + host.arrowStepCount + "步"));
        });
        if (host.selfAnalysisMode) {
            rows[7].addView(arrowBtn, menuActionLp());
            View arrowSpacer = new View(host);
            arrowSpacer.setEnabled(false);
            rows[7].addView(arrowSpacer, menuActionLp());
        } else {
            rows[6].addView(arrowBtn, menuActionLp());
        }

        dialog.setView(panel);
        dialog.setOnDismissListener(ignored -> {
            drawButton = null;
            resignButton = null;
            undoButton = null;
        });
        dialog.setOnShowListener(ignored -> {
            configureDialogWindow(dialog);
            updateDrawButtonState();
        });
        dialog.show();
    }

    void updateDrawButtonState() {
        boolean permanentlyFinished = host.completedDuelGame;
        int completedRounds = host.currentPly / 2;
        if (drawButton != null) {
            // 自选难度对局前五个完整回合不可提和；评测模式仍沿用更严格的 25 回合门槛。
            boolean drawRoundReady = host.evaluationMode
                    ? completedRounds >= 25 : completedRounds >= 5;
            boolean enabled = !permanentlyFinished && !host.gameOver
                    && !host.selfAnalysisMode
                    && drawRoundReady;
            drawButton.setEnabled(enabled);
            drawButton.setAlpha(enabled ? 1f : 0.4f);
            drawButton.setTextColor(enabled ? Color.rgb(42, 65, 54)
                    : Color.rgb(150, 150, 150));
        }
        if (resignButton != null) {
            boolean enabled = !permanentlyFinished && !host.gameOver && !host.selfAnalysisMode
                    && (!host.evaluationMode || completedRounds >= 5);
            resignButton.setEnabled(enabled);
            resignButton.setAlpha(enabled ? 1f : 0.4f);
            resignButton.setTextColor(enabled ? Color.rgb(42, 65, 54)
                    : Color.rgb(150, 150, 150));
        }
        if (host.evaluationMode && !host.completedDuelGame) {
            boolean drawEnabled = !host.gameOver && completedRounds >= 25;
            boolean resignEnabled = !host.gameOver && completedRounds >= 5;
            host.styleEvaluationToolbarButton(host.evaluationDrawToolbarButton,
                    false, drawEnabled);
            host.styleEvaluationToolbarButton(host.evaluationResignToolbarButton,
                    false, resignEnabled);
        }
        if (undoButton != null) {
            boolean enabled = !permanentlyFinished && !host.gameOver;
            undoButton.setEnabled(enabled);
            undoButton.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private Button menuActionButton(String text, Runnable action, AlertDialog dialog) {
        Button button = new Button(host);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        button.setTextColor(Color.rgb(42, 65, 54));
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        host.setRoundedBackground(button, Color.rgb(239, 242, 237), 8,
                Color.rgb(197, 205, 198));
        button.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return button;
    }

    private Button menuStayButton(String text) {
        Button button = new Button(host);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        button.setTextColor(Color.rgb(42, 65, 54));
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        host.setRoundedBackground(button, Color.rgb(239, 242, 237), 8,
                Color.rgb(197, 205, 198));
        return button;
    }

    private LinearLayout.LayoutParams menuActionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = host.dp(2);
        lp.rightMargin = host.dp(2);
        return lp;
    }

    private TextView buildArrowModeButton() {
        TextView button = new TextView(host);
        button.setTextSize(10);
        button.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(host.dp(2), 0, host.dp(2), 0);
        updateArrowModeButtonText(button);
        host.setRoundedBackground(button, Color.rgb(239, 242, 237), 5,
                Color.rgb(197, 205, 198));
        return button;
    }

    private boolean isMultiPvArrowMode() {
        int configured = host.analysisConfiguredMultiPv > 1
                ? host.analysisConfiguredMultiPv
                : host.getStoredManualOptionInt("MultiPV", 1);
        return configured > 1;
    }

    private void updateArrowModeButtonText(TextView button) {
        if (button == null) return;
        if (isMultiPvArrowMode()) {
            button.setText(host.showEngineArrows ? "显示箭头" : "不显示箭头");
        } else if (host.arrowStepCount <= 0) {
            button.setText("不显示箭头");
        } else {
            button.setText("箭头显示" + host.arrowStepCount + "步");
        }
        button.setTextColor(host.showEngineArrows
                ? Color.rgb(42, 65, 54) : Color.rgb(125, 130, 127));
    }

    private void requestCombinedManualEngineToggle(final AlertDialog menuDialog,
                                                   final Button button) {
        final boolean enabling = !host.combinedManualEngineMode;
        Runnable apply = () -> {
            host.combinedManualEngineMode = enabling;
            if (host.combinedManualEngineMode && host.selectedGameTab == 1) {
                host.selectedGameTab = 0;
            }
            host.saveLauncherPreferences();
            if (button != null) {
                button.setText(host.combinedManualEngineMode
                        ? "棋谱引擎二合一:开" : "棋谱引擎二合一:关");
            }
            if (menuDialog != null) menuDialog.dismiss();
            host.gameScreenController.applyCombinedModeLayout();
        };
        if (enabling && host.getStoredManualOptionInt("MultiPV", 1) > 4) {
            android.widget.Toast.makeText(host, "二合一之后还选这么多PV，估计没位置显示了。",
                    android.widget.Toast.LENGTH_LONG).show();
        }
        apply.run();
    }

    private void configureDialogWindow(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(host.dp(360),
                    host.getResources().getDisplayMetrics().widthPixels - host.dp(28));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}

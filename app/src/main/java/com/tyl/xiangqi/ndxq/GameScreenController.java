package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;

import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.ToolbarIconButton;

/** 棋盘页根布局、工具栏和标签区的生命周期控制器。 */
final class GameScreenController {
    private final MainActivity host;
    /** 当前棋盘页的标签栏；二合一开关只调整这块布局，不重建棋盘或引擎。 */
    private LinearLayout tabRow;

    GameScreenController(MainActivity host) {
        this.host = host;
    }

    void showGameScreen() {
        host.evaluationLauncherVisible = false;
        host.customDifficultyLauncherVisible = false;
        host.launcherScreenRoot = null;
        host.launcherDifficultyView = null;
        host.launcherContrastRefreshPosted = false;
        host.gameScreenVisible = true;
        host.recentScreenVisible = false;
        host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        host.appRoot.removeAllViews();

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));
        host.editModeToolbar = root.getChildAt(root.getChildCount() - 1);

        FrameLayout topInfoRow = new FrameLayout(host);
        topInfoRow.setBackgroundColor(host.globalSurfaceFillColor());
        host.topPlayerLabel = host.playerLabel();
        host.topPlayerLabel.setPadding(0, 0, 0, 0);
        topInfoRow.addView(host.topPlayerLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(MainActivity.PLAYER_ROW_HEIGHT_DP),
                Gravity.CENTER));
        root.addView(topInfoRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(MainActivity.PLAYER_ROW_HEIGHT_DP)));
        host.editModeTopRow = topInfoRow;

        host.boardView = new ChessBoardView(host, false);
        host.applyCurrentSkinToBoard(host.boardView, false);
        host.boardView.setListener(host);
        host.boardView.setOnLongClickListener(v -> {
            host.showBoardClipboardDialog();
            return true;
        });
        // 对弈/自主分析默认隐藏坐标；进入编辑模式时临时显示。
        host.boardView.setShowCoordinate(false);
        host.boardView.setPieceShadowEnabled(true);
        host.boardView.setBoardScalePercent(host.boardScalePercent);
        host.boardView.setShowArrow(host.showEngineArrows);
        host.boardView.newGame();
        host.boardView.setReversed(host.enginePlaysRed);
        root.addView(host.boardView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.bottomPlayerLabel = host.playerLabel();
        root.addView(host.bottomPlayerLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(MainActivity.PLAYER_ROW_HEIGHT_DP)));
        host.editModeBottomLabel = host.bottomPlayerLabel;

        LinearLayout tabs = new LinearLayout(host);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
        tabs.setBackgroundColor(host.globalSurfaceFillColor());
        tabRow = tabs;
        if (host.combinedManualEngineMode && host.selectedGameTab == 1) host.selectedGameTab = 0;
        host.manualTab = host.tabText(host.combinedManualEngineMode ? "棋谱+引擎" : "棋谱");
        host.engineTab = host.combinedManualEngineMode ? null : host.tabText("引擎");
        host.situationTab = host.tabText("局势图");
        tabs.addView(host.manualTab, new LinearLayout.LayoutParams(0, host.dp(30), 1f));
        if (host.engineTab != null) {
            tabs.addView(host.engineTab, new LinearLayout.LayoutParams(0, host.dp(30), 1f));
        }
        tabs.addView(host.situationTab, new LinearLayout.LayoutParams(0, host.dp(30), 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(36)));
        host.editModeTabs = tabs;
        if (host.evaluationMode && !host.completedDuelGame) tabs.setVisibility(View.GONE);
        host.manualTab.setOnClickListener(v -> host.selectGameTab(0));
        if (host.engineTab != null) host.engineTab.setOnClickListener(v -> host.selectGameTab(1));
        host.situationTab.setOnClickListener(v -> host.selectGameTab(2));

        host.gameContentHost = new LinearLayout(host);
        host.gameContentHost.setOrientation(LinearLayout.VERTICAL);
        host.gameContentHost.setPadding(host.dp(8), 0, host.dp(8), host.dp(8));
        host.gameContentHost.setMinimumHeight(host.dp(180));
        root.addView(host.gameContentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(180)));

        host.gamePageScroll = new ScrollView(host);
        host.gamePageScroll.setFillViewport(true);
        host.gamePageScroll.setClipToPadding(false);
        host.gamePageScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.appRoot.addView(host.gamePageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.updatePlayerLabels();
        host.refreshGameTabs();
        host.updateGameContent();
        host.gamePageScroll.post(this::adjustContentHeightForViewport);
    }

    /**
     * 评测终局为了展示复盘工具会按终局状态重建页面；开始下一局前必须丢弃这套 View，
     * 让正常开局流程按“进行中的评测”重新创建隐藏受限操作后的界面。
     */
    void discardFinishedEvaluationScreen() {
        host.gameScreenVisible = false;
        host.gamePageScroll = null;
        host.gameContentHost = null;
        host.boardView = null;
        host.manualTab = null;
        host.engineTab = null;
        host.situationTab = null;
        host.evaluationDrawToolbarButton = null;
        host.evaluationResignToolbarButton = null;
    }

    /**
     * 应用“棋谱引擎二合一”显示开关。
     *
     * <p>这是纯 UI 重排：保留当前 ChessBoardView、局面、分析缓存以及引擎搜索，
     * 只重建内容区中的棋谱/引擎视图，避免把布局切换误当成新局面。</p>
     */
    void applyCombinedModeLayout() {
        if (!host.gameScreenVisible || tabRow == null || host.manualTab == null) return;
        if (host.combinedManualEngineMode) {
            if (host.selectedGameTab == 1) host.selectedGameTab = 0;
            host.manualTab.setText("棋谱+引擎");
            if (host.engineTab != null) {
                tabRow.removeView(host.engineTab);
                host.engineTab = null;
            }
        } else {
            host.manualTab.setText("棋谱");
            if (host.engineTab == null) {
                TextView engineTab = host.tabText("引擎");
                engineTab.setOnClickListener(v -> host.selectGameTab(1));
                host.engineTab = engineTab;
                int situationIndex = tabRow.indexOfChild(host.situationTab);
                if (situationIndex < 0) situationIndex = tabRow.getChildCount();
                tabRow.addView(engineTab, situationIndex, new LinearLayout.LayoutParams(
                        0, host.dp(30), 1f));
            } else {
                host.engineTab.setVisibility(View.VISIBLE);
            }
        }
        host.refreshGameTabs();
        host.updateGameContent();
        adjustContentHeightForViewport();
        if (host.gamePageScroll != null) {
            host.gamePageScroll.post(this::adjustContentHeightForViewport);
        }
    }

    void adjustContentHeightForViewport() {
        if (host.gamePageScroll == null || host.gameContentHost == null || host.boardView == null) return;
        int fixedHeight = host.dp(34 + 36);
        if (host.editModeTopRow != null
                && host.editModeTopRow.getVisibility() != View.GONE) {
            fixedHeight += host.dp(MainActivity.PLAYER_ROW_HEIGHT_DP);
        }
        if (host.editModeBottomLabel != null
                && host.editModeBottomLabel.getVisibility() != View.GONE) {
            fixedHeight += host.dp(MainActivity.PLAYER_ROW_HEIGHT_DP);
        }
        int available = host.gamePageScroll.getHeight() - fixedHeight
                - host.boardView.getMeasuredHeight();
        int target = Math.max(host.dp(180), available);
        ViewGroup.LayoutParams raw = host.gameContentHost.getLayoutParams();
        if (raw != null && raw.height != target) {
            raw.height = target;
            host.gameContentHost.setLayoutParams(raw);
        }
    }

    private View buildToolbar() {
        LinearLayout outer = new LinearLayout(host);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(host.dp(3), host.dp(2), host.dp(3), host.dp(2));
        outer.setBackgroundColor(host.globalSurfaceFillColor());

        LinearLayout row = host.toolbarRow();
        row.addView(host.toolbarIconButton(ToolbarIconButton.Icon.MENU, "菜单",
                v -> host.showGameActionMenu()), host.toolbarLp());
        if (host.evaluationMode && !host.completedDuelGame) {
            host.evaluationDrawToolbarButton = host.evaluationToolbarButton("提和",
                    v -> host.offerDraw());
            host.evaluationResignToolbarButton = host.evaluationToolbarButton("认输",
                    v -> host.confirmResign());
            row.addView(host.evaluationDrawToolbarButton, host.toolbarLp());
            row.addView(host.evaluationResignToolbarButton, host.toolbarLp());
        } else {
            host.evaluationDrawToolbarButton = null;
            host.evaluationResignToolbarButton = null;
        }
        ToolbarIconButton newButton = host.toolbarIconButton(ToolbarIconButton.Icon.PAPER,
                "新建", v -> host.handleNewGameAction());
        if (host.evaluationMode) newButton.setVisibility(View.GONE);
        row.addView(newButton, host.toolbarLp());
        row.addView(host.toolbarIconButton(ToolbarIconButton.Icon.HOURGLASS,
                "翻转", v -> host.reverseBoard()), host.toolbarLp());
        if (host.selfAnalysisMode) {
            host.computerBlackButton = host.toolbarIconButton(
                    ToolbarIconButton.Icon.COMPUTER_BLACK, "电脑执黑",
                    v -> host.toggleComputerSide(false));
            row.addView(host.computerBlackButton, host.toolbarLp());
            host.computerRedButton = host.toolbarIconButton(
                    ToolbarIconButton.Icon.COMPUTER_RED, "电脑执红",
                    v -> host.toggleComputerSide(true));
            row.addView(host.computerRedButton, host.toolbarLp());
        } else {
            host.computerRedButton = null;
            host.computerBlackButton = null;
        }
        host.analysisButton = host.toolbarIconButton(ToolbarIconButton.Icon.MAGNIFY,
                "分析", v -> host.toggleAnalysis());
        if (host.evaluationMode && !host.completedDuelGame) {
            host.analysisButton.setVisibility(View.GONE);
        }
        row.addView(host.analysisButton, host.toolbarLp());
        ToolbarIconButton immediate = host.toolbarIconButton(ToolbarIconButton.Icon.LIGHTNING,
                "立即出招", v -> host.immediateMove());
        ToolbarIconButton alternative = host.toolbarIconButton(ToolbarIconButton.Icon.ALTERNATIVE,
                "变招", v -> host.forceAlternativeMove());
        if (host.evaluationMode && !host.completedDuelGame) {
            immediate.setVisibility(View.GONE);
            alternative.setVisibility(View.GONE);
        }
        row.addView(immediate, host.toolbarLp());
        row.addView(alternative, host.toolbarLp());
        if (!host.selfAnalysisMode) {
            ToolbarIconButton push = host.toolbarIconButton(ToolbarIconButton.Icon.PUSH,
                    "推演", v -> host.enterPushMode());
            if (host.evaluationMode && !host.completedDuelGame) push.setVisibility(View.GONE);
            row.addView(push, host.toolbarLp());
        }
        outer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.styleAnalysisButton();
        host.styleComputerSideButtons();
        return outer;
    }
}

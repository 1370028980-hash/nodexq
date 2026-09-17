package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** 临时推演、错题推演共用的辅助棋盘页面与临时分析流程。 */
final class AuxiliaryBoardController {
    private final MainActivity host;

    AuxiliaryBoardController(MainActivity host) {
        this.host = host;
    }

    /** 对弈模式临时推演：独立棋盘、独立走法列表，绝不写入正式对局字段。 */
    void enterPushMode() {
        if (host.selfAnalysisMode || host.boardView == null) return;
        if (host.boardView.isEditMode() || host.isRescoring || host.drawOfferInProgress
                || host.engineThinking || host.autoMoveInProgress) {
            Toast.makeText(host, "当前操作结束后再进入推演", Toast.LENGTH_SHORT).show();
            return;
        }
        final String pushBaseFen = host.boardView.getFen();
        final boolean reversed = host.boardView.isReversed();
        final boolean resumeAnalysis = host.analysisMode;
        host.pushModeActive = true;
        host.temporaryAnalysisRunning = false;
        host.temporaryAnalysisGeneration++;
        host.pushReturnReversed = reversed;
        host.pushResumeAnalysis = resumeAnalysis;
        host.stopSearchForPositionChange();
        host.computerRedActive = false;
        host.computerBlackActive = false;
        host.computerRedBlackActive = false;
        host.computerSideThinking = false;
        host.computerMoveGeneration++;
        showPushBoard(pushBaseFen, new ArrayList<String>(), 0, reversed, resumeAnalysis);
    }

    private void showPushBoard(final String pushBaseFen, final ArrayList<String> pushMoves,
                               final int pushPly, final boolean reversed,
                               final boolean resumeAnalysis) {
        final boolean restartTemporaryAnalysis = host.temporaryAnalysisRunning;
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.temporaryAnalysisGeneration++;
        host.appRoot.removeAllViews();
        host.gamePageScroll = null;

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dp(8), host.dp(6), host.dp(8), host.dp(8));
        root.setBackgroundColor(Color.TRANSPARENT);

        TextView title = new TextView(host);
        title.setText("临时推演");
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(host.globalBackgroundTextColor());
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));

        final ChessBoardView pushBoard = new ChessBoardView(host, false);
        host.applyCurrentSkinToBoard(pushBoard, false);
        pushBoard.setBoardScalePercent(100);
        pushBoard.setShowCoordinate(false);
        pushBoard.setPieceShadowEnabled(true);
        pushBoard.setShowArrow(host.showEngineArrows);
        pushBoard.setReversed(reversed);
        try {
            pushBoard.setBoardFromFen(pushBaseFen);
            for (int i = 0; i < pushPly && i < pushMoves.size(); i++) {
                pushBoard.playMoveSilently(Move.fromEngineStep(pushMoves.get(i)));
            }
        } catch (Exception e) {
            Toast.makeText(host, "推演局面恢复失败", Toast.LENGTH_SHORT).show();
        }
        pushBoard.setListener(new ChessBoardView.Listener() {
            @Override public void onMoveMade(Move move, char movedPiece, char capturedPiece,
                                             String fenAfterMove, boolean redToMoveNow) {
                while (pushMoves.size() > pushPly) pushMoves.remove(pushMoves.size() - 1);
                pushMoves.add(move.toEngineStep());
                if (host.soundEnabled) host.playMoveSoundForBoard(pushBoard, redToMoveNow);
                showPushBoard(pushBaseFen, pushMoves, pushPly + 1, reversed, resumeAnalysis);
            }

            @Override public void onMessage(String message) {
                if (message != null && message.startsWith("已选中：")) return;
                if (message != null && message.length() > 0) {
                    Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onEditBoardChanged() {}
        });
        root.addView(pushBoard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(host);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.addView(host.pushNavButton("|◀", pushPly > 0,
                v -> showPushBoard(pushBaseFen, pushMoves, 0, reversed, resumeAnalysis)),
                host.pushNavLp());
        nav.addView(host.pushNavButton("←", pushPly > 0,
                v -> showPushBoard(pushBaseFen, pushMoves, pushPly - 1, reversed, resumeAnalysis)),
                host.pushNavLp());
        nav.addView(host.pushNavButton("→", pushPly < pushMoves.size(),
                v -> showPushBoard(pushBaseFen, pushMoves, pushPly + 1, reversed, resumeAnalysis)),
                host.pushNavLp());
        nav.addView(host.pushNavButton("▶|", pushPly < pushMoves.size(),
                v -> showPushBoard(pushBaseFen, pushMoves, pushMoves.size(), reversed, resumeAnalysis)),
                host.pushNavLp());
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(48));
        navLp.topMargin = host.dp(6);
        root.addView(nav, navLp);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button analyze = host.largeButton(
                restartTemporaryAnalysis ? "停止分析" : "🔍 分析", true);
        Button exit = host.largeButton("退出推演", true);
        LinearLayout analysisHost = new LinearLayout(host);
        analysisHost.setOrientation(LinearLayout.VERTICAL);
        analysisHost.setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(12));
        analyze.setOnClickListener(v -> toggleTemporaryAnalysis(pushBoard, analysisHost, analyze));
        exit.setOnClickListener(v -> {
            host.temporaryAnalysisRunning = false;
            host.temporaryAnalysisGeneration++;
            if (host.manualEngine != null) host.manualEngine.stopAnalysis();
            exitPushMode(reversed, resumeAnalysis);
        });
        actions.addView(analyze, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        actions.addView(exit, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(46));
        exitLp.topMargin = host.dp(8);
        root.addView(actions, exitLp);
        showTemporaryAnalysisPlaceholder(analysisHost);
        root.addView(analysisHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView page = new ScrollView(host);
        page.setFillViewport(true);
        page.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.appRoot.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (restartTemporaryAnalysis) {
            host.handler.post(() -> startTemporaryAnalysisNow(pushBoard, analysisHost, analyze));
        }
    }

    void toggleTemporaryAnalysis(final ChessBoardView tempBoard,
                                 final LinearLayout analysisHost, final Button button) {
        if (host.temporaryAnalysisRunning) {
            host.temporaryAnalysisRunning = false;
            host.temporaryAnalysisGeneration++;
            if (host.manualEngine != null) host.manualEngine.stopAnalysis();
            if (tempBoard != null) {
                tempBoard.setAnalysisArrows(
                        Collections.<ChessBoardView.AnalysisArrow>emptyList());
            }
            if (button != null) button.setText("🔍 分析");
            showTemporaryAnalysisPlaceholder(analysisHost);
            return;
        }
        startTemporaryAnalysisNow(tempBoard, analysisHost, button);
    }

    void showTemporaryAnalysisPlaceholder(LinearLayout analysisHost) {
        if (analysisHost == null) return;
        analysisHost.removeAllViews();
        TextView placeholder = new TextView(host);
        placeholder.setText("点击放大镜开始分析");
        placeholder.setTextSize(13);
        placeholder.setTextColor(host.globalBackgroundTextColor());
        analysisHost.addView(placeholder);
    }

    void startTemporaryAnalysisNow(final ChessBoardView tempBoard,
                                   final LinearLayout analysisHost, final Button button) {
        if (tempBoard == null || analysisHost == null || host.manualEngine == null) return;
        host.manualEngine.stopAnalysis();
        host.applyStoredManualOptions(host.manualEngine);
        host.temporaryAnalysisRunning = true;
        final int generation = ++host.temporaryAnalysisGeneration;
        if (button != null) button.setText("停止分析");
        final String fen = host.normalizeFen(tempBoard.getFen());
        final boolean red = tempBoard.isRedToMove();
        final boolean redAtBottom = !tempBoard.isReversed();
        final char[][] rootBoard = tempBoard.copyBoard();
        final int expected = Math.max(1, Math.min(host.getStoredManualOptionInt("MultiPV", 1),
                tempBoard.legalMovesForSideToMove().size()));
        final TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> pvMap =
                new TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>>();
        analysisHost.removeAllViews();
        TextView wait = new TextView(host);
        wait.setText("分析中……");
        wait.setTextColor(host.globalBackgroundTextColor());
        analysisHost.addView(wait);
        tempBoard.setShowArrow(host.showEngineArrows);
        tempBoard.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
        host.executeManualAnalysisTask(() -> host.manualEngine.startAnalysis(fen,
                Collections.<String>emptyList(), null, null,
                () -> host.temporaryAnalysisRunning
                        && generation == host.temporaryAnalysisGeneration,
                new PikafishEngine.AnalysisCallback() {
            @Override public void onInfo(PikafishEngine.EngineInfo info, String raw) {
                if (!host.temporaryAnalysisRunning
                        || generation != host.temporaryAnalysisGeneration
                        || info == null
                        || Math.max(1, info.multiPv) > expected) return;
                List<String> displayPv = host.pvForDisplay(info.pv);
                String cn;
                try {
                    cn = ChineseNotation.translatePv(copyBoardArray(rootBoard), displayPv);
                } catch (Exception e) {
                    cn = host.join(displayPv);
                }
                AnalysisDisplayEntry entry = new AnalysisDisplayEntry(info, cn, red,
                        host.computeNoCaptureMoveCount(), MainActivity.SITUATION_MATE_LIMIT);
                synchronized (pvMap) {
                    int multiPv = Math.max(1, info.multiPv);
                    TreeMap<Integer, AnalysisDisplayEntry> branch = pvMap.get(multiPv);
                    if (branch == null) {
                        branch = new TreeMap<Integer, AnalysisDisplayEntry>();
                        pvMap.put(multiPv, branch);
                    }
                    branch.put(info.depth, entry);
                }
                host.handler.post(() -> {
                    if (!host.temporaryAnalysisRunning
                            || generation != host.temporaryAnalysisGeneration) return;
                    renderTemporaryAnalysis(analysisHost, pvMap, expected, redAtBottom);
                    updateTemporaryAnalysisArrows(tempBoard, pvMap, expected);
                });
            }

            @Override public void onBestMove(String bestMove, String raw) {}

            @Override public void onError(String message) {
                host.handler.post(() -> {
                    if (generation != host.temporaryAnalysisGeneration) return;
                    host.temporaryAnalysisRunning = false;
                    if (button != null) button.setText("🔍 分析");
                    analysisHost.removeAllViews();
                    TextView error = new TextView(host);
                    error.setText("分析失败：" + message);
                    error.setTextColor(host.globalBackgroundTextColor());
                    analysisHost.addView(error);
                });
            }
        }));
    }

    private char[][] copyBoardArray(char[][] source) {
        if (source == null) return null;
        char[][] copy = new char[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null
                    : java.util.Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    private void updateTemporaryAnalysisArrows(ChessBoardView board,
            TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> map, int expected) {
        if (board == null) return;
        board.setShowArrow(host.showEngineArrows);
        if (!host.showEngineArrows) {
            board.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
            return;
        }
        ArrayList<ChessBoardView.AnalysisArrow> arrows =
                new ArrayList<ChessBoardView.AnalysisArrow>();
        synchronized (map) {
            if (expected <= 1) {
                TreeMap<Integer, AnalysisDisplayEntry> branch = map.get(1);
                if (branch != null && !branch.isEmpty()) {
                    host.appendAnalysisArrows(arrows, branch.lastEntry().getValue(), 0,
                            Math.max(1, host.getArrowStepCount()));
                }
            } else {
                for (int pv = 1; pv <= expected; pv++) {
                    TreeMap<Integer, AnalysisDisplayEntry> branch = map.get(pv);
                    if (branch != null && !branch.isEmpty()) {
                        host.appendAnalysisArrows(arrows, branch.lastEntry().getValue(), pv, 2);
                    }
                }
            }
        }
        board.setAnalysisArrows(arrows);
    }

    private void renderTemporaryAnalysis(LinearLayout analysisHost,
            TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> map,
            int expected, boolean redAtBottom) {
        ArrayList<EngineAnalysisPanel.DepthModule> modules =
                new ArrayList<EngineAnalysisPanel.DepthModule>();
        AnalysisDisplayEntry meta = null;
        int depth = Integer.MAX_VALUE;
        synchronized (map) {
            for (int pv = 1; pv <= expected; pv++) {
                TreeMap<Integer, AnalysisDisplayEntry> branch = map.get(pv);
                if (branch != null && !branch.isEmpty()) {
                    AnalysisDisplayEntry entry = branch.lastEntry().getValue();
                    if (meta == null) meta = entry;
                    depth = Math.min(depth, entry.depth);
                }
            }
            if (meta != null) {
                EngineAnalysisPanel.DepthModule module = new EngineAnalysisPanel.DepthModule(
                        depth, meta.timeText, meta.npsText, meta.nodesText, meta.hashFullText);
                for (int pv = 1; pv <= expected; pv++) {
                    TreeMap<Integer, AnalysisDisplayEntry> branch = map.get(pv);
                    AnalysisDisplayEntry entry = branch == null || branch.isEmpty()
                            ? null : branch.lastEntry().getValue();
                    module.pvs.add(entry == null
                            ? new EngineAnalysisPanel.PvItem(pv, 0, "-", "-", "等待PV" + pv, true)
                            : new EngineAnalysisPanel.PvItem(pv, entry.ownScoreValue(redAtBottom),
                                    entry.ownScoreText(redAtBottom), entry.ownWdlText(redAtBottom),
                                    entry.cnPv, entry.redToMoveAtRoot));
                }
                modules.add(module);
            }
        }
        EngineAnalysisPanel.render(host, analysisHost, "分析中……", modules);
    }

    void exitPushMode(boolean reversed, boolean resumeAnalysis) {
        host.temporaryAnalysisRunning = false;
        host.temporaryAnalysisGeneration++;
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.pushModeActive = false;
        host.showGameScreen();
        try {
            host.rebuildBoardToPly(host.currentPly);
            host.boardView.setReversed(reversed);
            host.boardView.setShowArrow(host.showEngineArrows);
        } catch (Exception e) {
            host.appendLog("退出推演后恢复局面失败：" + e.getMessage() + "\n");
        }
        host.updatePlayerLabels();
        host.refreshBoardInputState();
        host.updateGameContent();
        if (resumeAnalysis && host.analysisMode) {
            host.continueManualAnalysisForCurrentPosition(30L);
        }
        if (!host.selfAnalysisMode) {
            host.handler.postDelayed(host::maybeAutoMove, 120L);
        }
    }
}

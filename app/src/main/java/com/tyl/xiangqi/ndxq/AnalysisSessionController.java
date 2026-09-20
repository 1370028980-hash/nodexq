package com.tyl.xiangqi.ndxq;

import android.text.SpannableStringBuilder;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * 实时放大镜分析的会话控制器。
 *
 * <p>棋盘排版、分析箭头和立即出招仍由 {@link MainActivity} 负责；本类只管理
 * 搜索生命周期、批量回调以及停止后的不可变显示快照。</p>
 */
final class AnalysisSessionController {
    private static final long UI_BATCH_MS = 50L;

    private final MainActivity host;
    private final Object uiBatchLock = new Object();
    private final ArrayList<AnalysisUiUpdate> pendingUiUpdates =
            new ArrayList<AnalysisUiUpdate>();
    private boolean uiBatchPosted;
    private final Runnable uiBatchDrain = new Runnable() {
        @Override public void run() {
            drainUiBatch();
        }
    };
    private final Runnable analysisRestart = new Runnable() {
        @Override public void run() {
            start();
        }
    };

    AnalysisSessionController(MainActivity host) {
        this.host = host;
    }

    void toggle() {
        ChessBoardView board = host.boardView;
        if (board == null || board.isEditMode() || host.isRescoring || host.drawOfferInProgress) {
            return;
        }
        if (host.analysisMode) {
            stop(true);
            host.appendLog("分析已停止。\n");
            return;
        }
        if (!host.selfAnalysisMode) host.disqualifyRating("在评测对局中开启放大镜分析");
        if (host.computerRedBlackActive) {
            host.computerSideController.cancelPendingImmediateRequest();
            host.computerRedActive = false;
            host.computerBlackActive = false;
            host.computerRedBlackActive = false;
            host.computerMoveGeneration++;
            host.computerSideThinking = false;
            host.manualEngine.cancelSearch();
            host.computerSideController.resetRestrictions();
            host.styleComputerSideButtons();
        }
        host.analysisMode = true;
        // 正式对弈中的放大镜只是辅助提示，不改变本局历史战绩资格。
        host.analysisBannedRootMoves.clear();
        host.cancelImmediateMoveRequest();
        host.latestAnalysisBestMove = "";
        host.analysisPositionKey = host.currentPositionKey();
        host.styleAnalysisButton();
        board.setShowArrow(host.showEngineArrows);
        host.appendLog("分析已开启：go infinite。\n");
        start();
    }

    void stop(boolean clearArrow) {
        // 只停止放大镜本身，不能递增共享 operationGeneration，避免把电脑引擎的
        // 合法回调一并判成过期。
        host.manualAnalysisLaunchGeneration++;
        host.analysisMode = false;
        host.reviewRefreshGeneration++;
        host.reviewRefreshPly = -1;
        host.cancelImmediateMoveRequest();
        host.manualEngine.stopAnalysis();
        if (!host.isRescoring && host.rescoreEngine != null) host.rescoreEngine.stopAnalysis();
        freezeDisplay();
        if (clearArrow && host.boardView != null) {
            host.boardView.setShowArrow(host.showEngineArrows);
            host.boardView.setAnalysisArrows(
                    Collections.<ChessBoardView.AnalysisArrow>emptyList());
            host.updateRescoreReviewArrows();
        }
        host.styleAnalysisButton();
        host.persistCurrentSession();
    }

    void scheduleRestart(long delayMs) {
        host.handler.removeCallbacks(analysisRestart);
        host.handler.postDelayed(analysisRestart, Math.max(0L, delayMs));
    }

    boolean shouldPauseForComputerTurn() {
        return host.analysisMode && !host.selfAnalysisMode && !host.completedDuelGame
                && !host.gameOver && host.boardView != null
                && !host.boardView.isEditMode()
                && host.currentPly == host.engineMoves.size()
                && host.boardView.isRedToMove() == host.enginePlaysRed;
    }

    void pauseForComputerTurn() {
        if (!host.analysisMode) return;
        host.handler.removeCallbacks(analysisRestart);
        host.manualAnalysisLaunchGeneration++;
        host.reviewRefreshGeneration++;
        host.reviewRefreshPly = -1;
        clearPendingUiUpdates();
        host.cancelImmediateMoveRequest();
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.resetAnalysisPositionState();
        clearFrozenDisplay();
        host.engineContent = "电脑思考中……\n放大镜分析将在轮到玩家后自动继续。";
        if (host.boardView != null) {
            host.boardView.setShowArrow(host.showEngineArrows);
            host.boardView.setAnalysisArrows(
                    Collections.<ChessBoardView.AnalysisArrow>emptyList());
        }
        host.refreshEngineContentText();
        host.styleAnalysisButton();
    }

    void continueForCurrentPosition(long delayMs) {
        if (!host.analysisMode) return;
        if (shouldPauseForComputerTurn()) pauseForComputerTurn();
        else scheduleRestart(delayMs);
    }

    private boolean isLaunchValid(int launchGeneration, int operationGeneration) {
        return host.analysisMode
                && launchGeneration == host.manualAnalysisLaunchGeneration
                && operationGeneration == host.operationGeneration;
    }

    void start() {
        host.handler.removeCallbacks(analysisRestart);
        ChessBoardView board = host.boardView;
        if (!host.analysisMode || board == null || board.isEditMode()
                || host.isRescoring || host.drawOfferInProgress) return;
        if (shouldPauseForComputerTurn()) {
            pauseForComputerTurn();
            return;
        }
        clearFrozenDisplay();
        if (!host.manualEngine.hasUsableEngine()) {
            host.analysisMode = false;
            host.cancelImmediateMoveRequest();
            host.styleAnalysisButton();
            host.engineContent = "分析失败：未找到可用引擎。\n"
                    + host.manualEngine.getInstallHint();
            host.appendLog("[分析失败] 未找到可用引擎。"
                    + host.manualEngine.getInstallHint() + "\n");
            host.refreshEngineContentText();
            Toast.makeText(host, "未找到可用引擎，请先在引擎设置中选择",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 在 UI 发起新一代分析时立即停止旧搜索，避免高频导航/变招时旧搜索滞留。
        host.manualEngine.stopAnalysis();
        host.appendLog("[分析启动] " + host.manualEngine.getCurrentEngineLabel() + "。\n");
        final int launchGeneration = ++host.manualAnalysisLaunchGeneration;
        final int generation = ++host.operationGeneration;
        final String positionKey = host.currentPositionKey();
        final boolean redToMove = board.isRedToMove();
        final boolean preserveImmediateRequest = host.immediateMovePending
                && positionKey.equals(host.immediateMovePositionKey);
        host.analysisPositionKey = positionKey;
        host.latestAnalysisInfo = null;
        host.latestAnalysisBestMove = "";
        host.latestAnalysisDepth = 0;
        if (preserveImmediateRequest) {
            host.immediateMoveGeneration = generation;
            host.immediateMoveStopRequested = false;
        } else {
            host.cancelImmediateMoveRequest();
        }
        host.analysisEntries.clear();
        host.analysisPvEntries.clear();
        host.lastMultiPvModules = Collections.<EngineAnalysisPanel.DepthModule>emptyList();
        host.lastEngineUiAt = 0L;
        host.lastAnalysisLogAt = 0L;
        clearPendingUiUpdates();
        board.setShowArrow(host.showEngineArrows);
        board.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
        host.updateAnalysisArrows();
        host.startReviewRefreshForCurrentPly(generation, positionKey);

        List<Move> legalRootMoves = board.legalMovesForSideToMove();
        ArrayList<String> allowed = new ArrayList<String>();
        if (!host.analysisBannedRootMoves.isEmpty()) {
            for (Move move : legalRootMoves) {
                String step = move.toEngineStep().toLowerCase(Locale.ROOT);
                if (!host.analysisBannedRootMoves.contains(step)) allowed.add(step);
            }
        }
        List<String> searchMoves = allowed.isEmpty() ? null : allowed;
        int configuredMultiPv = host.getStoredManualOptionInt("MultiPV", 1);
        host.analysisConfiguredMultiPv = Math.max(1, configuredMultiPv);
        int availableRootMoves = searchMoves == null ? legalRootMoves.size() : searchMoves.size();
        host.analysisExpectedMultiPv = Math.max(1,
                Math.min(host.analysisConfiguredMultiPv, Math.max(1, availableRootMoves)));
        List<String> banned = host.analysisBannedRootMoves.isEmpty()
                ? null : new ArrayList<String>(host.analysisBannedRootMoves);
        host.engineContent = "分析中……";
        host.refreshEngineContentText();

        final String analysisBaseFen = host.baseFen;
        final List<String> analysisMoves = new ArrayList<String>(host.movesUpToCurrentPly());
        final List<String> analysisSearchMoves = searchMoves == null
                ? null : new ArrayList<String>(searchMoves);
        final List<String> analysisBannedMoves = banned == null
                ? null : new ArrayList<String>(banned);
        final char[][] analysisRootBoard = board.copyBoard();
        final int analysisNoCaptureMoveCount = host.computeNoCaptureMoveCount();
        final int analysisMoveLimit = host.engineOutputMoveLimit;
        final PikafishEngine.EngineInfo[] latestInfoForSearch =
                new PikafishEngine.EngineInfo[1];
        final PikafishEngine.AnalysisCallback callback = new PikafishEngine.AnalysisCallback() {
            @Override public void onInfo(final PikafishEngine.EngineInfo info,
                                         final String rawLine) {
                // 引擎线程可能在 stop 后仍排空最后几行 stdout；这些行不能更新
                // 立即出招缓存，否则页面切换后会把旧局面的 PV 当成当前主着。
                if (info == null || generation != host.operationGeneration
                        || !host.analysisMode) return;
                PikafishEngine.EngineInfo infoCopy = host.copyInfo(info);
                if (Math.max(1, info.multiPv) == 1) latestInfoForSearch[0] = infoCopy;
                if (host.immediateMovePending) return;
                AnalysisDisplayEntry entry = buildDisplayEntry(infoCopy, analysisRootBoard,
                        redToMove, analysisNoCaptureMoveCount, analysisMoveLimit);
                enqueueUiUpdate(generation, positionKey, redToMove, infoCopy, entry);
            }

            @Override public void onBestMove(final String bestMove, final String rawInfo) {
                host.handler.post(() -> {
                    if (generation != host.operationGeneration) return;
                    String normalizedBestMove = host.normalizeStep(bestMove);
                    if (host.immediateMovePending && normalizedBestMove.length() >= 4
                            && host.immediateMoveGeneration == generation
                            && positionKey.equals(host.immediateMovePositionKey)
                            && positionKey.equals(host.currentPositionKey())) {
                        if (latestInfoForSearch[0] != null) {
                            host.latestAnalysisInfo = host.copyInfo(latestInfoForSearch[0]);
                            host.latestAnalysisDepth = Math.max(host.latestAnalysisDepth,
                                    latestInfoForSearch[0].depth);
                        }
                        host.latestAnalysisBestMove = normalizedBestMove;
                        host.executeImmediateAnalysisMove();
                        return;
                    }
                    host.appendLog("[分析停止] bestmove=" + bestMove + "。\n");
                });
            }

            @Override public void onError(final String message) {
                host.handler.post(() -> {
                    if (generation != host.operationGeneration || !host.analysisMode) return;
                    host.cancelImmediateMoveRequest();
                    host.engineContent = "分析失败：\n" + message;
                    host.appendLog("[分析失败] " + message + "\n");
                    host.refreshEngineContentText();
                    Toast.makeText(host, "分析引擎尚未就绪，请查看日志",
                            Toast.LENGTH_LONG).show();
                });
            }
        };

        // startAnalysis 可能等待上一条 stop/bestmove，不能阻塞 UI 线程。
        host.executeManualAnalysisTask(() -> {
            if (!isLaunchValid(launchGeneration, generation)) return;
            host.manualEngine.startAnalysis(analysisBaseFen, analysisMoves,
                    analysisSearchMoves, analysisBannedMoves,
                    () -> isLaunchValid(launchGeneration, generation), callback);
        });
        if (preserveImmediateRequest) {
            host.handler.removeCallbacks(host.immediateMoveStopCheck);
            host.handler.post(host.immediateMoveStopCheck);
        }
    }

    private void enqueueUiUpdate(int generation, String positionKey,
                                 boolean redToMove, PikafishEngine.EngineInfo info,
                                 AnalysisDisplayEntry entry) {
        if (info == null) return;
        synchronized (uiBatchLock) {
            if (host.latestDepthOnly) {
                int multiPv = Math.max(1, info.multiPv);
                for (int i = pendingUiUpdates.size() - 1; i >= 0; i--) {
                    AnalysisUiUpdate pending = pendingUiUpdates.get(i);
                    if (pending.generation == generation && pending.info != null
                            && Math.max(1, pending.info.multiPv) == multiPv) {
                        pendingUiUpdates.remove(i);
                    }
                }
            }
            pendingUiUpdates.add(new AnalysisUiUpdate(
                    generation, positionKey, redToMove, info, entry));
            if (!uiBatchPosted) {
                uiBatchPosted = true;
                host.handler.postDelayed(uiBatchDrain, UI_BATCH_MS);
            }
        }
    }

    private void drainUiBatch() {
        ArrayList<AnalysisUiUpdate> batch;
        synchronized (uiBatchLock) {
            batch = new ArrayList<AnalysisUiUpdate>(pendingUiUpdates);
            pendingUiUpdates.clear();
            uiBatchPosted = false;
        }
        PikafishEngine.EngineInfo lastInfo = null;
        boolean lastRedToMove = true;
        for (AnalysisUiUpdate update : batch) {
            if (!host.analysisMode || update.generation != host.operationGeneration
                    || !update.positionKey.equals(host.currentPositionKey())) continue;
            PikafishEngine.EngineInfo info = update.info;
            int multiPv = Math.max(1, info.multiPv);
            if (multiPv > host.analysisExpectedMultiPv) continue;
            String first = host.normalizeStep(info.firstMove());
            AnalysisDisplayEntry entry = update.entry;
            if (first.length() >= 4 && entry != null) {
                TreeMap<Integer, AnalysisDisplayEntry> branch =
                        host.analysisPvEntries.get(multiPv);
                if (branch == null) {
                    branch = new TreeMap<Integer, AnalysisDisplayEntry>();
                    host.analysisPvEntries.put(multiPv, branch);
                }
                branch.put(info.depth, entry);
                if (multiPv == 1) {
                    host.latestAnalysisInfo = host.copyInfo(info);
                    host.latestAnalysisBestMove = first;
                    host.latestAnalysisDepth = Math.max(host.latestAnalysisDepth, info.depth);
                    host.analysisEntries.put(info.depth, entry);
                    host.updateCurrentSituationScoreFromAnalysis(
                            info, update.redToMove, update.positionKey);
                } else if (host.latestAnalysisBestMove.length() < 4) {
                    host.latestAnalysisInfo = host.copyInfo(info);
                    host.latestAnalysisBestMove = first;
                }
            }
            lastInfo = info;
            lastRedToMove = update.redToMove;
        }
        if (lastInfo != null) {
            long now = System.currentTimeMillis();
            host.lastEngineUiAt = now;
            host.updateAnalysisArrows();
            if (host.selectedGameTab == 2) host.refreshSituationChart();
            host.engineContent = host.analysisPvEntries.isEmpty()
                    ? "引擎分析中……\n当前深度：" + lastInfo.depth
                    : host.buildV68EngineAnalysisContent(lastRedToMove);
            host.refreshEngineContentText();
            if (now - host.lastAnalysisLogAt >= 700L) {
                host.lastAnalysisLogAt = now;
                host.appendLog("[分析] " + host.compactInfo(lastInfo) + "\n");
            }
        }
        synchronized (uiBatchLock) {
            if (!pendingUiUpdates.isEmpty() && !uiBatchPosted) {
                uiBatchPosted = true;
                host.handler.postDelayed(uiBatchDrain, UI_BATCH_MS);
            }
        }
    }

    private AnalysisDisplayEntry buildDisplayEntry(PikafishEngine.EngineInfo info,
                                                    char[][] rootBoard,
                                                    boolean redToMove,
                                                    int noCaptureMoveCount,
                                                    int moveLimit) {
        List<String> displayPv = info.pv;
        if (moveLimit > 0 && displayPv.size() > moveLimit) {
            displayPv = new ArrayList<String>(displayPv.subList(0, moveLimit));
        }
        String cnPv;
        try {
            cnPv = ChineseNotation.translatePv(rootBoard, displayPv);
        } catch (Exception e) {
            StringBuilder fallback = new StringBuilder();
            for (String move : displayPv) {
                if (fallback.length() > 0) fallback.append(' ');
                fallback.append(move);
            }
            cnPv = fallback.toString();
        }
        return new AnalysisDisplayEntry(info, cnPv, redToMove, noCaptureMoveCount,
                MainActivity.SITUATION_MATE_LIMIT);
    }

    void clearPendingUiUpdates() {
        host.handler.removeCallbacks(uiBatchDrain);
        synchronized (uiBatchLock) {
            pendingUiUpdates.clear();
            uiBatchPosted = false;
        }
    }

    void freezeDisplay() {
        host.analysisDisplayFrozen = true;
        host.frozenAnalysisExpectedMultiPv = Math.max(1, host.analysisExpectedMultiPv);
        host.frozenEngineContent = new SpannableStringBuilder(
                host.engineContent == null ? "" : host.engineContent);
        host.frozenCompactEngineContent = new SpannableStringBuilder(
                host.analysisEntries.isEmpty() ? host.engineContent
                        : host.buildCompactSinglePvContent());
        List<EngineAnalysisPanel.DepthModule> modulesAtStop = host.lastMultiPvModules;
        if (host.frozenAnalysisExpectedMultiPv > 1 && !host.analysisPvEntries.isEmpty()) {
            modulesAtStop = host.buildMultiPvModules();
        }
        host.frozenMultiPvModules = copyDepthModules(modulesAtStop);
    }

    void clearFrozenDisplay() {
        host.analysisDisplayFrozen = false;
        host.frozenAnalysisExpectedMultiPv = 1;
        host.frozenEngineContent = "";
        host.frozenCompactEngineContent = "";
        host.frozenMultiPvModules = Collections.<EngineAnalysisPanel.DepthModule>emptyList();
    }

    private List<EngineAnalysisPanel.DepthModule> copyDepthModules(
            List<EngineAnalysisPanel.DepthModule> source) {
        if (source == null || source.isEmpty()) {
            return Collections.<EngineAnalysisPanel.DepthModule>emptyList();
        }
        ArrayList<EngineAnalysisPanel.DepthModule> copy =
                new ArrayList<EngineAnalysisPanel.DepthModule>(source.size());
        for (EngineAnalysisPanel.DepthModule src : source) {
            if (src == null) continue;
            EngineAnalysisPanel.DepthModule dst = new EngineAnalysisPanel.DepthModule(
                    src.depth, src.timeText, src.npsText, src.nodesText, src.hashFullText);
            for (EngineAnalysisPanel.PvItem pv : src.pvs) {
                if (pv == null) continue;
                dst.pvs.add(new EngineAnalysisPanel.PvItem(
                        pv.index, pv.ownScoreValue, pv.scoreText, pv.wdlText,
                        pv.moveText, pv.redToMoveAtRoot));
            }
            copy.add(dst);
        }
        return copy.isEmpty()
                ? Collections.<EngineAnalysisPanel.DepthModule>emptyList() : copy;
    }
}

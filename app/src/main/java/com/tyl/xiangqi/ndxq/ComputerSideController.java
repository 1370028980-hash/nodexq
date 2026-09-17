package com.tyl.xiangqi.ndxq;

import android.text.SpannableStringBuilder;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 自主分析模式中电脑执红/黑的有限搜索控制器。 */
final class ComputerSideController {
    private final MainActivity host;
    private boolean enginePrepared;
    private final ArrayList<String> bannedRootMoves = new ArrayList<String>();
    private String bannedPositionKey = "";
    private volatile String latestBestMove = "";
    private volatile String latestBestMovePositionKey = "";
    private boolean immediatePending;
    private long immediatePendingAtMs;
    private final Runnable immediateRetry = new Runnable() {
        @Override public void run() { requestPendingImmediateMove(); }
    };

    ComputerSideController(MainActivity host) {
        this.host = host;
    }

    void resetRestrictions() {
        cancelImmediateRequest();
        bannedRootMoves.clear();
        bannedPositionKey = "";
        latestBestMove = "";
        latestBestMovePositionKey = "";
    }

    /** 页面/局面已切换时撤销尚未发出的立即出招请求。 */
    void cancelPendingImmediateRequest() {
        cancelImmediateRequest();
    }

    void toggle(boolean red) {
        if (!host.selfAnalysisMode || host.boardView == null) return;
        if (host.analysisMode) host.stopManualAnalysis(true);
        // 放大镜停止时会保留最后一帧作为冻结快照；电脑执红/黑是新的实时搜索，
        // 必须解除该快照，否则后续 info 会继续渲染到旧内容上。
        host.clearFrozenAnalysisDisplay();
        boolean wasComputerActive = host.computerRedBlackActive;
        if (red) host.computerRedActive = !host.computerRedActive;
        else host.computerBlackActive = !host.computerBlackActive;
        host.computerRedBlackActive = host.computerRedActive || host.computerBlackActive;
        host.engineContent = host.computerRedBlackActive
                ? "电脑执红/黑已开启，等待行棋……" : "";
        host.refreshEngineContentText();
        host.styleComputerSideButtons();
        if (host.computerRedBlackActive && !wasComputerActive) configureEngine();
        if ((red && !host.computerRedActive) || (!red && !host.computerBlackActive)
                || !host.computerRedBlackActive) {
            cancelImmediateRequest();
            host.computerMoveGeneration++;
            host.computerSideThinking = false;
            host.manualEngine.cancelSearch();
            if (!host.computerRedBlackActive) resetRestrictions();
            host.refreshBoardInputState();
        }
        host.appendLog((red ? "电脑执红" : "电脑执黑")
                + (red ? (host.computerRedActive ? "已开启" : "已关闭")
                        : (host.computerBlackActive ? "已开启" : "已关闭")) + "。\n");
        if (host.computerRedBlackActive) startMove();
    }

    /** 电脑执红/黑时的立即出招：收束当前限时搜索，沿用其最终 bestmove。 */
    void immediateMove() {
        if (!isComputerTurn()) return;
        immediatePending = true;
        immediatePendingAtMs = System.currentTimeMillis();
        requestPendingImmediateMove();
    }

    private void requestPendingImmediateMove() {
        if (!immediatePending || !isComputerTurn()) {
            immediatePending = false;
            return;
        }
        if (host.manualEngine.requestCurrentTimedSearchBestMove()) {
            immediatePending = false;
            return;
        }
        // requestBestMoveWithInfo 还在排队/握手时，等 go 真正发出后再 stop，
        // 避免把“立即出招”误变成取消后又重开普通搜索。
        if (System.currentTimeMillis() - immediatePendingAtMs < 1600L) {
            host.handler.postDelayed(immediateRetry, 8L);
        } else {
            immediatePending = false;
            host.computerMoveGeneration++;
            host.computerSideThinking = false;
            host.manualEngine.cancelSearch();
            startMove();
        }
    }

    private void cancelImmediateRequest() {
        immediatePending = false;
        host.handler.removeCallbacks(immediateRetry);
    }

    /** 电脑执红/黑时的变招：排除当前主着后重启当前局面的限时搜索。 */
    void forceAlternativeMove() {
        if (!isComputerTurn()) return;
        cancelImmediateRequest();
        String key = host.currentPositionKey();
        ensurePositionRestrictions(key);
        String best = latestBestMovePositionKey.equals(key)
                ? host.normalizeStep(latestBestMove) : "";
        if (best.length() >= 4 && !bannedRootMoves.contains(best)) {
            bannedRootMoves.add(best);
        }
        List<Move> legal = host.boardView.legalMovesForSideToMove();
        int remaining = 0;
        for (Move move : legal) {
            if (!bannedRootMoves.contains(move.toEngineStep().toLowerCase(Locale.ROOT))) remaining++;
        }
        if (remaining <= 0) {
            bannedRootMoves.clear();
            latestBestMove = "";
            latestBestMovePositionKey = "";
        }
        host.computerMoveGeneration++;
        host.computerSideThinking = false;
        host.manualEngine.cancelSearch();
        startMove();
    }

    private boolean isComputerTurn() {
        ChessBoardView board = host.boardView;
        if (!host.selfAnalysisMode || !host.computerRedBlackActive || board == null
                || board.isEditMode() || host.gameOver || host.completedDuelGame) return false;
        boolean red = board.isRedToMove();
        return (red && host.computerRedActive) || (!red && host.computerBlackActive);
    }

    private void ensurePositionRestrictions(String key) {
        if (key == null) key = "";
        if (!key.equals(bannedPositionKey)) {
            bannedPositionKey = key;
            bannedRootMoves.clear();
            latestBestMove = "";
            latestBestMovePositionKey = "";
        }
    }

    private void configureEngine() {
        if (host.manualEngine == null || enginePrepared) return;
        host.manualEngine.setVirtualEngineSlot("131");
        host.applyStoredManualOptions(host.manualEngine);
        enginePrepared = true;
    }

    void warmUp() {
        configureEngine();
        if (host.manualEngine != null) host.manualEngine.warmUp(null);
    }

    void startMove() {
        ChessBoardView board = host.boardView;
        if (!host.selfAnalysisMode || board == null || !host.computerRedBlackActive
                || board.isEditMode() || host.gameOver || host.computerSideThinking) return;
        boolean red = board.isRedToMove();
        if ((red && !host.computerRedActive) || (!red && !host.computerBlackActive)) return;
        final int generation = ++host.computerMoveGeneration;
        final String key = host.currentPositionKey();
        final boolean searchRed = red;
        ensurePositionRestrictions(key);
        latestBestMove = "";
        latestBestMovePositionKey = "";
        host.computerSideThinking = true;
        host.latestGameInfo = null;
        host.engineContent = "电脑执" + (searchRed ? "红" : "黑") + "，思考中……";
        host.refreshEngineContentText();
        host.refreshBoardInputState();
        ArrayList<String> allowedRootMoves = new ArrayList<String>();
        if (!bannedRootMoves.isEmpty()) {
            for (Move move : board.legalMovesForSideToMove()) {
                String step = move.toEngineStep().toLowerCase(Locale.ROOT);
                if (!bannedRootMoves.contains(step)) allowedRootMoves.add(step);
            }
        }
        host.manualEngine.requestBestMoveWithInfo(host.baseFen, host.movesUpToCurrentPly(),
                host.manualPlayLimit, allowedRootMoves.isEmpty() ? null : allowedRootMoves,
                bannedRootMoves.isEmpty() ? null : new ArrayList<String>(bannedRootMoves),
                new PikafishEngine.AnalysisCallback() {
            @Override public void onInfo(PikafishEngine.EngineInfo info, String raw) {
                if (info != null && Math.max(1, info.multiPv) == 1) {
                    if (generation != host.computerMoveGeneration) return;
                    String first = host.normalizeStep(info.firstMove());
                    if (first.length() >= 4) {
                        latestBestMove = first;
                        latestBestMovePositionKey = key;
                    }
                    host.handler.post(() -> {
                        if (generation != host.computerMoveGeneration
                                || host.boardView != board
                                || !key.equals(host.currentPositionKey())) return;
                        host.latestGameInfo = host.copyInfo(info);
                        host.engineContent = buildEngineContent(info, searchRed);
                        host.refreshEngineContentText();
                    });
                }
            }

            @Override public void onBestMove(String bestMove, String raw) {
                host.handler.post(() -> {
                    if (generation != host.computerMoveGeneration
                            || host.boardView != board
                            || !key.equals(host.currentPositionKey())) return;
                    cancelImmediateRequest();
                    host.computerSideThinking = false;
                    host.refreshBoardInputState();
                    String step = host.normalizeStep(bestMove);
                    if (step.length() < 4) return;
                    try {
                        board.playMove(Move.fromEngineStep(step));
                    } catch (Exception ignored) {}
                });
            }

            @Override public void onError(String message) {
                host.handler.post(() -> {
                    if (generation != host.computerMoveGeneration
                            || host.boardView != board
                            || !key.equals(host.currentPositionKey())) return;
                    cancelImmediateRequest();
                    host.computerSideThinking = false;
                    host.refreshBoardInputState();
                    Toast.makeText(host, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private CharSequence buildEngineContent(PikafishEngine.EngineInfo info,
                                             boolean redToMoveAtRoot) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("电脑执").append(redToMoveAtRoot ? "红\n" : "黑\n");
        if (info == null) {
            sb.append("引擎分析中……");
            return sb;
        }
        List<String> displayPv = host.pvForDisplay(info.pv);
        String cnPv;
        try {
            cnPv = ChineseNotation.translatePv(
                    host.boardView == null ? null : host.boardView.copyBoard(), displayPv);
        } catch (Exception ignored) {
            cnPv = host.join(displayPv);
        }
        EngineAnalysisPresentation.appendFullEntry(host, sb,
                new AnalysisDisplayEntry(info, cnPv, redToMoveAtRoot,
                        host.computeNoCaptureMoveCount(), MainActivity.SITUATION_MATE_LIMIT),
                host.boardView == null || !host.boardView.isReversed());
        return sb;
    }
}

package com.tyl.xiangqi.ndxq;

import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;

import java.util.Collections;
import java.util.Locale;

/** 分析模式下“立即出招/变招”的请求与当前局面校验。 */
final class AnalysisMoveController {
    private static final long RETRY_MS = 8L;

    private final MainActivity host;

    AnalysisMoveController(MainActivity host) {
        this.host = host;
    }

    void immediateMove() {
        ChessBoardView board = host.boardView;
        if (board == null || board.isEditMode() || host.gameOver
                || host.completedDuelGame || host.isRescoring) return;
        if (!host.analysisMode && host.selfAnalysisMode && host.computerRedBlackActive) {
            host.computerSideController.immediateMove();
            return;
        }
        if (!host.analysisMode) {
            Toast.makeText(host, "请先点击“分析”", Toast.LENGTH_SHORT).show();
            return;
        }
        if (host.shouldPauseManualAnalysisForComputerTurn()) {
            // 电脑回合时放大镜已暂停，没有可收束的搜索，保持静默。
            return;
        }
        if (host.immediateMovePending) return;

        String currentKey = host.currentPositionKey();
        host.immediateMovePending = true;
        host.immediateMoveStopRequested = false;
        host.immediateMoveGeneration = host.operationGeneration;
        host.immediateMovePositionKey = currentKey;
        host.clearPendingAnalysisUiUpdates();
        if (!host.analysisPositionKey.equals(currentKey)) {
            host.scheduleManualAnalysisRestart(0L);
        }
        // 复用用户点击前已经持续进行的 go infinite 搜索；按钮按下即 stop。
        host.maybeRequestImmediateAnalysisBestMove();
    }

    void maybeRequestImmediateAnalysisBestMove() {
        host.handler.removeCallbacks(host.immediateMoveStopCheck);
        if (!host.immediateMovePending || host.immediateMoveStopRequested
                || !host.analysisMode || host.boardView == null
                || host.boardView.isEditMode() || host.gameOver
                || host.completedDuelGame || host.isRescoring) return;
        if (host.immediateMoveGeneration != host.operationGeneration
                || !host.immediateMovePositionKey.equals(host.currentPositionKey())) {
            cancelImmediateMoveRequest();
            return;
        }
        if (!host.analysisPositionKey.equals(host.immediateMovePositionKey)) {
            host.scheduleManualAnalysisRestart(0L);
            host.handler.postDelayed(host.immediateMoveStopCheck, RETRY_MS);
            return;
        }

        if (host.manualEngine.requestCurrentAnalysisBestMove()) {
            host.immediateMoveStopRequested = true;
            return;
        }
        // 引擎线程可能仍在完成 position/go；以一帧内的短间隔重试。
        host.handler.postDelayed(host.immediateMoveStopCheck, RETRY_MS);
    }

    void executeImmediateAnalysisMove() {
        ChessBoardView board = host.boardView;
        if (!host.analysisMode || board == null || board.isEditMode()
                || host.gameOver || host.completedDuelGame || host.isRescoring
                || host.immediateMoveGeneration != host.operationGeneration
                || !host.immediateMovePositionKey.equals(host.currentPositionKey())
                || !host.analysisPositionKey.equals(host.currentPositionKey())
                || host.latestAnalysisBestMove.length() < 4) return;
        String step = host.latestAnalysisBestMove;
        Integer redScore = host.latestAnalysisInfo == null || !host.latestAnalysisInfo.hasScore
                ? null : host.redScoreFromInfo(host.latestAnalysisInfo, board.isRedToMove());
        int matePly = host.latestAnalysisInfo != null && host.latestAnalysisInfo.mateScore
                ? Math.abs(host.latestAnalysisInfo.score) : 0;
        cancelImmediateMoveRequest();
        // “立即出招”仍属于放大镜辅助功能，不取消正式对局的战绩资格。
        host.operationGeneration++;
        board.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
        host.playEngineStep(step, "立即出招", redScore, matePly);
    }

    void cancelImmediateMoveRequest() {
        host.handler.removeCallbacks(host.immediateMoveStopCheck);
        host.immediateMovePending = false;
        host.immediateMoveStopRequested = false;
        host.immediateMoveGeneration = -1;
        host.immediateMovePositionKey = "";
    }

    void forceAlternativeMove() {
        ChessBoardView board = host.boardView;
        if (board == null || board.isEditMode() || host.gameOver
                || host.completedDuelGame || host.isRescoring) return;
        if (!host.analysisMode && host.selfAnalysisMode && host.computerRedBlackActive) {
            host.computerSideController.forceAlternativeMove();
            return;
        }
        if (!host.analysisMode) {
            Toast.makeText(host, "请先点击“分析”，并等待主选着法出现",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!host.analysisPositionKey.equals(host.currentPositionKey())
                || host.latestAnalysisBestMove.length() < 4) {
            Toast.makeText(host, "正在等待当前局面的主选着法", Toast.LENGTH_SHORT).show();
            return;
        }
        // 高频交替点击时采用“最后一次操作生效”。
        cancelImmediateMoveRequest();
        String banned = host.latestAnalysisBestMove;
        if (!host.analysisBannedRootMoves.contains(banned)) {
            host.analysisBannedRootMoves.add(banned);
        }
        int remaining = 0;
        for (Move move : board.legalMovesForSideToMove()) {
            if (!host.analysisBannedRootMoves.contains(
                    move.toEngineStep().toLowerCase(Locale.ROOT))) remaining++;
        }
        if (remaining <= 0) {
            host.analysisBannedRootMoves.remove(banned);
            Toast.makeText(host, "没有其他合法分支可供分析", Toast.LENGTH_SHORT).show();
            return;
        }
        host.appendLog("变招：排除根着 " + banned + "，继续 go infinite。\n");
        host.startManualAnalysis();
    }
}

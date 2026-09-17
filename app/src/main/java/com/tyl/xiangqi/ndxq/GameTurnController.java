package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 提和、认输、悔棋等对局操作的状态校验与执行编排。 */
final class GameTurnController {
    private final MainActivity host;

    GameTurnController(MainActivity host) {
        this.host = host;
    }

    void offerDraw() {
        if (host.boardView == null || host.boardView.isEditMode()
                || host.gameOver || host.completedDuelGame || host.isRescoring) return;
        if (host.evaluationMode && host.currentPly / 2 < 25) return;
        if (host.selfAnalysisMode) {
            Toast.makeText(host, "自主分析模式下无需提和", Toast.LENGTH_SHORT).show();
            return;
        }
        if (host.autoMoveInProgress || host.engineThinking || host.drawOfferInProgress) {
            Toast.makeText(host, "请等待当前引擎操作结束", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(host)
                .setTitle("提和")
                .setMessage("确定要提和吗？")
                .setPositiveButton("确认", (d, w) -> startDrawOffer())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 用户确认提和后再次校验状态，避免弹窗停留期间局面已经发生变化。 */
    private void startDrawOffer() {
        if (host.boardView == null || host.boardView.isEditMode()
                || host.gameOver || host.completedDuelGame || host.isRescoring
                || host.selfAnalysisMode || host.autoMoveInProgress || host.engineThinking
                || host.drawOfferInProgress) return;
        if (host.evaluationMode && host.currentPly / 2 < 25) return;
        final boolean resumeAnalysis = host.analysisMode;
        if (host.analysisMode) host.manualEngine.stopAnalysis();
        host.drawOfferInProgress = true;
        host.refreshBoardInputState();
        final int generation = ++host.drawGeneration;
        final String positionKey = host.currentPositionKey();
        final boolean redToMove = host.boardView.isRedToMove();
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        host.drawEngine.clearSessionOptions();
        host.drawEngine.setSessionOption("Threads", "8");
        host.drawEngine.setSessionOption("MultiPV", "1");
        host.drawEngine.setSessionOption("UCI_ShowWDL", "true");
        host.appendLog("玩家提和：使用 8 线程思考 2 秒。\n");
        host.showDrawAnalysisDialog();
        host.drawEngine.requestBestMoveWithInfo(host.baseFen, host.movesUpToCurrentPly(),
                PikafishEngine.SearchLimit.movetime(2000),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        latest[0] = host.copyInfo(info);
                    }

                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            if (generation != host.drawGeneration
                                    || !positionKey.equals(host.currentPositionKey())) return;
                            host.drawOfferInProgress = false;
                            host.dismissDrawAnalysisDialog();
                            host.refreshBoardInputState();
                            int redScore = latest[0] == null || !latest[0].hasScore
                                    ? MainActivity.SITUATION_MATE_LIMIT
                                    : host.redScoreFromInfo(latest[0], redToMove);
                            int computerScore = host.enginePlaysRed ? redScore : -redScore;
                            host.appendLog("提和评估：电脑视角 " + computerScore + " 分。\n");
                            if (computerScore <= 200) {
                                host.finishGame("同意和棋", GameEndType.AGREED_DRAW);
                            } else {
                                new AlertDialog.Builder(host)
                                        .setTitle("提和")
                                        .setMessage("电脑认为目前有优势！")
                                        .setPositiveButton("确定", null)
                                        .show();
                                if (resumeAnalysis && host.analysisMode) {
                                    host.handler.postDelayed(host::startManualAnalysis, 120L);
                                }
                                host.handler.postDelayed(host::maybeAutoMove, 180L);
                            }
                        });
                    }

                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            if (generation != host.drawGeneration) return;
                            host.drawOfferInProgress = false;
                            host.dismissDrawAnalysisDialog();
                            host.refreshBoardInputState();
                            host.appendLog("提和评估失败：" + message + "\n");
                            new AlertDialog.Builder(host)
                                    .setTitle("提和")
                                    .setMessage("电脑认为目前有优势！")
                                    .setPositiveButton("确定", null)
                                    .show();
                            if (resumeAnalysis && host.analysisMode) {
                                host.handler.postDelayed(host::startManualAnalysis, 120L);
                            }
                        });
                    }
                });
    }

    void confirmResign() {
        if (host.boardView == null || host.gameOver || host.completedDuelGame || host.isRescoring) return;
        if (host.evaluationMode && host.currentPly / 2 < 5) return;
        if (host.selfAnalysisMode) {
            Toast.makeText(host, "自主分析模式下无需认输", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(host)
                .setTitle("认输")
                .setMessage("确定要认输吗？")
                .setPositiveButton("确认", (d, w) -> {
                    boolean redWins = host.enginePlaysRed;
                    host.finishGame(redWins ? "黑方认输，红方胜了" : "红方认输，黑方胜了",
                            redWins ? GameEndType.RED_WIN : GameEndType.BLACK_WIN);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    boolean checkNoAttackDraw(char[][] board) {
        if (board == null) return false;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char piece = board[r][c];
                char lower = Character.toLowerCase(piece);
                if (lower == 'c' || lower == 'n' || lower == 'r' || lower == 'p') return false;
            }
        }
        return true;
    }

    void undoToPreviousHumanTurn() {
        if (host.boardView == null || host.boardView.isEditMode()
                || host.completedDuelGame || host.gameOver || host.isRescoring) return;
        if (host.currentPly <= 0 || host.engineMoves.isEmpty()) {
            Toast.makeText(host, "当前没有可悔的棋", Toast.LENGTH_SHORT).show();
            return;
        }
        int target = -1;
        for (int ply = host.currentPly - 1; ply >= 0; ply--) {
            if (isHumanTurnAtPly(ply)) {
                target = ply;
                break;
            }
        }
        if (target < 0 && !host.selfAnalysisMode && host.enginePlaysRed
                && host.currentPly == 1 && host.redToMoveAtPly(0)) {
            // 电脑执红并完成全局第一步时，允许特殊悔回初始局面。
            target = 0;
        }
        if (target < 0) {
            Toast.makeText(host, "当前没有更早的玩家回合", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasBranchesAtOrAfter(target)) {
            final int undoTarget = target;
            new AlertDialog.Builder(host)
                    .setMessage("悔棋会删除之后所有衍生分支，是否悔棋？")
                    .setPositiveButton("确定", (d, w) -> executeUndoTo(undoTarget))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        executeUndoTo(target);
    }

    private boolean hasBranchesAtOrAfter(int target) {
        for (Map.Entry<Integer, List<ManualVariation>> entry
                : host.manualVariations.entrySet()) {
            if (entry.getKey() != null && entry.getKey() >= target
                    && entry.getValue() != null && !entry.getValue().isEmpty()) return true;
        }
        return false;
    }

    private void executeUndoTo(int target) {
        host.stopSearchForPositionChange();
        host.truncateListsTo(target);
        ArrayList<Integer> keys = new ArrayList<Integer>(host.manualVariations.keySet());
        for (Integer key : keys) {
            if (key != null && key >= target) host.manualVariations.remove(key);
        }
        keys = new ArrayList<Integer>(host.activeBranchLabels.keySet());
        for (Integer key : keys) {
            if (key != null && key >= target) host.activeBranchLabels.remove(key);
        }
        host.currentPly = target;
        host.rebuildBoardToPly(target);
        host.gameOver = false;
        host.terminalDialogShown = false;
        host.gameResultTag = "*";
        host.updatePlayerLabels();
        host.updateGameContent();
        host.sixtyMoveDrawArmedPly = host.computeNoCaptureMoveCountAtPly(host.currentPly) == 119
                ? host.currentPly + 1 : -1;
        host.persistCurrentSession();
        host.appendLog("悔棋：已回退到第 " + target + " 手后的玩家回合，后续棋谱已删除。\n");
        if (!host.selfAnalysisMode && host.boardView.isRedToMove() == host.enginePlaysRed) {
            host.handler.postDelayed(host::maybeAutoMove, 220L);
        }
    }

    private boolean isHumanTurnAtPly(int ply) {
        if (host.selfAnalysisMode) return true;
        return host.redToMoveAtPly(ply) != host.enginePlaysRed;
    }
}

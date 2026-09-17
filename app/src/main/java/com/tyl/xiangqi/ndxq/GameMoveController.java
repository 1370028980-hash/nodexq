package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 棋盘落子、棋谱提交、分支切换与落子后的对局推进。 */
final class GameMoveController {
    private final MainActivity host;

    GameMoveController(MainActivity host) {
        this.host = host;
    }

    void playEngineStep(String step, String source, Integer scoreRed, int matePly) {
        try {
            host.pendingMoveScoreRed = scoreRed;
            host.pendingMoveMatePly = matePly;
            Move move = Move.fromEngineStep(step);
            String notation = ChineseNotation.translate(host.boardView.copyBoard(), step, false);
            host.appendLog(source + " 走 " + notation + "（" + step + "）。\n");
            if (!host.boardView.playMove(move)) {
                host.pendingMoveScoreRed = null;
                host.pendingMoveMatePly = 0;
                fallbackLegalMove(source + "着法不合法");
            }
        } catch (Exception e) {
            host.pendingMoveScoreRed = null;
            host.pendingMoveMatePly = 0;
            host.appendLog(source + "着法解析失败：" + e.getMessage() + "。\n");
            fallbackLegalMove(source + "解析回退");
        }
    }

    void onMoveMade(Move move, char movedPiece, char capturedPiece,
                    String fenAfterMove, boolean redToMoveNow) {
        if (host.boardView == null) return;
        if (host.boardView.isEditMode()) {
            host.updatePlayerLabels();
            return;
        }
        boolean postGameSandbox = host.completedDuelGame && !host.selfAnalysisMode
                && !host.evaluationMode;
        String step = move.toEngineStep().toLowerCase(Locale.ROOT);
        if (host.currentPly < host.engineMoves.size()
                && step.equals(host.engineMoves.get(host.currentPly))) {
            host.currentPly++;
            host.pendingMoveScoreRed = null;
            host.pendingMoveMatePly = 0;
            host.resetAnalysisPositionState();
            host.playMoveSoundForCurrentPosition(redToMoveNow);
            host.updatePlayerLabels();
            host.refreshBoardInputState();
            host.autoFollowLatestMove = true;
            host.updateGameContent();
            if (!host.completedDuelGame && checkTerminalPosition(capturedPiece, redToMoveNow)) return;
            if (!host.completedDuelGame && !host.selfAnalysisMode
                    && host.checkNoAttackDraw(host.boardView.copyBoard())) {
                host.finishGame("无进攻子力，和棋！", GameEndType.AGREED_DRAW);
                return;
            }
            if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(0L);
            if (host.selfAnalysisMode && host.computerRedBlackActive) {
                host.handler.post(host::startComputerSideMove);
            }
            host.handler.postDelayed(host::maybeAutoMove, 200L);
            return;
        }

        int existingVariationIndex = host.findVariationIndexByFirstStep(host.currentPly, step);
        if (existingVariationIndex >= 0) {
            host.pendingMoveScoreRed = null;
            host.pendingMoveMatePly = 0;
            host.switchToVariation(host.currentPly, existingVariationIndex);
            if (host.selfAnalysisMode && host.computerRedBlackActive) {
                host.handler.post(host::startComputerSideMove);
            }
            return;
        }

        final String readable = ChineseNotation.translate(host.boardView.copyBoard(), step, true);
        final int score = host.selfAnalysisMode
                ? (host.pendingMoveScoreRed == null ? host.lastKnownScore()
                        : host.pendingMoveScoreRed)
                : 0;
        final int matePly = host.selfAnalysisMode ? host.pendingMoveMatePly : 0;
        host.pendingMoveScoreRed = null;
        host.pendingMoveMatePly = 0;

        if (host.currentPly < host.engineMoves.size() && !host.selfAnalysisMode
                && postGameSandbox) {
            final int overwriteNode = host.currentPly;
            host.stopSearchForPositionChange();
            deleteAllBranchesAtOrAfter(overwriteNode);
            host.truncateListsTo(overwriteNode);
            host.rebuildBoardToPly(overwriteNode);
            if (!host.boardView.playMoveSilently(move)) {
                Toast.makeText(host, "新走法已失效，请重新操作", Toast.LENGTH_SHORT).show();
                return;
            }
            commitNewMove(step, readable, 0, 0, capturedPiece, host.boardView.isRedToMove());
            host.refreshBoardInputState();
            return;
        }

        if (host.currentPly < host.engineMoves.size() && !host.selfAnalysisMode) {
            final int overwriteNode = host.currentPly;
            host.stopSearchForPositionChange();
            host.rebuildBoardToPly(overwriteNode);
            host.updatePlayerLabels();
            host.updateGameContent();
            new AlertDialog.Builder(host)
                    .setMessage("新走法会覆盖旧走法，是否继续？")
                    .setPositiveButton("确定", (d, w) -> {
                        if (host.boardView == null || host.selfAnalysisMode
                                || host.currentPly != overwriteNode) return;
                        deleteAllBranchesAtOrAfter(overwriteNode);
                        host.truncateListsTo(overwriteNode);
                        host.rebuildBoardToPly(overwriteNode);
                        if (!host.boardView.playMoveSilently(move)) {
                            Toast.makeText(host, "新走法已失效，请重新操作", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        commitNewMove(step, readable, 0, 0, capturedPiece,
                                host.boardView.isRedToMove());
                    })
                    .setNegativeButton("取消", (d, w) -> {
                        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
                    })
                    .show();
            return;
        }

        if (host.currentPly < host.engineMoves.size()) {
            host.captureFutureAsVariation(host.currentPly);
            host.truncateListsTo(host.currentPly);
        }
        commitNewMove(step, readable, score, matePly, capturedPiece, redToMoveNow);
    }

    void fallbackLegalMove(String reason) {
        if (host.boardView == null || (host.gameOver && !host.completedDuelGame)) return;
        List<Move> legal = host.boardView.legalMovesForSideToMove();
        if (legal.isEmpty()) {
            host.appendLog(reason + "：当前无合法着法。\n");
            finishWinForSideThatJustMoved(host.boardView.isRedToMove());
            return;
        }
        Move fallback = legal.get(0);
        host.pendingMoveScoreRed = host.lastKnownScore();
        host.pendingMoveMatePly = host.lastKnownMatePly();
        host.appendLog(reason + "：执行第一步合法着法 " + fallback.toEngineStep() + "。\n");
        host.boardView.playMove(fallback);
    }

    private void deleteAllBranchesAtOrAfter(int ply) {
        ArrayList<Integer> keys = new ArrayList<Integer>(host.manualVariations.keySet());
        for (Integer key : keys) {
            if (key != null && key >= ply) host.manualVariations.remove(key);
        }
        keys = new ArrayList<Integer>(host.activeBranchLabels.keySet());
        for (Integer key : keys) {
            if (key != null && key >= ply) host.activeBranchLabels.remove(key);
        }
    }

    private void commitNewMove(String step, String readable, int score, int matePly,
                               char capturedPiece, boolean redToMoveNow) {
        host.engineMoves.add(step);
        host.readableMoves.add(readable == null || readable.trim().length() == 0
                ? step : readable.trim());
        host.moveComments.add("");
        host.redPerspectiveScores.add(score);
        host.scoreMatePlies.add(matePly);
        host.scoreKnown.add(false);
        host.rescoreRecommendedMoves.add("");
        host.rescoreScoreKnown.add(false);
        // 固定 100ms 局势评分通常先于下一手落子返回；按“走前局面”取出暂存推荐。
        host.applyPendingSituationRecommendationForMove(host.engineMoves.size() - 1);
        host.currentPly = host.engineMoves.size();
        host.updateQingyunDrawRule();
        host.playMoveSoundForCurrentPosition(redToMoveNow);
        if (!host.completedDuelGame) host.gameResultTag = "*";
        host.resetAnalysisPositionState();
        host.appendLog((redToMoveNow ? "黑方" : "红方") + "完成第 " + host.currentPly + " 手："
                + host.readableMoves.get(host.readableMoves.size() - 1) + "（" + step + "）。\n");
        host.updatePlayerLabels();
        host.refreshBoardInputState();
        host.autoFollowLatestMove = true;
        host.updateGameContent();
        if (!host.completedDuelGame) host.persistCurrentSession();
        if (!host.completedDuelGame && checkTerminalPosition(capturedPiece, redToMoveNow)) return;
        if (!host.completedDuelGame && !host.selfAnalysisMode
                && host.checkSixtyMoveDrawAfterMove(capturedPiece)) return;
        if (!host.completedDuelGame && !host.selfAnalysisMode
                && host.checkNoAttackDraw(host.boardView.copyBoard())) {
            host.finishGame("无进攻子力，和棋！", GameEndType.AGREED_DRAW);
            return;
        }
        if (!host.selfAnalysisMode && !host.completedDuelGame) {
            host.persistCurrentSession();
            host.requestSituationScoreForPly(host.currentPly - 1);
        }
        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(0L);
        if (host.selfAnalysisMode && host.computerRedBlackActive) {
            host.handler.post(host::startComputerSideMove);
        }
        host.handler.postDelayed(host::maybeAutoMove, 220L);
    }

    private boolean checkTerminalPosition(char capturedPiece, boolean redToMoveNow) {
        if (host.gameOver || host.boardView == null) return true;
        boolean kingCaptured = capturedPiece == 'K' || capturedPiece == 'k';
        boolean noLegal = XiangqiRules.generateLegalMoves(host.boardView.copyBoard(), redToMoveNow)
                .isEmpty();
        if (!kingCaptured && !noLegal) return false;
        finishWinForSideThatJustMoved(redToMoveNow);
        return true;
    }

    private void finishWinForSideThatJustMoved(boolean redToMoveNow) {
        if (redToMoveNow) {
            host.finishGame("黑方胜！", GameEndType.BLACK_WIN);
        } else {
            host.finishGame("红方胜！", GameEndType.RED_WIN);
        }
    }
}

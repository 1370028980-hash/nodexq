package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.book.ObkBook;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

import java.util.ArrayList;
import java.util.Random;

/** 正式人机对弈的自动出招、开局库选着和引擎回调协调。 */
final class DuelEngineController {
    private final MainActivity host;
    private final Random openingRandom = new Random();

    DuelEngineController(MainActivity host) {
        this.host = host;
    }

    void maybeAutoMove() {
        if (!canAutoMove()) return;
        if (host.currentPly != host.engineMoves.size() || host.autoMoveInProgress) return;
        if (host.boardView.isRedToMove() != host.enginePlaysRed) return;
        host.handler.postDelayed(() -> {
            if (!canAutoMove()) return;
            if (host.boardView.isRedToMove() == host.enginePlaysRed && !host.autoMoveInProgress) {
                computerMove();
            }
        }, 300L);
    }

    private boolean canAutoMove() {
        return !host.pushModeActive && !host.selfAnalysisMode && host.gameScreenVisible
                && host.boardView != null && !host.boardView.isEditMode()
                && !(host.completedDuelGame && !host.postGameSandboxActive)
                && !(host.gameOver && !host.completedDuelGame)
                && !host.isRescoring && !host.drawOfferInProgress;
    }

    private void computerMove() {
        if (!canAutoMove() || host.autoMoveInProgress) return;
        if (host.analysisMode) host.pauseManualAnalysisForComputerTurn();
        if (tryOpeningBookMove()) return;

        host.autoMoveInProgress = true;
        host.engineThinking = true;
        host.updatePlayerLabels();
        host.latestGameInfo = null;
        final DifficultyProfile profile = host.currentDifficulty();
        final String difficultyName = host.currentDifficultyDisplayName();
        final int generation = ++host.operationGeneration;
        final String positionKey = host.currentPositionKey();
        final boolean redToMove = host.boardView.isRedToMove();
        host.updateQingyunDrawRule();
        host.appendLog("[" + difficultyName + "] " + profile.limit.goCommand() + " 开始。\n");

        host.gameEngine.requestBestMoveWithInfo(host.baseFen, host.movesUpToCurrentPly(),
                profile.limit, new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(final PikafishEngine.EngineInfo info,
                                                 final String rawLine) {
                        host.handler.post(() -> {
                            if (generation != host.operationGeneration
                                    || !positionKey.equals(host.currentPositionKey())) return;
                            host.latestGameInfo = host.copyInfo(info);
                            long now = System.currentTimeMillis();
                            if (now - host.lastAnalysisLogAt >= 700L) {
                                host.lastAnalysisLogAt = now;
                                host.appendLog("[" + difficultyName + "] "
                                        + host.compactInfo(info) + "\n");
                            }
                        });
                    }

                    @Override public void onBestMove(final String bestMove, final String rawInfo) {
                        host.handler.post(() -> {
                            if (generation != host.operationGeneration
                                    || !positionKey.equals(host.currentPositionKey())) return;
                            host.autoMoveInProgress = false;
                            host.engineThinking = false;
                            host.updatePlayerLabels();
                            String step = host.normalizeStep(bestMove);
                            if (step.length() < 4) {
                                host.fallbackLegalMove(difficultyName + "未返回有效着法");
                                return;
                            }
                            Integer score = host.latestGameInfo == null
                                    || !host.latestGameInfo.hasScore ? null
                                    : host.redScoreFromInfo(host.latestGameInfo, redToMove);
                            int matePly = host.latestGameInfo != null && host.latestGameInfo.mateScore
                                    ? Math.abs(host.latestGameInfo.score) : 0;
                            host.playEngineStep(step, difficultyName, score, matePly);
                        });
                    }

                    @Override public void onError(final String message) {
                        host.handler.post(() -> {
                            if (generation != host.operationGeneration) return;
                            host.autoMoveInProgress = false;
                            host.engineThinking = false;
                            host.updatePlayerLabels();
                            host.appendLog("[" + difficultyName + "失败] " + message + "\n");
                            host.fallbackLegalMove(difficultyName + "引擎占位回退");
                        });
                    }
                });
    }

    private boolean tryOpeningBookMove() {
        DifficultyProfile profile = host.currentDifficulty();
        String difficultyName = host.currentDifficultyDisplayName();
        if (!host.openingBookReady || host.openingBook == null
                || host.currentPly >= profile.outBookRounds * 2) return false;
        try {
            ObkBook.QueryResult result = host.openingBook.query(host.boardView.getFen());
            if (result.candidates.isEmpty()) return false;
            ArrayList<ObkBook.Candidate> choices = new ArrayList<ObkBook.Candidate>();
            ArrayList<String> seen = new ArrayList<String>();
            for (ObkBook.Candidate candidate : result.candidates) {
                if (candidate == null || candidate.move == null || seen.contains(candidate.move)) continue;
                try {
                    Move move = Move.fromEngineStep(candidate.move);
                    if (XiangqiRules.isLegalMove(host.boardView.copyBoard(), move)) {
                        choices.add(candidate);
                        seen.add(candidate.move);
                    }
                } catch (Exception ignored) {
                }
            }
            if (choices.isEmpty()) return false;
            ObkBook.Candidate selected = choices.get(openingRandom.nextInt(choices.size()));
            Move move = Move.fromEngineStep(selected.move);
            host.appendLog("[开局库随机] " + difficultyName + " 从 " + choices.size()
                    + " 个合法候选中选择 " + selected.move + "。\n");
            host.pendingMoveScoreRed = host.lastKnownScore();
            host.pendingMoveMatePly = host.lastKnownMatePly();
            if (host.boardView.playMove(move)) return true;
            host.appendLog("[开局库] 随机着法不合法，转入引擎。\n");
        } catch (Exception e) {
            host.appendLog("[开局库] 查询失败：" + e.getMessage() + "。\n");
        }
        return false;
    }
}

package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 未完成对局、分析棋谱与评测棋局的快照编排和恢复。 */
final class GameSessionController {
    private final MainActivity host;

    GameSessionController(MainActivity host) {
        this.host = host;
    }

    private String savedSessionKey(boolean analysis) {
        return analysis ? MainActivity.SAVED_ANALYSIS_RECORD : MainActivity.SAVED_GAME_RECORD;
    }

    void clearEvaluationSession() {
        // 终局后不能留下可恢复的评测记录；同步落盘，避免用户立刻退出进程时旧局复现。
        host.sessionRepository.clear(MainActivity.SAVED_EVALUATION_RECORD, true);
    }

    void clearSavedSession(boolean analysis) {
        host.sessionRepository.clear(savedSessionKey(analysis));
    }

    void persistCurrentSession() {
        persistCurrentSession(false);
    }

    void persistCurrentSession(boolean synchronous) {
        if (host.completedDuelGame) return;
        writeCurrentSession(host.selfAnalysisMode, synchronous, false);
    }

    void persistCompletedDuelSession(boolean synchronous) {
        writeCurrentSession(false, synchronous, true);
    }

    private void writeCurrentSession(boolean analysisRecord, boolean synchronous,
                                     boolean allowCompletedDuel) {
        if (!host.gameScreenVisible || host.boardView == null || host.boardView.isEditMode()) return;
        if ((host.gameOver || host.completedDuelGame) && !allowCompletedDuel) return;
        boolean meaningful = host.evaluationMode || allowCompletedDuel || !host.engineMoves.isEmpty()
                || (host.initialComment != null && host.initialComment.trim().length() > 0)
                || host.manualMetadata.hasValues()
                || !host.normalizeFen(host.baseFen).equals(host.normalizeFen(MainActivity.START_FEN));
        if (!meaningful) {
            if (!allowCompletedDuel) clearSavedSession(analysisRecord);
            return;
        }
        try {
            SavedSession snapshot = new SavedSession();
            snapshot.analysisMode = host.evaluationMode ? false : analysisRecord;
            snapshot.competitiveResultEligible = allowCompletedDuel
                    ? false : host.competitiveResultEligible;
            snapshot.completedDuelGame = allowCompletedDuel && host.completedDuelGame;
            snapshot.gameResultTag = host.gameResultTag;
            snapshot.baseFen = host.baseFen;
            snapshot.difficultyIndex = host.selectedDifficultyIndex;
            snapshot.enginePlaysRed = host.enginePlaysRed;
            snapshot.sixtyMoveDrawArmedPly = host.sixtyMoveDrawArmedPly;
            snapshot.engineSteps.addAll(host.engineMoves);
            snapshot.readableMoves.addAll(host.readableMoves);
            snapshot.initialComment = host.initialComment;
            snapshot.manualMetadata.copyFrom(host.manualMetadata);
            snapshot.comments.addAll(host.moveComments);
            snapshot.scores.addAll(host.redPerspectiveScores);
            snapshot.matePlies.addAll(host.scoreMatePlies);
            snapshot.scoreKnown.addAll(host.scoreKnown);
            snapshot.rescoreRecommendedMoves.addAll(host.rescoreRecommendedMoves);
            snapshot.rescoreScoreKnown.addAll(host.rescoreScoreKnown);
            snapshot.initialRescoreScoreKnown = host.initialRescoreScoreKnown;
            snapshot.initialScoreRed = host.initialScoreRed;
            snapshot.initialMatePly = host.initialMatePly;
            snapshot.initialScoreKnown = host.initialScoreKnown;
            snapshot.variations.putAll(host.manualVariations);
            snapshot.activeBranchLabels.putAll(host.activeBranchLabels);
            String recordKey = host.evaluationMode
                    ? MainActivity.SAVED_EVALUATION_RECORD : savedSessionKey(analysisRecord);
            host.sessionRepository.write(recordKey, snapshot, synchronous);
        } catch (Exception e) {
            host.appendLog("留存棋谱失败：" + e.getMessage() + "。\n");
        }
    }

    SavedSession readSavedSession(boolean analysis) {
        GameSessionRepository.ReadResult result = host.sessionRepository.read(savedSessionKey(analysis),
                analysis, host.selectedDifficultyIndex, host.enginePlaysRed, false);
        if (result.error != null) host.appendLog("留存棋谱损坏，已清除：" + result.error + "。\n");
        if (result.session != null) ensureSavedSessionBranchLabels(result.session);
        return result.session;
    }

    SavedSession readSavedEvaluationSession() {
        GameSessionRepository.ReadResult result = host.sessionRepository.read(MainActivity.SAVED_EVALUATION_RECORD,
                false, host.selectedDifficultyIndex, host.enginePlaysRed, true);
        if (result.session != null && result.session.completedDuelGame) {
            clearEvaluationSession();
            return null;
        }
        if (result.error != null) host.appendLog("评测留存棋谱损坏，已清除：" + result.error + "。\n");
        if (result.session != null) ensureSavedSessionBranchLabels(result.session);
        return result.session;
    }

    private void ensureSavedSessionBranchLabels(SavedSession saved) {
        for (List<ManualVariation> variations : saved.variations.values()) {
            if (variations == null) continue;
            for (ManualVariation variation : variations) ensureSavedVariationBranchLabels(variation);
        }
    }

    private void ensureSavedVariationBranchLabels(ManualVariation variation) {
        if (variation == null) return;
        for (Integer node : new ArrayList<Integer>(variation.variations.keySet())) {
            if (node == null) continue;
            host.ensureNestedBranchLabels(variation, node);
            List<ManualVariation> children = variation.variations.get(node);
            if (children == null) continue;
            for (ManualVariation child : children) ensureSavedVariationBranchLabels(child);
        }
    }

    void restoreSavedSession(SavedSession saved) {
        if (saved == null || host.boardView == null) throw new IllegalStateException("留存内容为空");
        host.invalidateSituationScoreRequests();
        host.baseFen = host.normalizeFen(saved.baseFen);
        XiangqiRules.fromFen(host.baseFen);
        host.boardView.setBoardFromFen(host.baseFen);
        host.engineMoves.clear();
        host.readableMoves.clear();
        host.initialComment = saved.initialComment == null ? "" : saved.initialComment;
        host.manualMetadata.copyFrom(saved.manualMetadata);
        host.moveComments.clear();
        host.redPerspectiveScores.clear();
        host.scoreMatePlies.clear();
        host.scoreKnown.clear();
        host.rescoreRecommendedMoves.clear();
        host.rescoreScoreKnown.clear();
        host.initialRescoreScoreKnown = false;
        host.initialScoreRed = saved.initialScoreRed;
        host.initialMatePly = saved.initialMatePly;
        host.initialScoreKnown = saved.initialScoreKnown;
        host.initialRescoreScoreKnown = saved.initialRescoreScoreKnown;
        host.manualVariations.clear();
        host.activeBranchLabels.clear();
        for (int i = 0; i < saved.engineSteps.size(); i++) {
            String step = host.normalizeStep(saved.engineSteps.get(i));
            if (step.length() < 4) throw new IllegalArgumentException("第 " + (i + 1) + " 手无效");
            String readable = i < saved.readableMoves.size()
                    ? saved.readableMoves.get(i)
                    : ChineseNotation.translate(host.boardView.copyBoard(), step, false);
            if (!host.boardView.playMoveSilently(Move.fromEngineStep(step))) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 手无法重放");
            }
            host.engineMoves.add(step);
            host.readableMoves.add(readable == null || readable.trim().length() == 0
                    ? step : readable.trim());
            host.moveComments.add(i < saved.comments.size() ? saved.comments.get(i) : "");
            host.redPerspectiveScores.add(i < saved.scores.size() ? saved.scores.get(i) : 0);
            host.scoreMatePlies.add(i < saved.matePlies.size() ? saved.matePlies.get(i) : 0);
            host.scoreKnown.add(i < saved.scoreKnown.size() && Boolean.TRUE.equals(saved.scoreKnown.get(i)));
            host.rescoreRecommendedMoves.add(i < saved.rescoreRecommendedMoves.size() ? saved.rescoreRecommendedMoves.get(i) : "");
            host.rescoreScoreKnown.add(i < saved.rescoreScoreKnown.size() && Boolean.TRUE.equals(saved.rescoreScoreKnown.get(i)));
        }
        host.manualVariations.putAll(saved.variations);
        host.activeBranchLabels.putAll(saved.activeBranchLabels);
        for (Integer node : new ArrayList<Integer>(host.manualVariations.keySet())) {
            if (node != null) host.ensureBranchLabels(node);
        }
        host.currentPly = host.engineMoves.size();
        host.completedDuelGame = saved.completedDuelGame && !host.selfAnalysisMode;
        host.postGameSandboxActive = false;
        host.gameOver = host.completedDuelGame;
        host.terminalDialogShown = host.completedDuelGame;
        host.competitiveResultEligible = saved.competitiveResultEligible
                && !host.selfAnalysisMode && !host.completedDuelGame;
        host.resultRecordedForCurrentGame = host.completedDuelGame;
        host.gameResultTag = saved.gameResultTag == null || saved.gameResultTag.length() == 0
                ? "*" : saved.gameResultTag;
        host.sixtyMoveDrawArmedPly = saved.sixtyMoveDrawArmedPly > host.currentPly
                && host.computeNoCaptureMoveCountAtPly(host.currentPly) >= 119
                ? saved.sixtyMoveDrawArmedPly : -1;
        host.resetAnalysisPositionState();
        host.gameEngine.notifyNewGame();
        host.manualEngine.notifyNewGame();
        host.rescoreEngine.notifyNewGame();
        host.situationEngine.notifyNewGame();
        host.updatePlayerLabels();
        host.refreshBoardInputState();
        host.updateGameContent();
    }

    List<String> movesUpToCurrentPly() {
        if (host.currentPly <= 0) return Collections.emptyList();
        return new ArrayList<String>(host.engineMoves.subList(0,
                Math.min(host.currentPly, host.engineMoves.size())));
    }

    String currentPositionKey() {
        return host.normalizeFen(host.boardView == null ? host.baseFen : host.boardView.getFen())
                + "|" + host.join(movesUpToCurrentPly());
    }
}

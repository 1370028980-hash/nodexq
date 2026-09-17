package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 局势评分、推荐着法和终局后报告数据的串行回填。 */
final class SituationScoreController {
    private final MainActivity host;

    SituationScoreController(MainActivity host) {
        this.host = host;
    }

    void requestSituationScoreForPly(final int plyIndex) {
        requestSituationScoreForPly(plyIndex, null);
    }

    void requestSituationScoreForPly(final int plyIndex, final Runnable completion) {
        if (host.selfAnalysisMode || host.situationEngine == null || plyIndex < 0
                || plyIndex >= host.engineMoves.size()) {
            if (completion != null) completion.run();
            return;
        }
        if (!host.situationEngine.hasUsableEngine()) {
            if (!host.situationScoreEngineUnavailableLogged) {
                host.situationScoreEngineUnavailableLogged = true;
                host.appendLog("[局势评分失败] 未找到可用的局势评分引擎。\n");
            }
            if (completion != null) completion.run();
            return;
        }
        final int generation = host.situationScoreGeneration;
        final List<String> moves = new ArrayList<String>(host.engineMoves.subList(0, plyIndex + 1));
        final boolean redToMoveAtPosition = host.redToMoveAtPly(plyIndex + 1);
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        host.situationEngine.requestBestMoveWithInfo(host.baseFen, moves,
                PikafishEngine.SearchLimit.movetime(100),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        if (info != null && Math.max(1, info.multiPv) == 1) latest[0] = host.copyInfo(info);
                    }
                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            applySituationScoreResult(generation, plyIndex, moves,
                                    redToMoveAtPosition, latest[0], null, bestMove);
                            if (completion != null) completion.run();
                        });
                    }
                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            applySituationScoreResult(generation, plyIndex, moves,
                                    redToMoveAtPosition, latest[0], message, "");
                            if (completion != null) completion.run();
                        });
                    }
                });
    }

    void requestInitialSituationScore() {
        requestInitialSituationScore(null);
    }

    void requestInitialSituationScore(final Runnable completion) {
        boolean firstRecommendationReady = host.engineMoves.isEmpty()
                ? host.normalizeStep(host.pendingSituationRecommendations.get(
                        situationRecommendationKey(Collections.<String>emptyList()))).length() >= 4
                : (!host.rescoreRecommendedMoves.isEmpty()
                    && host.normalizeStep(host.rescoreRecommendedMoves.get(0)).length() >= 4);
        boolean initialReviewReady = host.initialScoreKnown && host.initialRescoreScoreKnown
                && firstRecommendationReady;
        if (host.selfAnalysisMode || host.situationEngine == null || initialReviewReady) {
            if (completion != null) completion.run();
            return;
        }
        if (!host.situationEngine.hasUsableEngine()) {
            if (!host.situationScoreEngineUnavailableLogged) {
                host.situationScoreEngineUnavailableLogged = true;
                host.appendLog("[局势评分失败] 未找到可用的局势评分引擎。\n");
            }
            if (completion != null) completion.run();
            return;
        }
        final int generation = host.situationScoreGeneration;
        final String requestFen = host.baseFen;
        final boolean redToMove = XiangqiRules.redToMoveFromFen(requestFen);
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        host.situationEngine.requestBestMoveWithInfo(requestFen, Collections.<String>emptyList(),
                PikafishEngine.SearchLimit.movetime(100),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        if (info != null && Math.max(1, info.multiPv) == 1) latest[0] = host.copyInfo(info);
                    }
                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            applyInitialSituationScore(generation, requestFen, redToMove, latest[0], bestMove);
                            if (completion != null) completion.run();
                        });
                    }
                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            applyInitialSituationScore(generation, requestFen, redToMove, latest[0], "");
                            if (message != null && message.length() > 0) {
                                host.appendLog("[初始局势评分失败] " + message + "\n");
                            }
                            if (completion != null) completion.run();
                        });
                    }
                });
    }

    private void applyInitialSituationScore(int generation, String requestFen,
                                            boolean redToMove,
                                            PikafishEngine.EngineInfo info,
                                            String bestMoveFallback) {
        if (generation != host.situationScoreGeneration
                || !host.normalizeFen(requestFen).equals(host.normalizeFen(host.baseFen))) return;
        if (info != null && info.hasScore) {
            int newScore = redScoreFromInfo(info, redToMove);
            int newMate = info.mateScore ? Math.abs(info.score) : 0;
            invalidateInitialRescoreIfChanged(newScore, newMate);
            host.initialScoreRed = newScore;
            host.initialMatePly = newMate;
            host.initialScoreKnown = true;
            // V19.7：正式对弈的固定 100ms 局势评分同时是同一来源的复盘数据。
            host.initialRescoreScoreKnown = true;
            storeSituationRecommendation(Collections.<String>emptyList(), 0,
                    info, bestMoveFallback);
            host.refreshSituationChart();
            host.persistCurrentSession();
        }
    }

    /**
     * 终局后按顺序补齐固定 100ms 评分与复盘推荐。对弈过程中每个“走后局面”的 bestmove
     * 本身就是下一手的推荐，因此复用同一次搜索即可生成优劣箭头，不额外并发开引擎。
     */
    void backfillCompletedDuelScores() {
        if (host.selfAnalysisMode || !host.completedDuelGame || host.situationEngine == null) return;
        if (host.buildGameReport().complete && completedDuelReviewDataComplete()) {
            host.refreshSituationChart();
            host.updateRescoreReviewArrows();
            return;
        }
        if (host.reportBackfillPass >= 3) {
            host.refreshSituationChart();
            host.updateRescoreReviewArrows();
            return;
        }
        host.reportBackfillPass++;
        boolean firstRecommendationReady = host.engineMoves.isEmpty()
                || (!host.rescoreRecommendedMoves.isEmpty()
                    && host.normalizeStep(host.rescoreRecommendedMoves.get(0)).length() >= 4);
        if (!host.initialScoreKnown || !host.initialRescoreScoreKnown || !firstRecommendationReady) {
            requestInitialSituationScore(() -> backfillCompletedDuelScoresFrom(0));
        } else {
            backfillCompletedDuelScoresFrom(0);
        }
    }

    private void backfillCompletedDuelScoresFrom(int startIndex) {
        if (!host.completedDuelGame || host.selfAnalysisMode) return;
        host.ensureScoreSize(host.engineMoves.size());
        int missing = -1;
        for (int i = Math.max(0, startIndex); i < host.engineMoves.size(); i++) {
            if (needsCompletedDuelReviewBackfillAtPosition(i)) {
                missing = i;
                break;
            }
        }
        if (missing < 0) {
            host.refreshSituationChart();
            host.updateRescoreReviewArrows();
            host.persistCurrentSession();
            if ((!host.buildGameReport().complete || !completedDuelReviewDataComplete())
                    && host.reportBackfillPass < 3) {
                host.handler.postDelayed(this::backfillCompletedDuelScores, 120L);
            }
            return;
        }
        final int next = missing + 1;
        requestSituationScoreForPly(missing,
                () -> host.handler.postDelayed(() -> backfillCompletedDuelScoresFrom(next), 20L));
    }

    private boolean needsCompletedDuelReviewBackfillAtPosition(int plyIndex) {
        host.ensureScoreSize(host.engineMoves.size());
        if (plyIndex < 0 || plyIndex >= host.engineMoves.size()) return false;
        if (!Boolean.TRUE.equals(host.scoreKnown.get(plyIndex))
                || !Boolean.TRUE.equals(host.rescoreScoreKnown.get(plyIndex))) return true;
        int nextMove = plyIndex + 1;
        return nextMove < host.engineMoves.size()
                && host.normalizeStep(host.rescoreRecommendedMoves.get(nextMove)).length() < 4;
    }

    private boolean completedDuelReviewDataComplete() {
        if (host.engineMoves.isEmpty()) return true;
        host.ensureScoreSize(host.engineMoves.size());
        if (!host.initialScoreKnown || !host.initialRescoreScoreKnown
                || host.normalizeStep(host.rescoreRecommendedMoves.get(0)).length() < 4) return false;
        for (int i = 0; i < host.engineMoves.size(); i++) {
            if (!Boolean.TRUE.equals(host.scoreKnown.get(i))
                    || !Boolean.TRUE.equals(host.rescoreScoreKnown.get(i))) return false;
            if (i + 1 < host.engineMoves.size()
                    && host.normalizeStep(host.rescoreRecommendedMoves.get(i + 1)).length() < 4) return false;
        }
        return true;
    }

    private void applySituationScoreResult(int generation, int plyIndex, List<String> moves,
                                           boolean redToMoveAtPosition,
                                           PikafishEngine.EngineInfo info, String error,
                                           String bestMoveFallback) {
        if (generation != host.situationScoreGeneration || !historyPrefixMatches(moves)) return;
        host.ensureScoreSize(host.engineMoves.size());
        // 评分“走后局面”时返回的 PV1/bestmove，正好是下一手的走前推荐。
        storeSituationRecommendation(moves, plyIndex + 1, info, bestMoveFallback);
        if (info != null && info.hasScore) {
            host.updateSixtyMoveDrawArmFromScore(plyIndex + 1);
            host.persistCurrentSession();
            // 当前点正在由用户主动开启的实时分析更新时，不让较晚返回的 100ms 固定评分覆盖它。
            if (host.analysisMode && host.currentPly == plyIndex + 1
                    && host.analysisPositionKey.equals(host.currentPositionKey())
                    && host.latestAnalysisInfo != null) return;
            host.ensureScoreSize(host.engineMoves.size());
            if (plyIndex >= 0 && plyIndex < host.redPerspectiveScores.size()) {
                int newScore = redScoreFromInfo(info, redToMoveAtPosition);
                int newMate = info.mateScore ? Math.abs(info.score) : 0;
                invalidateRescoreIfChanged(plyIndex, newScore, newMate);
                host.redPerspectiveScores.set(plyIndex, newScore);
                host.scoreMatePlies.set(plyIndex, newMate);
                host.scoreKnown.set(plyIndex, true);
                host.rescoreScoreKnown.set(plyIndex, true);
                host.refreshSituationChart();
                host.persistCurrentSession();
            }
            return;
        }
        if (error != null && error.length() > 0) {
            host.appendLog("[局势评分失败] 第 " + (plyIndex + 1) + " 手：" + error + "\n");
        }
        // 引擎未返回可用分数时（绝杀局面、bestmove (none) 等），直接从棋盘状态补分。
        host.ensureScoreSize(host.engineMoves.size());
        if (plyIndex >= 0 && plyIndex < host.redPerspectiveScores.size()) {
            try {
                char[][] board = XiangqiRules.fromFen(host.baseFen);
                for (int i = 0; i < moves.size(); i++) {
                    Move m = Move.fromEngineStep(moves.get(i));
                    if (m == null) break;
                    char piece = board[m.fromRow][m.fromCol];
                    board[m.toRow][m.toCol] = piece;
                    board[m.fromRow][m.fromCol] = ' ';
                }
                boolean noLegal = XiangqiRules.generateLegalMoves(board, redToMoveAtPosition).isEmpty();
                if (noLegal) {
                    int fallback = redToMoveAtPosition ? -MainActivity.SITUATION_MATE_LIMIT : MainActivity.SITUATION_MATE_LIMIT;
                    invalidateRescoreIfChanged(plyIndex, fallback, 0);
                    host.redPerspectiveScores.set(plyIndex, fallback);
                    host.scoreMatePlies.set(plyIndex, 0);
                    host.scoreKnown.set(plyIndex, true);
                    host.rescoreScoreKnown.set(plyIndex, true);
                    host.refreshSituationChart();
                    host.persistCurrentSession();
                }
            } catch (Exception ignored) {}
        }
    }

    private String situationRecommendationKey(List<String> positionMoves) {
        return host.normalizeFen(host.baseFen) + "|" + host.join(positionMoves == null
                ? Collections.<String>emptyList() : positionMoves);
    }

    private void storeSituationRecommendation(List<String> positionMoves, int targetMoveIndex,
                                               PikafishEngine.EngineInfo info,
                                               String bestMoveFallback) {
        String step = info == null ? "" : host.normalizeStep(info.firstMove());
        if (step.length() < 4) step = host.normalizeStep(bestMoveFallback);
        if (step.length() < 4 || targetMoveIndex < 0) return;
        host.ensureScoreSize(host.engineMoves.size());
        if (targetMoveIndex < host.rescoreRecommendedMoves.size()) {
            host.rescoreRecommendedMoves.set(targetMoveIndex, step);
            host.pendingSituationRecommendations.remove(situationRecommendationKey(positionMoves));
        } else {
            host.pendingSituationRecommendations.put(situationRecommendationKey(positionMoves), step);
        }
    }

    void applyPendingSituationRecommendationForMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= host.rescoreRecommendedMoves.size()) return;
        List<String> beforeMoves = moveIndex == 0
                ? Collections.<String>emptyList()
                : new ArrayList<String>(host.engineMoves.subList(0, moveIndex));
        String step = host.normalizeStep(host.pendingSituationRecommendations.remove(
                situationRecommendationKey(beforeMoves)));
        if (step.length() >= 4) host.rescoreRecommendedMoves.set(moveIndex, step);
    }

    private boolean historyPrefixMatches(List<String> prefix) {
        if (prefix == null || host.engineMoves.size() < prefix.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(host.engineMoves.get(i))) return false;
        }
        return true;
    }

    /** 非重新打分来源若改变了同一评分点，则该点不再视为“纯重新打分数据”。 */
    void invalidateRescoreIfChanged(int index, int newScore, int newMate) {
        host.ensureScoreSize(host.engineMoves.size());
        if (index < 0 || index >= host.rescoreScoreKnown.size()
                || !Boolean.TRUE.equals(host.rescoreScoreKnown.get(index))) return;
        if (index >= host.redPerspectiveScores.size() || index >= host.scoreMatePlies.size()
                || host.redPerspectiveScores.get(index) != newScore
                || host.scoreMatePlies.get(index) != newMate) {
            host.rescoreScoreKnown.set(index, false);
        }
    }

    void invalidateInitialRescoreIfChanged(int newScore, int newMate) {
        if (host.initialRescoreScoreKnown
                && (host.initialScoreRed != newScore || host.initialMatePly != newMate)) {
            host.initialRescoreScoreKnown = false;
        }
    }

    void invalidateSituationScoreRequests() {
        host.situationScoreGeneration++;
        if (host.situationEngine != null) host.situationEngine.stopAnalysis();
    }

    int redScoreFromInfo(PikafishEngine.EngineInfo info, boolean redToMove) {
        if (info == null || !info.hasScore) return 0;
        int own = info.mateScore
                ? (info.score >= 0 ? MainActivity.SITUATION_MATE_LIMIT : -MainActivity.SITUATION_MATE_LIMIT)
                : info.score;
        return redToMove ? own : -own;
    }

    int lastKnownScore() {
        return host.redPerspectiveScores.isEmpty() ? 0
                : host.redPerspectiveScores.get(host.redPerspectiveScores.size() - 1);
    }

    int lastKnownMatePly() {
        return host.scoreMatePlies.isEmpty() ? 0 : host.scoreMatePlies.get(host.scoreMatePlies.size() - 1);
    }
}

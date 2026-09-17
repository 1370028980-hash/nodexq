package com.tyl.xiangqi.ndxq;

import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 重打分任务的生命周期和串行调度。
 *
 * <p>评分结果仍由 MainActivity 持有，避免改变棋谱保存格式；本类只负责把主线和分支
 * 按同一个引擎实例排队处理，并保留 generation 防陈旧回调保护。</p>
 */
final class RescoreController {
    private final MainActivity host;
    private final RescoreCommentController commentController;

    private int generation;
    private int progressIndex = -1;
    private int progressTotal;
    private int progressDepth = Integer.MIN_VALUE;
    private boolean allBranches;
    private int processedSteps;
    private int selectedTimeMs;
    private boolean selectedForward;
    private boolean appendScoresToComments;
    private final List<RescoreBranchTask> branchTasks = new ArrayList<RescoreBranchTask>();
    private int branchTaskIndex;

    RescoreController(MainActivity host) {
        this.host = host;
        commentController = new RescoreCommentController(host);
    }

    boolean isActive() {
        return host.isRescoring;
    }

    void reset() {
        generation++;
        host.isRescoring = false;
        allBranches = false;
        appendScoresToComments = false;
        branchTasks.clear();
        branchTaskIndex = 0;
        clearProgress();
    }

    void start(int timeMs, int startIndex, int endExclusive,
               boolean forward, boolean scoreAllBranches, boolean appendScores) {
        if (isActive() || host.engineMoves.isEmpty()) return;
        int allMax = Math.max(host.engineMoves.size(), host.maxManualLinePlyCount());
        startIndex = host.clamp(startIndex, 0, Math.max(0, allMax - 1));
        endExclusive = host.clamp(endExclusive, startIndex + 1, allMax);

        allBranches = scoreAllBranches;
        selectedTimeMs = timeMs;
        selectedForward = forward;
        appendScoresToComments = appendScores;
        processedSteps = 0;
        branchTaskIndex = 0;
        branchTasks.clear();
        if (scoreAllBranches) {
            collectBranchTasks(Collections.<String>emptyList(), host.engineMoves,
                    host.rescoreRecommendedMoves, host.manualVariations,
                    startIndex, endExclusive, branchTasks);
        }

        int mainStart = Math.min(startIndex, host.engineMoves.size());
        int mainEnd = Math.min(endExclusive, host.engineMoves.size());
        int mainCount = Math.max(0, mainEnd - mainStart);
        int branchCount = 0;
        for (RescoreBranchTask task : branchTasks) {
            branchCount += Math.max(0, task.localEndExclusive - task.localStart);
        }
        int totalSteps = mainCount + branchCount;
        if (totalSteps <= 0) {
            Toast.makeText(host, "所选范围内没有可重新打分的棋步", Toast.LENGTH_SHORT).show();
            return;
        }

        host.isRescoring = true;
        host.reviewRefreshGeneration++;
        host.reviewRefreshPly = -1;
        if (host.rescoreEngine != null) host.rescoreEngine.stopAnalysis();
        progressIndex = 0;
        progressTotal = totalSteps;
        progressDepth = Integer.MIN_VALUE;
        final int taskGeneration = ++generation;
        if (host.gameEngine != null) host.gameEngine.stopAnalysis();
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.invalidateSituationScoreRequests();
        ensureEngineAlive();
        if (!host.rescoreEngine.hasUsableEngine()) {
            host.isRescoring = false;
            allBranches = false;
            branchTasks.clear();
            host.appendLog("重新打分失败：未找到可用引擎。" + host.rescoreEngine.getInstallHint() + "\n");
            host.refreshBoardInputState();
            host.updatePlayerLabels();
            host.updateGameContent();
            Toast.makeText(host, "未找到可用引擎，请先在引擎设置中选择", Toast.LENGTH_LONG).show();
            return;
        }
        host.appendLog("[重新打分引擎] " + host.rescoreEngine.getCurrentEngineLabel() + "。\n");
        host.autoMoveInProgress = false;
        host.engineThinking = false;
        host.refreshBoardInputState();
        host.updatePlayerLabels();
        host.updateGameContent();
        host.appendLog("重新打分开始：每步 " + timeMs + "ms，范围第 " + (startIndex + 1)
                + "～" + endExclusive + " 手，方向"
                + (forward ? "从前往后" : "从后往前")
                + (scoreAllBranches ? "，包含所有分支" : "，仅当前主线")
                + "，共 " + totalSteps + " 个局面，使用独立分析引擎串行处理。\n");
        updateProgressText();

        int firstIndex = forward ? mainStart : mainEnd - 1;
        if (startIndex == 0) {
            scoreInitialPosition(taskGeneration, mainStart, mainEnd,
                    firstIndex, timeMs, forward);
        } else if (mainCount > 0) {
            processMainStep(taskGeneration, mainStart, mainEnd, firstIndex, timeMs, forward);
        } else {
            startNextBranchOrFinish(taskGeneration);
        }
    }

    private void collectBranchTasks(List<String> prefix, List<String> lineMoves,
                                    List<String> lineRecommendations,
                                    Map<Integer, List<ManualVariation>> branches,
                                    int rangeStart, int rangeEndExclusive,
                                    List<RescoreBranchTask> out) {
        if (lineMoves == null || branches == null || branches.isEmpty() || out == null) return;
        ArrayList<Integer> nodes = new ArrayList<Integer>(branches.keySet());
        Collections.sort(nodes);
        for (Integer nodeValue : nodes) {
            if (nodeValue == null) continue;
            int node = nodeValue;
            if (node < 0 || node > lineMoves.size()) continue;
            List<ManualVariation> vars = branches.get(nodeValue);
            if (vars == null || vars.isEmpty()) continue;
            ArrayList<ManualVariation> ordered = new ArrayList<ManualVariation>(vars);
            Collections.sort(ordered, (a, b) -> Integer.compare(
                    host.branchLabelOrdinal(a == null ? "" : a.label),
                    host.branchLabelOrdinal(b == null ? "" : b.label)));
            ArrayList<String> branchPrefix = new ArrayList<String>();
            if (prefix != null) branchPrefix.addAll(prefix);
            branchPrefix.addAll(lineMoves.subList(0, node));
            int absoluteStart = branchPrefix.size();
            for (ManualVariation variation : ordered) {
                if (variation == null || variation.engineSteps.isEmpty()) continue;
                host.ensureVariationScoreSize(variation);
                int localStart = Math.max(0, rangeStart - absoluteStart);
                int localEnd = Math.min(variation.engineSteps.size(),
                        rangeEndExclusive - absoluteStart);
                if (localStart < localEnd) {
                    out.add(new RescoreBranchTask(new ArrayList<String>(branchPrefix), variation,
                            lineRecommendations, node, localStart, localEnd));
                }
                collectBranchTasks(branchPrefix, variation.engineSteps,
                        variation.rescoreRecommendedMoves, variation.variations,
                        rangeStart, rangeEndExclusive, out);
            }
        }
    }

    private void scoreInitialPosition(final int taskGeneration, final int rangeStart,
                                      final int rangeEndExclusive, final int firstIndex,
                                      final int timeMs, final boolean forward) {
        if (!isCurrent(taskGeneration)) return;
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        final boolean redToMove = XiangqiRules.redToMoveFromFen(host.baseFen);
        host.rescoreEngine.requestBestMoveWithInfo(host.baseFen, Collections.<String>emptyList(),
                PikafishEngine.SearchLimit.movetime(timeMs),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        if (info != null && Math.max(1, info.multiPv) == 1) {
                            latest[0] = host.copyInfo(info);
                            host.handler.post(() -> {
                                if (!isCurrent(taskGeneration)) return;
                                progressDepth = info.depth;
                                updateProgressText();
                            });
                        }
                    }

                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            if (latest[0] != null && latest[0].hasScore) {
                                host.initialScoreRed = host.redScoreFromInfo(latest[0], redToMove);
                                host.initialMatePly = latest[0].mateScore
                                        ? Math.abs(latest[0].score) : 0;
                                host.initialScoreKnown = true;
                                host.initialRescoreScoreKnown = true;
                                host.applyRescoreRecommendation(0, latest[0], bestMove);
                                appendInitialCommentIfRequested();
                            } else {
                                host.appendLog("重新打分初始局面未返回可用评分；保留原初始评分，若此前缺失则报告不可用。\n");
                            }
                            processMainStep(taskGeneration, rangeStart, rangeEndExclusive,
                                    firstIndex, timeMs, forward);
                        });
                    }

                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            if (latest[0] != null && latest[0].hasScore) {
                                host.initialScoreRed = host.redScoreFromInfo(latest[0], redToMove);
                                host.initialMatePly = latest[0].mateScore
                                        ? Math.abs(latest[0].score) : 0;
                                host.initialScoreKnown = true;
                                host.initialRescoreScoreKnown = true;
                                host.applyRescoreRecommendation(0, latest[0]);
                                appendInitialCommentIfRequested();
                            } else {
                                host.appendLog("重新打分初始局面失败：" + message
                                        + "（保留原初始评分；若此前缺失则报告不可用）\n");
                            }
                            processMainStep(taskGeneration, rangeStart, rangeEndExclusive,
                                    firstIndex, timeMs, forward);
                        });
                    }
                });
    }

    void ensureEngineAlive() {
        if (host.rescoreEngine == null) {
            host.rescoreEngine = new PikafishEngine(host, false);
            host.rescoreEngine.setVirtualEngineSlot(
                    host.rescoreEngine.hasVirtualEngine("131") ? "131" : "");
            host.applyStoredManualOptions(host.rescoreEngine);
            return;
        }
        try {
            if (!host.rescoreEngine.tryPing()) {
                host.rescoreEngine.stopQuietly();
                host.rescoreEngine = new PikafishEngine(host, false);
                host.rescoreEngine.setVirtualEngineSlot(
                        host.rescoreEngine.hasVirtualEngine("131") ? "131" : "");
                host.applyStoredManualOptions(host.rescoreEngine);
            }
        } catch (Exception e) {
            host.rescoreEngine = new PikafishEngine(host, false);
            host.rescoreEngine.setVirtualEngineSlot(
                    host.rescoreEngine.hasVirtualEngine("131") ? "131" : "");
            host.applyStoredManualOptions(host.rescoreEngine);
        }
    }

    private void processMainStep(final int taskGeneration, final int rangeStart,
                                 final int rangeEndExclusive, final int index,
                                 final int timeMs, final boolean forward) {
        if (!isCurrent(taskGeneration)) return;
        if (index < rangeStart || index >= rangeEndExclusive || index >= host.engineMoves.size()) {
            startNextBranchOrFinish(taskGeneration);
            return;
        }
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        final int total = host.engineMoves.size();
        progressIndex = processedSteps;
        progressDepth = Integer.MIN_VALUE;
        updateProgressText();
        final boolean redToMoveAtPosition = host.redToMoveAtPly(index + 1);
        List<String> moves = new ArrayList<String>(host.engineMoves.subList(0, index + 1));
        host.rescoreEngine.requestBestMoveWithInfo(host.baseFen, moves,
                PikafishEngine.SearchLimit.movetime(timeMs),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        if (info != null && Math.max(1, info.multiPv) == 1) {
                            latest[0] = host.copyInfo(info);
                            host.handler.post(() -> {
                                if (!isCurrent(taskGeneration)) return;
                                progressIndex = processedSteps;
                                progressDepth = info.depth;
                                updateProgressText();
                            });
                        }
                    }

                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            boolean scored = host.applyRescoreScore(
                                    index, total, redToMoveAtPosition, latest[0]);
                            host.applyRescoreRecommendation(index + 1, latest[0], bestMove);
                            if (scored) appendMainCommentIfRequested(index);
                            processedSteps++;
                            int next = forward ? index + 1 : index - 1;
                            processMainStep(taskGeneration, rangeStart, rangeEndExclusive,
                                    next, timeMs, forward);
                        });
                    }

                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            boolean recovered = host.applyRescoreScore(
                                    index, total, redToMoveAtPosition, latest[0]);
                            host.applyRescoreRecommendation(index + 1, latest[0]);
                            if (recovered) appendMainCommentIfRequested(index);
                            host.appendLog("重新打分第 " + (index + 1) + " 手失败：" + message
                                    + (recovered ? "（已按终局结果补分）" : "（保留原分）") + "\n");
                            processedSteps++;
                            int next = forward ? index + 1 : index - 1;
                            processMainStep(taskGeneration, rangeStart, rangeEndExclusive,
                                    next, timeMs, forward);
                        });
                    }
                });
    }

    private void startNextBranchOrFinish(final int taskGeneration) {
        if (!isCurrent(taskGeneration)) return;
        if (!allBranches || branchTaskIndex >= branchTasks.size()) {
            finish(false);
            return;
        }
        RescoreBranchTask task = branchTasks.get(branchTaskIndex++);
        host.ensureVariationScoreSize(task.variation);
        if (task.parentRecommendations != null
                && task.parentRecommendationIndex >= 0
                && task.parentRecommendationIndex < task.parentRecommendations.size()
                && !task.variation.rescoreRecommendedMoves.isEmpty()) {
            String shared = host.normalizeStep(
                    task.parentRecommendations.get(task.parentRecommendationIndex));
            if (shared.length() >= 4) {
                task.variation.rescoreRecommendedMoves.set(0, shared);
            }
        }
        int first = selectedForward ? task.localStart : task.localEndExclusive - 1;
        processBranchStep(taskGeneration, task, first);
    }

    private void processBranchStep(final int taskGeneration, final RescoreBranchTask task,
                                   final int localIndex) {
        if (!isCurrent(taskGeneration) || task == null) return;
        if (localIndex < task.localStart || localIndex >= task.localEndExclusive
                || localIndex >= task.variation.engineSteps.size()) {
            startNextBranchOrFinish(taskGeneration);
            return;
        }
        host.ensureVariationScoreSize(task.variation);
        progressIndex = processedSteps;
        progressDepth = Integer.MIN_VALUE;
        updateProgressText();

        final ArrayList<String> moves = new ArrayList<String>(task.prefixMoves);
        moves.addAll(task.variation.engineSteps.subList(0, localIndex + 1));
        final boolean redToMoveAtPosition = host.redToMoveAtPly(moves.size());
        final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
        host.rescoreEngine.requestBestMoveWithInfo(host.baseFen, moves,
                PikafishEngine.SearchLimit.movetime(selectedTimeMs),
                new PikafishEngine.AnalysisCallback() {
                    @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                        if (info != null && Math.max(1, info.multiPv) == 1) {
                            latest[0] = host.copyInfo(info);
                            host.handler.post(() -> {
                                if (!isCurrent(taskGeneration)) return;
                                progressIndex = processedSteps;
                                progressDepth = info.depth;
                                updateProgressText();
                            });
                        }
                    }

                    @Override public void onBestMove(String bestMove, String rawInfo) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            boolean scored = host.applyRescoreBranchScore(task, localIndex, moves,
                                    redToMoveAtPosition, latest[0]);
                            host.applyRescoreBranchRecommendation(task.variation, localIndex + 1,
                                    latest[0], bestMove);
                            if (scored) appendVariationCommentIfRequested(task.variation, localIndex);
                            processedSteps++;
                            int next = selectedForward ? localIndex + 1 : localIndex - 1;
                            processBranchStep(taskGeneration, task, next);
                        });
                    }

                    @Override public void onError(String message) {
                        host.handler.post(() -> {
                            if (!isCurrent(taskGeneration)) return;
                            boolean recovered = host.applyRescoreBranchScore(task, localIndex, moves,
                                    redToMoveAtPosition, latest[0]);
                            host.applyRescoreBranchRecommendation(task.variation, localIndex + 1,
                                    latest[0], "");
                            if (recovered) appendVariationCommentIfRequested(task.variation, localIndex);
                            host.appendLog("重新打分分支 " + safeBranchLabel(task.variation.label)
                                    + " 第 " + moves.size() + " 手失败：" + message
                                    + (recovered ? "（已记录可用评分）" : "（保留原分）") + "\n");
                            processedSteps++;
                            int next = selectedForward ? localIndex + 1 : localIndex - 1;
                            processBranchStep(taskGeneration, task, next);
                        });
                    }
                });
    }

    private String safeBranchLabel(String label) {
        return label == null || label.trim().length() == 0 ? "?" : label.trim();
    }

    private boolean isCurrent(int taskGeneration) {
        return host.isRescoring && taskGeneration == generation;
    }

    void stop(boolean userInitiated) {
        if (!isActive()) return;
        generation++;
        if (host.rescoreEngine != null) host.rescoreEngine.stopAnalysis();
        finish(userInitiated);
    }

    void cancelAll() {
        generation++;
        if (host.rescoreEngine != null) host.rescoreEngine.stopAnalysis();
        host.isRescoring = false;
        allBranches = false;
        appendScoresToComments = false;
        branchTasks.clear();
        branchTaskIndex = 0;
        clearProgress();
    }

    private void finish(boolean interrupted) {
        host.isRescoring = false;
        allBranches = false;
        appendScoresToComments = false;
        branchTasks.clear();
        branchTaskIndex = 0;
        clearProgress();
        host.refreshBoardInputState();
        host.updatePlayerLabels();
        host.updateGameContent();
        host.persistCurrentSession();
        host.appendLog(interrupted ? "重新打分已中断。\n" : "重新打分完成。\n");
        if (interrupted) Toast.makeText(host, "已中断打分", Toast.LENGTH_SHORT).show();
        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
        host.appendLog("重新打分仅更新评分数据，不触发自动走棋或历史战绩结算。\n");
    }

    void updateProgressText() {
        if (!isActive()) return;
        host.updateRescoreProgress(progressText());
    }

    String progressText() {
        String depth = progressDepth == Integer.MIN_VALUE ? "-" : String.valueOf(progressDepth);
        int shown = Math.max(1, progressIndex + 1);
        int total = Math.max(shown, progressTotal);
        return shown + "/" + total + "  深度 " + depth;
    }

    void clearProgress() {
        progressIndex = -1;
        progressTotal = 0;
        progressDepth = Integer.MIN_VALUE;
    }

    private void appendInitialCommentIfRequested() {
        if (appendScoresToComments) commentController.updateInitialComment();
    }

    private void appendMainCommentIfRequested(int index) {
        if (appendScoresToComments) commentController.updateMainComment(index);
    }

    private void appendVariationCommentIfRequested(ManualVariation variation, int index) {
        if (appendScoresToComments) commentController.updateVariationComment(variation, index);
    }
}

package com.tyl.xiangqi.ndxq;

import android.graphics.Color;

import com.tyl.xiangqi.ndxq.core.GameReportCalculator;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** 分析内容、PV 箭头和重新打分复盘标记的显示协调。 */
final class AnalysisDisplayController {
    private final MainActivity host;

    AnalysisDisplayController(MainActivity host) {
        this.host = host;
    }

    List<String> pvForDisplay(List<String> pv) {
        if (pv == null || pv.isEmpty() || host.engineOutputMoveLimit <= 0
                || pv.size() <= host.engineOutputMoveLimit) {
            return pv == null ? Collections.<String>emptyList() : pv;
        }
        return new ArrayList<String>(pv.subList(0, host.engineOutputMoveLimit));
    }

    void refreshEngineContentText() {
        boolean engineVisible = host.selectedGameTab == 1
                || (host.combinedManualEngineMode && host.selectedGameTab == 0);
        if (!engineVisible || host.engineContentHost == null) return;

        // 停止分析后只展示停止瞬间的快照，切页和导航不得改写该内容。
        if (host.analysisDisplayFrozen) {
            List<EngineAnalysisPanel.DepthModule> frozenModules =
                    host.frozenAnalysisExpectedMultiPv > 1 ? host.frozenMultiPvModules : null;
            if (host.combinedManualEngineMode && host.selectedGameTab == 0) {
                EngineAnalysisPanel.renderCompact(host, host.engineContentHost,
                        host.frozenCompactEngineContent, frozenModules);
            } else {
                EngineAnalysisPanel.render(host, host.engineContentHost,
                        host.frozenEngineContent, frozenModules);
            }
            return;
        }

        List<EngineAnalysisPanel.DepthModule> modules = null;
        if (host.analysisExpectedMultiPv > 1
                && (host.analysisMode || !host.lastMultiPvModules.isEmpty())) {
            modules = buildMultiPvModules();
        }
        if (host.combinedManualEngineMode && host.selectedGameTab == 0) {
            CharSequence compactFallback = host.analysisEntries.isEmpty()
                    ? host.engineContent : buildCompactSinglePvContent();
            EngineAnalysisPanel.renderCompact(host, host.engineContentHost, compactFallback, modules);
        } else {
            EngineAnalysisPanel.render(host, host.engineContentHost, host.engineContent, modules);
        }
    }

    void updateRescoreReviewArrows() {
        if (host.boardView == null || host.analysisMode) return;
        ArrayList<ChessBoardView.AnalysisArrow> arrows = buildRescoreReviewArrows();
        host.boardView.setShowArrow(!arrows.isEmpty() || host.showEngineArrows);
        host.boardView.setAnalysisArrows(arrows);
    }

    /** 构建当前棋步的重新打分推荐着与优劣标记。 */
    ArrayList<ChessBoardView.AnalysisArrow> buildRescoreReviewArrows() {
        ArrayList<ChessBoardView.AnalysisArrow> arrows =
                new ArrayList<ChessBoardView.AnalysisArrow>();
        // 正式人机对局仅在终局后展示复盘推荐及优劣标记。
        if (!host.selfAnalysisMode && !host.completedDuelGame) return arrows;
        if (host.boardView == null || host.isRescoring || host.boardView.isEditMode()
                || host.currentPly <= 0) return arrows;
        int index = host.currentPly - 1;
        host.ensureScoreSize(host.engineMoves.size());
        if (!hasCompleteRescoreReview(index)) return arrows;

        String actual = host.normalizeStep(host.engineMoves.get(index));
        String recommended = host.normalizeStep(host.rescoreRecommendedMoves.get(index));
        int beforeScore = index == 0 ? host.initialScoreRed
                : host.redPerspectiveScores.get(index - 1);
        int beforeMate = index == 0 ? host.initialMatePly : host.scoreMatePlies.get(index - 1);
        int afterScore = host.redPerspectiveScores.get(index);
        int afterMate = host.scoreMatePlies.get(index);
        boolean redMover = host.redToMoveAtPly(index);
        double loss = GameReportCalculator.moveLoss(
                beforeScore, beforeMate, afterScore, afterMate, redMover);
        String quality;
        int qualityColor;
        if (loss <= 50d) {
            quality = "★";
            qualityColor = Color.rgb(38, 158, 79);
        } else if (loss <= 150d) {
            quality = "优";
            qualityColor = Color.rgb(38, 158, 79);
        } else if (loss <= 500d) {
            quality = "中";
            qualityColor = Color.rgb(213, 153, 38);
        } else if (loss <= 1000d) {
            quality = "差";
            qualityColor = Color.rgb(226, 112, 38);
        } else {
            quality = "错";
            qualityColor = Color.rgb(201, 50, 48);
        }

        try {
            int moverArrowColor = host.boardView.getSideArrowColor(redMover);
            if (!recommended.equals(actual)) {
                arrows.add(new ChessBoardView.AnalysisArrow(
                        Move.fromEngineStep(recommended), 0, false, false,
                        moverArrowColor, null, Color.TRANSPARENT));
            }
            // 实际着法已有四角框，仅叠加质量徽标，不重复绘制实际着法箭头。
            arrows.add(new ChessBoardView.AnalysisArrow(
                    Move.fromEngineStep(actual), 0, false, false,
                    moverArrowColor, quality, qualityColor, false));
        } catch (Exception ignored) {
            arrows.clear();
        }
        return arrows;
    }

    boolean hasCompleteRescoreReview(int index) {
        host.ensureScoreSize(host.engineMoves.size());
        if (index < 0 || index >= host.engineMoves.size()
                || index >= host.rescoreScoreKnown.size()
                || !Boolean.TRUE.equals(host.rescoreScoreKnown.get(index))
                || index >= host.scoreKnown.size()
                || !Boolean.TRUE.equals(host.scoreKnown.get(index))) return false;
        boolean beforeReady = index == 0
                ? host.initialRescoreScoreKnown && host.initialScoreKnown
                : index - 1 < host.rescoreScoreKnown.size()
                    && Boolean.TRUE.equals(host.rescoreScoreKnown.get(index - 1))
                    && index - 1 < host.scoreKnown.size()
                    && Boolean.TRUE.equals(host.scoreKnown.get(index - 1));
        if (!beforeReady) return false;
        String actual = host.normalizeStep(host.engineMoves.get(index));
        String recommended = index < host.rescoreRecommendedMoves.size()
                ? host.normalizeStep(host.rescoreRecommendedMoves.get(index)) : "";
        return actual.length() >= 4 && recommended.length() >= 4;
    }

    void updateAnalysisArrows() {
        if (!host.analysisMode || host.boardView == null) return;
        ArrayList<ChessBoardView.AnalysisArrow> arrows =
                new ArrayList<ChessBoardView.AnalysisArrow>();
        if (host.showEngineArrows) {
            if (host.analysisExpectedMultiPv <= 1) {
                TreeMap<Integer, AnalysisDisplayEntry> pv1 = host.analysisPvEntries.get(1);
                if (pv1 != null && !pv1.isEmpty()) {
                    appendAnalysisArrows(arrows, pv1.lastEntry().getValue(), 0,
                            Math.max(1, host.arrowStepCount));
                }
            } else {
                TreeSet<Integer> completeDepths = completeMultiPvDepths();
                if (!completeDepths.isEmpty()) {
                    int depth = completeDepths.last();
                    for (int multiPv = 1; multiPv <= host.analysisExpectedMultiPv; multiPv++) {
                        appendAnalysisArrows(arrows,
                                host.analysisPvEntries.get(multiPv).get(depth), multiPv, 2);
                    }
                } else {
                    for (int multiPv = 1; multiPv <= host.analysisExpectedMultiPv; multiPv++) {
                        TreeMap<Integer, AnalysisDisplayEntry> branch =
                                host.analysisPvEntries.get(multiPv);
                        if (branch != null && !branch.isEmpty()) {
                            appendAnalysisArrows(arrows, branch.lastEntry().getValue(), multiPv, 2);
                        }
                    }
                }
            }
        }
        arrows.addAll(buildRescoreReviewArrows());
        host.boardView.setShowArrow(!arrows.isEmpty() || host.showEngineArrows);
        host.boardView.setAnalysisArrows(arrows);
    }

    void appendAnalysisArrows(List<ChessBoardView.AnalysisArrow> arrows,
                              AnalysisDisplayEntry entry, int label, int maxSteps) {
        if (entry == null) return;
        for (int i = 0; i < entry.pv.size() && i < maxSteps; i++) {
            try {
                arrows.add(new ChessBoardView.AnalysisArrow(
                        Move.fromEngineStep(entry.pv.get(i)), label, i == 1, i >= 2));
            } catch (Exception ignored) {
            }
        }
    }

    CharSequence buildV68EngineAnalysisContent() {
        TreeMap<Integer, AnalysisDisplayEntry> pv1 = host.analysisPvEntries.get(1);
        boolean redAtBottom = host.boardView == null || !host.boardView.isReversed();
        return EngineAnalysisPresentation.buildSinglePvContent(host, pv1,
                host.latestDepthOnly, redAtBottom, "引擎分析中……");
    }

    List<EngineAnalysisPanel.DepthModule> buildMultiPvModules() {
        boolean redAtBottom = host.boardView == null || !host.boardView.isReversed();
        List<EngineAnalysisPanel.DepthModule> modules =
                EngineAnalysisPresentation.buildMultiPvModules(host.analysisPvEntries,
                        host.analysisExpectedMultiPv, host.latestDepthOnly, redAtBottom,
                        host.lastMultiPvModules);
        if (!modules.isEmpty()) host.lastMultiPvModules = modules;
        return modules;
    }

    CharSequence buildCompactSinglePvContent() {
        TreeMap<Integer, AnalysisDisplayEntry> pv1 = host.analysisPvEntries.get(1);
        boolean redAtBottom = host.boardView == null || !host.boardView.isReversed();
        return EngineAnalysisPresentation.buildCompactSinglePvContent(host, pv1,
                host.latestDepthOnly, redAtBottom, host.engineContent);
    }

    private TreeSet<Integer> completeMultiPvDepths() {
        return EngineAnalysisPresentation.completeDepths(host.analysisPvEntries,
                host.analysisExpectedMultiPv);
    }
}

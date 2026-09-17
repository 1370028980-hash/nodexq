package com.tyl.xiangqi.ndxq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Activity 持有的棋局会话、分支及重打分任务数据。 */
enum GameEndType {
    RED_WIN, BLACK_WIN, AGREED_DRAW, NO_CAPTURE_DRAW
}

final class SavedSession {
    private static final String START_FEN =
            "rnbakabnr/9/1c5c1/p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    boolean analysisMode;
    boolean competitiveResultEligible;
    boolean completedDuelGame;
    boolean enginePlaysRed;
    int difficultyIndex;
    int sixtyMoveDrawArmedPly = -1;
    String gameResultTag = "*";
    String baseFen = START_FEN;
    String initialComment = "";
    final ManualMetadata manualMetadata = new ManualMetadata();
    final List<String> engineSteps = new ArrayList<String>();
    final List<String> readableMoves = new ArrayList<String>();
    final List<String> comments = new ArrayList<String>();
    final List<Integer> scores = new ArrayList<Integer>();
    final List<Integer> matePlies = new ArrayList<Integer>();
    final List<Boolean> scoreKnown = new ArrayList<Boolean>();
    final List<String> rescoreRecommendedMoves = new ArrayList<String>();
    final List<Boolean> rescoreScoreKnown = new ArrayList<Boolean>();
    int initialScoreRed;
    int initialMatePly;
    boolean initialScoreKnown;
    boolean initialRescoreScoreKnown;
    final Map<Integer, List<ManualVariation>> variations =
            new HashMap<Integer, List<ManualVariation>>();
    final Map<Integer, String> activeBranchLabels = new HashMap<Integer, String>();
}

final class BranchDisplay {
    final String label;
    final String preview;
    final boolean active;
    final int variationIndex;

    private BranchDisplay(String label, String preview, boolean active, int variationIndex) {
        this.label = label == null || label.length() == 0 ? "A" : label;
        this.preview = preview == null || preview.length() == 0 ? "----" : preview;
        this.active = active;
        this.variationIndex = variationIndex;
    }

    static BranchDisplay active(String label, String preview) {
        return new BranchDisplay(label, preview, true, -1);
    }

    static BranchDisplay variation(String label, String preview, int index) {
        return new BranchDisplay(label, preview, false, index);
    }
}

final class RescoreBranchTask {
    final List<String> prefixMoves;
    final ManualVariation variation;
    final List<String> parentRecommendations;
    final int parentRecommendationIndex;
    final int localStart;
    final int localEndExclusive;

    RescoreBranchTask(List<String> prefixMoves, ManualVariation variation,
                      List<String> parentRecommendations, int parentRecommendationIndex,
                      int localStart, int localEndExclusive) {
        this.prefixMoves = prefixMoves == null
                ? Collections.<String>emptyList() : prefixMoves;
        this.variation = variation;
        this.parentRecommendations = parentRecommendations;
        this.parentRecommendationIndex = parentRecommendationIndex;
        this.localStart = localStart;
        this.localEndExclusive = localEndExclusive;
    }
}

final class ManualVariation {
    String label = "";
    /** 原东萍 UBB 的变例 ID；零表示非东萍来源或后来新增的分支。 */
    int dhtmlVariationId;
    final List<String> engineSteps = new ArrayList<String>();
    final List<String> readableMoves = new ArrayList<String>();
    final List<String> comments = new ArrayList<String>();
    final List<Integer> scores = new ArrayList<Integer>();
    final List<Integer> matePlies = new ArrayList<Integer>();
    final List<Boolean> scoreKnown = new ArrayList<Boolean>();
    final List<String> rescoreRecommendedMoves = new ArrayList<String>();
    final List<Boolean> rescoreScoreKnown = new ArrayList<Boolean>();
    /** 相对本线路起点的后续分支。 */
    final Map<Integer, List<ManualVariation>> variations =
            new HashMap<Integer, List<ManualVariation>>();
    /** 相对本线路起点的后续分支当前选中编号。 */
    final Map<Integer, String> activeBranchLabels = new HashMap<Integer, String>();
}

package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.List;

/** 引擎信息进入 UI 前使用的不可变数据。 */
final class AnalysisUiUpdate {
    final int generation;
    final String positionKey;
    final boolean redToMove;
    final PikafishEngine.EngineInfo info;
    final AnalysisDisplayEntry entry;

    AnalysisUiUpdate(int generation, String positionKey, boolean redToMove,
                     PikafishEngine.EngineInfo info, AnalysisDisplayEntry entry) {
        this.generation = generation;
        this.positionKey = positionKey == null ? "" : positionKey;
        this.redToMove = redToMove;
        this.info = info;
        this.entry = entry;
    }
}

final class AnalysisDisplayEntry {
    final int depth;
    final boolean hasScore;
    final boolean mateScore;
    final int redScoreRaw;
    final int redScoreValue;
    final boolean hasWdl;
    final int redWdlWin;
    final int redWdlDraw;
    final int redWdlLoss;
    final String timeText;
    final String npsText;
    final String nodesText;
    final String hashFullText;
    final int noCaptureMoveCount;
    final String cnPv;
    final List<String> pv;
    final boolean redToMoveAtRoot;

    AnalysisDisplayEntry(PikafishEngine.EngineInfo info, String cnPv, boolean redToMoveAtRoot,
                         int noCaptureMoveCount, int situationMateLimit) {
        this.redToMoveAtRoot = redToMoveAtRoot;
        this.depth = info == null ? -1 : info.depth;
        this.hasScore = info != null && info.hasScore;
        this.mateScore = hasScore && info.mateScore;
        int raw = !hasScore ? 0 : info.score;
        this.redScoreRaw = redToMoveAtRoot ? raw : -raw;
        this.redScoreValue = !hasScore ? 0 : (mateScore
                ? (redScoreRaw >= 0 ? situationMateLimit : -situationMateLimit)
                : redScoreRaw);
        this.hasWdl = info != null && info.hasWdl;
        if (!hasWdl) {
            redWdlWin = redWdlDraw = redWdlLoss = -1;
        } else if (redToMoveAtRoot) {
            redWdlWin = info.wdlWin;
            redWdlDraw = info.wdlDraw;
            redWdlLoss = info.wdlLoss;
        } else {
            redWdlWin = info.wdlLoss;
            redWdlDraw = info.wdlDraw;
            redWdlLoss = info.wdlWin;
        }
        this.timeText = EngineAnalysisPanel.formatTime(info == null ? -1L : info.timeMs);
        this.npsText = EngineAnalysisPanel.formatCount(info == null ? -1L : info.nps);
        this.nodesText = EngineAnalysisPanel.formatCount(info == null ? -1L : info.nodes);
        this.hashFullText = EngineAnalysisPanel.formatHashFull(info == null ? -1 : info.hashFull);
        this.noCaptureMoveCount = noCaptureMoveCount;
        this.cnPv = cnPv == null || cnPv.length() == 0 ? "-" : cnPv;
        this.pv = info == null ? new ArrayList<String>() : new ArrayList<String>(info.pv);
    }

    int ownScoreValue(boolean redAtBottom) {
        return redAtBottom ? redScoreValue : -redScoreValue;
    }

    String ownScoreText(boolean redAtBottom) {
        if (!hasScore) return "-";
        int score = redAtBottom ? redScoreRaw : -redScoreRaw;
        if (mateScore) return score >= 0 ? "M" + Math.abs(score) : "-M" + Math.abs(score);
        return String.valueOf(score);
    }

    String ownWdlText(boolean redAtBottom) {
        if (!hasWdl) return "-";
        int win = redAtBottom ? redWdlWin : redWdlLoss;
        int draw = redWdlDraw;
        int loss = redAtBottom ? redWdlLoss : redWdlWin;
        return win + "/" + draw + "/" + loss;
    }
}

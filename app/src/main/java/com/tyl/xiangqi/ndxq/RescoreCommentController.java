package com.tyl.xiangqi.ndxq;

import java.util.List;

/** Writes the optional re-score summary line without changing the user's comment body. */
final class RescoreCommentController {
    private static final String PREFIX = "【节点象棋重新打分：";

    private final MainActivity host;

    RescoreCommentController(MainActivity host) {
        this.host = host;
    }

    void updateInitialComment() {
        if (!host.initialScoreKnown || !host.initialRescoreScoreKnown) return;
        host.initialComment = withSummary(host.initialComment,
                summary(host.initialScoreRed, host.initialMatePly));
    }

    void updateMainComment(int index) {
        if (index < 0 || index >= host.moveComments.size()
                || index >= host.redPerspectiveScores.size()
                || index >= host.scoreMatePlies.size()
                || index >= host.scoreKnown.size()
                || index >= host.rescoreScoreKnown.size()
                || !Boolean.TRUE.equals(host.scoreKnown.get(index))
                || !Boolean.TRUE.equals(host.rescoreScoreKnown.get(index))) return;
        host.moveComments.set(index, withSummary(host.moveComments.get(index),
                summary(host.redPerspectiveScores.get(index), host.scoreMatePlies.get(index))));
    }

    void updateVariationComment(ManualVariation variation, int index) {
        if (variation == null || index < 0 || index >= variation.comments.size()
                || index >= variation.scores.size() || index >= variation.matePlies.size()
                || index >= variation.scoreKnown.size() || index >= variation.rescoreScoreKnown.size()
                || !Boolean.TRUE.equals(variation.scoreKnown.get(index))
                || !Boolean.TRUE.equals(variation.rescoreScoreKnown.get(index))) return;
        variation.comments.set(index, withSummary(variation.comments.get(index),
                summary(variation.scores.get(index), variation.matePlies.get(index))));
    }

    private String summary(int redScore, int matePly) {
        if (matePly > 0) {
            return PREFIX + (redScore >= 0 ? "红方胜 M" : "黑方胜 M") + matePly + "】";
        }
        if (redScore >= MainActivity.SITUATION_MATE_LIMIT) return PREFIX + "红方胜】";
        if (redScore <= -MainActivity.SITUATION_MATE_LIMIT) return PREFIX + "黑方胜】";
        if (redScore > 0) return PREFIX + "红优 +" + redScore + "】";
        if (redScore < 0) return PREFIX + "黑优 +" + Math.abs(redScore) + "】";
        return PREFIX + "均势 0】";
    }

    private String withSummary(String comment, String summary) {
        String body = comment == null ? "" : comment.trim();
        if (body.startsWith(PREFIX)) {
            int lineEnd = body.indexOf('\n');
            body = lineEnd < 0 ? "" : body.substring(lineEnd + 1).trim();
        }
        return body.length() == 0 ? summary : summary + "\n" + body;
    }
}

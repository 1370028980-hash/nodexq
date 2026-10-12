package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.PositionScore;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.util.List;
import java.util.Map;

/** 旧留存的 ±6000 经局面或正式已完成对局结果确认后升级为终局标记，包括嵌套分支。 */
final class SessionScoreMigration {
    private SessionScoreMigration() {}

    static void upgrade(SavedSession saved) {
        try {
            char[][] board = XiangqiRules.fromFen(saved.baseFen);
            boolean redToMove = XiangqiRules.redToMoveFromFen(saved.baseFen);
            if (isCandidate(saved.initialScoreRed, saved.initialMatePly, saved.initialScoreKnown)) {
                Integer terminal = PositionScore.terminalScore(board, redToMove);
                if (terminal != null) {
                    saved.initialScoreRed = terminal;
                    saved.initialMatePly = PositionScore.TERMINAL;
                }
            }
            upgradeLine(board, redToMove, saved.engineSteps, saved.scores,
                    saved.matePlies, saved.scoreKnown, saved.variations);
            // 认输等结束方式不一定是棋盘将死；仅采信正式已完成对局的明确结果。
            int last = saved.engineSteps.size() - 1;
            if (saved.completedDuelGame && !saved.analysisMode && last >= 0
                    && isCandidate(saved.scores.get(last), saved.matePlies.get(last),
                    Boolean.TRUE.equals(saved.scoreKnown.get(last)))) {
                int score = saved.scores.get(last);
                if ((score > 0 && "1-0".equals(saved.gameResultTag))
                        || (score < 0 && "0-1".equals(saved.gameResultTag))) {
                    saved.matePlies.set(last, PositionScore.TERMINAL);
                }
            }
        } catch (Exception ignored) {
            // 不完整或损坏的棋谱不能仅凭分值猜测胜负，保留已有数据。
        }
    }

    private static void upgradeLine(char[][] board, boolean redToMove, List<String> moves,
                                    List<Integer> scores, List<Integer> mates,
                                    List<Boolean> known,
                                    Map<Integer, List<ManualVariation>> branches) {
        for (int i = 0; i <= moves.size(); i++) {
            List<ManualVariation> alternatives = branches == null ? null : branches.get(i);
            if (alternatives != null) {
                for (ManualVariation branch : alternatives) {
                    if (branch == null) continue;
                    try {
                        upgradeLine(copyBoard(board), redToMove, branch.engineSteps,
                                branch.scores, branch.matePlies, branch.scoreKnown, branch.variations);
                    } catch (Exception ignored) {
                        // 一个坏分支不影响同节点的其他分支与主线。
                    }
                }
            }
            if (i == moves.size()) break;
            Move move = Move.fromEngineStep(moves.get(i));
            if (!XiangqiRules.isLegalMove(board, move)) return;
            board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
            board[move.fromRow][move.fromCol] = ' ';
            redToMove = !redToMove;
            if (i < scores.size() && i < mates.size() && i < known.size()
                    && isCandidate(scores.get(i), mates.get(i), Boolean.TRUE.equals(known.get(i)))) {
                Integer terminal = PositionScore.terminalScore(board, redToMove);
                if (terminal != null) {
                    scores.set(i, terminal);
                    mates.set(i, PositionScore.TERMINAL);
                }
            }
        }
    }

    private static boolean isCandidate(int score, int mate, boolean known) {
        return known && mate == 0 && Math.abs((long) score) == PositionScore.WIN_DISPLAY_SCORE;
    }

    private static char[][] copyBoard(char[][] board) {
        char[][] copy = new char[board.length][];
        for (int i = 0; i < board.length; i++) copy[i] = board[i].clone();
        return copy;
    }
}

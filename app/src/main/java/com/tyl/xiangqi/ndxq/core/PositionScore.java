package com.tyl.xiangqi.ndxq.core;

/** 评分元数据：0 为普通分，正数为引擎 Mate 距离，-1 为已确认的胜负终局。 */
public final class PositionScore {
    public static final int TERMINAL = -1;
    public static final int WIN_DISPLAY_SCORE = 6000;

    private PositionScore() {}

    public static boolean isForced(int matePly) {
        return matePly > 0 || matePly == TERMINAL;
    }

    /** 仅棋盘规则确认的胜负；普通分恰为 ±6000 不参与终局判定。 */
    public static Integer terminalScore(char[][] board, boolean redToMove) {
        boolean redKing = false;
        boolean blackKing = false;
        for (char[] row : board) {
            for (char piece : row) {
                if (piece == 'K') redKing = true;
                else if (piece == 'k') blackKing = true;
            }
        }
        if (!redKing && !blackKing) return null;
        if (!redKing) return -WIN_DISPLAY_SCORE;
        if (!blackKing) return WIN_DISPLAY_SCORE;
        if (XiangqiRules.generateLegalMoves(board, redToMove).isEmpty()) {
            return redToMove ? -WIN_DISPLAY_SCORE : WIN_DISPLAY_SCORE;
        }
        return null;
    }
}

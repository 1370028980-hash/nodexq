package com.tyl.xiangqi.ndxq.core;

/**
 * 中国象棋一步棋。字段命名沿用 PC 端 ChessBoard.Step 的含义：
 * row/col 对应 board[row][col]，row=0 是黑方底线，row=9 是红方底线。
 */
public final class Move {
    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;

    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
    }

    /**
     * 生成与原 PC 端 stepForEngine 一致的四字符着法：
     * col -> a-i，row -> 9-row，例如红方底线车从 a0 起算。
     */
    public String toEngineStep() {
        return "" + (char) ('a' + fromCol) + (9 - fromRow)
                + (char) ('a' + toCol) + (9 - toRow);
    }

    /**
     * 解析 UCI/UCCI 引擎返回的四字符着法，例如 a0a1。
     * 这里与 toEngineStep 完全互逆，便于接入皮卡鱼 bestmove。
     */
    public static Move fromEngineStep(String step) {
        if (step == null || step.length() < 4) {
            throw new IllegalArgumentException("引擎着法长度不足：" + step);
        }
        int fromCol = step.charAt(0) - 'a';
        int fromRow = 9 - (step.charAt(1) - '0');
        int toCol = step.charAt(2) - 'a';
        int toRow = 9 - (step.charAt(3) - '0');
        if (fromCol < 0 || fromCol > 8 || toCol < 0 || toCol > 8 || fromRow < 0 || fromRow > 9 || toRow < 0 || toRow > 9) {
            throw new IllegalArgumentException("引擎着法越界：" + step);
        }
        return new Move(fromRow, fromCol, toRow, toCol);
    }

    @Override
    public String toString() {
        return toEngineStep();
    }
}

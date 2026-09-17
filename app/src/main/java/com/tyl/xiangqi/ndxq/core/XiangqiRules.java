package com.tyl.xiangqi.ndxq.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 PC 端 public-Xiangqi 的 ChessBoard / XiangqiUtils 思路移植出的 Android 规则核心。
 *
 * 棋盘编码保持一致：
 *   红方大写：R N B A K C P = 车 马 相 仕 帅 炮 兵
 *   黑方小写：r n b a k c p = 车 马 象 士 将 炮 卒
 *   空位：空格 ' '
 *
 * 坐标保持一致：board[row][col]，row 0..9，col 0..8。
 */
public final class XiangqiRules {
    private XiangqiRules() {}

    public static final String START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    public static char[][] newBoard() {
        char[][] b = new char[10][9];
        initBoard(b);
        return b;
    }

    public static void initBoard(char[][] board) {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                board[r][c] = ' ';
            }
        }
        char[] black = {'r','n','b','a','k','a','b','n','r'};
        char[] red = {'R','N','B','A','K','A','B','N','R'};
        for (int c = 0; c < 9; c++) {
            board[0][c] = black[c];
            board[9][c] = red[c];
        }
        board[2][1] = board[2][7] = 'c';
        board[7][1] = board[7][7] = 'C';
        for (int c = 0; c < 9; c += 2) {
            board[3][c] = 'p';
            board[6][c] = 'P';
        }
    }

    public static boolean inBoard(int row, int col) {
        return row >= 0 && row < 10 && col >= 0 && col < 9;
    }

    public static boolean isRed(char p) {
        return p >= 'A' && p <= 'Z';
    }

    public static boolean isBlack(char p) {
        return p >= 'a' && p <= 'z';
    }

    public static boolean isPiece(char p) {
        return isRed(p) || isBlack(p);
    }

    public static boolean sameSide(char a, char b) {
        return isPiece(a) && isPiece(b) && isRed(a) == isRed(b);
    }

    /** 标准中国象棋中单方同类棋子的最大数量。 */
    public static int maxPieceCount(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'k': return 1;
            case 'p': return 5;
            case 'r': case 'n': case 'b': case 'a': case 'c': return 2;
            default: return 0;
        }
    }

    public static int countPiece(char[][] board, char piece) {
        if (board == null || !isPiece(piece)) return 0;
        int count = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == piece) count++;
            }
        }
        return count;
    }

    public static char[][] copyBoard(char[][] board) {
        char[][] copy = new char[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    /**
     * 与原 PC 端 canGo 相同，只判断棋子自身走法和目标方，不判断“送将”。
     */
    public static boolean canPieceMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (!inBoard(fromRow, fromCol) || !inBoard(toRow, toCol)) return false;
        char piece = board[fromRow][fromCol];
        char target = board[toRow][toCol];
        if (!isPiece(piece) || sameSide(piece, target)) return false;

        int dr = toRow - fromRow;
        int dc = toCol - fromCol;
        int absR = Math.abs(dr);
        int absC = Math.abs(dc);

        switch (piece) {
            case 'r': case 'R':
                return canRookMove(board, fromRow, fromCol, toRow, toCol);
            case 'n': case 'N':
                if (!((absR == 2 && absC == 1) || (absR == 1 && absC == 2))) return false;
                if (absR == 2) {
                    return board[fromRow + Integer.signum(dr)][fromCol] == ' ';
                }
                return board[fromRow][fromCol + Integer.signum(dc)] == ' ';
            case 'b': case 'B': {
                boolean red = isRed(piece);
                // 相/象不能过河：红方只能在 row 5..9，黑方只能在 row 0..4。
                if (red && toRow < 5) return false;
                if (!red && toRow > 4) return false;
                if (absR != 2 || absC != 2) return false;
                return board[(fromRow + toRow) / 2][(fromCol + toCol) / 2] == ' ';
            }
            case 'a': case 'A': {
                boolean red = isRed(piece);
                if (toCol < 3 || toCol > 5) return false;
                if (red && toRow < 7) return false;
                if (!red && toRow > 2) return false;
                return absR == 1 && absC == 1;
            }
            case 'k': case 'K': {
                boolean red = isRed(piece);
                if (toCol < 3 || toCol > 5) return false;
                if (red && toRow < 7) return false;
                if (!red && toRow > 2) return false;
                return absR + absC == 1;
            }
            case 'c': case 'C': {
                if (fromRow != toRow && fromCol != toCol) return false;
                int screens = countBetween(board, fromRow, fromCol, toRow, toCol);
                return target == ' ' ? screens == 0 : screens == 1;
            }
            case 'p': {
                // 黑卒向 row 增大的方向走；过河前只能前进，过河后可横走。
                if (fromRow < 5) return dc == 0 && dr == 1;
                return (dc == 0 && dr == 1) || (dr == 0 && absC == 1);
            }
            case 'P': {
                // 红兵向 row 减小的方向走；过河前只能前进，过河后可横走。
                if (fromRow >= 5) return dc == 0 && dr == -1;
                return (dc == 0 && dr == -1) || (dr == 0 && absC == 1);
            }
            default:
                return false;
        }
    }

    public static boolean isLegalMove(char[][] board, Move move) {
        return isLegalMove(board, move.fromRow, move.fromCol, move.toRow, move.toCol);
    }

    /**
     * 合法走法 = canPieceMove + 走后己方不被将军。
     * 这一步对应 PC 端 ChessBoard.move() 里先走子、再调用 XiangqiUtils.isJiang()、非法则回滚的逻辑。
     */
    public static boolean isLegalMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (!canPieceMove(board, fromRow, fromCol, toRow, toCol)) return false;
        char piece = board[fromRow][fromCol];
        boolean red = isRed(piece);
        char captured = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = ' ';
        boolean safe = !isInCheck(board, red);
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = captured;
        return safe;
    }

    public static Move applyMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (!isLegalMove(board, fromRow, fromCol, toRow, toCol)) return null;
        Move move = new Move(fromRow, fromCol, toRow, toCol);
        board[toRow][toCol] = board[fromRow][fromCol];
        board[fromRow][fromCol] = ' ';
        return move;
    }

    public static List<Move> generateLegalMoves(char[][] board, boolean redSide) {
        ArrayList<Move> out = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (!isPiece(p) || isRed(p) != redSide) continue;
                for (int tr = 0; tr < 10; tr++) {
                    for (int tc = 0; tc < 9; tc++) {
                        if ((r != tr || c != tc) && isLegalMove(board, r, c, tr, tc)) {
                            out.add(new Move(r, c, tr, tc));
                        }
                    }
                }
            }
        }
        return out;
    }

    public static boolean isCheckmate(char[][] board, boolean redSide) {
        return isInCheck(board, redSide) && generateLegalMoves(board, redSide).isEmpty();
    }

    public static boolean isInCheck(char[][] board, boolean redKing) {
        int[] king = findKing(board, redKing);
        if (king == null) return false;
        int kr = king[0];
        int kc = king[1];

        // 将帅照面。
        int[] enemyKing = findKing(board, !redKing);
        if (enemyKing != null && enemyKing[1] == kc && countBetween(board, kr, kc, enemyKing[0], enemyKing[1]) == 0) {
            return true;
        }

        // 其它棋子攻击。
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (!isPiece(p) || isRed(p) == redKing) continue;
                if (canPieceAttack(board, r, c, kr, kc)) return true;
            }
        }
        return false;
    }

    private static int[] findKing(char[][] board, boolean red) {
        char king = red ? 'K' : 'k';
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == king) return new int[] {r, c};
            }
        }
        return null;
    }

    private static boolean canPieceAttack(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        char piece = board[fromRow][fromCol];
        int dr = toRow - fromRow;
        int dc = toCol - fromCol;
        int absR = Math.abs(dr);
        int absC = Math.abs(dc);
        switch (piece) {
            case 'r': case 'R':
                return canRookMove(board, fromRow, fromCol, toRow, toCol);
            case 'n': case 'N':
                if (!((absR == 2 && absC == 1) || (absR == 1 && absC == 2))) return false;
                if (absR == 2) return board[fromRow + Integer.signum(dr)][fromCol] == ' ';
                return board[fromRow][fromCol + Integer.signum(dc)] == ' ';
            case 'b': case 'B':
                return absR == 2 && absC == 2 && board[(fromRow + toRow) / 2][(fromCol + toCol) / 2] == ' ';
            case 'a': case 'A':
                return absR == 1 && absC == 1;
            case 'k': case 'K':
                return absR + absC == 1;
            case 'c': case 'C':
                return (fromRow == toRow || fromCol == toCol) && countBetween(board, fromRow, fromCol, toRow, toCol) == 1;
            case 'p':
                if (fromRow < 5) return dc == 0 && dr == 1;
                return (dc == 0 && dr == 1) || (dr == 0 && absC == 1);
            case 'P':
                if (fromRow >= 5) return dc == 0 && dr == -1;
                return (dc == 0 && dr == -1) || (dr == 0 && absC == 1);
            default:
                return false;
        }
    }

    private static boolean canRookMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        return countBetween(board, fromRow, fromCol, toRow, toCol) == 0;
    }

    private static int countBetween(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow == toRow) {
            int start = Math.min(fromCol, toCol) + 1;
            int end = Math.max(fromCol, toCol);
            int count = 0;
            for (int c = start; c < end; c++) if (board[fromRow][c] != ' ') count++;
            return count;
        }
        if (fromCol == toCol) {
            int start = Math.min(fromRow, toRow) + 1;
            int end = Math.max(fromRow, toRow);
            int count = 0;
            for (int r = start; r < end; r++) if (board[r][fromCol] != ' ') count++;
            return count;
        }
        return Integer.MAX_VALUE / 2;
    }

    public static String toFen(char[][] board, boolean redToMove) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            int empty = 0;
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (p == ' ') {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(p);
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 9) sb.append('/');
        }
        sb.append(redToMove ? " w - - 0 1" : " b - - 0 1");
        return sb.toString();
    }

    public static char[][] fromFen(String fen) {
        char[][] board = new char[10][9];
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) board[r][c] = ' ';
        }
        if (fen == null) return newBoard();
        String placement = fen.trim().split("\\s+")[0];
        String[] rows = placement.split("/");
        if (rows.length != 10) throw new IllegalArgumentException("FEN 行数必须为 10");
        for (int r = 0; r < 10; r++) {
            int c = 0;
            for (int i = 0; i < rows[r].length(); i++) {
                char ch = rows[r].charAt(i);
                if (Character.isDigit(ch)) {
                    int n = ch - '0';
                    for (int k = 0; k < n; k++) {
                        if (c >= 9) throw new IllegalArgumentException("FEN 列数越界");
                        board[r][c++] = ' ';
                    }
                } else {
                    if (c >= 9 || "rnbakcpRNBAKCP".indexOf(ch) < 0) throw new IllegalArgumentException("FEN 棋子非法：" + ch);
                    board[r][c++] = ch;
                }
            }
            if (c != 9) throw new IllegalArgumentException("FEN 第 " + (r + 1) + " 行列数不是 9");
        }
        return board;
    }

    public static boolean redToMoveFromFen(String fen) {
        if (fen == null) return true;
        String[] parts = fen.trim().split("\\s+");
        return parts.length < 2 || !"b".equalsIgnoreCase(parts[1]);
    }

    public static String pieceName(char p) {
        switch (p) {
            case 'r': case 'R': return "车";
            case 'n': case 'N': return "马";
            case 'b': return "象";
            case 'B': return "相";
            case 'a': return "士";
            case 'A': return "仕";
            case 'k': return "将";
            case 'K': return "帅";
            case 'c': case 'C': return "炮";
            case 'p': return "卒";
            case 'P': return "兵";
            default: return "";
        }
    }
}

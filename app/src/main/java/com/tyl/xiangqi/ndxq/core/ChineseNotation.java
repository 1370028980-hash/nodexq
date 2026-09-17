package com.tyl.xiangqi.ndxq.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中文记谱转换器。
 *
 * 这部分按 public-Xiangqi/TChess 的 XiangqiUtils.translate 思路移植：
 * - 引擎只返回 ICCS/UCI 坐标，例如 h2e2；
 * - 界面必须结合当前棋盘，把它翻译成“炮二平五、马８进７”这类中文记谱；
 * - 翻译一串 PV 时，每翻译一步都在临时棋盘上落子，保证后续着法按变化线继续解释。
 */
public final class ChineseNotation {
    private static final Map<Character, String> CN = new HashMap<Character, String>();

    static {
        CN.put('r', "车"); CN.put('R', "车");
        CN.put('n', "马"); CN.put('N', "马");
        CN.put('b', "象"); CN.put('B', "相");
        CN.put('a', "士"); CN.put('A', "仕");
        CN.put('k', "将"); CN.put('K', "帅");
        CN.put('c', "炮"); CN.put('C', "炮");
        CN.put('p', "卒"); CN.put('P', "兵");
        CN.put('１', "一"); CN.put('２', "二"); CN.put('３', "三");
        CN.put('４', "四"); CN.put('５', "五"); CN.put('６', "六");
        CN.put('７', "七"); CN.put('８', "八"); CN.put('９', "九");
    }

    private ChineseNotation() {}

    public static String translate(char[][] board, Move move, boolean hasGo) {
        if (move == null) return "";
        return translate(board, move.toEngineStep(), hasGo);
    }

    /**
     * 翻译单步。
     * @param hasGo true 表示传入棋盘已经走完这步，此时棋子在目标格；false 表示棋子仍在起点格。
     */
    public static String translate(char[][] board, String move, boolean hasGo) {
        if (move == null || move.length() < 4) return String.valueOf(move);
        try {
            Move m = Move.fromEngineStep(move.toLowerCase(Locale.ROOT));
            char piece = hasGo ? board[m.toRow][m.toCol] : board[m.fromRow][m.fromCol];
            if (!XiangqiRules.isPiece(piece)) return move;
            boolean isRed = XiangqiRules.isRed(piece);
            StringBuilder sb = new StringBuilder();
            String prefix = getSameFilePrefix(board, m.fromRow, m.fromCol, m.toRow, m.toCol, piece, isRed, hasGo);
            if (prefix != null) {
                sb.append(prefix).append(CN.get(piece));
            } else {
                sb.append(CN.get(piece));
                char pos = getPos(m.fromCol, isRed);
                sb.append(isRed ? CN.get(pos) : Character.toString((char)('0' + (pos - '０'))));
            }

            if (m.fromRow == m.toRow && m.fromCol != m.toCol) {
                sb.append("平");
                char pos = getPos(m.toCol, isRed);
                sb.append(isRed ? CN.get(pos) : Character.toString((char)('0' + (pos - '０'))));
            } else if (m.fromRow != m.toRow && m.fromCol == m.toCol) {
                if (isRed) sb.append(m.fromRow > m.toRow ? "进" : "退");
                else sb.append(m.fromRow < m.toRow ? "进" : "退");
                int dist = Math.abs(m.fromRow - m.toRow);
                char pos = (char) ('０' + dist);
                sb.append(isRed ? CN.get(pos) : String.valueOf(dist));
            } else {
                if (isRed) sb.append(m.fromRow > m.toRow ? "进" : "退");
                else sb.append(m.fromRow < m.toRow ? "进" : "退");
                char pos = getPos(m.toCol, isRed);
                sb.append(isRed ? CN.get(pos) : Character.toString((char)('0' + (pos - '０'))));
            }
            return normalizeArabicDigits(sb.toString());
        } catch (Exception ex) {
            return normalizeArabicDigits(move);
        }
    }

    /**
     * 全局统一：中文记谱中出现的阿拉伯数字一律使用全角形式。
     * 红方仍保留“一二三四五六七八九”，黑方和外部棋谱中的“1/2/…”显示为“１/２/…”。
     */
    public static String normalizeArabicDigits(String text) {
        if (text == null || text.length() == 0) return text == null ? "" : text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') out.append((char)('０' + (ch - '0')));
            else out.append(ch);
        }
        return out.toString();
    }

    /**
     * 兼容旧命名：此前函数名叫 normalizeArabicDigits，现语义改为“统一为全角阿拉伯数字”。
     */
    public static String toFullWidthArabicDigits(String text) {
        return normalizeArabicDigits(text);
    }

    /** 翻译引擎 PV，并在临时棋盘上逐步落子，等价于 PC 端 ChessBoard.translate(List<String>)。 */
    public static String translatePv(char[][] currentBoard, List<String> moves) {
        if (moves == null || moves.isEmpty()) return "";
        char[][] board = XiangqiRules.copyBoard(currentBoard);
        StringBuilder sb = new StringBuilder();
        for (String step : moves) {
            if (step == null || step.length() < 4) continue;
            String normalized = step.toLowerCase(Locale.ROOT);
            Move m;
            try {
                m = Move.fromEngineStep(normalized);
            } catch (Exception ex) {
                continue;
            }
            if (sb.length() > 0) sb.append("  ");
            sb.append(translate(board, normalized, false));
            if (XiangqiRules.inBoard(m.fromRow, m.fromCol) && XiangqiRules.inBoard(m.toRow, m.toCol)) {
                char piece = board[m.fromRow][m.fromCol];
                board[m.toRow][m.toCol] = piece;
                board[m.fromRow][m.fromCol] = ' ';
            }
        }
        return sb.toString();
    }

    public static List<Move> firstMovesFromPv(List<String> pv, int max) {
        List<Move> list = new ArrayList<Move>();
        if (pv == null) return list;
        for (String s : pv) {
            if (list.size() >= max) break;
            try {
                list.add(Move.fromEngineStep(s));
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static char getPos(int col, boolean isRed) {
        if (isRed) return (char) ('０' + 9 - col);
        return (char) ('０' + col + 1);
    }

    private static String cnDigit(int n) {
        String[] arr = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        return n >= 1 && n <= 9 ? arr[n] : String.valueOf(n);
    }

    private static String getSameFilePrefix(char[][] board, int fromRow, int fromCol, int toRow, int toCol,
                                            char piece, boolean isRed, boolean hasGo) {
        if (piece == 'r' || piece == 'c' || piece == 'n' || piece == 'R' || piece == 'C' || piece == 'N') {
            for (int i = 0; i < fromRow; i++) {
                if (board[i][fromCol] == piece && !(hasGo && i == toRow && fromCol == toCol)) {
                    return isRed ? "后" : "前";
                }
            }
            for (int i = fromRow + 1; i < 10; i++) {
                if (board[i][fromCol] == piece && !(hasGo && i == toRow && fromCol == toCol)) {
                    return isRed ? "前" : "后";
                }
            }
            return null;
        }
        if (piece == 'p' || piece == 'P') {
            int before = 0, after = 0;
            for (int i = 0; i < fromRow; i++) {
                if (board[i][fromCol] == piece && !(hasGo && i == toRow && fromCol == toCol)) before++;
            }
            for (int i = fromRow + 1; i < 10; i++) {
                if (board[i][fromCol] == piece && !(hasGo && i == toRow && fromCol == toCol)) after++;
            }
            if (before == 0 && after == 0) return null;
            if (before + after >= 3) {
                return cnDigit((isRed ? before : after) + 1);
            }

            int left = 0, right = 0;
            for (int j = 8; j > fromCol; j--) {
                int count = countFile(board, j, piece, hasGo, toRow, toCol);
                if (count > 1) { right += count; break; }
            }
            for (int j = fromCol - 1; j >= 0; j--) {
                int count = countFile(board, j, piece, hasGo, toRow, toCol);
                if (count > 1) { left += count; break; }
            }
            if (left == 0 && right == 0) {
                if (before == 1 && after == 1) return "中";
                if (after == 0) return isRed ? "后" : "前";
                if (before == 0) return isRed ? "前" : "后";
            } else if (left > 0) {
                return cnDigit((isRed ? before : after + left) + 1);
            } else if (right > 0) {
                return cnDigit((isRed ? before + right : after) + 1);
            }
        }
        return null;
    }

    private static int countFile(char[][] board, int col, char piece, boolean hasGo, int toRow, int toCol) {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            if (board[i][col] == piece && !(hasGo && i == toRow && col == toCol)) count++;
        }
        return count;
    }
}

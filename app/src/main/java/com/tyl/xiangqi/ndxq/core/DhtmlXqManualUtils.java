package com.tyl.xiangqi.ndxq.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东萍 DhtmlXQ UBB 棋谱的读写工具。
 *
 * <p>坐标采用东萍的左上角 (0,0) 至右下角 (8,9)，与内部 ICCS 的行号方向相反。
 * 分支标签 {@code move_父变着_起始着号_本变着} 中的起始着号以整盘第几着计、从 1 开始；
 * 变例注释的着号也以整盘计；内部变招树则以相对父线路的 0 基“被替换着法下标”保存，
 * 因此嵌套时需扣除父分支起始着号。</p>
 */
public final class DhtmlXqManualUtils {
    private static final String PIECES = "RNBAKABNRCCPPPPPrnbakabnrccppppp";
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "\\[DhtmlXQ_([A-Za-z0-9_]+)\\](.*?)\\[/DhtmlXQ_\\1\\]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MOVE_TAG_PATTERN = Pattern.compile(
            "move_(\\d+)_(\\d+)_(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIN_COMMENT_TAG_PATTERN = Pattern.compile(
            "comment(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRANCH_COMMENT_TAG_PATTERN = Pattern.compile(
            "comment(\\d+)_(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_TREE_DEPTH = 512;

    private DhtmlXqManualUtils() {}

    public static boolean looksLikeDhtmlXq(String text) {
        if (text == null) return false;
        return Pattern.compile("(?i)\\[DhtmlXQ_binit\\]").matcher(text).find()
                && Pattern.compile("(?i)\\[DhtmlXQ_movelist\\]").matcher(text).find();
    }

    /** 将 FEN 局面转换成东萍固定 64 位 binit，供外部数据适配器复用。 */
    public static String binitFromFen(String fen) {
        return binitFromBoard(XiangqiRules.fromFen(normalizeFen(fen)));
    }

    public static PgnManualUtils.ParsedManual parse(String text, String fallbackFen) {
        if (!looksLikeDhtmlXq(text)) {
            throw new IllegalArgumentException("不是有效的东萍 DhtmlXQ UBB 棋谱");
        }
        Map<String, String> tags = parseTags(text);
        String binit = tags.get("binit");
        if (binit == null) throw new IllegalArgumentException("缺少 DhtmlXQ 初始局面");
        char[][] initialBoard = boardFromBinit(binit);

        RawLine main = new RawLine(0, -1, 1, movesFromDhtml(tags.get("movelist")));
        Map<Integer, RawLine> lines = new HashMap<Integer, RawLine>();
        lines.put(0, main);
        List<RawLine> variants = new ArrayList<RawLine>();
        Map<Integer, Map<Integer, String>> comments =
                new HashMap<Integer, Map<Integer, String>>();
        PgnManualUtils.ParsedManual out = new PgnManualUtils.ParsedManual();
        out.title = nonEmpty(tags.get("title"), "东萍 UBB 棋谱");
        out.event = nonEmpty(tags.get("event"), out.title);
        out.site = nonEmpty(tags.get("site"), tags.get("place"));
        out.date = nonEmpty(tags.get("date"), "");
        out.round = nonEmpty(tags.get("round"), "");
        out.red = nonEmpty(tags.get("red"), "");
        out.black = nonEmpty(tags.get("black"), "");
        out.initialComment = decodeComment(tags.get("remark"));
        // 早期节点象棋曾把东萍的整谱说明误写到 comment0；仍可打开，回存时统一修正。
        if (out.initialComment.length() == 0) out.initialComment = decodeComment(tags.get("comment0"));
        out.result = resultFromDhtml(tags.get("result"));
        out.hasExplicitFen = true;

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            Matcher moveMatcher = MOVE_TAG_PATTERN.matcher(name);
            if (moveMatcher.matches()) {
                int parent = parseInt(moveMatcher.group(1), -1);
                int start = parseInt(moveMatcher.group(2), 0);
                int id = parseInt(moveMatcher.group(3), -1);
                if (id <= 0 || lines.containsKey(id) || start <= 0) continue;
                RawLine line = new RawLine(id, parent, start, movesFromDhtml(value));
                lines.put(id, line);
                variants.add(line);
                continue;
            }
            Matcher branchCommentMatcher = BRANCH_COMMENT_TAG_PATTERN.matcher(name);
            if (branchCommentMatcher.matches()) {
                putComment(comments, parseInt(branchCommentMatcher.group(1), -1),
                        parseInt(branchCommentMatcher.group(2), 0), decodeComment(value));
                continue;
            }
            Matcher mainCommentMatcher = MAIN_COMMENT_TAG_PATTERN.matcher(name);
            if (mainCommentMatcher.matches()) {
                int index = parseInt(mainCommentMatcher.group(1), -1);
                if (index > 0) putComment(comments, 0, index, decodeComment(value));
            }
        }

        Collections.sort(variants, RAW_LINE_COMPARATOR);
        for (RawLine line : variants) {
            RawLine parent = lines.get(line.parentId);
            int node = line.startPlyOneBased - parent.startPlyOneBased;
            if (parent == null || parent == line || node < 0 || node > parent.steps.size()) continue;
            List<RawLine> children = parent.children.get(node);
            if (children == null) {
                children = new ArrayList<RawLine>();
                parent.children.put(node, children);
            }
            children.add(line);
        }
        sortRawChildren(main, new HashSet<Integer>());

        LineState red = parseLine(main, new Position(initialBoard, true), comments,
                new HashSet<Integer>(), 0);
        LineState black = red == null ? parseLine(main, new Position(initialBoard, false), comments,
                new HashSet<Integer>(), 0) : null;
        LineState selected = red != null ? red : black;
        if (selected == null) {
            throw new IllegalArgumentException("主线中含有与初始局面不符的东萍坐标着法");
        }

        out.fen = XiangqiRules.toFen(initialBoard, red != null);
        out.moves.addAll(selected.moves);
        out.chineseMoves.addAll(selected.chineseMoves);
        out.comments.addAll(selected.line.comments);
        out.variations.putAll(selected.line.variations);
        out.sourceMoveCount = out.moves.size();
        return out;
    }

    public static String export(String initialFen, List<String> steps, List<String> comments,
                                String initialComment,
                                Map<Integer, List<PgnManualUtils.VariationLine>> variations,
                                String resultToken) {
        char[][] board = XiangqiRules.fromFen(normalizeFen(initialFen));
        StringBuilder out = new StringBuilder();
        out.append("[DhtmlXQ]\n");
        appendTag(out, "title", "节点象棋棋谱");
        appendTag(out, "event", "");
        appendTag(out, "date", "");
        appendTag(out, "red", "红方");
        appendTag(out, "black", "黑方");
        appendTag(out, "result", resultToDhtml(resultToken));
        appendTag(out, "firstnum", "0");
        appendTag(out, "binit", binitFromBoard(board));
        appendTag(out, "movelist", movesToDhtml(steps));
        appendTag(out, "length", String.valueOf(steps == null ? 0 : steps.size()));

        // DhtmlXQ readers first build the complete variation table, then bind comments to it.
        // Keep that ordering so comments never shift a later variation reference in strict readers.
        ArrayList<ExportedVariation> exportedVariations = new ArrayList<ExportedVariation>();
        ExportPlan exportPlan = createExportPlan(variations);
        collectVariations(0, 1, variations, exportPlan, exportedVariations,
                new HashSet<PgnManualUtils.VariationLine>());
        Collections.sort(exportedVariations, EXPORTED_VARIATION_COMPARATOR);
        appendVariationMoves(out, exportedVariations);

        if (initialComment != null && initialComment.trim().length() > 0) {
            appendTag(out, "remark", encodeComment(initialComment));
        }
        appendMainComments(out, comments);
        appendVariationComments(out, exportedVariations);
        return out.toString();
    }

    private static LineState parseLine(RawLine raw, Position start,
                                       Map<Integer, Map<Integer, String>> comments,
                                       Set<Integer> visiting, int depth) {
        if (raw == null || start == null || depth > MAX_TREE_DEPTH || !visiting.add(raw.id)) return null;
        try {
            LineState state = new LineState();
            state.line.dhtmlVariationId = raw.id;
            char[][] board = XiangqiRules.copyBoard(start.board);
            boolean redToMove = start.redToMove;
            state.positions.add(new Position(board, redToMove));
            for (int index = 0; index < raw.steps.size(); index++) {
                Move move;
                try {
                    move = Move.fromEngineStep(raw.steps.get(index));
                } catch (Exception ignored) {
                    return null;
                }
                if (!isLegalForSide(board, move, redToMove)) return null;
                state.moves.add(move);
                state.chineseMoves.add(ChineseNotation.translate(board, move, false));
                state.line.engineSteps.add(move.toEngineStep());
                state.line.chineseMoves.add(state.chineseMoves.get(state.chineseMoves.size() - 1));
                state.line.comments.add(commentFor(comments, raw.id,
                        raw.startPlyOneBased + index));
                applyMove(board, move);
                redToMove = !redToMove;
                state.positions.add(new Position(board, redToMove));
            }

            for (Map.Entry<Integer, List<RawLine>> entry : raw.children.entrySet()) {
                Integer nodeValue = entry.getKey();
                if (nodeValue == null || nodeValue < 0 || nodeValue >= state.positions.size()) continue;
                List<PgnManualUtils.VariationLine> parsedChildren =
                        new ArrayList<PgnManualUtils.VariationLine>();
                List<RawLine> children = entry.getValue();
                if (children != null) {
                    for (RawLine child : children) {
                        LineState parsed = parseLine(child, state.positions.get(nodeValue), comments,
                                visiting, depth + 1);
                        if (parsed != null && !parsed.line.engineSteps.isEmpty()) {
                            parsedChildren.add(parsed.line);
                        }
                    }
                }
                if (!parsedChildren.isEmpty()) state.line.variations.put(nodeValue, parsedChildren);
            }
            return state;
        } finally {
            visiting.remove(raw.id);
        }
    }

    private static ExportPlan createExportPlan(
            Map<Integer, List<PgnManualUtils.VariationLine>> variations) {
        ExportPlan plan = new ExportPlan();
        reserveDhtmlVariationIds(variations, plan, new HashSet<PgnManualUtils.VariationLine>());
        assignGeneratedVariationIds(variations, plan, new HashSet<PgnManualUtils.VariationLine>());
        return plan;
    }

    private static void reserveDhtmlVariationIds(
            Map<Integer, List<PgnManualUtils.VariationLine>> variations, ExportPlan plan,
            Set<PgnManualUtils.VariationLine> visited) {
        if (variations == null) return;
        for (List<PgnManualUtils.VariationLine> lines : variations.values()) {
            if (lines == null) continue;
            for (PgnManualUtils.VariationLine line : lines) {
                if (line == null || !visited.add(line)) continue;
                if (line.dhtmlVariationId > 0 && plan.usedIds.add(line.dhtmlVariationId)) {
                    plan.ids.put(line, line.dhtmlVariationId);
                    plan.nextGeneratedId = Math.max(plan.nextGeneratedId, line.dhtmlVariationId + 1);
                }
                reserveDhtmlVariationIds(line.variations, plan, visited);
            }
        }
    }

    private static void assignGeneratedVariationIds(
            Map<Integer, List<PgnManualUtils.VariationLine>> variations, ExportPlan plan,
            Set<PgnManualUtils.VariationLine> visited) {
        if (variations == null) return;
        for (List<PgnManualUtils.VariationLine> lines : variations.values()) {
            if (lines == null) continue;
            for (PgnManualUtils.VariationLine line : lines) {
                if (line == null || !visited.add(line)) continue;
                if (!plan.ids.containsKey(line)) {
                    while (plan.usedIds.contains(plan.nextGeneratedId)) plan.nextGeneratedId++;
                    plan.ids.put(line, plan.nextGeneratedId);
                    plan.usedIds.add(plan.nextGeneratedId++);
                }
                assignGeneratedVariationIds(line.variations, plan, visited);
            }
        }
    }

    private static void collectVariations(int parentId, int parentStartPlyOneBased,
                                          Map<Integer, List<PgnManualUtils.VariationLine>> variations,
                                          ExportPlan plan,
                                          List<ExportedVariation> exportedVariations,
                                          Set<PgnManualUtils.VariationLine> visiting) {
        if (variations == null || variations.isEmpty()) return;
        TreeMap<Integer, List<PgnManualUtils.VariationLine>> ordered =
                new TreeMap<Integer, List<PgnManualUtils.VariationLine>>(variations);
        for (Map.Entry<Integer, List<PgnManualUtils.VariationLine>> entry : ordered.entrySet()) {
            Integer nodeValue = entry.getKey();
            if (nodeValue == null || nodeValue < 0 || entry.getValue() == null) continue;
            ArrayList<PgnManualUtils.VariationLine> lines =
                    new ArrayList<PgnManualUtils.VariationLine>(entry.getValue());
            Collections.sort(lines, VARIATION_COMPARATOR);
            for (PgnManualUtils.VariationLine line : lines) {
                if (line == null || line.engineSteps == null || line.engineSteps.isEmpty()
                        || !visiting.add(line)) continue;
                Integer assignedId = plan.ids.get(line);
                if (assignedId == null) continue;
                int id = assignedId;
                int startPlyOneBased = parentStartPlyOneBased + nodeValue;
                exportedVariations.add(new ExportedVariation(parentId, id, startPlyOneBased,
                        line.engineSteps, line.comments));
                collectVariations(id, startPlyOneBased, line.variations, plan,
                        exportedVariations, visiting);
                visiting.remove(line);
            }
        }
    }

    private static void appendVariationMoves(StringBuilder out,
                                             List<ExportedVariation> exportedVariations) {
        if (exportedVariations == null) return;
        for (ExportedVariation variation : exportedVariations) {
            if (variation == null) continue;
            appendTag(out, "move_" + variation.parentId + "_" + variation.startPlyOneBased
                    + "_" + variation.id, movesToDhtml(variation.steps));
        }
    }

    private static void appendMainComments(StringBuilder out, List<String> comments) {
        if (comments == null) return;
        for (int i = 0; i < comments.size(); i++) {
            String comment = comments.get(i);
            if (comment == null || comment.trim().length() == 0) continue;
            appendTag(out, "comment" + (i + 1), encodeComment(comment));
        }
    }

    private static void appendLineComments(StringBuilder out, int id, int startPlyOneBased,
                                           List<String> comments) {
        if (comments == null) return;
        for (int i = 0; i < comments.size(); i++) {
            String comment = comments.get(i);
            if (comment == null || comment.trim().length() == 0) continue;
            appendTag(out, "comment" + id + "_" + (startPlyOneBased + i),
                    encodeComment(comment));
        }
    }

    private static void appendVariationComments(StringBuilder out,
                                                List<ExportedVariation> exportedVariations) {
        if (exportedVariations == null) return;
        for (ExportedVariation variation : exportedVariations) {
            if (variation == null) continue;
            appendLineComments(out, variation.id, variation.startPlyOneBased, variation.comments);
        }
    }

    private static Map<String, String> parseTags(String text) {
        Map<String, String> out = new HashMap<String, String>();
        Matcher matcher = TAG_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            out.put(key, matcher.group(2).trim());
        }
        return out;
    }

    private static char[][] boardFromBinit(String value) {
        String text = compactDigits(value, "初始局面");
        if (text.length() != 64) throw new IllegalArgumentException("东萍初始局面应为 64 位坐标");
        char[][] board = new char[10][9];
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) board[row][col] = ' ';
        }
        for (int i = 0; i < PIECES.length(); i++) {
            int at = i * 2;
            int x = text.charAt(at) - '0';
            int y = text.charAt(at + 1) - '0';
            if (x == 9 && y == 9) continue;
            if (x < 0 || x > 8 || y < 0 || y > 9) {
                throw new IllegalArgumentException("东萍初始局面含越界坐标");
            }
            // 东萍 binit 的 y 从棋盘顶部开始，正好等于 FEN/内部 board 的行号。
            // 只有 ICCS 着法的数字 rank 才需按 9 - y 换算。
            int row = y;
            if (board[row][x] != ' ') throw new IllegalArgumentException("东萍初始局面棋子重叠");
            board[row][x] = PIECES.charAt(i);
        }
        return board;
    }

    private static String binitFromBoard(char[][] board) {
        StringBuilder out = new StringBuilder(64);
        boolean[][] used = new boolean[10][9];
        for (int i = 0; i < PIECES.length(); i++) {
            char target = PIECES.charAt(i);
            int foundRow = -1;
            int foundCol = -1;
            for (int row = 0; row < 10 && foundRow < 0; row++) {
                for (int col = 0; col < 9; col++) {
                    if (!used[row][col] && board[row][col] == target) {
                        foundRow = row;
                        foundCol = col;
                        break;
                    }
                }
            }
            if (foundRow < 0) {
                out.append("99");
            } else {
                used[foundRow][foundCol] = true;
                out.append((char) ('0' + foundCol));
                out.append((char) ('0' + foundRow));
            }
        }
        return out.toString();
    }

    private static List<String> movesFromDhtml(String value) {
        String text = compactDigits(value, "走法");
        if (text.length() % 4 != 0) throw new IllegalArgumentException("东萍走法长度不是 4 的倍数");
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += 4) {
            int fromX = text.charAt(i) - '0';
            int fromY = text.charAt(i + 1) - '0';
            int toX = text.charAt(i + 2) - '0';
            int toY = text.charAt(i + 3) - '0';
            if (fromX < 0 || fromX > 8 || toX < 0 || toX > 8
                    || fromY < 0 || fromY > 9 || toY < 0 || toY > 9) {
                throw new IllegalArgumentException("东萍走法含越界坐标");
            }
            out.add("" + (char) ('a' + fromX) + (char) ('0' + (9 - fromY))
                    + (char) ('a' + toX) + (char) ('0' + (9 - toY)));
        }
        return out;
    }

    private static String movesToDhtml(List<String> steps) {
        StringBuilder out = new StringBuilder();
        if (steps == null) return out.toString();
        for (String value : steps) {
            String step = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!step.matches("[a-i][0-9][a-i][0-9]")) {
                throw new IllegalArgumentException("无法写出非 ICCS 着法：" + value);
            }
            out.append((char) ('0' + (step.charAt(0) - 'a')));
            out.append((char) ('0' + (9 - (step.charAt(1) - '0'))));
            out.append((char) ('0' + (step.charAt(2) - 'a')));
            out.append((char) ('0' + (9 - (step.charAt(3) - '0'))));
        }
        return out.toString();
    }

    private static String compactDigits(String value, String fieldName) {
        String text = value == null ? "" : value.replaceAll("\\s+", "");
        if (!text.matches("[0-9]*")) throw new IllegalArgumentException("东萍" + fieldName + "含非数字内容");
        return text;
    }

    private static boolean isLegalForSide(char[][] board, Move move, boolean redToMove) {
        if (board == null || move == null || !XiangqiRules.inBoard(move.fromRow, move.fromCol)
                || !XiangqiRules.inBoard(move.toRow, move.toCol)) return false;
        char piece = board[move.fromRow][move.fromCol];
        return XiangqiRules.isPiece(piece) && XiangqiRules.isRed(piece) == redToMove
                && XiangqiRules.isLegalMove(board, move);
    }

    private static void applyMove(char[][] board, Move move) {
        char piece = board[move.fromRow][move.fromCol];
        board[move.toRow][move.toCol] = piece;
        board[move.fromRow][move.fromCol] = ' ';
    }

    private static void putComment(Map<Integer, Map<Integer, String>> comments,
                                   int lineId, int index, String value) {
        if (lineId < 0 || index <= 0) return;
        Map<Integer, String> byStep = comments.get(lineId);
        if (byStep == null) {
            byStep = new HashMap<Integer, String>();
            comments.put(lineId, byStep);
        }
        byStep.put(index, value);
    }

    private static String commentFor(Map<Integer, Map<Integer, String>> comments,
                                     int lineId, int oneBasedIndex) {
        Map<Integer, String> byStep = comments.get(lineId);
        if (byStep == null) return "";
        String value = byStep.get(oneBasedIndex);
        return value == null ? "" : value;
    }

    private static void sortRawChildren(RawLine line, Set<Integer> visited) {
        if (line == null || !visited.add(line.id)) return;
        for (List<RawLine> children : line.children.values()) {
            if (children == null) continue;
            Collections.sort(children, RAW_LINE_COMPARATOR);
            for (RawLine child : children) sortRawChildren(child, visited);
        }
    }

    private static String decodeComment(String value) {
        if (value == null) return "";
        return value.trim().replace("\r\n", "\n").replace('\r', '\n').replace("||", "\n");
    }

    private static String encodeComment(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "||").trim();
    }

    private static int resultFromDhtml(String value) {
        String text = value == null ? "" : value.trim();
        if ("红胜".equals(text) || "先胜".equals(text) || "1-0".equals(text)) return 0;
        if ("黑胜".equals(text) || "后胜".equals(text) || "0-1".equals(text)) return 1;
        if ("和棋".equals(text) || "和".equals(text) || "1/2-1/2".equals(text)) return 2;
        return 3;
    }

    private static String resultToDhtml(String value) {
        if ("1-0".equals(value)) return "红胜";
        if ("0-1".equals(value)) return "黑胜";
        if ("1/2-1/2".equals(value)) return "和棋";
        return "";
    }

    private static String normalizeFen(String value) {
        if (value == null || value.trim().length() == 0) return XiangqiRules.START_FEN;
        String text = value.trim();
        String[] parts = text.split("\\s+");
        String side = parts.length > 1 && "b".equalsIgnoreCase(parts[1]) ? "b" : "w";
        return parts[0] + " " + side + " - - 0 1";
    }

    private static void appendTag(StringBuilder out, String key, String value) {
        String safeKey = key == null ? "" : key;
        String safeValue = value == null ? "" : value;
        out.append("[DhtmlXQ_").append(safeKey).append(']').append(safeValue)
                .append("[/DhtmlXQ_").append(safeKey).append("]\n");
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private static final Comparator<RawLine> RAW_LINE_COMPARATOR = new Comparator<RawLine>() {
        @Override public int compare(RawLine left, RawLine right) {
            return Integer.compare(left == null ? Integer.MAX_VALUE : left.id,
                    right == null ? Integer.MAX_VALUE : right.id);
        }
    };

    private static final Comparator<PgnManualUtils.VariationLine> VARIATION_COMPARATOR =
            new Comparator<PgnManualUtils.VariationLine>() {
                @Override public int compare(PgnManualUtils.VariationLine left,
                                             PgnManualUtils.VariationLine right) {
                    String a = left == null || left.label == null ? "" : left.label;
                    String b = right == null || right.label == null ? "" : right.label;
                    return a.compareToIgnoreCase(b);
                }
            };

    private static final Comparator<ExportedVariation> EXPORTED_VARIATION_COMPARATOR =
            new Comparator<ExportedVariation>() {
                @Override public int compare(ExportedVariation left, ExportedVariation right) {
                    return Integer.compare(left == null ? Integer.MAX_VALUE : left.id,
                            right == null ? Integer.MAX_VALUE : right.id);
                }
            };

    private static final class RawLine {
        final int id;
        final int parentId;
        final int startPlyOneBased;
        final List<String> steps;
        final Map<Integer, List<RawLine>> children = new TreeMap<Integer, List<RawLine>>();

        RawLine(int id, int parentId, int startPlyOneBased, List<String> steps) {
            this.id = id;
            this.parentId = parentId;
            this.startPlyOneBased = startPlyOneBased;
            this.steps = steps == null ? Collections.<String>emptyList() : steps;
        }
    }

    private static final class Position {
        final char[][] board;
        final boolean redToMove;

        Position(char[][] board, boolean redToMove) {
            this.board = XiangqiRules.copyBoard(board);
            this.redToMove = redToMove;
        }
    }

    private static final class LineState {
        final PgnManualUtils.VariationLine line = new PgnManualUtils.VariationLine();
        final List<Move> moves = new ArrayList<Move>();
        final List<String> chineseMoves = new ArrayList<String>();
        final List<Position> positions = new ArrayList<Position>();
    }

    private static final class ExportPlan {
        final Map<PgnManualUtils.VariationLine, Integer> ids =
                new IdentityHashMap<PgnManualUtils.VariationLine, Integer>();
        final Set<Integer> usedIds = new HashSet<Integer>();
        int nextGeneratedId = 1;
    }

    private static final class ExportedVariation {
        final int parentId;
        final int id;
        final int startPlyOneBased;
        final List<String> steps;
        final List<String> comments;

        ExportedVariation(int parentId, int id, int startPlyOneBased, List<String> steps,
                          List<String> comments) {
            this.parentId = parentId;
            this.id = id;
            this.startPlyOneBased = startPlyOneBased;
            this.steps = steps;
            this.comments = comments;
        }
    }
}

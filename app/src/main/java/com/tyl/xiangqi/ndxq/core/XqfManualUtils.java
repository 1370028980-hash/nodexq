package com.tyl.xiangqi.ndxq.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * XQF 棋谱读写工具。
 * v14: 按通用 XQF 高/低版本解密流程修复：
 * - 头部 0x03-0x0F 用 <BIBBBBBBBB> 拆 KeyMask/ProductId/KeyOrA-D/KeysSum/KeyXY/KeyXYf/KeyXYt；
 * - 高版本使用 F32Keys 解密 0x400 之后的走法区；
 * - 初始 32 子位置按 KeyXY 重排并减 KeyXY；高版本即使零密钥也必须执行槽位轮转；
 * - 兼容节点象棋 V18.5 及更早版本误把零密钥高版本位置按低版本直写的历史文件；
 * - 兼容低版本 flag 与注释长度直接保存；
 * - 读取主线，同时把 XQF 变招入口记录到 variations，避免完全丢失分支信息；
 * - 写出采用未加密 XQF 1.0，便于与 cchess、鲨鱼等常见程序交换。
 */
public final class XqfManualUtils {
    private static final int HEADER_SIZE = 0x400;
    private static final int KEY_RMK_SIZE_ZERO = 767;
    private static final String[] RED_PIECES = {"R","N","B","A","K","A","B","N","R","C","C","P","P","P","P","P"};
    private static final String[] BLACK_PIECES = {"r","n","b","a","k","a","b","n","r","c","c","p","p","p","p","p"};
    private static final String PIECES = "RNBAKABNRCCPPPPPrnbakabnrccppppp";

    private XqfManualUtils() {}

    public static boolean looksLikeXqf(byte[] data) {
        return data != null && data.length > 3 && data[0] == 'X' && data[1] == 'Q';
    }

    public static PgnManualUtils.ParsedManual parse(byte[] data, String fallbackFen) {
        PgnManualUtils.ParsedManual out = new PgnManualUtils.ParsedManual();
        out.title = "XQF 棋谱";
        out.fen = fallbackFen == null || fallbackFen.trim().length() == 0 ? XiangqiRules.START_FEN : fallbackFen;
        if (!looksLikeXqf(data)) return out;
        // XQF 头显式描述初始局面；只有头部、没有走法的局面文件也应可以打开。
        out.hasExplicitFen = true;
        if (data.length < 0x30) return out;

        int version = u(data[2]);
        XqfKeys keys = version <= 0x0A ? null : readKeys(data);
        // 标准 XQF：版本 > 10 时，无论密钥是否为 0，32 个棋子位置都要执行 KeyXY 槽位重排。
        // 节点象棋 <= V18.5 曾在 zeroKeys 时错误跳过该步骤，并以同样错误格式导出；
        // 因而这里同时生成标准候选与历史 raw 候选，再按首着合法性/固有落点约束选出正确局面。
        String standardFen = readInitialFen(data, version, keys, false);
        String headerFen = standardFen;
        if (version > 0x0A && keys != null && keys.zeroKeys) {
            String legacyFen = readInitialFen(data, version, keys, true);
            headerFen = chooseZeroKeyFen(standardFen, legacyFen, new byte[0], version, keys);
        }
        if (headerFen != null && headerFen.length() > 0 && isFenSane(headerFen)) out.fen = headerFen;
        else out.fen = XiangqiRules.START_FEN;
        String title = readHeaderText(data, 0x50, 64);
        if (title.length() > 0) out.title = title;
        out.event = out.title;
        out.result = parseHeaderResult(data);
        if (data.length <= HEADER_SIZE) return out;

        byte[] area = new byte[data.length - HEADER_SIZE];
        System.arraycopy(data, HEADER_SIZE, area, 0, area.length);
        if (version > 0x0A && keys != null) area = decodeMoveArea(keys, area);
        if (version > 0x0A && keys != null && keys.zeroKeys) {
            String legacyFen = readInitialFen(data, version, keys, true);
            headerFen = chooseZeroKeyFen(standardFen, legacyFen, area, version, keys);
            if (headerFen != null && headerFen.length() > 0 && isFenSane(headerFen)) out.fen = headerFen;
        }
        String rootComment = readRootComment(area, version, keys);
        parseBranchMetadata(rootComment, out);
        out.initialComment = sanitizeRootComment(rootComment);

        int off = skipRoot(area, version, keys);
        char[][] board = XiangqiRules.fromFen(out.fen);
        // XQF 头部并不可靠地保存行棋方，部分鲨鱼棋谱的 FEN 也会固定写 w。
        // 先用根节点的合法性判定红/黑，避免跳过黑方首着后在中途失步。
        boolean redToMove = XiangqiRules.redToMoveFromFen(out.fen);
        boolean rootRed = isLegalEncodedMove(area, off, version, keys, board, true);
        boolean rootBlack = isLegalEncodedMove(area, off, version, keys, board, false);
        if (rootRed != rootBlack) {
            redToMove = rootRed;
        } else {
            FirstMoveCandidate found = findFirstMoveCandidate(area, version, keys, board);
            if (found != null) {
                off = found.offset;
                redToMove = found.redToMove;
            }
        }
        out.fen = withSideToMove(out.fen, redToMove);
        if (off < 0 || off + 4 > area.length) return out;

        // 每个节点至少会消耗一个完整走法记录；不能再用固定节点数截断正常的大型变招谱。
        TreeReadState treeState = new TreeReadState(maxTreeNodeCount(area, version));
        XqfNode root = readNodeTree(area, off, version, keys, treeState, 0);
        if (root != null) {
            populateParsedManual(root, board, redToMove, out);
            applyBranchMetadata(out);
        }
        return out;
    }

    /**
     * 导出未加密 XQF 1.0。
     *
     * XQF 走法区是“子节点（下一着）+ 兄弟节点（同一局面的变招）”树。
     * 旧实现只写了 0x40 变招标记，没有继续写兄弟节点，导致重新读取后分支丢失。
     * 这里先构建完整节点树，再按 XQF 的深度优先顺序写出，主线和当前数据模型支持的变招均可往返保存。
     */
    public static byte[] exportSimpleXqf(String initialFen, List<String> steps, List<String> comments,
                                         Map<Integer, List<PgnManualUtils.VariationLine>> variations) {
        return exportSimpleXqf(initialFen, steps, comments, "", variations);
    }

    public static byte[] exportSimpleXqf(String initialFen, List<String> steps, List<String> comments,
                                         String initialComment,
                                         Map<Integer, List<PgnManualUtils.VariationLine>> variations) {
        return exportSimpleXqf(initialFen, steps, comments, initialComment, variations,
                Collections.<Integer, String>emptyMap());
    }

    /** 导出节点象棋扩展的分支标签，仍保持标准 XQF 主体结构。 */
    public static byte[] exportSimpleXqf(String initialFen, List<String> steps, List<String> comments,
                                         String initialComment,
                                         Map<Integer, List<PgnManualUtils.VariationLine>> variations,
                                         Map<Integer, String> activeBranchLabels) {
        return exportSimpleXqf(initialFen, steps, comments, initialComment, variations,
                activeBranchLabels, "*");
    }

    /**
     * 导出未加密 XQF 1.0，并将已有对局结果写入标准头字段。
     * 低版本节点固定保留四字节注释长度，和 cchess 的写出格式一致。
     */
    public static byte[] exportSimpleXqf(String initialFen, List<String> steps, List<String> comments,
                                         String initialComment,
                                         Map<Integer, List<PgnManualUtils.VariationLine>> variations,
                                         Map<Integer, String> activeBranchLabels,
                                         String resultToken) {
        byte[] header = new byte[HEADER_SIZE];
        header[0] = 'X'; header[1] = 'Q'; header[2] = 0x0A;
        header[0x33] = (byte) headerResultForToken(resultToken);
        writeInitialPiecePositions(header, initialFen, false);
        XqfNode root = buildExportTree(steps, comments, variations);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try { out.write(header); } catch (Exception ignored) {}
        // 低版本根记录和普通节点相同，始终带四字节注释长度。
        // 根注释以“#0,0#”标记开头；其后保存初始局面/整盘棋谱说明。
        out.write(0x18); out.write(0x20); out.write(root == null ? 0 : 0xF0); out.write(0xFF);
        String rootText = "#0,0#" + (initialComment == null ? "" : initialComment.trim())
                + buildBranchMetadata(variations, activeBranchLabels);
        byte[] rootComment = rootText.getBytes(Charset.forName("GBK"));
        writeIntLE(out, rootComment.length);
        try { out.write(rootComment); } catch (Exception ignored) {}

        if (root != null) writeNodeTree(out, root, 0);
        return out.toByteArray();
    }

    private static XqfNode buildExportTree(List<String> steps, List<String> comments,
                                           Map<Integer, List<PgnManualUtils.VariationLine>> variations) {
        XqfNode first = null;
        XqfNode previous = null;
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                String step = normalizeExportStep(steps.get(i));
                if (step.length() == 0) break;
                String comment = comments != null && i < comments.size() && comments.get(i) != null
                        ? comments.get(i) : "";
                XqfNode node = new XqfNode(step, comment);
                if (first == null) first = node;
                if (previous != null) previous.child = node;
                previous = node;
            }
        }
        XqfNode main = first;
        int ply = 0;
        while (main != null) {
            List<PgnManualUtils.VariationLine> lines = variations == null ? null : variations.get(ply);
            main.sibling = buildVariationForest(lines);
            main = main.child;
            ply++;
        }
        return first;
    }

    private static XqfNode buildVariationForest(List<PgnManualUtils.VariationLine> lines) {
        XqfNode root = null;
        XqfNode tail = null;
        if (lines == null) return null;
        for (PgnManualUtils.VariationLine line : lines) {
            if (line == null || line.engineSteps == null || line.engineSteps.isEmpty()) continue;
            XqfNode lineRoot = null;
            XqfNode previous = null;
            for (int i = 0; i < line.engineSteps.size(); i++) {
                String step = normalizeExportStep(line.engineSteps.get(i));
                if (step.length() == 0) break;
                String comment = line.comments != null && i < line.comments.size() && line.comments.get(i) != null
                        ? line.comments.get(i) : "";
                XqfNode node = new XqfNode(step, comment);
                if (lineRoot == null) lineRoot = node;
                if (previous != null) previous.child = node;
                if (i > 0 && line.variations != null) {
                    List<PgnManualUtils.VariationLine> nested = line.variations.get(i);
                    if (nested != null && !nested.isEmpty()) {
                        node.sibling = buildVariationForest(nested);
                    }
                }
                previous = node;
            }
            if (lineRoot != null) {
                if (root == null) root = lineRoot;
                else tail.sibling = lineRoot;
                tail = lineRoot;
            }
        }
        return root;
    }

    private static String normalizeExportStep(String step) {
        if (step == null) return "";
        String s = step.trim().toLowerCase();
        if (s.length() < 4) return "";
        s = s.substring(0, 4);
        int from = iccsToCoord(s.substring(0, 2));
        int to = iccsToCoord(s.substring(2, 4));
        return from < 0 || to < 0 ? "" : s;
    }

    private static void writeNodeTree(ByteArrayOutputStream out, XqfNode node, int depth) {
        if (out == null || node == null || depth > 4096) return;
        int from = iccsToCoord(node.step.substring(0, 2));
        int to = iccsToCoord(node.step.substring(2, 4));
        if (from < 0 || to < 0) return;
        byte[] cbytes = node.comment.length() == 0 ? new byte[0]
                : node.comment.getBytes(Charset.forName("GBK"));
        int flag = 0;
        if (node.child != null) flag |= 0xF0;
        if (node.sibling != null) flag |= 0x0F;
        out.write((from + 0x18) & 255);
        out.write((to + 0x20) & 255);
        out.write(flag);
        out.write(0);
        writeIntLE(out, cbytes.length);
        try { out.write(cbytes); } catch (Exception ignored) {}
        if (node.child != null) writeNodeTree(out, node.child, depth + 1);
        if (node.sibling != null) writeNodeTree(out, node.sibling, depth + 1);
    }

    private static XqfNode readNodeTree(byte[] area, int off, int version, XqfKeys keys,
                                        TreeReadState state, int depth) {
        if (area == null || state == null || off < 0 || off + 4 > area.length
                || depth > 4096 || state.totalNodes >= state.maxNodes) return null;
        Step step = readStep(area, off, version, keys);
        if (step == null || step.step == null || step.step.length() != 4) return null;
        state.totalNodes++;
        XqfNode node = new XqfNode(step.step, step.comment == null ? "" : step.comment);
        int nextOffset = step.nextOffset;
        state.nextOffset = nextOffset;
        if (step.hasNext) {
            node.child = readNodeTree(area, nextOffset, version, keys, state, depth + 1);
            if (node.child == null) {
                state.nextOffset = nextOffset;
                return node;
            }
            nextOffset = state.nextOffset;
        }
        if (step.hasVariation) {
            node.sibling = readNodeTree(area, nextOffset, version, keys, state, depth + 1);
            if (node.sibling != null) nextOffset = state.nextOffset;
        }
        state.nextOffset = nextOffset;
        return node;
    }

    private static void populateParsedManual(XqfNode root, char[][] initialBoard, boolean redToMove,
                                             PgnManualUtils.ParsedManual out) {
        XqfNode main = root;
        char[][] board = XiangqiRules.copyBoard(initialBoard);
        boolean side = redToMove;
        int ply = 0;
        while (main != null) {
            char[][] boardBefore = XiangqiRules.copyBoard(board);
            appendTopLevelVariations(main.sibling, boardBefore, side, ply, out);
            Move move = legalMoveForNode(main, board, side);
            if (move == null) break;
            String cn = ChineseNotation.translate(board, move, false);
            char piece = board[move.fromRow][move.fromCol];
            board[move.toRow][move.toCol] = piece;
            board[move.fromRow][move.fromCol] = ' ';
            out.moves.add(move);
            out.chineseMoves.add(cn);
            out.comments.add(main.comment);
            side = !side;
            ply++;
            main = main.child;
        }
    }

    private static void appendTopLevelVariations(XqfNode sibling, char[][] boardBefore, boolean redToMove,
                                                 int parentPly, PgnManualUtils.ParsedManual out) {
        List<PgnManualUtils.VariationLine> parsed = parseDirectVariations(
                sibling, boardBefore, redToMove, 0);
        if (!parsed.isEmpty()) out.variations.put(parentPly, parsed);
    }

    /**
     * 只解析当前局面的直接兄弟走法。每条线路沿首个子节点继续作为选中线，
     * 子节点的兄弟走法保存到该线路自己的 variations 中，等导航到对应步数再显示。
     */
    private static List<PgnManualUtils.VariationLine> parseDirectVariations(
            XqfNode firstAtPosition, char[][] boardBefore, boolean redToMove, int depth) {
        List<PgnManualUtils.VariationLine> out = new ArrayList<PgnManualUtils.VariationLine>();
        if (firstAtPosition == null || boardBefore == null || depth > 4096) return out;
        for (XqfNode candidate = firstAtPosition; candidate != null; candidate = candidate.sibling) {
            PgnManualUtils.VariationLine line = parseVariationLine(
                    candidate, XiangqiRules.copyBoard(boardBefore), redToMove, depth + 1);
            if (line != null && !line.engineSteps.isEmpty()) out.add(line);
        }
        return out;
    }

    private static PgnManualUtils.VariationLine parseVariationLine(
            XqfNode first, char[][] board, boolean redToMove, int depth) {
        if (first == null || board == null || depth > 4096) return null;
        PgnManualUtils.VariationLine line = new PgnManualUtils.VariationLine();
        XqfNode current = first;
        boolean side = redToMove;
        int relativePly = 0;
        while (current != null && relativePly <= 4096) {
            Move move = legalMoveForNode(current, board, side);
            if (move == null) break;
            String cn = ChineseNotation.translate(board, move, false);
            char piece = board[move.fromRow][move.fromCol];
            board[move.toRow][move.toCol] = piece;
            board[move.fromRow][move.fromCol] = ' ';
            line.engineSteps.add(move.toEngineStep());
            line.chineseMoves.add(cn);
            line.comments.add(current.comment == null ? "" : current.comment);

            if (current.child != null && current.child.sibling != null) {
                List<PgnManualUtils.VariationLine> nested = parseDirectVariations(
                        current.child.sibling, XiangqiRules.copyBoard(board), !side, depth + relativePly + 1);
                if (!nested.isEmpty()) line.variations.put(relativePly + 1, nested);
            }
            current = current.child;
            side = !side;
            relativePly++;
        }
        return line;
    }

    private static Move legalMoveForNode(XqfNode node, char[][] board, boolean redToMove) {
        if (node == null || board == null) return null;
        Move move;
        try { move = Move.fromEngineStep(node.step); } catch (Exception e) { return null; }
        if (!XiangqiRules.inBoard(move.fromRow, move.fromCol) || !XiangqiRules.inBoard(move.toRow, move.toCol)) return null;
        char piece = board[move.fromRow][move.fromCol];
        if (!XiangqiRules.isPiece(piece) || XiangqiRules.isRed(piece) != redToMove) return null;
        return XiangqiRules.isLegalMove(board, move) ? move : null;
    }

    private static int parseNode(byte[] area, int off, int version, XqfKeys keys, char[][] board,
                                 boolean redToMove, int parentPly, boolean mainLine, ParseState state) {
        if (off < 0 || off + 4 > area.length || state.totalNodes > 2000) return off;
        state.totalNodes++;
        Step s = readStep(area, off, version, keys);
        if (s == null) return area.length;
        off = s.nextOffset;
        char[][] boardBefore = XiangqiRules.copyBoard(board);
        Move m;
        try { m = Move.fromEngineStep(s.step); } catch (Exception e) { return off; }
        boolean ok = XiangqiRules.inBoard(m.fromRow, m.fromCol) && XiangqiRules.inBoard(m.toRow, m.toCol)
                && XiangqiRules.isPiece(board[m.fromRow][m.fromCol])
                && XiangqiRules.isRed(board[m.fromRow][m.fromCol]) == redToMove
                && XiangqiRules.isLegalMove(board, m);
        if (ok) {
            String cn = ChineseNotation.translate(board, m, false);
            char p = board[m.fromRow][m.fromCol];
            board[m.toRow][m.toCol] = p;
            board[m.fromRow][m.fromCol] = ' ';
            if (mainLine) {
                state.out.moves.add(m);
                state.out.chineseMoves.add(cn);
                state.out.comments.add(s.comment == null ? "" : s.comment);
                parentPly = state.out.moves.size();
            } else {
                state.currentVariation.engineSteps.add(s.step);
                state.currentVariation.chineseMoves.add(cn);
                state.currentVariation.comments.add(s.comment == null ? "" : s.comment);
            }
        }
        if (s.hasNext) {
            off = parseNode(area, off, version, keys, board, !redToMove, parentPly, mainLine && ok, state);
        }
        if (s.hasVariation) {
            if (mainLine) {
                PgnManualUtils.VariationLine old = state.currentVariation;
                state.currentVariation = new PgnManualUtils.VariationLine();
                off = parseNode(area, off, version, keys, boardBefore, redToMove, parentPly - 1, false, state);
                if (!state.currentVariation.engineSteps.isEmpty()) {
                    List<PgnManualUtils.VariationLine> lines = state.out.variations.get(Math.max(0, parentPly - 1));
                    if (lines == null) {
                        lines = new ArrayList<PgnManualUtils.VariationLine>();
                        state.out.variations.put(Math.max(0, parentPly - 1), lines);
                    }
                    lines.add(state.currentVariation);
                }
                state.currentVariation = old;
            } else {
                off = parseNode(area, off, version, keys, boardBefore, redToMove, parentPly, false, state);
            }
        }
        return off;
    }

    private static Step readStep(byte[] area, int off, int version, XqfKeys keys) {
        if (off + 4 > area.length) return null;
        int fb = u(area[off]);
        int tb = u(area[off + 1]);
        int flag = u(area[off + 2]);
        int next = off + 4;
        Step s = new Step();
        if (version <= 0x0A) {
            s.hasNext = (flag & 0xF0) != 0;
            s.hasVariation = (flag & 0x0F) != 0;
            s.from = (fb - 0x18) & 255;
            s.to = (tb - 0x20) & 255;
            int len = 0;
            if (next + 4 <= area.length) {
                len = readIntLE(area, next);
                next += 4;
            }
            if (len > 0 && len < 1024 * 1024 && next + len <= area.length) {
                byte[] c = new byte[len];
                System.arraycopy(area, next, c, 0, len);
                s.comment = decodeText(c);
                next += len;
            }
        } else {
            flag &= 0xE0;
            s.hasNext = (flag & 0x80) != 0;
            s.hasVariation = (flag & 0x40) != 0;
            s.from = (fb - 0x18 - (keys == null ? 0 : keys.keyXYf)) & 255;
            s.to = (tb - 0x20 - (keys == null ? 0 : keys.keyXYt)) & 255;
            if ((flag & 0x20) != 0 && next + 4 <= area.length) {
                int stored = readIntLE(area, next);
                next += 4;
                int len = stored - (keys == null ? KEY_RMK_SIZE_ZERO : keys.keyRmkSize);
                if (len > 0 && len < 1024 * 1024 && next + len <= area.length) {
                    byte[] c = new byte[len];
                    System.arraycopy(area, next, c, 0, len);
                    s.comment = decodeText(c);
                    next += len;
                }
            }
        }
        s.step = coordToIccs(s.from) + coordToIccs(s.to);
        s.nextOffset = next;
        return s;
    }

    private static String readRootComment(byte[] area, int version, XqfKeys keys) {
        if (area == null || area.length < 8) return "";
        int flag = u(area[2]);
        int off = 4;
        int len = 0;
        if (version <= 0x0A) {
            if (off + 4 > area.length) return "";
            len = readIntLE(area, off);
            off += 4;
        } else {
            flag &= 0xE0;
            if ((flag & 0x20) == 0 || off + 4 > area.length) return "";
            len = readIntLE(area, off) - (keys == null ? KEY_RMK_SIZE_ZERO : keys.keyRmkSize);
            off += 4;
        }
        if (len <= 0 || len >= 1024 * 1024 || off + len > area.length) return "";
        byte[] bytes = new byte[len];
        System.arraycopy(area, off, bytes, 0, len);
        return decodeText(bytes);
    }

    private static String sanitizeRootComment(String comment) {
        if (comment == null) return "";
        String value = comment.trim().replaceFirst("^#0,0#\\s*", "")
                .replaceFirst("^#0,0,0#\\s*", "").trim();
        int marker = value.indexOf("#NDXQ-BRANCHES#");
        return marker >= 0 ? value.substring(0, marker).trim() : value;
    }

    private static String readHeaderText(byte[] data, int offset, int fieldSize) {
        if (data == null || offset < 0 || fieldSize < 2 || offset + fieldSize > data.length) return "";
        int length = Math.min(u(data[offset]), fieldSize - 1);
        if (length <= 0) return "";
        byte[] bytes = new byte[length];
        System.arraycopy(data, offset + 1, bytes, 0, length);
        return decodeText(bytes);
    }

    private static int parseHeaderResult(byte[] data) {
        if (data == null || data.length <= 0x33) return 3;
        switch (u(data[0x33])) {
            case 1: return 0;
            case 2: return 1;
            case 3: return 2;
            default: return 3;
        }
    }

    private static int headerResultForToken(String resultToken) {
        if ("1-0".equals(resultToken)) return 1;
        if ("0-1".equals(resultToken)) return 2;
        if ("1/2-1/2".equals(resultToken)) return 3;
        return 0;
    }

    private static int skipRoot(byte[] area, int version, XqfKeys keys) {
        if (area == null || area.length < 4) return 0;
        int off = 4;
        int flag = u(area[2]);
        if (version <= 0x0A) {
            if (off + 4 <= area.length) {
                int len = readIntLE(area, off);
                off += 4;
                if (len > 0 && len < 1024 * 1024 && off + len <= area.length) off += len;
            }
        } else {
            flag &= 0xE0;
            if ((flag & 0x20) != 0 && off + 4 <= area.length) {
                int len = readIntLE(area, off) - (keys == null ? KEY_RMK_SIZE_ZERO : keys.keyRmkSize);
                off += 4;
                if (len > 0 && len < 1024 * 1024 && off + len <= area.length) off += len;
            }
        }
        return off;
    }

    /**
     * XQF 树递归读取的资源边界。节点数只能受走法区的最小记录长度约束，
     * 固定 20000 会把合法的大型开局库/注释谱从中间截断。
     */
    private static int maxTreeNodeCount(byte[] area, int version) {
        if (area == null || area.length == 0) return 1;
        int minimumRecordBytes = version <= 0x0A ? 8 : 4;
        return Math.max(1, area.length / minimumRecordBytes);
    }

    private static XqfKeys readKeys(byte[] data) {
        XqfKeys keys = new XqfKeys();
        if (allZeroKeys(data)) {
            keys.keyXY = 0; keys.keyXYf = 0; keys.keyXYt = 0; keys.keyRmkSize = KEY_RMK_SIZE_ZERO;
            keys.zeroKeys = true;
            keys.f32Keys = new byte[32];
            return keys;
        }
        int p = 0x03;
        int keyMask = u(data[p++]);
        p += 4; // ProductId, little-endian dword
        int keyOrA = u(data[p++]);
        int keyOrB = u(data[p++]);
        int keyOrC = u(data[p++]);
        int keyOrD = u(data[p++]);
        int keysSum = u(data[p++]);
        int rawKeyXY = u(data[p++]);
        int rawKeyXYf = u(data[p++]);
        int rawKeyXYt = u(data[p]);
        keys.keyXY = (f(rawKeyXY) * rawKeyXY) & 255;
        keys.keyXYf = (f(rawKeyXYf) * keys.keyXY) & 255;
        keys.keyXYt = (f(rawKeyXYt) * keys.keyXYf) & 255;
        keys.keyRmkSize = ((keysSum * 256 + rawKeyXY) % 32000) + 767;
        int b1 = (keysSum & keyMask) | keyOrA;
        int b2 = (rawKeyXY & keyMask) | keyOrB;
        int b3 = (rawKeyXYf & keyMask) | keyOrC;
        int b4 = (rawKeyXYt & keyMask) | keyOrD;
        byte[] base = "[(C) Copyright Mr. Dong Shiwei.]".getBytes(Charset.forName("US-ASCII"));
        keys.f32Keys = new byte[32];
        for (int i = 0; i < 32; i++) {
            int k = (i % 4 == 0) ? b1 : (i % 4 == 1 ? b2 : (i % 4 == 2 ? b3 : b4));
            keys.f32Keys[i] = (byte)((base[i] & k) & 255);
        }
        return keys;
    }

    private static byte[] decodeMoveArea(XqfKeys keys, byte[] area) {
        byte[] out = new byte[area.length];
        for (int i = 0; i < area.length; i++) {
            int keyByte = keys.f32Keys[(HEADER_SIZE + i) % 32] & 255;
            out[i] = (byte)((u(area[i]) - keyByte) & 255);
        }
        return out;
    }

    private static String readInitialFen(byte[] data, int version, XqfKeys keys, boolean legacyRawZeroKey) {
        if (data == null || data.length < 0x30) return null;
        byte[] man = new byte[32];
        System.arraycopy(data, 0x10, man, 0, 32);
        byte[] pos = new byte[32];
        if (version > 0x0A && keys != null && !legacyRawZeroKey) {
            // 标准高版本规则。注意 zeroKeys 只表示加密数值为 0，并不取消 (i+1) 槽位轮转。
            for (int i = 0; i < 32; i++) pos[(keys.keyXY + i + 1) & 0x1F] = man[i];
            for (int i = 0; i < 32; i++) {
                int v = (u(pos[i]) - keys.keyXY) & 255;
                pos[i] = (byte)(v > 89 ? 0xFF : v);
            }
        } else {
            // 低版本原始位置；也用于兼容节点象棋旧版误写的 zero-key v18 文件。
            System.arraycopy(man, 0, pos, 0, 32);
        }
        char[][] board = new char[10][9];
        for (int r = 0; r < 10; r++) for (int c = 0; c < 9; c++) board[r][c] = ' ';
        boolean any = false;
        for (int i = 0; i < 32; i++) {
            int coord = u(pos[i]);
            if (coord > 89) continue;
            int x = coord / 10;
            int y = coord % 10;
            int row = 9 - y;
            if (x < 0 || x > 8 || row < 0 || row > 9) continue;
            board[row][x] = PIECES.charAt(i);
            any = true;
        }
        if (!any) return null;
        return boardToFen(board) + " w - - 0 1";
    }

    private static String chooseZeroKeyFen(String standardFen, String legacyFen, byte[] area,
                                           int version, XqfKeys keys) {
        boolean standardSane = standardFen != null && isFenSane(standardFen);
        boolean legacySane = legacyFen != null && isFenSane(legacyFen);
        if (!legacySane) return standardFen;
        if (!standardSane) return legacyFen;
        if (standardFen.equals(legacyFen)) return standardFen;

        // 有走法时，优先选择能让第一着成为合法着法的候选。
        boolean standardMove = hasLegalFirstMove(area, version, keys, standardFen);
        boolean legacyMove = hasLegalFirstMove(area, version, keys, legacyFen);
        if (standardMove != legacyMove) return standardMove ? standardFen : legacyFen;

        // 无走法或两边均能碰巧匹配时，用永远受规则限制的帅/士/象固有活动区域判别。
        // 车马炮兵可走遍较大区域，不参与评分，避免对残局/排局造成误判。
        int standardScore = fixedPiecePlacementScore(standardFen);
        int legacyScore = fixedPiecePlacementScore(legacyFen);
        return legacyScore > standardScore ? legacyFen : standardFen;
    }

    private static boolean hasLegalFirstMove(byte[] area, int version, XqfKeys keys, String fen) {
        if (area == null || fen == null) return false;
        try {
            char[][] board = XiangqiRules.fromFen(fen);
            return findFirstMoveCandidate(area, version, keys, board) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String withSideToMove(String fen, boolean redToMove) {
        if (fen == null || fen.trim().length() == 0) return fen;
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 2) return fen.trim() + (redToMove ? " w" : " b") + " - - 0 1";
        parts[1] = redToMove ? "w" : "b";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            out.append(parts[i]);
        }
        return out.toString();
    }

    private static FirstMoveCandidate findFirstMoveCandidate(byte[] area, int version,
                                                               XqfKeys keys, char[][] board) {
        int root = skipRoot(area, version, keys);
        if (isLegalEncodedMove(area, root, version, keys, board, true)) {
            return new FirstMoveCandidate(root, true);
        }
        if (isLegalEncodedMove(area, root, version, keys, board, false)) {
            return new FirstMoveCandidate(root, false);
        }
        int limit = Math.min(area.length - 4, 160);
        for (int i = 0; i <= limit; i++) {
            if (isLegalEncodedMove(area, i, version, keys, board, true)) {
                return new FirstMoveCandidate(i, true);
            }
            if (isLegalEncodedMove(area, i, version, keys, board, false)) {
                return new FirstMoveCandidate(i, false);
            }
        }
        return null;
    }

    private static String buildBranchMetadata(
            Map<Integer, List<PgnManualUtils.VariationLine>> variations,
            Map<Integer, String> activeBranchLabels) {
        if (variations == null || variations.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n#NDXQ-BRANCHES#");
        ArrayList<Integer> nodes = new ArrayList<Integer>(variations.keySet());
        Collections.sort(nodes);
        for (Integer node : nodes) {
            if (node == null) continue;
            List<PgnManualUtils.VariationLine> lines = variations.get(node);
            if (lines == null || lines.isEmpty()) continue;
            String active = activeBranchLabels == null ? "A" : activeBranchLabels.get(node);
            if (active == null || active.length() == 0) active = "A";
            out.append(node).append('=').append(cleanBranchLabel(active)).append(':');
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) out.append(',');
                PgnManualUtils.VariationLine line = lines.get(i);
                out.append(cleanBranchLabel(line == null ? "" : line.label));
            }
            out.append(';');
        }
        return out.append("#END-NDXQ#").toString();
    }

    private static String cleanBranchLabel(String label) {
        if (label == null || label.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            char ch = Character.toUpperCase(label.charAt(i));
            if (ch >= 'A' && ch <= 'Z') out.append(ch);
        }
        return out.toString();
    }

    private static void applyBranchMetadata(PgnManualUtils.ParsedManual out) {
        for (Map.Entry<Integer, List<String>> entry : out.variationLabels.entrySet()) {
            List<PgnManualUtils.VariationLine> lines = out.variations.get(entry.getKey());
            List<String> labels = entry.getValue();
            if (lines == null || labels == null) continue;
            for (int i = 0; i < lines.size() && i < labels.size(); i++) {
                String label = labels.get(i);
                if (label != null && label.length() > 0) lines.get(i).label = label;
            }
        }
    }

    private static void parseBranchMetadata(String rootComment, PgnManualUtils.ParsedManual out) {
        if (rootComment == null || out == null) return;
        String marker = "#NDXQ-BRANCHES#";
        int start = rootComment.indexOf(marker);
        if (start < 0) return;
        int payloadStart = start + marker.length();
        int end = rootComment.indexOf("#END-NDXQ#", payloadStart);
        if (end < 0) return;
        String payload = rootComment.substring(payloadStart, end);
        for (String record : payload.split(";")) {
            int equals = record.indexOf('=');
            int colon = record.indexOf(':', equals + 1);
            if (equals <= 0 || colon < 0) continue;
            try {
                int node = Integer.parseInt(record.substring(0, equals));
                String active = cleanBranchLabel(record.substring(equals + 1, colon));
                if (active.length() > 0) out.activeBranchLabels.put(node, active);
                ArrayList<String> labels = new ArrayList<String>();
                for (String label : record.substring(colon + 1).split(",")) {
                    labels.add(cleanBranchLabel(label));
                }
                out.variationLabels.put(node, labels);
            } catch (Exception ignored) {}
        }
    }

    private static int fixedPiecePlacementScore(String fen) {
        if (fen == null) return Integer.MIN_VALUE;
        try {
            char[][] b = XiangqiRules.fromFen(fen);
            int score = 0;
            for (int r = 0; r < 10; r++) for (int c = 0; c < 9; c++) {
                char p = b[r][c];
                if (p == 'K') score += inPalace(r, c, true) ? 12 : -24;
                else if (p == 'k') score += inPalace(r, c, false) ? 12 : -24;
                else if (p == 'A') score += inPalace(r, c, true) ? 5 : -12;
                else if (p == 'a') score += inPalace(r, c, false) ? 5 : -12;
                else if (p == 'B') score += isElephantPoint(r, c, true) ? 5 : -12;
                else if (p == 'b') score += isElephantPoint(r, c, false) ? 5 : -12;
            }
            return score;
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean inPalace(int row, int col, boolean red) {
        return col >= 3 && col <= 5 && (red ? row >= 7 && row <= 9 : row >= 0 && row <= 2);
    }

    private static boolean isElephantPoint(int row, int col, boolean red) {
        if (red) {
            return (row == 9 && (col == 2 || col == 6))
                    || (row == 7 && (col == 0 || col == 4 || col == 8))
                    || (row == 5 && (col == 2 || col == 6));
        }
        return (row == 0 && (col == 2 || col == 6))
                || (row == 2 && (col == 0 || col == 4 || col == 8))
                || (row == 4 && (col == 2 || col == 6));
    }

    private static boolean isFenSane(String fen) {
        if (fen == null) return false;
        try {
            char[][] b = XiangqiRules.fromFen(fen);
            boolean redK = false, blackK = false;
            int pieces = 0;
            for (int r = 0; r < 10; r++) for (int c = 0; c < 9; c++) {
                if (b[r][c] == 'K') redK = true;
                if (b[r][c] == 'k') blackK = true;
                if (XiangqiRules.isPiece(b[r][c])) pieces++;
            }
            return redK && blackK && pieces >= 2;
        } catch (Exception e) { return false; }
    }

    private static String boardToFen(char[][] board) {
        StringBuilder fen = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            int empty = 0;
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (p == ' ') empty++;
                else { if (empty > 0) { fen.append(empty); empty = 0; } fen.append(p); }
            }
            if (empty > 0) fen.append(empty);
            if (r < 9) fen.append('/');
        }
        return fen.toString();
    }

    private static int findFirstMoveOffset(byte[] area, int version, XqfKeys keys, char[][] board, boolean redToMove) {
        int root = skipRoot(area, version, keys);
        if (isLegalEncodedMove(area, root, version, keys, board, redToMove)) return root;
        if (isLegalEncodedMove(area, 13, version, keys, board, redToMove)) return 13;
        for (int i = 0; i < Math.min(area.length - 4, 160); i++) if (isLegalEncodedMove(area, i, version, keys, board, redToMove)) return i;
        return -1;
    }

    private static boolean isLegalEncodedMove(byte[] area, int off, int version, XqfKeys keys, char[][] board, boolean redToMove) {
        Step s = readStep(area, off, version, keys);
        if (s == null || s.step.length() != 4) return false;
        try {
            Move m = Move.fromEngineStep(s.step);
            if (!XiangqiRules.inBoard(m.fromRow, m.fromCol)
                    || !XiangqiRules.inBoard(m.toRow, m.toCol)) return false;
            char p = board[m.fromRow][m.fromCol];
            return XiangqiRules.isPiece(p) && XiangqiRules.isRed(p) == redToMove && XiangqiRules.isLegalMove(board, m);
        } catch (Exception e) { return false; }
    }

    private static boolean allZeroKeys(byte[] data) {
        if (data == null || data.length < 0x10) return true;
        for (int i = 0x03; i <= 0x0F; i++) if (data[i] != 0) return false;
        return true;
    }

    private static int f(int b) {
        int v = b & 255;
        v = ((((((v * v) * 3 + 9) * 3 + 8) * 2 + 1) * 3 + 8)) & 255;
        return v;
    }

    private static String coordToIccs(int coord) {
        int x = coord / 10;
        int y = coord % 10;
        if (x < 0 || x > 8 || y < 0 || y > 9) return "";
        return String.valueOf((char)('a' + x)) + y;
    }

    private static int iccsToCoord(String s) {
        if (s == null || s.length() != 2) return -1;
        int x = Character.toLowerCase(s.charAt(0)) - 'a';
        int y = s.charAt(1) - '0';
        if (x < 0 || x > 8 || y < 0 || y > 9) return -1;
        return x * 10 + y;
    }

    private static void writeInitialPiecePositions(byte[] header, String fen, boolean highVersionZeroKey) {
        if (header == null || header.length < 0x30) return;
        char[][] board = XiangqiRules.fromFen(fen == null ? XiangqiRules.START_FEN : fen);
        boolean[] used = new boolean[10 * 9];
        byte[] logical = new byte[32];
        for (int i = 0; i < 32 && i < PIECES.length(); i++) {
            char p = PIECES.charAt(i);
            int coord = 0xFF;
            outer: for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = row * 9 + col;
                    if (!used[idx] && board[row][col] == p) {
                        used[idx] = true;
                        coord = col * 10 + (9 - row);
                        break outer;
                    }
                }
            }
            logical[i] = (byte)(coord & 255);
        }
        if (highVersionZeroKey) {
            // 解码端规则为 pos[(i+1)&31] = man[i]（KeyXY=0），因此写出时取其逆变换：
            // man[i] = logical[(i+1)&31]。这与红鲨 v18 样本的头部布局一致。
            for (int i = 0; i < 32; i++) header[0x10 + i] = logical[(i + 1) & 0x1F];
        } else {
            System.arraycopy(logical, 0, header, 0x10, 32);
        }
    }

    private static int readIntLE(byte[] b, int off) {
        if (b == null || off + 4 > b.length) return 0;
        return u(b[off]) | (u(b[off + 1]) << 8) | (u(b[off + 2]) << 16) | (u(b[off + 3]) << 24);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        if (out == null) return;
        out.write(value & 255);
        out.write((value >>> 8) & 255);
        out.write((value >>> 16) & 255);
        out.write((value >>> 24) & 255);
    }

    private static String decodeText(byte[] data) {
        // XQF 注释常见编码是 GBK/GB18030；优先按 GB18030 读，避免 GBK 字节被 UTF-8 误判成乱码。
        try {
            String gb = new String(data, Charset.forName("GB18030"));
            if (gb.length() > 0) return gb;
        } catch (Exception ignored) {}
        try {
            String utf = new String(data, Charset.forName("UTF-8"));
            if (utf.indexOf('�') < 0 && utf.length() > 0) return utf;
        } catch (Exception ignored) {}
        return "";
    }

    private static int u(byte b) { return b & 255; }

    private static final class FirstMoveCandidate {
        final int offset;
        final boolean redToMove;
        FirstMoveCandidate(int offset, boolean redToMove) {
            this.offset = offset;
            this.redToMove = redToMove;
        }
    }

    private static final class XqfNode {
        final String step;
        String comment;
        XqfNode child;
        XqfNode sibling;
        XqfNode(String step, String comment) {
            this.step = step == null ? "" : step;
            this.comment = comment == null ? "" : comment;
        }
    }

    private static final class TreeReadState {
        final int maxNodes;
        int totalNodes;
        int nextOffset;
        TreeReadState(int maxNodes) {
            this.maxNodes = Math.max(1, maxNodes);
        }
    }

    private static final class XqfKeys {
        int keyXY;
        int keyXYf;
        int keyXYt;
        int keyRmkSize = KEY_RMK_SIZE_ZERO;
        byte[] f32Keys = new byte[32];
        boolean zeroKeys;
    }

    private static final class Step {
        int from;
        int to;
        String step = "";
        String comment = "";
        boolean hasNext;
        boolean hasVariation;
        int nextOffset;
    }

    private static final class ParseState {
        final PgnManualUtils.ParsedManual out;
        int totalNodes;
        PgnManualUtils.VariationLine currentVariation = new PgnManualUtils.VariationLine();
        ParseState(PgnManualUtils.ParsedManual out) { this.out = out; }
    }
}

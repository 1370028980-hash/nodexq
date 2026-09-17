package com.tyl.xiangqi.ndxq.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PGN/中文棋谱工具。
 *
 * V9 粘贴识别原则：
 * - 坐标棋谱逐手校验行棋方与合法性；
 * - 中文棋谱不再依靠“猜起点”，而是枚举当前局面的全部合法着法，
 *   将其标准中文记谱与剪贴板着法归一化后做唯一匹配；
 * - 任意疑似着法无法识别、存在歧义或不合法时立即终止，不再跳过错误后继续解析；
 * - 支持标准 PGN 标签、ICCS、常见中文数字/阿拉伯数字、繁简体棋子名、
 *   紧凑连续着法以及本应用导出的“初始FEN：”文本格式。
 */
public final class PgnManualUtils {
    public static final class ParsedManual {
        public String fen;
        public final List<Move> moves = new ArrayList<Move>();
        public final List<String> chineseMoves = new ArrayList<String>();
        public final List<String> comments = new ArrayList<String>();
        /** 初始局面/整盘棋谱说明；PGN 中使用标签后、第一手前的首个注释保存。 */
        public String initialComment = "";
        public final Map<Integer, List<VariationLine>> variations = new HashMap<Integer, List<VariationLine>>();
        /** 节点象棋 XQF 扩展保存的当前线路标签。标准 PGN 没有此字段时为空。 */
        public final Map<Integer, String> activeBranchLabels = new HashMap<Integer, String>();
        /** 节点象棋 XQF 扩展保存的分支标签，键为主线着法下标。 */
        public final Map<Integer, List<String>> variationLabels = new HashMap<Integer, List<String>>();
        public String title = "PGN 棋谱";
        /** 与标准中国象棋 PGN 对应的来源标签；为空时由导出层补默认值。 */
        public String event = "";
        public String site = "";
        public String date = "";
        public String round = "";
        public String red = "";
        public String black = "";
        /** 0=红胜，1=黑胜，2=和棋，3=未知。 */
        public int result = 3;
        /** 剪贴板中是否明确提供了 FEN；即使没有着法，也可作为局面粘贴。 */
        public boolean hasExplicitFen;
        /** 实际识别并严格校验的主线着法数。 */
        public int sourceMoveCount;
    }

    public static final class VariationLine {
        /** 节点象棋分支标签；标准 PGN 解析时为空。 */
        public String label = "";
        /** 东萍 UBB 变例 ID；零表示该分支不来自东萍文件，保存时自动分配。 */
        public int dhtmlVariationId;
        public final List<String> engineSteps = new ArrayList<String>();
        public final List<String> chineseMoves = new ArrayList<String>();
        public final List<String> comments = new ArrayList<String>();
        /** 相对本线路起点的后续分支；键为该线路中的 0 基着法下标。 */
        public final Map<Integer, List<VariationLine>> variations = new HashMap<Integer, List<VariationLine>>();
    }

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "(?im)^\\s*\\[\\s*([A-Za-z][A-Za-z0-9_]*)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\s*]\\s*$");
    private static final Pattern EXPLICIT_FEN_LINE = Pattern.compile(
            "(?im)^\\s*(?:初始\\s*FEN|FEN)\\s*[：:]\\s*(.+?)\\s*$");
    private static final Pattern MOVE_NUMBER = Pattern.compile(
            "(?m)(^|\\s)[0-9０-９]+\\s*[.．]{1,3}\\s*");

    /** 主线正文中的一个顺序元素：要么是着法 token，要么是花括号评注。 */
    private static final class MovetextElement {
        final String token;
        final String comment;

        private MovetextElement(String token, String comment) {
            this.token = token;
            this.comment = comment;
        }

        static MovetextElement token(String value) {
            return new MovetextElement(value, null);
        }

        static MovetextElement comment(String value) {
            return new MovetextElement(null, value == null ? "" : value.trim());
        }

        boolean isComment() {
            return comment != null;
        }
    }

    private PgnManualUtils() {}

    public static ParsedManual parse(String text, String fallbackFen) {
        ParsedManual result = new ParsedManual();
        result.fen = normalizeFen(fallbackFen, XiangqiRules.START_FEN);
        if (text == null) return result;

        String source = text.replace('\uFEFF', ' ')
                .replace("\r\n", "\n").replace('\r', '\n');

        // 标签可大小写混用，并允许标签之间有空行；统一从全文提取，避免正文起始判断错位。
        Matcher tagMatcher = TAG_PATTERN.matcher(source);
        StringBuffer bodyBuffer = new StringBuffer();
        while (tagMatcher.find()) {
            String tag = tagMatcher.group(1);
            String value = PgnTextEscapes.decode(tagMatcher.group(2));
            if ("Event".equalsIgnoreCase(tag)) {
                result.event = value.trim();
                if (result.event.length() > 0) result.title = result.event;
            } else if ("Site".equalsIgnoreCase(tag)) {
                result.site = value.trim();
            } else if ("Date".equalsIgnoreCase(tag)) {
                result.date = value.trim();
            } else if ("Round".equalsIgnoreCase(tag)) {
                result.round = value.trim();
            } else if ("Red".equalsIgnoreCase(tag)) {
                result.red = value.trim();
            } else if ("Black".equalsIgnoreCase(tag)) {
                result.black = value.trim();
            } else if ("FEN".equalsIgnoreCase(tag)) {
                result.fen = normalizeFen(value, fallbackFen);
                result.hasExplicitFen = true;
            } else if ("Result".equalsIgnoreCase(tag)) {
                result.result = parseResultToken(value);
            }
            tagMatcher.appendReplacement(bodyBuffer, " ");
        }
        tagMatcher.appendTail(bodyBuffer);
        String body = bodyBuffer.toString();

        // 兼容“初始FEN：xxx”文本棋谱。
        Matcher fenLine = EXPLICIT_FEN_LINE.matcher(body);
        if (fenLine.find()) {
            result.fen = normalizeFen(fenLine.group(1), fallbackFen);
            result.hasExplicitFen = true;
            body = fenLine.replaceAll(" ");
        }
        body = body.replaceAll("(?im)^\\s*格式\\s*[：:].*$", " ");
        result.fen = normalizeFen(result.fen, fallbackFen);

        int leadingCommentStart = skipWhitespace(body, 0);
        if (leadingCommentStart < body.length() && body.charAt(leadingCommentStart) == '{') {
            int leadingCommentEnd = findCommentEnd(body, leadingCommentStart + 1);
            if (leadingCommentEnd >= 0) {
                result.initialComment = stripLegacyRootComment(PgnTextEscapes.decode(
                        body.substring(leadingCommentStart + 1, leadingCommentEnd)));
                body = body.substring(leadingCommentEnd + 1);
            }
        }

        List<MovetextElement> elements = tokenizeMainLineWithComments(body);

        char[][] board = XiangqiRules.fromFen(result.fen);
        boolean redToMove = XiangqiRules.redToMoveFromFen(result.fen);
        int moveNumber = 0;
        int previousMoveIndex = -1;

        for (MovetextElement element : elements) {
            if (element.isComment()) {
                if (previousMoveIndex < 0) {
                    // 兼容旧节点象棋 PGN：根注释可能带 #1,1# 前缀，且未必恰在正文开头。
                    appendCommentToRoot(result, stripLegacyRootComment(element.comment));
                } else {
                    appendCommentToMove(result.comments, previousMoveIndex, element.comment);
                }
                continue;
            }
            for (String expanded : expandMoveToken(element.token)) {
                String token = cleanToken(expanded);
                if (token.length() == 0 || isIgnorableToken(token)) continue;
                if (isResultToken(token)) {
                    if (result.result == 3) result.result = parseResultToken(token);
                    return result;
                }
                if (!looksLikeMoveToken(token)) continue;

                moveNumber++;
                Move move = parseExactLegalMove(board, token, redToMove);
                if (move == null) {
                    throw new IllegalArgumentException("第 " + moveNumber
                            + " 手无法唯一识别或不合法：" + token);
                }

                String cn = ChineseNotation.translate(board, move, false);
                result.moves.add(move);
                result.chineseMoves.add(cn);
                result.comments.add("");
                previousMoveIndex = result.moves.size() - 1;
                applyMove(board, move);
                redToMove = !redToMove;
                result.sourceMoveCount++;
            }
        }
        return result;
    }

    public static String exportChineseText(String initialFen, List<String> readableMoves) {
        return exportChineseText(initialFen, readableMoves, null);
    }

    public static String exportChineseText(String initialFen, List<String> readableMoves,
                                           List<String> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("初始FEN：").append(initialFen == null ? "" : initialFen).append('\n');
        sb.append("格式：中文记谱\n\n");
        if (readableMoves == null || readableMoves.isEmpty()) {
            sb.append("棋谱为空。\n");
            return sb.toString();
        }
        for (int i = 0; i < readableMoves.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(ChineseNotation.normalizeArabicDigits(readableMoves.get(i)));
            if (comments != null && i < comments.size() && comments.get(i) != null
                    && comments.get(i).trim().length() > 0) {
                sb.append(" {").append(comments.get(i).trim()).append("}");
            }
            if (i % 2 == 0) sb.append("  "); else sb.append('\n');
        }
        if (readableMoves.size() % 2 == 1) sb.append('\n');
        return sb.toString();
    }

    /** 坐标着法直接校验；中文着法通过“全部合法着法反向生成标准记谱”做唯一匹配。 */
    private static Move parseExactLegalMove(char[][] board, String token, boolean redToMove) {
        String coordinate = token.toLowerCase(Locale.ROOT).replace("-", "");
        if (coordinate.matches("^[a-i][0-9][a-i][0-9]$")) {
            try {
                Move move = Move.fromEngineStep(coordinate);
                char piece = board[move.fromRow][move.fromCol];
                if (!XiangqiRules.isPiece(piece) || XiangqiRules.isRed(piece) != redToMove) return null;
                return XiangqiRules.isLegalMove(board, move) ? move : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        String target = canonicalChineseMove(token);
        if (target == null) return null;
        Move match = null;
        for (Move candidate : XiangqiRules.generateLegalMoves(board, redToMove)) {
            String generated = canonicalChineseMove(
                    ChineseNotation.translate(board, candidate, false));
            if (!target.equals(generated)) continue;
            if (match != null && !match.toEngineStep().equals(candidate.toEngineStep())) {
                return null; // 同一文本对应多步，拒绝猜测。
            }
            match = candidate;
        }
        return match;
    }

    /**
     * 把繁简体、红黑棋子名称及中文/全角/半角数字统一为可比较的四字符记谱。
     * 例：炮二平五、砲２平５ -> C2=5；馬8進7 -> N8+7。
     */
    private static String canonicalChineseMove(String token) {
        String t = cleanToken(token);
        if (t.length() != 4) return null;
        StringBuilder out = new StringBuilder(4);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            char normalized;
            switch (ch) {
                case '车': case '車': case '俥': normalized = 'R'; break;
                case '马': case '馬': case '傌': normalized = 'N'; break;
                case '象': case '相': normalized = 'B'; break;
                case '士': case '仕': normalized = 'A'; break;
                case '将': case '將': case '帅': case '帥': normalized = 'K'; break;
                case '炮': case '砲': normalized = 'C'; break;
                case '兵': case '卒': normalized = 'P'; break;
                case '进': case '進': normalized = '+'; break;
                case '退': normalized = '-'; break;
                case '平': normalized = '='; break;
                case '前': case '中': case '后': case '後': normalized = ch == '後' ? '后' : ch; break;
                default:
                    int digit = digitValue(ch);
                    if (digit < 1 || digit > 9) return null;
                    normalized = (char) ('0' + digit);
                    break;
            }
            out.append(normalized);
        }
        char action = out.charAt(2);
        if (action != '+' && action != '-' && action != '=') return null;
        char first = out.charAt(0);
        char second = out.charAt(1);
        boolean normal = isPieceCode(first) && second >= '1' && second <= '9';
        boolean prefixed = (first == '前' || first == '中' || first == '后'
                || (first >= '1' && first <= '5')) && isPieceCode(second);
        char last = out.charAt(3);
        if ((!normal && !prefixed) || last < '1' || last > '9') return null;
        return out.toString();
    }

    private static boolean isPieceCode(char ch) {
        return ch == 'R' || ch == 'N' || ch == 'B' || ch == 'A'
                || ch == 'K' || ch == 'C' || ch == 'P';
    }

    private static void applyMove(char[][] board, Move move) {
        char piece = board[move.fromRow][move.fromCol];
        board[move.toRow][move.toCol] = piece;
        board[move.fromRow][move.fromCol] = ' ';
    }

    private static String stripLegacyRootComment(String comment) {
        String value = comment == null ? "" : comment.trim();
        return value.replaceFirst("^#1,1#\\s*", "").trim();
    }

    private static void appendCommentToRoot(ParsedManual result, String comment) {
        if (result == null || comment == null || comment.length() == 0) return;
        if (result.initialComment == null || result.initialComment.length() == 0) {
            result.initialComment = comment;
        } else {
            result.initialComment += "\n" + comment;
        }
    }

    private static void appendCommentToMove(List<String> comments, int moveIndex, String comment) {
        if (comments == null || comment == null || comment.length() == 0
                || moveIndex < 0 || moveIndex >= comments.size()) return;
        String previous = comments.get(moveIndex);
        comments.set(moveIndex, previous == null || previous.length() == 0
                ? comment : previous + "\n" + comment);
    }

    /**
     * 只保留主线并按正文顺序保留花括号位置。旧实现先全局抽取评注再按数量回填，
     * 会把出现在第 6 手之后的第一条评注错误挂到第 1 手；这里必须让评注紧跟前一着。
     */
    private static List<MovetextElement> tokenizeMainLineWithComments(String body) {
        List<MovetextElement> out = new ArrayList<MovetextElement>();
        if (body == null || body.length() == 0) return out;
        StringBuilder plainText = new StringBuilder();
        int variationDepth = 0;

        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (variationDepth > 0) {
                if (ch == '{') {
                    int end = findCommentEnd(body, i + 1);
                    if (end < 0) break;
                    i = end;
                } else if (ch == '(' || ch == '（') {
                    variationDepth++;
                } else if (ch == ')' || ch == '）') {
                    variationDepth--;
                }
                continue;
            }
            if (ch == '(' || ch == '（') {
                appendMoveTokens(out, plainText);
                variationDepth = 1;
                continue;
            }
            if (ch == '{') {
                int end = findCommentEnd(body, i + 1);
                if (end < 0) {
                    plainText.append(ch);
                    continue;
                }
                appendMoveTokens(out, plainText);
                out.add(MovetextElement.comment(PgnTextEscapes.decode(
                        body.substring(i + 1, end))));
                i = end;
                continue;
            }
            if (ch == ';') {
                appendMoveTokens(out, plainText);
                while (i + 1 < body.length() && body.charAt(i + 1) != '\n') i++;
                continue;
            }
            plainText.append(ch);
        }
        appendMoveTokens(out, plainText);
        return out;
    }

    private static void appendMoveTokens(List<MovetextElement> out, StringBuilder plainText) {
        if (plainText.length() == 0) return;
        for (String token : tokenizeMoves(plainText.toString())) {
            out.add(MovetextElement.token(token));
        }
        plainText.setLength(0);
    }

    private static int skipWhitespace(String text, int start) {
        int index = Math.max(0, start);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    /** 返回未被反斜杠转义的右花括号，兼容把文本括号写成 \\} 的来源。 */
    private static int findCommentEnd(String text, int contentStart) {
        if (text == null) return -1;
        for (int i = Math.max(0, contentStart); i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                if (i + 1 < text.length()) i++;
            } else if (ch == '}') {
                return i;
            }
        }
        return -1;
    }

    private static List<String> tokenizeMoves(String body) {
        String text = body == null ? "" : body;
        text = text.replaceAll("\\{[^}]*\\}", " ");
        text = text.replaceAll("(?m);[^\\n]*$", " ");

        StringBuilder mainLine = new StringBuilder();
        int variationDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == '（') { variationDepth++; continue; }
            if (ch == ')' || ch == '）') {
                if (variationDepth > 0) variationDepth--;
                continue;
            }
            if (variationDepth == 0) mainLine.append(ch);
        }
        text = mainLine.toString().replaceAll("\\$[0-9]+", " ")
                .replace('，', ' ').replace(',', ' ')
                .replace('、', ' ').replace('；', ' ')
                .replace('\t', ' ').replace('\u00A0', ' ').replace('\u3000', ' ');
        text = MOVE_NUMBER.matcher(text).replaceAll(" ");
        // 清理常见棋谱导出器（鲨鱼/象棋巫师等）在末尾追加的非着法行
        text = text.replaceAll("(?im)^\\s*(感谢|软件|鲨鱼|象棋巫师|自动生成|powered|generated).*$", " ");

        List<String> out = new ArrayList<String>();
        for (String part : text.split("\\s+")) {
            String token = part.trim();
            if (token.length() == 0) continue;
            token = token.replaceFirst("^[0-9０-９]+[.．]{1,3}", "");
            if (token.length() > 0) out.add(token);
        }
        return out;
    }

    /** 支持“炮二平五馬8進7”以及“h2e2h9g7”一类无空格连续着法。 */
    private static List<String> expandMoveToken(String raw) {
        List<String> one = new ArrayList<String>();
        String token = cleanToken(raw);
        if (token.length() == 0) return one;

        String coordinate = token.toLowerCase(Locale.ROOT).replace("-", "");
        if (coordinate.length() > 4 && coordinate.length() % 4 == 0) {
            boolean allCoordinates = true;
            for (int i = 0; i < coordinate.length(); i += 4) {
                if (!coordinate.substring(i, i + 4).matches("^[a-i][0-9][a-i][0-9]$")) {
                    allCoordinates = false;
                    break;
                }
            }
            if (allCoordinates) {
                for (int i = 0; i < coordinate.length(); i += 4) {
                    one.add(coordinate.substring(i, i + 4));
                }
                return one;
            }
        }

        if (token.length() > 4 && token.length() % 4 == 0) {
            boolean allChinese = true;
            for (int i = 0; i < token.length(); i += 4) {
                if (canonicalChineseMove(token.substring(i, i + 4)) == null) {
                    allChinese = false;
                    break;
                }
            }
            if (allChinese) {
                for (int i = 0; i < token.length(); i += 4) {
                    one.add(token.substring(i, i + 4));
                }
                return one;
            }
        }

        one.add(token);
        return one;
    }

    private static boolean looksLikeMoveToken(String token) {
        if (token == null || token.length() == 0) return false;
        String coordinate = token.toLowerCase(Locale.ROOT).replace("-", "");
        if (coordinate.matches("^[a-i][0-9][a-i][0-9]$")) return true;
        if (canonicalChineseMove(token) != null) return true;
        boolean hasPiece = token.matches(".*[车車俥马馬傌象相士仕将將帅帥炮砲兵卒].*");
        boolean hasAction = token.matches(".*[进進退平].*");
        boolean hasDigit = token.matches(".*[一二三四五六七八九0-9０-９].*");
        return (hasPiece && hasAction)
                || (token.length() == 4 && hasAction && hasDigit)
                || (token.length() == 4 && hasPiece && hasDigit);
    }

    private static boolean isIgnorableToken(String token) {
        if (token == null) return true;
        String t = token.trim();
        return t.length() == 0 || t.matches("^[.．…]+$")
                || "棋谱".equals(t) || "棋谱如下".equals(t) || "主线".equals(t)
                || "红方".equals(t) || "黑方".equals(t)
                || "先手".equals(t) || "后手".equals(t)
                || "棋谱为空".equals(t) || "棋谱为空。".equals(t);
    }

    private static boolean isResultToken(String token) {
        String t = cleanToken(token);
        return "1-0".equals(t) || "0-1".equals(t)
                || "1/2-1/2".equals(t) || "1/2".equals(t) || "*".equals(t)
                || "红胜".equals(t) || "先胜".equals(t)
                || "黑胜".equals(t) || "后胜".equals(t)
                || "和棋".equals(t) || "和".equals(t) || "draw".equalsIgnoreCase(t);
    }

    private static int parseResultToken(String token) {
        if (token == null) return 3;
        String t = cleanToken(token);
        if ("1-0".equals(t) || "红胜".equals(t) || "先胜".equals(t)) return 0;
        if ("0-1".equals(t) || "黑胜".equals(t) || "后胜".equals(t)) return 1;
        if ("1/2-1/2".equals(t) || "1/2".equals(t)
                || "和棋".equals(t) || "和".equals(t) || "draw".equalsIgnoreCase(t)) return 2;
        return 3;
    }

    private static String cleanToken(String token) {
        if (token == null) return "";
        String cleaned = token.trim()
                .replace("+", "").replace("#", "")
                .replace("！", "").replace("？", "")
                .replace("!", "").replace("?", "")
                .replace("。", "").replace("：", "").replace(":", "")
                .replace("“", "").replace("”", "").replace("\"", "")
                .replace("【", "").replace("】", "")
                .replace("[", "").replace("]", "");
        // 兼容旧版本错误导出的“炮二平五 吃卒”显示后缀。
        cleaned = cleaned.replaceAll("[\\s　]*吃[车車俥马馬傌相象士仕帅帥将將炮砲兵卒]+$", "");
        return cleaned.trim();
    }

    private static int digitValue(char ch) {
        if (ch >= '1' && ch <= '9') return ch - '0';
        if (ch >= '１' && ch <= '９') return ch - '１' + 1;
        switch (ch) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static String normalizeFen(String fen, String fallbackFen) {
        String value = emptyTo(fen, fallbackFen);
        if (value == null || value.trim().length() == 0) return XiangqiRules.START_FEN;
        value = value.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("fen ")) value = value.substring(4).trim();
        String[] parts = value.split("\\s+");
        String side = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "w";
        if ("r".equals(side)) side = "w";
        if (!"w".equals(side) && !"b".equals(side)) side = "w";
        if (parts.length >= 6) {
            return parts[0] + " " + side + " " + parts[2] + " "
                    + parts[3] + " " + parts[4] + " " + parts[5];
        }
        if (parts.length >= 2) return parts[0] + " " + side + " - - 0 1";
        return parts[0] + " w - - 0 1";
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value;
    }
}

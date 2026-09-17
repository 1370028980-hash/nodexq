package com.tyl.xiangqi.ndxq.core;

/** PGN 标签和兼容性注释使用的反斜杠转义编解码。 */
public final class PgnTextEscapes {
    private PgnTextEscapes() {}

    /**
     * 兼容部分棋谱软件把换行等写成 JSON/C 风格反斜杠转义的历史文本。
     * 未知转义原样保留，避免将普通 Windows 路径等内容静默改写。
     */
    public static String decode(String value) {
        if (value == null || value.length() == 0) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                out.append(ch);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\n');
                    // 把字面量 "\\r\\n" 归一为一个换行，避免多出空行。
                    if (i + 2 < value.length() && value.charAt(i + 1) == '\\'
                            && value.charAt(i + 2) == 'n') {
                        i += 2;
                    }
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case '{':
                    out.append('{');
                    break;
                case '}':
                    out.append('}');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        int codePoint = hexCodePoint(value, i + 1);
                        if (codePoint >= 0) {
                            out.append((char) codePoint);
                            i += 4;
                            break;
                        }
                    }
                    out.append('\\').append('u');
                    break;
                default:
                    out.append('\\').append(escaped);
                    break;
            }
        }
        return normalizeLineEndings(out.toString());
    }

    /** PGN 标签值必须转义反斜杠、双引号和控制换行，避免破坏标签边界。 */
    public static String encodeTagValue(String value) {
        String text = normalizeLineEndings(value == null ? "" : value);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (ch < 0x20) appendUnicodeEscape(out, ch);
                    else out.append(ch);
                    break;
            }
        }
        return out.toString();
    }

    private static int hexCodePoint(String text, int start) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(start + i), 16);
            if (digit < 0) return -1;
            value = (value << 4) | digit;
        }
        return value;
    }

    private static void appendUnicodeEscape(StringBuilder out, char value) {
        final char[] hex = "0123456789abcdef".toCharArray();
        out.append("\\u");
        out.append(hex[(value >>> 12) & 0xF]);
        out.append(hex[(value >>> 8) & 0xF]);
        out.append(hex[(value >>> 4) & 0xF]);
        out.append(hex[value & 0xF]);
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}

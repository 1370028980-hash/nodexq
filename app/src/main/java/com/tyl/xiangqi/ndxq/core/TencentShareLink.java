package com.tyl.xiangqi.ndxq.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从分享文案提取棋谱链接；仅检查链接自身的 QipuId，不把普通棋谱里的网址当分享。 */
public final class TencentShareLink {
    private static final Pattern MANUAL_TEXT = Pattern.compile(
            "(?im)^\\s*(?:\\[\\s*[a-z][a-z0-9_]*\\s+\"|(?:初始\\s*)?FEN\\s*[：:]"
                    + "|[0-9０-９]+\\s*[.．]{1,3}\\s*(?:[车車俥马馬傌炮砲相象仕士帅帥将將兵卒前中后後]"
                    + "|[a-i][0-9]-?[a-i][0-9]))");
    private static final Pattern URL = Pattern.compile(
            "(?i)https?://[^\\s<>\"'\\[\\]{}（）【】《》，。；！、]+"
    );
    private static final Pattern QIPU_ID = Pattern.compile(
            "(?i)[?&#]qipuid=([^&#\\s]+)");

    private TencentShareLink() {}

    public static String extract(String text) {
        if (text == null || MANUAL_TEXT.matcher(text).find()) return null;
        // 无标签 PGN 也可能带链接注释；未闭合的注释同样不能触发外部请求。
        String probe = text.replace("&amp;", "&")
                .replaceAll("(?s)\\{[^}]*?(?:\\}|$)", " ")
                .replaceAll("(?m)(^|\\s);[^\\r\\n]*", " ");
        for (int level = 0; level <= 3; level++) {
            Matcher urls = URL.matcher(probe);
            while (urls.find()) {
                String candidate = urls.group();
                // 分享文案常用括号包裹 URL，右括号不是链接内容。
                while (candidate.endsWith(")") || candidate.endsWith(".")) {
                    candidate = candidate.substring(0, candidate.length() - 1);
                }
                if (hasQipuId(candidate)) return candidate;
            }
            String decoded = decode(probe);
            if (decoded.equals(probe)) break;
            probe = decoded;
        }
        return null;
    }

    private static boolean hasQipuId(String url) {
        String probe = url;
        for (int level = 0; level <= 3; level++) {
            if (QIPU_ID.matcher(probe).find()) return true;
            String decoded = decode(probe);
            if (decoded.equals(probe)) break;
            probe = decoded;
        }
        return false;
    }

    private static String decode(String value) {
        try {
            // 只为识别解码；已有明文 URL 时返回原链接，保留 + 和嵌套参数。
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }
}

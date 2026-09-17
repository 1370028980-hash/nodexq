package com.tyl.xiangqi.ndxq.storage;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** XQF / PGN / 东萍 UBB 的系统文件选择器与 URI 读写。 */
public final class ManualFileIo {
    public static final int FORMAT_XQF = 0;
    public static final int FORMAT_PGN = 1;
    public static final int FORMAT_DHTML_UBB = 2;

    private ManualFileIo() {}

    public static Intent createOpenIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (Build.VERSION.SDK_INT >= 19) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/x-xqf", "application/octet-stream",
                    "application/x-chess-pgn", "application/vnd.chess-pgn", "text/plain", "text/html"
            });
        }
        return intent;
    }

    public static Intent createSaveIntent(int format, String baseTitle) {
        boolean pgn = format == FORMAT_PGN;
        boolean dhtmlUbb = format == FORMAT_DHTML_UBB;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 使用与扩展名匹配的 MIME，避免部分文档提供器把 .pgn/.xqf 改成 .txt/.bin。
        intent.setType(dhtmlUbb ? "text/html" : (pgn ? "application/x-chess-pgn" : "application/x-xqf"));
        String safeBase = baseTitle == null || baseTitle.trim().isEmpty()
                ? "node_chess" : baseTitle.trim();
        intent.putExtra(Intent.EXTRA_TITLE, safeBase + (dhtmlUbb ? ".htm" : (pgn ? ".pgn" : ".xqf")));
        return intent;
    }

    public static String formatName(int format) {
        if (format == FORMAT_DHTML_UBB) return "东萍 UBB";
        return format == FORMAT_PGN ? "PGN" : "XQF";
    }

    public static byte[] readBytes(ContentResolver resolver, Uri uri) throws Exception {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IllegalStateException("无法打开输入流");
        try (InputStream input = in; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    public static String decodeText(byte[] data) {
        byte[] safe = data == null ? new byte[0] : data;
        String utf8 = new String(safe, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') < 0) return utf8;
        try {
            return new String(safe, Charset.forName("GB18030"));
        } catch (Exception ignored) {
            return new String(safe, Charset.forName("GBK"));
        }
    }

    public static void writeBytes(ContentResolver resolver, Uri uri, byte[] bytes) throws Exception {
        OutputStream out = resolver.openOutputStream(uri);
        if (out == null) throw new IllegalStateException("无法打开输出流");
        try (OutputStream output = out) {
            output.write(bytes == null ? new byte[0] : bytes);
            output.flush();
        }
    }
}

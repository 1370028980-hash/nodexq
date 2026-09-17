package com.tyl.xiangqi.ndxq.storage;

import com.tyl.xiangqi.ndxq.core.PgnManualUtils;
import com.tyl.xiangqi.ndxq.core.XqfManualUtils;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 最近对局文件的枚举、命名解析与删除，避免 MainActivity 继续承担文件管理细节。 */
public final class RecentGameStore {
    public static final class Record {
        public final File file;
        public final String displayLine;
        public final String result;
        public final long sortKey;
        /** 红黑各走一步计 1 回合；最后只有单方一着时向上取整。 */
        public final int turnCount;

        Record(File file, String displayLine, String result, long sortKey, int turnCount) {
            this.file = file;
            this.displayLine = displayLine;
            this.result = result;
            this.sortKey = sortKey;
            this.turnCount = turnCount;
        }
    }

    private static final Map<String, CountCache> TURN_COUNT_CACHE = new HashMap<String, CountCache>();

    private static final class CountCache {
        final long length;
        final long modified;
        final int turns;
        CountCache(long length, long modified, int turns) {
            this.length = length; this.modified = modified; this.turns = turns;
        }
    }

    private final File directory;

    public RecentGameStore(File directory) {
        this.directory = directory;
    }

    public List<Record> list() {
        ArrayList<Record> out = new ArrayList<Record>();
        if (directory == null) return out;
        File[] files = directory.listFiles((dir, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith(".xqf"));
        if (files == null) return out;
        for (File file : files) {
            Record record = parse(file);
            if (record != null) out.add(record);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.sortKey, a.sortKey));
        return out;
    }

    public boolean delete(Record record) {
        return record != null && deleteFile(record.file);
    }

    public int clear() {
        int deleted = 0;
        if (directory == null) return deleted;
        File[] files = directory.listFiles((dir, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith(".xqf"));
        if (files == null) return deleted;
        for (File file : files) if (deleteFile(file)) deleted++;
        return deleted;
    }

    private boolean deleteFile(File file) {
        if (file == null || !file.isFile()) return false;
        try { return file.delete(); }
        catch (Exception ignored) { return false; }
    }

    private Record parse(File file) {
        if (file == null || !file.isFile()) return null;
        String name = file.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".xqf")) name = name.substring(0, name.length() - 4);
        String[] parts = name.split("__", 4);
        if (parts.length != 4 || !parts[0].matches("\\d{12,14}")) return null;
        String stamp = parts[0];
        String displayStamp = stamp.substring(0, Math.min(12, stamp.length()));
        if (displayStamp.length() > 6) displayStamp = displayStamp.substring(0, 6) + "," + displayStamp.substring(6);
        long sortKey;
        try { sortKey = Long.parseLong(stamp); }
        catch (Exception ignored) { sortKey = file.lastModified(); }
        int turns = readTurnCount(file);
        String line = parts[1] + " " + parts[2] + " " + parts[3] + "  " + displayStamp
                + (turns > 0 ? "  " + turns + "回合" : "");
        return new Record(file, line, parts[2], sortKey, turns);
    }

    private int readTurnCount(File file) {
        if (file == null || !file.isFile()) return 0;
        String key = file.getAbsolutePath();
        long length = file.length();
        long modified = file.lastModified();
        synchronized (TURN_COUNT_CACHE) {
            CountCache cached = TURN_COUNT_CACHE.get(key);
            if (cached != null && cached.length == length && cached.modified == modified) return cached.turns;
        }
        int turns = 0;
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) > 0) output.write(buffer, 0, n);
            PgnManualUtils.ParsedManual manual = XqfManualUtils.parse(output.toByteArray(), XiangqiRules.START_FEN);
            int plies = manual.moves.size();
            if (plies > 0) turns = (plies + 1) / 2;
        } catch (Exception ignored) {}
        synchronized (TURN_COUNT_CACHE) {
            TURN_COUNT_CACHE.put(key, new CountCache(length, modified, turns));
        }
        return turns;
    }
}

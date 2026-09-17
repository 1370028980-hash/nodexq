package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 评测等级分变动的轻量本地历史存储，不把记录逻辑堆到 MainActivity。 */
final class EvaluationRatingHistory {
    private static final String KEY = "evaluation_rating_history_v1";
    private static final int MAX_RECORDS = 200;

    static final class Entry {
        final long time;
        final int before;
        final int after;
        final int opponent;
        final int k;
        final int streak;
        final String result;

        Entry(long time, int before, int after, int opponent, int k, int streak, String result) {
            this.time = time; this.before = before; this.after = after;
            this.opponent = opponent; this.k = k; this.streak = streak;
            this.result = result == null ? "" : result;
        }
    }

    private EvaluationRatingHistory() {}

    static void append(Context context, int before, int after, int opponent, int k,
                       int streak, String result) {
        if (context == null) return;
        List<Entry> entries = read(context);
        entries.add(new Entry(System.currentTimeMillis(), before, after, opponent, k, streak, result));
        while (entries.size() > MAX_RECORDS) entries.remove(0);
        StringBuilder encoded = new StringBuilder();
        for (Entry e : entries) {
            if (encoded.length() > 0) encoded.append('\n');
            encoded.append(e.time).append('|').append(e.before).append('|').append(e.after)
                    .append('|').append(e.opponent).append('|').append(e.k).append('|')
                    .append(e.streak).append('|').append(e.result.replace("|", ""));
        }
        prefs(context).edit().putString(KEY, encoded.toString()).apply();
    }

    static List<Entry> read(Context context) {
        ArrayList<Entry> out = new ArrayList<Entry>();
        if (context == null) return out;
        String raw = prefs(context).getString(KEY, "");
        if (raw == null || raw.length() == 0) return out;
        for (String line : raw.split("\\r?\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 7) continue;
            try {
                out.add(new Entry(Long.parseLong(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), p[6]));
            } catch (Exception ignored) {}
        }
        return out;
    }

    static String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }
}

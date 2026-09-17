package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.core.PgnManualUtils;

/** 棋谱来源携带的通用 PGN 标签，独立于走法和注释保存。 */
final class ManualMetadata {
    String event = "";
    String site = "";
    String date = "";
    String round = "";
    String red = "";
    String black = "";

    void clear() {
        event = "";
        site = "";
        date = "";
        round = "";
        red = "";
        black = "";
    }

    void copyFrom(ManualMetadata other) {
        if (other == null) {
            clear();
            return;
        }
        event = clean(other.event);
        site = clean(other.site);
        date = clean(other.date);
        round = clean(other.round);
        red = clean(other.red);
        black = clean(other.black);
    }

    void copyFromParsed(PgnManualUtils.ParsedManual manual) {
        if (manual == null) {
            clear();
            return;
        }
        event = clean(manual.event);
        site = clean(manual.site);
        date = clean(manual.date);
        round = clean(manual.round);
        red = clean(manual.red);
        black = clean(manual.black);
    }

    boolean hasValues() {
        return event.length() > 0 || site.length() > 0 || date.length() > 0
                || round.length() > 0 || red.length() > 0 || black.length() > 0;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

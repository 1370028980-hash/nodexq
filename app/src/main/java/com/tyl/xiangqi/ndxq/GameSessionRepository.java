package com.tyl.xiangqi.ndxq;

import android.content.SharedPreferences;

import com.tyl.xiangqi.ndxq.core.XiangqiRules;

/** SharedPreferences 中未完成棋局的底层读写，不参与界面恢复与引擎编排。 */
final class GameSessionRepository {
    static final class ReadResult {
        final SavedSession session;
        final String error;

        ReadResult(SavedSession session, String error) {
            this.session = session;
            this.error = error;
        }
    }

    private final SharedPreferences preferences;
    private final String startFen;
    private final int difficultyCount;

    GameSessionRepository(SharedPreferences preferences, String startFen, int difficultyCount) {
        this.preferences = preferences;
        this.startFen = startFen;
        this.difficultyCount = difficultyCount;
    }

    void clear(String key) {
        clear(key, false);
    }

    void clear(String key, boolean synchronous) {
        SharedPreferences.Editor editor = preferences.edit().remove(key);
        if (synchronous) editor.commit();
        else editor.apply();
    }

    void write(String key, SavedSession snapshot, boolean synchronous) throws Exception {
        SharedPreferences.Editor editor = preferences.edit().putString(key,
                SessionJsonCodec.encode(snapshot, startFen));
        if (synchronous) editor.commit();
        else editor.apply();
    }

    ReadResult read(String key, boolean analysis, int selectedDifficultyIndex,
                    boolean enginePlaysRed, boolean allowEmptyStartPosition) {
        String raw = preferences.getString(key, "");
        if (raw == null || raw.trim().length() == 0) return new ReadResult(null, null);
        try {
            SavedSession saved = SessionJsonCodec.decode(raw, analysis, startFen,
                    selectedDifficultyIndex, enginePlaysRed, difficultyCount);
            XiangqiRules.fromFen(saved.baseFen);
            boolean emptyStart = saved.engineSteps.isEmpty()
                    && (saved.initialComment == null || saved.initialComment.trim().length() == 0)
                    && !saved.manualMetadata.hasValues()
                    && normalizeFen(saved.baseFen).equals(normalizeFen(startFen));
            if (!allowEmptyStartPosition && !saved.completedDuelGame && emptyStart) {
                clear(key);
                return new ReadResult(null, null);
            }
            return new ReadResult(saved, null);
        } catch (Exception e) {
            clear(key);
            return new ReadResult(null, e.getMessage());
        }
    }

    private static String normalizeFen(String fen) {
        if (fen == null) return "";
        String value = fen.trim().replaceAll("\\s+", " ");
        if (value.length() == 0) return "";
        String[] parts = value.split("\\s+");
        String side = parts.length >= 2 ? parts[1].toLowerCase() : "w";
        if ("r".equals(side)) side = "w";
        if (!"w".equals(side) && !"b".equals(side)) side = "w";
        if (parts.length >= 6) return parts[0] + " " + side + " " + parts[2] + " "
                + parts[3] + " " + parts[4] + " " + parts[5];
        if (parts.length >= 2) return parts[0] + " " + side + " - - 0 1";
        return parts[0] + " w - - 0 1";
    }
}

package com.tyl.xiangqi.ndxq;

import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 难度选择、历史战绩和自定义名称的持久化边界。
 *
 * <p>这里保留既有 preference 键及迁移顺序，避免版本升级后旧 13 档的名称、战绩和
 * 留存棋局错位。界面与棋局编排仍由 MainActivity 负责。</p>
 */
final class DifficultyPreferences {
    private static final String DIFFICULTY_KEY = "difficulty";
    private static final String CUSTOM_SELECTION_KEY = "custom_difficulty_selection";
    private static final String CUSTOM_ENGINE_RED_KEY = "custom_engine_red";
    private static final String NAME_PREFIX = "difficulty_custom_name_";
    private static final String STATS_PREFIX = "difficulty_stats_";
    private static final String V199_MIGRATED = "__v199_difficulty_migrated";
    private static final String V202_MIGRATED = "__v202_difficulty_migrated";
    private static final String V202_MAPPING_FIXED = "__v202_custom_mapping_fixed";
    private static final String[] RESULT_SUFFIXES = new String[]{"win", "draw", "loss"};

    private final SharedPreferences preferences;
    private final DifficultyProfile[] difficulties;
    private final int[] customDifficultyIndices;

    DifficultyPreferences(SharedPreferences preferences, DifficultyProfile[] difficulties,
                          int[] customDifficultyIndices) {
        this.preferences = preferences;
        this.difficulties = difficulties;
        this.customDifficultyIndices = customDifficultyIndices;
    }

    void migrateFromV199(String savedGameKey, String savedAnalysisKey) {
        if (preferences.getBoolean(V199_MIGRATED, false)) return;

        boolean hasLegacyData = preferences.contains(DIFFICULTY_KEY)
                || preferences.contains(savedGameKey) || preferences.contains(savedAnalysisKey);
        for (int i = 0; i < 9 && !hasLegacyData; i++) {
            hasLegacyData = preferences.contains(statsKey(i, "win"))
                    || preferences.contains(statsKey(i, "draw"))
                    || preferences.contains(statsKey(i, "loss"));
        }

        SharedPreferences.Editor editor = preferences.edit();
        if (hasLegacyData) {
            if (preferences.contains(DIFFICULTY_KEY)) {
                editor.putInt(DIFFICULTY_KEY, mapV198Index(
                        clamp(preferences.getInt(DIFFICULTY_KEY, 0), 0, 8)));
            }

            int[][] stats = new int[9][RESULT_SUFFIXES.length];
            boolean[][] present = new boolean[9][RESULT_SUFFIXES.length];
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < RESULT_SUFFIXES.length; j++) {
                    String key = statsKey(i, RESULT_SUFFIXES[j]);
                    present[i][j] = preferences.contains(key);
                    stats[i][j] = preferences.getInt(key, 0);
                    editor.remove(key);
                }
            }
            for (int i = 0; i < 9; i++) {
                int mapped = mapV198Index(i);
                for (int j = 0; j < RESULT_SUFFIXES.length; j++) {
                    if (present[i][j]) editor.putInt(statsKey(mapped, RESULT_SUFFIXES[j]), stats[i][j]);
                }
            }
            migrateSavedDifficultyIndex(editor, savedGameKey, true);
            migrateSavedDifficultyIndex(editor, savedAnalysisKey, true);
        }
        editor.putBoolean(V199_MIGRATED, true).apply();
    }

    void migrateFromV202(String savedGameKey, String savedAnalysisKey) {
        if (preferences.getBoolean(V202_MIGRATED, false)) return;

        boolean hasLegacyData = preferences.contains(DIFFICULTY_KEY)
                || preferences.contains(savedGameKey) || preferences.contains(savedAnalysisKey);
        for (int i = 0; i < customDifficultyIndices.length && !hasLegacyData; i++) {
            hasLegacyData = preferences.contains(nameKey(i))
                    || preferences.contains(statsKey(i, "win"))
                    || preferences.contains(statsKey(i, "draw"))
                    || preferences.contains(statsKey(i, "loss"));
        }

        SharedPreferences.Editor editor = preferences.edit();
        if (hasLegacyData) {
            if (preferences.contains(DIFFICULTY_KEY)) {
                editor.putInt(DIFFICULTY_KEY, mapV201Index(clamp(
                        preferences.getInt(DIFFICULTY_KEY, 0), 0,
                        customDifficultyIndices.length - 1)));
            }

            int[][] stats = new int[customDifficultyIndices.length][RESULT_SUFFIXES.length];
            boolean[][] hasStats = new boolean[customDifficultyIndices.length][RESULT_SUFFIXES.length];
            String[] names = new String[customDifficultyIndices.length];
            boolean[] hasNames = new boolean[customDifficultyIndices.length];
            for (int old = 0; old < customDifficultyIndices.length; old++) {
                String nameKey = nameKey(old);
                hasNames[old] = preferences.contains(nameKey);
                names[old] = preferences.getString(nameKey, "");
                editor.remove(nameKey);
                for (int i = 0; i < RESULT_SUFFIXES.length; i++) {
                    String key = statsKey(old, RESULT_SUFFIXES[i]);
                    hasStats[old][i] = preferences.contains(key);
                    stats[old][i] = preferences.getInt(key, 0);
                    editor.remove(key);
                }
            }
            for (int old = 0; old < customDifficultyIndices.length; old++) {
                int mapped = mapV201Index(old);
                if (hasNames[old]) editor.putString(nameKey(mapped), names[old]);
                for (int i = 0; i < RESULT_SUFFIXES.length; i++) {
                    if (hasStats[old][i]) {
                        editor.putInt(statsKey(mapped, RESULT_SUFFIXES[i]), stats[old][i]);
                    }
                }
            }
            migrateSavedDifficultyIndex(editor, savedGameKey, false);
            migrateSavedDifficultyIndex(editor, savedAnalysisKey, false);
        }
        editor.putBoolean(V202_MIGRATED, true).apply();
    }

    void fixEarlyV202CustomMapping() {
        if (!preferences.getBoolean(V202_MIGRATED, false)
                || preferences.getBoolean(V202_MAPPING_FIXED, false)) return;
        SharedPreferences.Editor editor = preferences.edit();
        String oldName = preferences.getString(nameKey(12), "");
        if (!preferences.contains(nameKey(11)) && oldName != null && oldName.trim().length() > 0) {
            editor.putString(nameKey(11), oldName);
            editor.remove(nameKey(12));
        }
        for (String suffix : RESULT_SUFFIXES) {
            String oldKey = statsKey(12, suffix);
            String newKey = statsKey(11, suffix);
            if (!preferences.contains(newKey) && preferences.contains(oldKey)) {
                editor.putInt(newKey, preferences.getInt(oldKey, 0));
                editor.remove(oldKey);
            }
        }
        if (preferences.getInt(DIFFICULTY_KEY, -1) == 12) editor.putInt(DIFFICULTY_KEY, 11);
        editor.putBoolean(V202_MAPPING_FIXED, true).apply();
    }

    String displayName(int difficultyIndex) {
        int index = clamp(difficultyIndex, 0, difficulties.length - 1);
        String builtIn = difficulties[index].name;
        if (builtIn == null || builtIn.trim().isEmpty()) return "难度" + (index + 1);
        if (index == difficulties.length - 1) return builtIn;
        String custom = preferences.getString(nameKey(index), "");
        custom = custom == null ? "" : custom.trim();
        return custom.isEmpty() ? builtIn : custom;
    }

    String nameKey(int difficultyIndex) {
        return NAME_PREFIX + clamp(difficultyIndex, 0, difficulties.length - 1);
    }

    String statsKey(int difficultyIndex, String suffix) {
        return STATS_PREFIX + clamp(difficultyIndex, 0, difficulties.length - 1) + "_" + suffix;
    }

    int getStat(int difficultyIndex, String suffix) {
        return preferences.getInt(statsKey(difficultyIndex, suffix), 0);
    }

    String[] displayLabels() {
        String[] labels = new String[difficulties.length];
        for (int i = 0; i < labels.length; i++) labels[i] = displayName(i);
        return labels;
    }

    String[] customDisplayLabels() {
        String[] labels = new String[customDifficultyIndices.length];
        for (int i = 0; i < labels.length; i++) labels[i] = displayName(customDifficultyIndices[i]);
        return labels;
    }

    int[] customDisplayNumbers() {
        int[] numbers = new int[customDifficultyIndices.length];
        for (int i = 0; i < numbers.length; i++) numbers[i] = customDifficultyIndices[i] + 1;
        return numbers;
    }

    int customPosition(int difficultyIndex) {
        for (int i = 0; i < customDifficultyIndices.length; i++) {
            if (customDifficultyIndices[i] == difficultyIndex) return i;
        }
        int nearest = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < customDifficultyIndices.length; i++) {
            int current = Math.abs(customDifficultyIndices[i] - difficultyIndex);
            if (current < distance) {
                nearest = i;
                distance = current;
            }
        }
        return nearest;
    }

    /** 自选难度与评测随机匹配分别保存，避免评测档位污染自选入口。 */
    boolean hasCustomSelection() {
        return preferences.contains(CUSTOM_SELECTION_KEY);
    }

    int customSelectedDifficulty(int fallbackIndex) {
        int fallback = customDifficultyIndices[customPosition(fallbackIndex)];
        int saved = preferences.getInt(CUSTOM_SELECTION_KEY, fallback);
        return customDifficultyIndices[customPosition(saved)];
    }

    boolean customEnginePlaysRed(boolean fallback) {
        return preferences.getBoolean(CUSTOM_ENGINE_RED_KEY, fallback);
    }

    void saveCustomSelection(int difficultyIndex, boolean enginePlaysRed) {
        int normalized = customDifficultyIndices[customPosition(difficultyIndex)];
        preferences.edit()
                .putInt(CUSTOM_SELECTION_KEY, normalized)
                .putBoolean(CUSTOM_ENGINE_RED_KEY, enginePlaysRed)
                .apply();
    }

    void resetAllStats() {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < difficulties.length; i++) {
            for (String suffix : RESULT_SUFFIXES) editor.remove(statsKey(i, suffix));
        }
        editor.commit();
    }

    private int mapV198Index(int oldIndex) {
        return oldIndex <= 0 ? 1 : clamp(oldIndex + 4, 0, difficulties.length - 1);
    }

    private int mapV201Index(int oldIndex) {
        return customDifficultyIndices[clamp(oldIndex, 0, customDifficultyIndices.length - 1)];
    }

    private void migrateSavedDifficultyIndex(SharedPreferences.Editor editor, String key,
                                             boolean fromV198) {
        String raw = preferences.getString(key, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject object = new JSONObject(raw);
            int oldIndex = object.optInt("difficulty", -1);
            boolean valid = fromV198 ? oldIndex >= 0 && oldIndex <= 8
                    : oldIndex >= 0 && oldIndex < customDifficultyIndices.length;
            if (!valid) return;
            object.put("difficulty", fromV198 ? mapV198Index(oldIndex) : mapV201Index(oldIndex));
            editor.putString(key, object.toString());
        } catch (Exception ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

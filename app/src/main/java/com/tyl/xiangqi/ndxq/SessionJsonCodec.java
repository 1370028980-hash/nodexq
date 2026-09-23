package com.tyl.xiangqi.ndxq;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 棋局留存记录的 JSON 编解码器。
 *
 * schema 和键名必须长期保持兼容，Activity 只负责提供当前状态及持久化位置。
 */
final class SessionJsonCodec {
    private SessionJsonCodec() {}

    static String encode(SavedSession session, String startFen) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 7);
        root.put("analysisMode", session.analysisMode);
        root.put("competitiveEligible", session.competitiveResultEligible);
        root.put("completedDuel", session.completedDuelGame);
        root.put("resultTag", session.gameResultTag == null ? "*" : session.gameResultTag);
        String normalizedBaseFen = normalizeFen(session.baseFen);
        root.put("baseFen", normalizedBaseFen.length() == 0 ? startFen : normalizedBaseFen);
        root.put("difficulty", session.difficultyIndex);
        root.put("viewPly", session.currentPly);
        root.put("engineRed", session.enginePlaysRed);
        root.put("evaluationNavigationLocked", session.evaluationNavigationLocked);
        root.put("sixtyArm", session.sixtyMoveDrawArmedPly);
        root.put("savedAt", System.currentTimeMillis());
        root.put("moves", stringsToJson(session.engineSteps));
        root.put("readable", stringsToJson(session.readableMoves));
        root.put("initialComment", session.initialComment == null ? "" : session.initialComment);
        root.put("manualMetadata", manualMetadataToJson(session.manualMetadata));
        root.put("comments", stringsToJson(session.comments));
        root.put("scores", integersToJson(session.scores));
        root.put("mates", integersToJson(session.matePlies));
        root.put("scoreKnown", booleansToJson(session.scoreKnown));
        root.put("rescoreRecommended", stringsToJson(session.rescoreRecommendedMoves));
        root.put("rescoreScoreKnown", booleansToJson(session.rescoreScoreKnown));
        root.put("initialRescoreScoreKnown", session.initialRescoreScoreKnown);
        root.put("initialScore", session.initialScoreRed);
        root.put("initialMate", session.initialMatePly);
        root.put("initialScoreKnown", session.initialScoreKnown);
        root.put("variations", variationMapToJson(session.variations));
        root.put("activeBranchLabels", activeBranchLabelsToJson(session.activeBranchLabels));
        return root.toString();
    }

    static SavedSession decode(String raw, boolean analysis, String startFen,
                               int defaultDifficulty, boolean defaultEnginePlaysRed,
                               int difficultyCount) throws Exception {
        JSONObject root = new JSONObject(raw);
        SavedSession saved = new SavedSession();
        saved.analysisMode = analysis;
        saved.competitiveResultEligible = root.optBoolean("competitiveEligible", !analysis);
        saved.completedDuelGame = root.optBoolean("completedDuel", false) && !analysis;
        saved.gameResultTag = root.optString("resultTag", "*");
        String normalizedBaseFen = normalizeFen(root.optString("baseFen", startFen));
        saved.baseFen = normalizedBaseFen.length() == 0 ? startFen : normalizedBaseFen;
        saved.difficultyIndex = clamp(root.optInt("difficulty", defaultDifficulty),
                0, Math.max(0, difficultyCount - 1));
        saved.currentPly = root.optInt("viewPly", -1);
        saved.enginePlaysRed = root.optBoolean("engineRed", defaultEnginePlaysRed);
        saved.evaluationNavigationLocked = root.optBoolean("evaluationNavigationLocked", false);
        saved.sixtyMoveDrawArmedPly = root.optInt("sixtyArm", -1);
        jsonToStrings(root.optJSONArray("moves"), saved.engineSteps, true);
        jsonToStrings(root.optJSONArray("readable"), saved.readableMoves, false);
        saved.initialComment = root.optString("initialComment", "");
        readManualMetadata(root.optJSONObject("manualMetadata"), saved.manualMetadata);
        jsonToStrings(root.optJSONArray("comments"), saved.comments, false);
        jsonToIntegers(root.optJSONArray("scores"), saved.scores);
        jsonToIntegers(root.optJSONArray("mates"), saved.matePlies);
        jsonToBooleans(root.optJSONArray("scoreKnown"), saved.scoreKnown);
        jsonToMoveHints(root.optJSONArray("rescoreRecommended"), saved.rescoreRecommendedMoves);
        jsonToBooleans(root.optJSONArray("rescoreScoreKnown"), saved.rescoreScoreKnown);
        saved.initialRescoreScoreKnown = root.optBoolean("initialRescoreScoreKnown", false);
        saved.initialScoreRed = root.optInt("initialScore", 0);
        saved.initialMatePly = root.optInt("initialMate", 0);
        saved.initialScoreKnown = root.optBoolean("initialScoreKnown", false);
        readVariations(root.optJSONObject("variations"), saved.variations);
        readActiveBranchLabels(root.optJSONObject("activeBranchLabels"), saved.activeBranchLabels);
        normalizeSavedLists(saved);
        return saved;
    }

    private static void normalizeSavedLists(SavedSession saved) {
        while (saved.comments.size() < saved.engineSteps.size()) saved.comments.add("");
        if (saved.comments.size() > saved.engineSteps.size()) {
            saved.comments.subList(saved.engineSteps.size(), saved.comments.size()).clear();
        }
        while (saved.scores.size() < saved.engineSteps.size()) saved.scores.add(0);
        while (saved.matePlies.size() < saved.engineSteps.size()) saved.matePlies.add(0);
        while (saved.scoreKnown.size() < saved.engineSteps.size()) saved.scoreKnown.add(false);
        while (saved.rescoreRecommendedMoves.size() < saved.engineSteps.size()) {
            saved.rescoreRecommendedMoves.add("");
        }
        while (saved.rescoreScoreKnown.size() < saved.engineSteps.size()) {
            saved.rescoreScoreKnown.add(false);
        }
        trimToMoveCount(saved.scores, saved.engineSteps.size());
        trimToMoveCount(saved.matePlies, saved.engineSteps.size());
        trimToMoveCount(saved.scoreKnown, saved.engineSteps.size());
        trimToMoveCount(saved.rescoreRecommendedMoves, saved.engineSteps.size());
        trimToMoveCount(saved.rescoreScoreKnown, saved.engineSteps.size());
    }

    private static <T> void trimToMoveCount(List<T> values, int moveCount) {
        if (values.size() > moveCount) values.subList(moveCount, values.size()).clear();
    }

    private static JSONArray stringsToJson(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (String value : values) array.put(value == null ? "" : value);
        return array;
    }

    private static JSONObject manualMetadataToJson(ManualMetadata metadata) throws Exception {
        JSONObject out = new JSONObject();
        if (metadata == null) return out;
        out.put("event", metadata.event == null ? "" : metadata.event);
        out.put("site", metadata.site == null ? "" : metadata.site);
        out.put("date", metadata.date == null ? "" : metadata.date);
        out.put("round", metadata.round == null ? "" : metadata.round);
        out.put("red", metadata.red == null ? "" : metadata.red);
        out.put("black", metadata.black == null ? "" : metadata.black);
        return out;
    }

    private static void readManualMetadata(JSONObject source, ManualMetadata destination) {
        if (destination == null) return;
        destination.clear();
        if (source == null) return;
        destination.event = source.optString("event", "").trim();
        destination.site = source.optString("site", "").trim();
        destination.date = source.optString("date", "").trim();
        destination.round = source.optString("round", "").trim();
        destination.red = source.optString("red", "").trim();
        destination.black = source.optString("black", "").trim();
    }

    private static JSONArray integersToJson(List<Integer> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (Integer value : values) array.put(value == null ? 0 : value);
        return array;
    }

    private static JSONArray booleansToJson(List<Boolean> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (Boolean value : values) array.put(Boolean.TRUE.equals(value));
        return array;
    }

    private static JSONObject variationMapToJson(Map<Integer, List<ManualVariation>> source)
            throws Exception {
        JSONObject root = new JSONObject();
        if (source == null) return root;
        for (Map.Entry<Integer, List<ManualVariation>> item : source.entrySet()) {
            if (item.getKey() == null || item.getValue() == null || item.getValue().isEmpty()) continue;
            JSONArray branches = new JSONArray();
            for (ManualVariation variation : item.getValue()) {
                JSONObject branch = manualVariationToJson(variation);
                if (branch != null) branches.put(branch);
            }
            if (branches.length() > 0) root.put(String.valueOf(item.getKey()), branches);
        }
        return root;
    }

    private static JSONObject manualVariationToJson(ManualVariation variation) throws Exception {
        if (variation == null || variation.engineSteps.isEmpty()) return null;
        JSONObject branch = new JSONObject();
        branch.put("label", variation.label == null ? "" : variation.label);
        branch.put("dhtmlVariationId", variation.dhtmlVariationId);
        branch.put("moves", stringsToJson(variation.engineSteps));
        branch.put("readable", stringsToJson(variation.readableMoves));
        branch.put("comments", stringsToJson(variation.comments));
        branch.put("scores", integersToJson(variation.scores));
        branch.put("mates", integersToJson(variation.matePlies));
        branch.put("scoreKnown", booleansToJson(variation.scoreKnown));
        branch.put("rescoreRecommended", stringsToJson(variation.rescoreRecommendedMoves));
        branch.put("rescoreScoreKnown", booleansToJson(variation.rescoreScoreKnown));
        branch.put("variations", variationMapToJson(variation.variations));
        branch.put("activeBranchLabels", activeBranchLabelsToJson(variation.activeBranchLabels));
        return branch;
    }

    private static JSONObject activeBranchLabelsToJson(Map<Integer, String> source)
            throws Exception {
        JSONObject root = new JSONObject();
        if (source == null) return root;
        for (Map.Entry<Integer, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().length() == 0) {
                continue;
            }
            root.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return root;
    }

    private static void jsonToStrings(JSONArray array, List<String> out, boolean normalizeMoves) {
        if (array == null || out == null) return;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (normalizeMoves) value = normalizeStep(value);
            if (!normalizeMoves || value.length() >= 4) out.add(value);
        }
    }

    private static void jsonToMoveHints(JSONArray array, List<String> out) {
        if (array == null || out == null) return;
        for (int i = 0; i < array.length(); i++) {
            String value = normalizeStep(array.optString(i, ""));
            out.add(value.length() >= 4 ? value : "");
        }
    }

    private static void jsonToIntegers(JSONArray array, List<Integer> out) {
        if (array == null || out == null) return;
        for (int i = 0; i < array.length(); i++) out.add(array.optInt(i, 0));
    }

    private static void jsonToBooleans(JSONArray array, List<Boolean> out) {
        if (array == null || out == null) return;
        for (int i = 0; i < array.length(); i++) out.add(array.optBoolean(i, false));
    }

    private static void readVariations(JSONObject root,
                                       Map<Integer, List<ManualVariation>> destination) {
        if (root == null || destination == null) return;
        java.util.Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            int node;
            try { node = Integer.parseInt(key); }
            catch (Exception ignored) { continue; }
            JSONArray branches = root.optJSONArray(key);
            if (branches == null) continue;
            ArrayList<ManualVariation> parsed = new ArrayList<ManualVariation>();
            for (int i = 0; i < branches.length(); i++) {
                ManualVariation variation = readManualVariation(branches.optJSONObject(i));
                if (variation != null) parsed.add(variation);
            }
            if (!parsed.isEmpty()) destination.put(node, parsed);
        }
    }

    private static ManualVariation readManualVariation(JSONObject branch) {
        if (branch == null) return null;
        ManualVariation variation = new ManualVariation();
        variation.label = branch.optString("label", "");
        variation.dhtmlVariationId = branch.optInt("dhtmlVariationId", 0);
        jsonToStrings(branch.optJSONArray("moves"), variation.engineSteps, true);
        jsonToStrings(branch.optJSONArray("readable"), variation.readableMoves, false);
        jsonToStrings(branch.optJSONArray("comments"), variation.comments, false);
        jsonToIntegers(branch.optJSONArray("scores"), variation.scores);
        jsonToIntegers(branch.optJSONArray("mates"), variation.matePlies);
        jsonToBooleans(branch.optJSONArray("scoreKnown"), variation.scoreKnown);
        jsonToMoveHints(branch.optJSONArray("rescoreRecommended"), variation.rescoreRecommendedMoves);
        jsonToBooleans(branch.optJSONArray("rescoreScoreKnown"), variation.rescoreScoreKnown);
        while (variation.readableMoves.size() < variation.engineSteps.size()) {
            variation.readableMoves.add(variation.engineSteps.get(variation.readableMoves.size()));
        }
        while (variation.comments.size() < variation.engineSteps.size()) variation.comments.add("");
        while (variation.scores.size() < variation.engineSteps.size()) variation.scores.add(0);
        while (variation.matePlies.size() < variation.engineSteps.size()) variation.matePlies.add(0);
        while (variation.scoreKnown.size() < variation.engineSteps.size()) variation.scoreKnown.add(false);
        while (variation.rescoreRecommendedMoves.size() < variation.engineSteps.size()) {
            variation.rescoreRecommendedMoves.add("");
        }
        while (variation.rescoreScoreKnown.size() < variation.engineSteps.size()) {
            variation.rescoreScoreKnown.add(false);
        }
        readVariations(branch.optJSONObject("variations"), variation.variations);
        readActiveBranchLabels(branch.optJSONObject("activeBranchLabels"),
                variation.activeBranchLabels);
        return variation.engineSteps.isEmpty() ? null : variation;
    }

    private static void readActiveBranchLabels(JSONObject root, Map<Integer, String> destination) {
        if (root == null || destination == null) return;
        java.util.Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int node = Integer.parseInt(key);
                String label = root.optString(key, "");
                if (label.length() > 0) destination.put(node, label);
            } catch (Exception ignored) {}
        }
    }

    private static String normalizeStep(String move) {
        if (move == null) return "";
        String value = move.trim().toLowerCase(java.util.Locale.ROOT);
        return value.length() >= 4 ? value.substring(0, 4) : "";
    }

    private static String normalizeFen(String fen) {
        if (fen == null || fen.trim().length() == 0) return "";
        String value = fen.trim();
        if (value.toLowerCase(java.util.Locale.ROOT).startsWith("fen ")) value = value.substring(4).trim();
        String[] parts = value.split("\\s+");
        String side = parts.length >= 2 ? parts[1].toLowerCase(java.util.Locale.ROOT) : "w";
        if ("r".equals(side)) side = "w";
        if (!"w".equals(side) && !"b".equals(side)) side = "w";
        if (parts.length >= 6) return parts[0] + " " + side + " " + parts[2] + " "
                + parts[3] + " " + parts[4] + " " + parts[5];
        if (parts.length >= 2) return parts[0] + " " + side + " - - 0 1";
        return parts[0] + " w - - 0 1";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

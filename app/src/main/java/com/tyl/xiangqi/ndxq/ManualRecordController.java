package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.DhtmlXqManualUtils;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.PgnManualUtils;
import com.tyl.xiangqi.ndxq.core.PgnTextEscapes;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.core.XqfManualUtils;
import com.tyl.xiangqi.ndxq.storage.ManualFileIo;
import com.tyl.xiangqi.ndxq.ui.ManualStoreDialog;

import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** 棋谱文件、外部打开以及棋盘剪贴板操作。 */
final class ManualRecordController {
    private static final String TTXQ_API = "https://www.zbtool.store:8779/get-qipu";
    private static final String TTXQ_SECRET = "jiedian_xiangqi_secret_2026_74e8c2a91bd563f0c48a7e15d9b326ac";
    private final MainActivity host;

    ManualRecordController(MainActivity host) {
        this.host = host;
    }

    void openManualFilePicker() {
        try {
            host.startActivityForResult(ManualFileIo.createOpenIntent(), MainActivity.REQ_OPEN_MANUAL);
        } catch (Exception e) {
            Toast.makeText(host, "无法打开系统文件选择器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void showStoreManualDialog() {
        ManualStoreDialog.show(host, host.pendingStoreFormat, format -> {
            host.pendingStoreFormat = host.clamp(format, MainActivity.STORE_FORMAT_XQF,
                    MainActivity.STORE_FORMAT_DHTML_UBB);
            host.saveLauncherPreferences();
            openStoreManualFilePicker(host.pendingStoreFormat);
        });
    }

    private void openStoreManualFilePicker(int format) {
        try {
            String title = "node_chess_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
            host.startActivityForResult(ManualFileIo.createSaveIntent(format, title), MainActivity.REQ_STORE_MANUAL);
        } catch (Exception e) {
            Toast.makeText(host, "无法打开存储路径选择器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    byte[] buildStoredManualBytes(int format) {
        host.ensureCommentSize(host.engineMoves.size());
        if (format == MainActivity.STORE_FORMAT_PGN) {
            // 鲨鱼象棋按中文 Windows ANSI（CP936/GBK）读取 PGN；UTF-8 会导致中文标签和注释乱码。
            return exportSharkLeagueManualText().getBytes(java.nio.charset.Charset.forName("GBK"));
        }
        if (format == MainActivity.STORE_FORMAT_DHTML_UBB) {
            return DhtmlXqManualUtils.export(host.baseFen, host.engineMoves, host.moveComments,
                    host.initialComment, exportVariationsForXqf(), host.gameResultTag)
                    .getBytes(java.nio.charset.Charset.forName("GBK"));
        }
        return XqfManualUtils.exportSimpleXqf(host.baseFen, host.engineMoves, host.moveComments,
                host.initialComment, exportVariationsForXqf(), exportActiveBranchLabelsForXqf(),
                host.gameResultTag);
    }

    private Map<Integer, List<PgnManualUtils.VariationLine>> exportVariationsForXqf() {
        Map<Integer, List<PgnManualUtils.VariationLine>> out =
                new HashMap<Integer, List<PgnManualUtils.VariationLine>>();
        for (Map.Entry<Integer, List<ManualVariation>> entry : host.manualVariations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            ArrayList<PgnManualUtils.VariationLine> lines =
                    new ArrayList<PgnManualUtils.VariationLine>();
            for (ManualVariation src : entry.getValue()) {
                PgnManualUtils.VariationLine line = toPgnVariation(src);
                if (line != null && !line.engineSteps.isEmpty()) lines.add(line);
            }
            if (!lines.isEmpty()) out.put(entry.getKey(), lines);
        }
        return out;
    }

    private Map<Integer, String> exportActiveBranchLabelsForXqf() {
        return new HashMap<Integer, String>(host.activeBranchLabels);
    }

    private PgnManualUtils.VariationLine toPgnVariation(ManualVariation src) {
        if (src == null || src.engineSteps.isEmpty()) return null;
        PgnManualUtils.VariationLine line = new PgnManualUtils.VariationLine();
        line.label = src.label == null ? "" : src.label;
        line.dhtmlVariationId = src.dhtmlVariationId;
        line.engineSteps.addAll(src.engineSteps);
        line.chineseMoves.addAll(src.readableMoves);
        line.comments.addAll(src.comments);
        while (line.chineseMoves.size() < line.engineSteps.size()) {
            line.chineseMoves.add(line.engineSteps.get(line.chineseMoves.size()));
        }
        while (line.comments.size() < line.engineSteps.size()) line.comments.add("");
        for (Map.Entry<Integer, List<ManualVariation>> entry : src.variations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            ArrayList<PgnManualUtils.VariationLine> nested =
                    new ArrayList<PgnManualUtils.VariationLine>();
            for (ManualVariation child : entry.getValue()) {
                PgnManualUtils.VariationLine converted = toPgnVariation(child);
                if (converted != null) nested.add(converted);
            }
            if (!nested.isEmpty()) line.variations.put(entry.getKey(), nested);
        }
        return line;
    }

    private ManualVariation fromPgnVariation(PgnManualUtils.VariationLine src) {
        if (src == null || src.engineSteps.isEmpty()) return null;
        ManualVariation dst = new ManualVariation();
        dst.label = src.label == null ? "" : src.label;
        dst.dhtmlVariationId = src.dhtmlVariationId;
        dst.engineSteps.addAll(src.engineSteps);
        dst.readableMoves.addAll(src.chineseMoves);
        dst.comments.addAll(src.comments);
        while (dst.readableMoves.size() < dst.engineSteps.size()) {
            dst.readableMoves.add(dst.engineSteps.get(dst.readableMoves.size()));
        }
        while (dst.comments.size() < dst.engineSteps.size()) dst.comments.add("");
        while (dst.scores.size() < dst.engineSteps.size()) dst.scores.add(0);
        while (dst.matePlies.size() < dst.engineSteps.size()) dst.matePlies.add(0);
        while (dst.scoreKnown.size() < dst.engineSteps.size()) dst.scoreKnown.add(false);
        while (dst.rescoreRecommendedMoves.size() < dst.engineSteps.size()) dst.rescoreRecommendedMoves.add("");
        while (dst.rescoreScoreKnown.size() < dst.engineSteps.size()) dst.rescoreScoreKnown.add(false);
        for (Map.Entry<Integer, List<PgnManualUtils.VariationLine>> entry : src.variations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            ArrayList<ManualVariation> nested = new ArrayList<ManualVariation>();
            for (PgnManualUtils.VariationLine child : entry.getValue()) {
                ManualVariation converted = fromPgnVariation(child);
                if (converted != null) nested.add(converted);
            }
            if (!nested.isEmpty()) {
                dst.variations.put(entry.getKey(), nested);
                dst.activeBranchLabels.put(entry.getKey(), "A");
                ensureNestedBranchLabels(dst, entry.getKey());
            }
        }
        return dst;
    }

    void ensureNestedBranchLabels(ManualVariation owner, int relativeNode) {
        if (owner == null) return;
        List<ManualVariation> vars = owner.variations.get(relativeNode);
        String active = owner.activeBranchLabels.get(relativeNode);
        if (active == null || active.length() == 0) active = "A";
        TreeSet<String> used = new TreeSet<String>();
        used.add(active);
        if (vars != null) {
            for (ManualVariation variation : vars) {
                if (variation == null) continue;
                String label = variation.label;
                if (label == null || label.length() == 0 || used.contains(label)) {
                    int ordinal = 0;
                    do { label = host.branchLabelForOrdinal(ordinal++); }
                    while (used.contains(label));
                    variation.label = label;
                }
                used.add(variation.label);
            }
            Collections.sort(vars, (a, b) -> Integer.compare(
                    host.branchLabelOrdinal(a == null ? null : a.label),
                    host.branchLabelOrdinal(b == null ? null : b.label)));
        }
        if (vars != null && !vars.isEmpty()) owner.activeBranchLabels.put(relativeNode, active);
    }

    private void importManualVariations(PgnManualUtils.ParsedManual manual) {
        if (manual == null || manual.variations == null) return;
        for (Map.Entry<Integer, List<PgnManualUtils.VariationLine>> entry
                : manual.variations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            ArrayList<ManualVariation> branches = new ArrayList<ManualVariation>();
            for (PgnManualUtils.VariationLine src : entry.getValue()) {
                ManualVariation dst = fromPgnVariation(src);
                if (dst != null && !dst.engineSteps.isEmpty()) branches.add(dst);
            }
            if (!branches.isEmpty()) {
                host.manualVariations.put(entry.getKey(), branches);
                String active = manual.activeBranchLabels.get(entry.getKey());
                host.activeBranchLabels.put(entry.getKey(), active == null || active.length() == 0 ? "A" : active);
                host.ensureBranchLabels(entry.getKey());
            }
        }
    }

    void loadManualFromUri(Uri uri) {
        if (uri == null) return;
        try {
            // 先完整读取并解析，确认文件有效后再切换页面，避免错误文件清空当前棋局。
            byte[] bytes = ManualFileIo.readBytes(host.getContentResolver(), uri);
            PgnManualUtils.ParsedManual manual;
            String format;
            if (XqfManualUtils.looksLikeXqf(bytes)) {
                manual = XqfManualUtils.parse(bytes, MainActivity.START_FEN);
                format = "XQF";
            } else {
                String text = ManualFileIo.decodeText(bytes);
                if (DhtmlXqManualUtils.looksLikeDhtmlXq(text)) {
                    manual = DhtmlXqManualUtils.parse(text, MainActivity.START_FEN);
                    format = "东萍 UBB";
                } else {
                    manual = PgnManualUtils.parse(text, MainActivity.START_FEN);
                    format = "PGN";
                }
            }
            openParsedExternalManual(manual, format);
        } catch (Exception e) {
            Toast.makeText(host, "打开棋谱失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            host.appendLog("打开棋谱失败：" + e.getMessage() + "。\n");
        }
    }

    private void openParsedExternalManual(PgnManualUtils.ParsedManual manual, String format) {
        if (manual == null || (manual.moves.isEmpty() && !manual.hasExplicitFen)) {
            throw new IllegalArgumentException("未从文件中解析到有效局面或着法");
        }
        if (!host.gameScreenVisible || host.boardView == null || !host.selfAnalysisMode) host.startSelfAnalysisSession();
        loadParsedManual(manual);
        Toast.makeText(host, "已打开 " + format + " 棋谱，共 " + manual.moves.size() + " 手", Toast.LENGTH_SHORT).show();
        host.appendLog("已打开外部 " + format + " 棋谱，共 " + manual.moves.size() + " 手。\n");
    }

    void loadParsedManual(PgnManualUtils.ParsedManual manual) {
        if (manual == null) throw new IllegalArgumentException("棋谱内容为空");
        host.stopSearchForPositionChange();
        host.invalidateSituationScoreRequests();
        host.baseFen = host.normalizeFen(manual.fen);
        XiangqiRules.fromFen(host.baseFen);
        host.boardView.setBoardFromFen(host.baseFen);
        host.engineMoves.clear();
        host.readableMoves.clear();
        host.initialComment = sanitizeManualComment(manual.initialComment);
        host.manualMetadata.copyFromParsed(manual);
        host.moveComments.clear();
        host.redPerspectiveScores.clear();
        host.scoreMatePlies.clear();
        host.scoreKnown.clear();
        host.rescoreRecommendedMoves.clear();
        host.rescoreScoreKnown.clear();
        host.initialRescoreScoreKnown = false;
        host.initialScoreRed = 0;
        host.initialMatePly = 0;
        host.initialScoreKnown = false;
        host.manualVariations.clear();
        host.activeBranchLabels.clear();
        for (int i = 0; i < manual.moves.size(); i++) {
            Move move = manual.moves.get(i);
            String step = host.normalizeStep(move.toEngineStep());
            if (step.length() < 4 || !host.boardView.playMoveSilently(move)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 手非法：" + move.toEngineStep());
            }
            host.engineMoves.add(step);
            String readable = i < manual.chineseMoves.size() ? manual.chineseMoves.get(i) : "";
            host.readableMoves.add(readable == null || readable.trim().length() == 0 ? step : readable.trim());
            String comment = i < manual.comments.size() ? manual.comments.get(i) : "";
            host.moveComments.add(sanitizeManualComment(comment));
            host.redPerspectiveScores.add(0);
            host.scoreMatePlies.add(0);
            host.scoreKnown.add(false);
            host.rescoreRecommendedMoves.add("");
            host.rescoreScoreKnown.add(false);
        }
        importManualVariations(manual);
        host.currentPly = host.engineMoves.size();
        host.gameResultTag = manual.result == 0 ? "1-0"
                : (manual.result == 1 ? "0-1" : (manual.result == 2 ? "1/2-1/2" : "*"));
        host.selfAnalysisMode = true;
        host.competitiveResultEligible = false;
        host.completedDuelGame = false;
        host.postGameSandboxActive = false;
        host.gameOver = false;
        host.terminalDialogShown = false;
        host.resultRecordedForCurrentGame = false;
        host.resetAnalysisPositionState();
        host.gameEngine.notifyNewGame();
        host.manualEngine.notifyNewGame();
        host.rescoreEngine.notifyNewGame();
        host.situationEngine.notifyNewGame();
        host.updateModeToggleButton();
        host.updatePlayerLabels();
        host.refreshBoardInputState();
        host.updateGameContent();
        host.persistCurrentSession();
        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
    }

    private String sanitizeManualComment(String comment) {
        String value = comment == null ? "" : comment.trim();
        value = value.replaceFirst("^#1,1#\\s*", "");
        value = value.replaceFirst("^#0,0,0#\\s*", "");
        return value.trim();
    }

    void handleIncomingManualIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri;
        if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                ClipData clipData = intent.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    uri = clipData.getItemAt(0).getUri();
                }
            }
            intent.removeExtra(Intent.EXTRA_STREAM);
            intent.setClipData(null);
        } else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            uri = intent.getData();
            intent.setData(null);
        } else {
            return;
        }
        if (uri == null) return;
        loadManualFromUri(uri);
    }

    void showBoardClipboardDialog() {
        new AlertDialog.Builder(host)
                .setTitle("棋盘操作")
                .setItems(new String[]{"复制局面", "复制棋谱", "粘贴", "粘贴（天天象棋URL）", "加入错题本"}, (dialog, which) -> {
                    if (which == 0) copyFen();
                    else if (which == 1) copyManual();
                    else if (which == 2) pasteFromClipboard();
                    else if (which == 3) pasteTencentUrlFromClipboard();
                    else host.addCurrentPositionToCorrectionBook();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 从剪贴板读取天天象棋分享 URL，调用接口后直接载入为可分析棋谱。 */
    private void pasteTencentUrlFromClipboard() {
        ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(host, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(host);
        final String shareUrl = value == null ? "" : value.toString().trim();
        if (!isTencentQipuUrl(shareUrl)) {
            Toast.makeText(host, "未识别到包含 QipuId 的天天象棋链接", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(host, "正在获取天天象棋棋谱…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String body = httpPostTencent(shareUrl);
                String ubb = convertTencentResponseToUbb(body);
                PgnManualUtils.ParsedManual manual = DhtmlXqManualUtils.parse(ubb, MainActivity.START_FEN);
                if (manual.moves.isEmpty() && !manual.hasExplicitFen) throw new Exception("接口未返回有效着法");
                host.handler.post(() -> {
                    try {
                        if (!host.gameScreenVisible || host.boardView == null || !host.selfAnalysisMode) {
                            host.startSelfAnalysisSession();
                        }
                        loadParsedManual(manual);
                        Toast.makeText(host, "天天象棋棋谱已载入，共 " + manual.moves.size() + " 手", Toast.LENGTH_SHORT).show();
                        host.appendLog("已通过天天象棋 URL 载入棋谱，共 " + manual.moves.size() + " 手。\n");
                    } catch (Exception e) {
                        showRecognitionError(e.getMessage());
                    }
                });
            } catch (Exception e) {
                host.handler.post(() -> showRecognitionError("天天象棋接口调用失败：" + e.getMessage()));
            }
        }, "ttxq-url-loader").start();
    }

    /** 天天象棋分享链接可能经过 URL 编码，识别时兼容大小写和嵌套编码。 */
    private boolean isTencentQipuUrl(String value) {
        if (value == null || !value.matches("(?is).*https?://.*")) return false;
        String probe = value;
        for (int i = 0; i < 3; i++) {
            if (probe.matches("(?is).*qipuid\\s*=.*")) return true;
            try {
                String decoded = java.net.URLDecoder.decode(probe, StandardCharsets.UTF_8.name());
                if (decoded.equals(probe)) break;
                probe = decoded;
            } catch (Exception ignored) {
                break;
            }
        }
        return probe.matches("(?is).*qipuid\\s*=.*");
    }

    private String httpPostTencent(String shareUrl) throws Exception {
        URL url = new URL(TTXQ_API);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = java.util.UUID.randomUUID().toString();
        String path = url.getPath();
        String signText = timestamp + "\n" + nonce + "\nPOST\n" + path + "\n" + shareUrl;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TTXQ_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(signText.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        conn.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
        conn.setRequestProperty("X-Signature", hex.toString());
        conn.setRequestProperty("X-Timestamp", timestamp);
        conn.setRequestProperty("X-Nonce", nonce);
        try (java.io.OutputStream out = conn.getOutputStream()) { out.write(shareUrl.getBytes(StandardCharsets.UTF_8)); }
        int code = conn.getResponseCode();
        java.io.InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) throw new Exception("HTTP " + code);
        byte[] bytes = readAll(stream);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + "：" + new String(bytes, StandardCharsets.UTF_8));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readAll(java.io.InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) >= 0) { if (n > 0) out.write(buf, 0, n); }
        return out.toByteArray();
    }

    private String convertTencentResponseToUbb(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONObject data = root.optJSONObject("data");
        if (data == null) data = root;
        String movelist = data.optString("movelist", "");
        if (movelist.length() == 0) throw new Exception("接口返回数据中没有 movelist");
        String title = data.optString("title", "天天象棋棋谱").replace("\r", "").replace("\n", "||");
        String binit = data.optString("binit", "");
        // 天天象棋普通开局棋谱可能不返回 binit。东萍解析器要求该标签始终为
        // 32 个棋子的 64 位坐标，因此缺失时必须补标准初始局面，不能写空标签。
        if (!binit.matches("\\d{64}")) {
            binit = DhtmlXqManualUtils.binitFromFen(MainActivity.START_FEN);
        }
        StringBuilder out = new StringBuilder("[DhtmlXQ]\r\n[DhtmlXQ_ver]www_dpxq_com[/DhtmlXQ_ver]\r\n[DhtmlXQ_init]500,350[/DhtmlXQ_init]\r\n");
        out.append("[DhtmlXQ_title]").append(title).append("[/DhtmlXQ_title]\r\n[DhtmlXQ_binit]").append(binit).append("[/DhtmlXQ_binit]\r\n[DhtmlXQ_movelist]").append(movelist).append("[/DhtmlXQ_movelist]\r\n");
        JSONObject movetag = data.optJSONObject("movetag");
        BranchConversion branches = normalizeBranches(movetag);
        if (movetag != null) {
            for (Branch branch : branches.normalized) {
                String id = branch.parentId + "_" + branch.absoluteStep + "_" + branch.branchId;
                out.append("[DhtmlXQ_move_").append(id).append("]")
                        .append(escapeUbb(branch.moves)).append("[/DhtmlXQ_move_").append(id).append("]\r\n");
            }
        }
        JSONObject comments = data.optJSONObject("commentv2");
        if (comments != null) {
            Iterator<String> commentKeys = comments.keys();
            while (commentKeys.hasNext()) {
                String key = commentKeys.next();
                String text = getCommentText(comments.opt(key));
                if (text.length() == 0) continue;
                String id = key;
                String[] parts = key.split("-");
                if (parts.length == 2 && movetag != null) {
                    Integer offset = branches.offsets.get(parts[0]);
                    if (offset == null) continue;
                    try { id = parts[0] + "_" + (offset + Integer.parseInt(parts[1])); }
                    catch (NumberFormatException ignored) { continue; }
                }
                if (id.matches("\\d+(?:_\\d+)?")) {
                    out.append("[DhtmlXQ_comment").append(id).append("]")
                            .append(text).append("[/DhtmlXQ_comment").append(id).append("]\r\n");
                }
            }
        }
        return out.append("[DhtmlXQ_type]实战全局/开局[/DhtmlXQ_type]\r\n[DhtmlXQ_generator]www.zbtool.store[/DhtmlXQ_generator]\r\n[/DhtmlXQ]").toString();
    }

    private static String escapeUbb(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "||");
    }

    private static String getCommentText(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String item = getCommentText(array.opt(i));
                if (item.length() == 0) continue;
                if (out.length() > 0) out.append("||");
                out.append(item);
            }
            return out.toString();
        }
        if (value instanceof JSONObject) return escapeUbb(((JSONObject) value).optString("msg", ""));
        return escapeUbb(value == null ? "" : String.valueOf(value));
    }

    private static final class Branch {
        String parentId;
        String branchId;
        int absoluteStep;
        String moves;
    }

    private static final class BranchConversion {
        final Map<String, Integer> offsets = new HashMap<String, Integer>();
        final List<Branch> normalized = new ArrayList<Branch>();
    }

    private static BranchConversion normalizeBranches(JSONObject movetag) {
        BranchConversion result = new BranchConversion();
        result.offsets.put("0", 0);
        if (movetag == null) return result;
        List<String> pending = new ArrayList<String>();
        Iterator<String> moveKeys = movetag.keys();
        while (moveKeys.hasNext()) pending.add(moveKeys.next());
        while (!pending.isEmpty()) {
            boolean resolved = false;
            for (int i = 0; i < pending.size();) {
                String key = pending.get(i);
                String[] parts = key.split("-");
                if (parts.length != 3 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+") || !parts[2].matches("\\d+")) { pending.remove(i); continue; }
                Integer offset = result.offsets.get(parts[0]);
                String moves = movetag.optString(key, "");
                if (offset == null) { i++; continue; }
                if (moves.length() == 0) { pending.remove(i); continue; }
                Branch branch = new Branch();
                branch.parentId = parts[0]; branch.branchId = parts[2]; branch.moves = moves;
                branch.absoluteStep = offset + Integer.parseInt(parts[1]);
                result.offsets.put(branch.branchId, branch.absoluteStep - 1);
                result.normalized.add(branch);
                pending.remove(i); resolved = true;
            }
            if (!resolved) break;
        }
        return result;
    }

    private void copyFen() {
        if (host.boardView == null) return;
        host.copyToClipboard("局面 FEN", host.boardView.getFen());
        host.appendLog("已复制局面 FEN。\n");
    }

    private void copyManual() {
        host.copyToClipboard("棋谱", exportSharkLeagueManualText());
        host.appendLog("已复制棋谱。\n");
    }

    private String exportSharkLeagueManualText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Game \"Chinese Chess\"]\n");
        sb.append("[Event \"").append(pgnTagValue(host.manualMetadata.event, "棋谱导出")).append("\"]\n");
        sb.append("[Site \"").append(pgnTagValue(host.manualMetadata.site, "节点象棋")).append("\"]\n");
        sb.append("[Date \"").append(pgnTagValue(host.manualMetadata.date,
                new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(new Date()))).append("\"]\n");
        sb.append("[Round \"").append(pgnTagValue(host.manualMetadata.round, "1")).append("\"]\n");
        sb.append("[Red \"").append(pgnTagValue(host.manualMetadata.red,
                host.enginePlaysRed ? "电脑" : "玩家")).append("\"]\n");
        sb.append("[Black \"").append(pgnTagValue(host.manualMetadata.black,
                host.enginePlaysRed ? "玩家" : "电脑")).append("\"]\n");
        String result = normalizePgnResult(host.gameResultTag);
        sb.append("[Result \"").append(result).append("\"]\n");
        sb.append("[FEN \"").append(host.baseFen).append("\"]\n");
        String rootComment = host.initialComment == null ? "" : host.initialComment.replace('}', '）').trim();
        if (rootComment.length() > 0) sb.append("{").append(rootComment).append("}\n");
        host.ensureCommentSize(host.readableMoves.size());
        for (int i = 0; i < host.readableMoves.size(); i++) {
            if (i % 2 == 0) sb.append("  ").append(i / 2 + 1).append(". ");
            else sb.append("     ");
            String comment = i < host.moveComments.size() ? host.moveComments.get(i) : "";
            comment = comment == null ? "" : comment.replace('}', '）').trim();
            sb.append(host.readableMoves.get(i));
            if (comment.length() > 0) sb.append(" {").append(comment).append("}");
            sb.append('\n');
        }
        sb.append(result).append('\n');
        return sb.toString();
    }

    private String pgnTagValue(String value, String fallback) {
        String resolved = value == null ? "" : value.trim();
        if (resolved.length() == 0) resolved = fallback == null ? "" : fallback;
        return PgnTextEscapes.encodeTagValue(resolved);
    }

    private String normalizePgnResult(String value) {
        String result = value == null ? "" : value.trim();
        return "1-0".equals(result) || "0-1".equals(result) || "1/2-1/2".equals(result)
                || "*".equals(result) ? result : "*";
    }

    private boolean isStandaloneFenOrMoves(String text) {
        if (text == null) return false;
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.toLowerCase(Locale.ROOT).startsWith("position fen ")) {
            t = t.substring(13).trim();
        }
        if (t.length() == 0 || t.startsWith("[") || t.contains("\n[")
                || t.matches("(?s).*\\b\\d+\\.\\s+.*") || t.contains("{#")) return false;
        int movesAt = indexOfMovesKeyword(t);
        String head = movesAt >= 0 ? t.substring(0, movesAt).trim() : t;
        String[] fields = head.split("\\s+");
        return fields.length > 0 && fields[0].split("/").length == 10;
    }

    private int indexOfMovesKeyword(String text) {
        if (text == null) return -1;
        String lower = text.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(" moves ");
        if (index >= 0) return index;
        if (lower.startsWith("moves ")) return 0;
        return -1;
    }

    private String normalizePastedCoordinateStep(String raw, int moveNumber) {
        String step = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("-", "");
        if (!step.matches("^[a-i][0-9][a-i][0-9]$")) {
            throw new IllegalArgumentException("第 " + moveNumber + " 手坐标格式无效：" + raw);
        }
        return step;
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(host, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(host);
        if (value == null) return;
        String text = value.toString().trim();
        try {
            if (isStandaloneFenOrMoves(text)) {
                String normalizedText = text.trim().replaceAll("\\s+", " ");
                if (normalizedText.toLowerCase(Locale.ROOT).startsWith("position fen ")) {
                    normalizedText = normalizedText.substring(13).trim();
                }
                int movesAt = indexOfMovesKeyword(normalizedText);
                String fenText = movesAt >= 0
                        ? normalizedText.substring(0, movesAt).trim() : normalizedText;
                String movesText = movesAt >= 0
                        ? normalizedText.substring(movesAt + 7).trim() : "";
                String[] rawSteps = movesText.length() == 0 ? new String[0] : movesText.split("\\s+");
                String[] strictSteps = new String[rawSteps.length];
                for (int i = 0; i < rawSteps.length; i++) {
                    strictSteps[i] = normalizePastedCoordinateStep(rawSteps[i], i + 1);
                }
                String normalizedFen = host.normalizeFen(fenText);
                validatePastedPosition(normalizedFen, strictSteps);
                host.stopSearchForPositionChange();
                loadPastedPosition(normalizedFen, strictSteps, null, null);
                host.appendLog("已从剪贴板粘贴 FEN / moves。\n");
            } else {
                PgnManualUtils.ParsedManual manual = PgnManualUtils.parse(text, MainActivity.START_FEN);
                if (manual.moves.isEmpty() && !manual.hasExplicitFen) {
                    showRecognitionError();
                    return;
                }
                String[] steps = new String[manual.moves.size()];
                for (int i = 0; i < manual.moves.size(); i++) {
                    steps[i] = manual.moves.get(i).toEngineStep();
                }
                String normalizedFen = host.normalizeFen(manual.fen);
                validatePastedPosition(normalizedFen, steps);
                host.stopSearchForPositionChange();
                loadPastedPosition(normalizedFen, steps, manual.chineseMoves, manual.comments);
                host.initialComment = sanitizeManualComment(manual.initialComment);
                host.manualMetadata.copyFromParsed(manual);
                importManualVariations(manual);
                if (manual.result == 0) host.gameResultTag = "1-0";
                else if (manual.result == 1) host.gameResultTag = "0-1";
                else if (manual.result == 2) host.gameResultTag = "1/2-1/2";
                host.appendLog("已从剪贴板解析文字棋谱/PGN，共 " + steps.length + " 手。\n");
            }
            host.markCurrentLineAsAnalysisOnly("已粘贴外部局面或棋谱");
            host.gameEngine.notifyNewGame();
            host.manualEngine.notifyNewGame();
            host.rescoreEngine.notifyNewGame();
            host.situationEngine.notifyNewGame();
            host.updatePlayerLabels();
            host.updateGameContent();
            host.sixtyMoveDrawArmedPly = host.computeNoCaptureMoveCountAtPly(host.currentPly) == 119
                    ? host.currentPly + 1 : -1;
            host.persistCurrentSession();
            if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
            host.appendLog("粘贴完成后不会自动续走，确保棋谱与剪贴板内容保持一致。\n");
        } catch (Exception e) {
            showRecognitionError(e.getMessage());
            host.appendLog("粘贴识别失败：" + e.getMessage() + "。\n");
        }
    }

    private void showRecognitionError() {
        showRecognitionError(null);
    }

    private void showRecognitionError(String detail) {
        String message = "识别错误！棋谱未作任何修改。";
        if (detail != null && detail.trim().length() > 0) {
            message += "\n\n" + detail.trim();
        }
        new AlertDialog.Builder(host)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void validatePastedPosition(String fen, String[] steps) {
        char[][] board = XiangqiRules.fromFen(fen);
        boolean redToMove = XiangqiRules.redToMoveFromFen(fen);
        if (steps == null) return;
        for (int i = 0; i < steps.length; i++) {
            String step = normalizePastedCoordinateStep(steps[i], i + 1);
            Move move = Move.fromEngineStep(step);
            char piece = board[move.fromRow][move.fromCol];
            if (!XiangqiRules.isPiece(piece) || XiangqiRules.isRed(piece) != redToMove) {
                throw new IllegalArgumentException("第 " + (i + 1)
                        + " 手行棋方不一致：" + step);
            }
            if (!XiangqiRules.isLegalMove(board, move)) {
                throw new IllegalArgumentException("第 " + (i + 1)
                        + " 手为非法着法：" + step);
            }
            board[move.toRow][move.toCol] = piece;
            board[move.fromRow][move.fromCol] = ' ';
            redToMove = !redToMove;
        }
    }

    private void loadPastedPosition(String fen, String[] steps, List<String> knownChinese,
                                    List<String> knownComments) {
        host.invalidateSituationScoreRequests();
        XiangqiRules.fromFen(fen);
        host.baseFen = fen;
        host.boardView.setBoardFromFen(host.baseFen);
        host.engineMoves.clear();
        host.readableMoves.clear();
        host.initialComment = "";
        host.manualMetadata.clear();
        host.moveComments.clear();
        host.redPerspectiveScores.clear();
        host.scoreMatePlies.clear();
        host.scoreKnown.clear();
        host.rescoreRecommendedMoves.clear();
        host.rescoreScoreKnown.clear();
        host.initialRescoreScoreKnown = false;
        host.initialScoreRed = 0;
        host.initialMatePly = 0;
        host.initialScoreKnown = false;
        host.manualVariations.clear();
        host.activeBranchLabels.clear();
        host.gameResultTag = "*";
        if (steps != null) {
            for (int i = 0; i < steps.length; i++) {
                String step = normalizePastedCoordinateStep(steps[i], i + 1);
                String readable = knownChinese != null && i < knownChinese.size()
                        ? knownChinese.get(i) : ChineseNotation.translate(host.boardView.copyBoard(), step, false);
                if (!host.boardView.playMoveSilently(Move.fromEngineStep(step))) {
                    throw new IllegalArgumentException("非法着法：" + step);
                }
                host.engineMoves.add(step);
                host.readableMoves.add(readable == null || readable.trim().length() == 0
                        ? step : readable.trim());
                String comment = knownComments != null && i < knownComments.size()
                        ? knownComments.get(i) : "";
                host.moveComments.add(sanitizeManualComment(comment));
                host.redPerspectiveScores.add(0);
                host.scoreMatePlies.add(0);
                host.scoreKnown.add(false);
                host.rescoreRecommendedMoves.add("");
                host.rescoreScoreKnown.add(false);
            }
        }
        host.currentPly = host.engineMoves.size();
        host.gameOver = false;
        host.completedDuelGame = false;
        host.postGameSandboxActive = false;
        host.terminalDialogShown = false;
        host.resultRecordedForCurrentGame = false;
        host.resetAnalysisPositionState();
    }
}

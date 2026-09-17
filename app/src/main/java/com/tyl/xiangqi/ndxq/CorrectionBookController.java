package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.PgnManualUtils;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** 错题本文件操作、列表页面和错题推演页面控制器。 */
final class CorrectionBookController {
    private final MainActivity host;

    CorrectionBookController(MainActivity host) {
        this.host = host;
    }

    void addCurrentPositionToCorrectionBook() {
        if (host.boardView == null || !host.ensureNodeStorageReady(true)) return;
        final EditText input = new EditText(host);
        input.setHint("错题名称");
        input.setSingleLine(true);
        new AlertDialog.Builder(host).setTitle("加入错题本").setView(input)
                .setPositiveButton("确认", (d, w) -> {
                    try {
                        File dir = host.correctionDirectory();
                        if (!dir.isDirectory() && !dir.mkdirs()) {
                            throw new Exception("无法创建目录");
                        }
                        String stamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA)
                                .format(new Date());
                        int nextIndex = host.currentPly;
                        if (nextIndex < 0 || nextIndex >= host.engineMoves.size()) {
                            throw new Exception("当前局面没有可记录的下一步");
                        }
                        String name = input.getText() == null
                                ? "" : input.getText().toString().trim();
                        File out = new File(dir, stamp + ".pgn");
                        String step = host.engineMoves.get(nextIndex);
                        String readable = nextIndex < host.readableMoves.size()
                                ? host.readableMoves.get(nextIndex) : step;
                        StringBuilder body = new StringBuilder();
                        body.append("[Game \"Chinese Chess\"]\n");
                        body.append("[Event \"").append(escapeTag(name.length() > 0 ? name : "错题")).append("\"]\n");
                        body.append("[Site \"节点象棋错题本\"]\n");
                        body.append("[Result \"*\"]\n");
                        body.append("[FEN \"").append(host.normalizeFen(host.boardView.getFen())).append("\"]\n\n");
                        body.append("1. ").append(readable == null || readable.length() == 0 ? step : readable).append("\n*\n");
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            fos.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        Toast.makeText(host, "已加入错题本", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(host, "加入错题本失败：" + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    void openFromSituation() {
        if (host.isRescoring) return;
        host.correctionReturnToGame = true;
        host.correctionReturnReversed = host.boardView != null
                && host.boardView.isReversed();
        host.selectedGameTab = 2;
        if (!host.ensureNodeStorageReady(false)) {
            host.correctionPermissionPending = true;
            host.requestNodeStorageAccess();
            return;
        }
        host.correctionPermissionPending = false;
        showList();
    }

    void returnFromList() {
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.temporaryAnalysisRunning = false;
        host.temporaryAnalysisGeneration++;
        host.correctionListVisible = false;
        host.correctionBoardVisible = false;
        if (!host.correctionReturnToGame) {
            host.showRecentGamesScreen();
            return;
        }
        host.correctionReturnToGame = false;
        host.selectedGameTab = 2;
        host.showGameScreen();
        try {
            host.rebuildBoardToPly(host.currentPly);
            host.boardView.setReversed(host.correctionReturnReversed);
            host.boardView.setShowArrow(host.showEngineArrows);
        } catch (Exception e) {
            host.appendLog("退出错题本后恢复局面失败：" + e.getMessage() + "。\n");
        }
        host.updatePlayerLabels();
        host.refreshBoardInputState();
        host.updateGameContent();
        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
        if (!host.completedDuelGame) host.handler.postDelayed(host::maybeAutoMove, 180L);
    }

    void showList() {
        if (!host.ensureNodeStorageReady(false)) {
            host.correctionPermissionPending = true;
            host.requestNodeStorageAccess();
            return;
        }
        host.correctionPermissionPending = false;
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.temporaryAnalysisRunning = false;
        host.temporaryAnalysisGeneration++;
        host.appRoot.removeAllViews();
        host.recentScreenVisible = false;
        host.correctionListVisible = true;
        host.correctionBoardVisible = false;
        host.gameScreenVisible = false;

        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
        header.setBackgroundColor(Color.rgb(35, 46, 41));
        Button back = host.compactButton("返回");
        back.setOnClickListener(v -> returnFromList());
        header.addView(back, new LinearLayout.LayoutParams(host.dp(64), host.dp(36)));
        TextView title = new TextView(host);
        title.setText("错题本");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, host.dp(36), 1f));
        Button clear = host.compactButton("清空");
        File[] currentFiles = listCorrectionFiles();
        clear.setEnabled(currentFiles.length > 0);
        clear.setOnClickListener(v -> new AlertDialog.Builder(host)
                .setMessage("是否清空所有错题？")
                .setPositiveButton("确定", (d, w) -> {
                    File[] files = listCorrectionFiles();
                    int failed = 0;
                    for (File file : files) if (file != null && !file.delete()) failed++;
                    if (failed > 0) {
                        Toast.makeText(host, "部分错题删除失败", Toast.LENGTH_LONG).show();
                    }
                    showList();
                })
                .setNegativeButton("取消", null)
                .show());
        header.addView(clear, new LinearLayout.LayoutParams(host.dp(64), host.dp(36)));
        page.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(52)));

        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(18));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        host.appRoot.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        File[] files = listCorrectionFiles();
        if (files.length == 0) {
            TextView empty = new TextView(host);
            empty.setText("暂无错题");
            empty.setTextSize(16);
            empty.setTextColor(host.globalBackgroundTextColor());
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(120)));
            return;
        }
        for (File file : files) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Button item = host.largeButton(readCorrectionName(file), true);
            item.setTextSize(13);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setPadding(host.dp(14), 0, host.dp(10), 0);
            // 新旧格式统一走正式分析模式；旧 TXT 仅含 FEN 时由 PGN 解析器作为局面棋谱载入。
            item.setOnClickListener(v -> openCorrectionFile(file));
            row.addView(item, new LinearLayout.LayoutParams(0, host.dp(50), 1f));
            TextView delete = correctionDeleteButton();
            delete.setContentDescription("删除本条错题");
            delete.setOnClickListener(v -> new AlertDialog.Builder(host)
                    .setMessage("是否删除本条错题？")
                    .setPositiveButton("确定", (d, w) -> {
                        if (!file.delete()) {
                            Toast.makeText(host, "删除错题失败", Toast.LENGTH_LONG).show();
                        }
                        showList();
                    })
                    .setNegativeButton("取消", null)
                    .show());
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                    host.dp(30), host.dp(30));
            deleteLp.leftMargin = host.dp(7);
            row.addView(delete, deleteLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(50));
            rowLp.bottomMargin = host.dp(8);
            list.addView(row, rowLp);
        }
    }

    private File[] listCorrectionFiles() {
        File[] files = host.correctionDirectory().listFiles((dir, name) -> {
            if (name == null) return false;
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".pgn") || lower.endsWith(".txt");
        });
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    private TextView correctionDeleteButton() {
        TextView view = new TextView(host);
        view.setText("×");
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        host.setRoundedBackground(view, Color.rgb(145, 149, 146), 6, Color.TRANSPARENT);
        return view;
    }

    private String readCorrectionText(File file) {
        if (file == null) return "";
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readCorrectionName(File file) {
        String text = readCorrectionText(file);
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("[Event \"") && value.endsWith("\"]")) {
                String name = value.substring(8, value.length() - 2).trim();
                if (!name.isEmpty()) return name + " · " + file.getName().replaceFirst("\\.(?i:pgn|txt)$", "");
            }
            if (value.startsWith("#")) {
                String name = value.substring(1).trim();
                if (!name.isEmpty()) {
                    return name + " · " + file.getName().replace(".txt", "");
                }
            }
        }
        return file == null ? "错题" : file.getName().replace(".txt", "");
    }

    private String readCorrectionFen(File file) {
        String text = readCorrectionText(file);
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) {
                return host.normalizeFen(value);
            }
        }
        return MainActivity.START_FEN;
    }

    private static String escapeTag(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    /** 错题条目直接作为一局一步 PGN 进入正式分析模式。 */
    private void openCorrectionFile(File file) {
        try {
            String text = readCorrectionText(file);
            if (file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                String fen = readCorrectionFen(file);
                text = "[Game \"Chinese Chess\"]\n[Event \"旧版错题\"]\n"
                        + "[Site \"节点象棋错题本\"]\n[Result \"*\"]\n[FEN \""
                        + escapeTag(fen) + "\"]\n\n*\n";
            }
            PgnManualUtils.ParsedManual manual = PgnManualUtils.parse(text, MainActivity.START_FEN);
            if (manual.moves.isEmpty() && !manual.hasExplicitFen) throw new Exception("错题棋谱为空");
            if (analysisManualNeedsOverwriteConfirmation()) {
                new AlertDialog.Builder(host)
                        .setTitle("覆盖分析棋谱")
                        .setMessage("分析模式已有棋谱，是否覆盖？")
                        .setPositiveButton("覆盖", (dialog, which) -> loadCorrectionManual(manual))
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                loadCorrectionManual(manual);
            }
        } catch (Exception e) {
            Toast.makeText(host, "打开错题失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean analysisManualNeedsOverwriteConfirmation() {
        if (!host.selfAnalysisMode) return false;
        if (!host.engineMoves.isEmpty()) return true;
        return !host.normalizeFen(host.baseFen).equals(host.normalizeFen(MainActivity.START_FEN));
    }

    private void loadCorrectionManual(PgnManualUtils.ParsedManual manual) {
        try {
            if (!host.gameScreenVisible || host.boardView == null || !host.selfAnalysisMode) {
                host.startSelfAnalysisSession();
            }
            host.loadParsedManualForCorrection(manual);
            host.correctionListVisible = false;
            host.correctionBoardVisible = false;
            Toast.makeText(host, "已进入分析模式", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(host, "打开错题失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void showBoard(String fen) {
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        host.temporaryAnalysisRunning = false;
        host.temporaryAnalysisGeneration++;
        host.correctionListVisible = false;
        host.correctionBoardVisible = true;
        host.recentScreenVisible = false;
        host.gameScreenVisible = false;
        host.appRoot.removeAllViews();

        final ArrayList<String> moves = new ArrayList<String>();
        final String base = host.normalizeFen(fen);
        final int[] ply = {0};

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dp(8), host.dp(6), host.dp(8), host.dp(8));
        root.setBackgroundColor(Color.TRANSPARENT);
        TextView title = new TextView(host);
        title.setText("错题推演");
        title.setTextSize(16);
        title.setTextColor(host.globalBackgroundTextColor());
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));

        final ChessBoardView board = new ChessBoardView(host, false);
        host.applyCurrentSkinToBoard(board, false);
        board.setBoardFromFen(base);
        board.setPieceShadowEnabled(true);
        board.setShowCoordinate(false);
        board.setShowArrow(host.showEngineArrows);
        // 错题推演：默认把当前走棋方放棋盘下方。
        board.setReversed(!board.isRedToMove());
        root.addView(board, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(host);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        final TextView first = host.pushNavButton("|◀", false, null);
        final TextView prev = host.pushNavButton("←", false, null);
        final TextView next = host.pushNavButton("→", false, null);
        final TextView last = host.pushNavButton("▶|", false, null);
        nav.addView(first, host.pushNavLp());
        nav.addView(prev, host.pushNavLp());
        nav.addView(next, host.pushNavLp());
        nav.addView(last, host.pushNavLp());
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(48));
        navLp.topMargin = host.dp(6);
        root.addView(nav, navLp);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final Button analyze = host.largeButton("🔍 分析", true);
        Button exit = host.largeButton("退出", true);
        actions.addView(analyze, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        actions.addView(exit, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(46));
        actionsLp.topMargin = host.dp(8);
        root.addView(actions, actionsLp);

        final LinearLayout analysisHost = new LinearLayout(host);
        analysisHost.setOrientation(LinearLayout.VERTICAL);
        analysisHost.setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(12));
        host.showTemporaryAnalysisPlaceholder(analysisHost);
        root.addView(analysisHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable refresh = () -> {
            boolean restart = host.temporaryAnalysisRunning;
            if (host.manualEngine != null) host.manualEngine.stopAnalysis();
            host.temporaryAnalysisGeneration++;
            try {
                board.setBoardFromFen(base);
                for (int i = 0; i < ply[0] && i < moves.size(); i++) {
                    board.playMoveSilently(Move.fromEngineStep(moves.get(i)));
                }
            } catch (Exception ignored) {
            }
            updateTemporaryNavButtonState(first, ply[0] > 0);
            updateTemporaryNavButtonState(prev, ply[0] > 0);
            updateTemporaryNavButtonState(next, ply[0] < moves.size());
            updateTemporaryNavButtonState(last, ply[0] < moves.size());
            board.setShowArrow(host.showEngineArrows);
            board.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
            if (restart) host.startTemporaryAnalysisNow(board, analysisHost, analyze);
            else host.showTemporaryAnalysisPlaceholder(analysisHost);
        };

        first.setOnClickListener(v -> {
            ply[0] = 0;
            refresh.run();
        });
        prev.setOnClickListener(v -> {
            if (ply[0] > 0) ply[0]--;
            refresh.run();
        });
        next.setOnClickListener(v -> {
            if (ply[0] < moves.size()) ply[0]++;
            refresh.run();
        });
        last.setOnClickListener(v -> {
            ply[0] = moves.size();
            refresh.run();
        });
        analyze.setOnClickListener(v -> host.toggleTemporaryAnalysis(board, analysisHost, analyze));
        exit.setOnClickListener(v -> {
            if (host.manualEngine != null) host.manualEngine.stopAnalysis();
            host.temporaryAnalysisRunning = false;
            host.temporaryAnalysisGeneration++;
            showList();
        });

        board.setListener(new ChessBoardView.Listener() {
            @Override public void onMoveMade(Move move, char movedPiece, char capturedPiece,
                                             String fenAfterMove, boolean redToMoveNow) {
                while (moves.size() > ply[0]) moves.remove(moves.size() - 1);
                moves.add(move.toEngineStep());
                ply[0]++;
                if (host.soundEnabled) host.playMoveSoundForBoard(board, redToMoveNow);
                refresh.run();
            }

            @Override public void onMessage(String message) {
                if (message != null && !message.startsWith("已选中：")
                        && message.length() > 0) {
                    Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onEditBoardChanged() {}
        });

        ScrollView page = new ScrollView(host);
        page.setFillViewport(true);
        page.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.appRoot.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updateTemporaryNavButtonState(TextView button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setClickable(enabled);
        button.setTextColor(enabled ? host.globalBackgroundTextColor()
                : Color.rgb(145, 150, 147));
        host.setRoundedBackground(button, host.globalSurfaceFillColor(), 5, Color.TRANSPARENT);
    }
}

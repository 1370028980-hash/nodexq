package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 手动棋谱页的导航、注释和分支列表交互。 */
final class ManualNavigationController {
    private final MainActivity host;

    ManualNavigationController(MainActivity host) {
        this.host = host;
    }

    View buildNavigationBar() {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        boolean enabled = !host.isRescoring;
        bar.addView(navButton("|◀", enabled && host.currentPly > 0,
                v -> navigateWithFollow(0)), squareNavLp());
        bar.addView(navButton("←", enabled && host.currentPly > 0,
                v -> navigateWithFollow(host.currentPly - 1)), squareNavLp());
        bar.addView(navButton("→", enabled && host.currentPly < host.engineMoves.size(),
                v -> navigateWithFollow(host.currentPly + 1)), squareNavLp());
        bar.addView(navButton("▶|", enabled && host.currentPly < host.engineMoves.size(),
                v -> navigateWithFollow(host.engineMoves.size())), squareNavLp());
        return bar;
    }

    View buildAlignedNavigationRow() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View spacer = new View(host);
        row.addView(spacer, new LinearLayout.LayoutParams(
                host.measureManualPaneWidth() + host.dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(buildNavigationBar(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    void navigateWithFollow(int target) {
        host.manualScrollToCurrentPly = true;
        host.navigateToPly(target);
    }

    void scrollRowIntoView(View row, View listRoot) {
        if (host.manualScrollView == null || row == null || listRoot == null) return;
        int rowTop = descendantTop(row, listRoot);
        int rowHeight = Math.max(host.dp(30), row.getHeight());
        int viewport = Math.max(1, host.manualScrollView.getHeight());
        int maxScroll = Math.max(0, listRoot.getHeight() - viewport);
        int targetY = host.clamp(rowTop - Math.max(0, (viewport - rowHeight) / 2), 0, maxScroll);
        host.manualScrollView.scrollTo(0, targetY);
        host.manualScrollY = targetY;
    }

    private int descendantTop(View child, View ancestor) {
        int top = 0;
        View current = child;
        while (current != null && current != ancestor) {
            top += current.getTop();
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return top;
    }

    private TextView navButton(String text, boolean enabled, View.OnClickListener listener) {
        TextView button = new TextView(host);
        button.setText(text);
        button.setTextSize(text.length() > 1 ? 10 : 12);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setEnabled(enabled);
        button.setClickable(enabled);
        button.setTextColor(enabled ? host.globalBackgroundTextColor() : Color.rgb(145, 150, 147));
        host.setRoundedBackground(button, host.globalSurfaceFillColor(), 5, Color.TRANSPARENT);
        if (enabled) button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams squareNavLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, host.dp(28), 1f);
        lp.leftMargin = host.dp(1);
        lp.rightMargin = host.dp(1);
        return lp;
    }

    View buildCommentEditor() {
        return host.manualBranchPanel.buildCommentEditor(currentManualComment(),
                host.manualBranchActions);
    }

    void showCommentDialog(final int targetIndex) {
        ensureCommentSize(host.engineMoves.size());
        String current = targetIndex < 0 ? host.initialComment
                : (targetIndex < host.moveComments.size() ? host.moveComments.get(targetIndex) : "");
        final EditText editor = new EditText(host);
        editor.setText(current);
        editor.setTextSize(15);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        editor.setSingleLine(false);
        editor.setMinLines(7);
        editor.setMaxLines(12);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setSelection(editor.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(targetIndex < 0 ? "编辑初始局面注释" : "编辑第" + (targetIndex + 1) + "步注释")
                .setView(editor)
                .setPositiveButton("保存", (d, which) -> {
                    String value = editor.getText() == null ? "" : editor.getText().toString();
                    if (targetIndex < 0) {
                        host.initialComment = value;
                    } else {
                        ensureCommentSize(host.engineMoves.size());
                        if (targetIndex < host.moveComments.size()) {
                            host.moveComments.set(targetIndex, value);
                        }
                    }
                    host.manualBranchPanel.updateVisibleComment(targetIndex, value);
                    host.persistCurrentSession();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            editor.requestFocus();
            editor.postDelayed(() -> {
                Window activeWindow = dialog.getWindow();
                if (activeWindow != null) {
                    activeWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    activeWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
                InputMethodManager keyboard = (InputMethodManager)
                        host.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120L);
        });
        dialog.show();
    }

    void ensureCommentSize(int size) {
        while (host.moveComments.size() < size) host.moveComments.add("");
        while (host.moveComments.size() > size) {
            host.moveComments.remove(host.moveComments.size() - 1);
        }
    }

    View buildBranchList() {
        return host.manualBranchPanel.buildBranchList(currentBranchList(), host.manualBranchActions);
    }

    ManualBranchPanel.Comment currentManualComment() {
        int commentIndex = host.currentPly - 1;
        ensureCommentSize(host.engineMoves.size());
        boolean enabled = commentIndex < 0 || commentIndex < host.engineMoves.size();
        String value = commentIndex < 0 ? host.initialComment
                : (commentIndex < host.moveComments.size()
                    ? host.moveComments.get(commentIndex) : "");
        return new ManualBranchPanel.Comment(commentIndex, enabled, value);
    }

    ManualBranchPanel.BranchList currentBranchList() {
        int node = resolveVariationNode();
        List<BranchDisplay> displays = new ArrayList<BranchDisplay>();
        if (node >= 0 && node < host.readableMoves.size()) {
            displays.add(BranchDisplay.active(host.activeBranchLabel(node),
                    formatBranchMove(host.readableMoves, node)));
        }
        List<ManualVariation> vars = host.manualVariations.get(node);
        host.ensureBranchLabels(node);
        if (vars != null) {
            for (int i = 0; i < vars.size(); i++) {
                ManualVariation variation = vars.get(i);
                if (variation == null || variation.engineSteps.isEmpty()) continue;
                displays.add(BranchDisplay.variation(variation.label,
                        formatBranchMove(variation.readableMoves, 0), i));
            }
        }
        Collections.sort(displays, (a, b) -> Integer.compare(
                host.branchLabelOrdinal(a.label), host.branchLabelOrdinal(b.label)));
        return new ManualBranchPanel.BranchList(node,
                Math.min(host.engineMoves.size(), node + 1), displays);
    }

    void showBranchLongPressDialog(int node, int variationIndex,
                                   boolean active, String label) {
        new AlertDialog.Builder(host)
                .setMessage("分支 " + label)
                .setPositiveButton("删除", (dialog, which) ->
                        new AlertDialog.Builder(host)
                                .setMessage("此举动会删除本分支及其之后的衍生分支，是否删除？")
                                .setPositiveButton("确认", (d, w) ->
                                        deleteBranch(node, variationIndex, active, label))
                                .setNegativeButton("取消", null)
                                .show())
                .setNegativeButton("取消", null)
                .show();
    }

    void deleteBranch(int node, int variationIndex,
                              boolean deleteActive, String label) {
        host.stopSearchForPositionChange();
        host.ensureBranchLabels(node);
        if (deleteActive) {
            int oldEnd = host.engineMoves.size();
            host.removeDescendantBranches(node, oldEnd);
            List<ManualVariation> vars = host.manualVariations.get(node);
            ManualVariation replacement = null;
            if (vars != null && !vars.isEmpty()) replacement = vars.remove(0);
            host.truncateListsTo(node);
            if (replacement != null) {
                host.engineMoves.addAll(replacement.engineSteps);
                host.readableMoves.addAll(replacement.readableMoves);
                host.moveComments.addAll(replacement.comments);
                host.redPerspectiveScores.addAll(replacement.scores);
                host.scoreMatePlies.addAll(replacement.matePlies);
                host.scoreKnown.addAll(replacement.scoreKnown);
                host.rescoreRecommendedMoves.addAll(replacement.rescoreRecommendedMoves);
                host.rescoreScoreKnown.addAll(replacement.rescoreScoreKnown);
                host.ensureScoreSize(host.engineMoves.size());
                host.restoreDescendantBranches(replacement, node);
                host.activeBranchLabels.put(node, replacement.label);
                if (vars == null || vars.isEmpty()) host.manualVariations.remove(node);
                else host.manualVariations.put(node, vars);
                host.currentPly = Math.min(host.engineMoves.size(), node + 1);
            } else {
                host.manualVariations.remove(node);
                host.activeBranchLabels.remove(node);
                host.currentPly = node;
            }
            host.rebuildBoardToPly(host.currentPly);
        } else {
            List<ManualVariation> vars = host.manualVariations.get(node);
            if (vars == null || variationIndex < 0 || variationIndex >= vars.size()) return;
            vars.remove(variationIndex);
            if (vars.isEmpty()) host.manualVariations.remove(node);
        }
        host.renumberBranchLabels(node);
        host.gameOver = false;
        host.terminalDialogShown = false;
        if (!host.completedDuelGame) host.gameResultTag = "*";
        host.updatePlayerLabels();
        host.updateGameContent();
        host.persistCurrentSession();
        host.appendLog("已删除分支 " + label + " 及其衍生分支。\n");
        if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(20L);
    }

    private int resolveVariationNode() {
        if (host.currentPly > 0) {
            return Math.min(host.currentPly - 1, Math.max(0, host.engineMoves.size() - 1));
        }
        return 0;
    }

    private String formatBranchMove(List<String> moves, int index) {
        if (moves == null || index < 0 || index >= moves.size()) return "----";
        String move = moves.get(index);
        return move == null || move.trim().length() == 0 ? "----" : move.trim();
    }
}

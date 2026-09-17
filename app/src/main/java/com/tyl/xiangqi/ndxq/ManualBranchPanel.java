package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.UiTheme;

import java.util.List;

/** 棋谱分支列表与注释框的 View 构建，不持有或修改棋局数据。 */
final class ManualBranchPanel {
    static final int ROW_HEIGHT_DP = 34;

    interface Actions {
        void navigateToPly(int target);
        void switchToVariation(int node, int variationIndex);
        void showBranchActions(int node, int variationIndex, boolean active, String label);
        void editComment(int targetIndex);
    }

    static final class BranchList {
        final int node;
        final int activeTarget;
        final List<BranchDisplay> displays;

        BranchList(int node, int activeTarget, List<BranchDisplay> displays) {
            this.node = node;
            this.activeTarget = activeTarget;
            this.displays = displays;
        }
    }

    static final class Comment {
        final int targetIndex;
        final boolean enabled;
        final String value;

        Comment(int targetIndex, boolean enabled, String value) {
            this.targetIndex = targetIndex;
            this.enabled = enabled;
            this.value = value == null ? "" : value;
        }
    }

    private final MainActivity activity;
    private EditText commentEdit;
    private int visibleCommentIndex = Integer.MIN_VALUE;

    ManualBranchPanel(MainActivity activity) {
        this.activity = activity;
    }

    View buildBranchList(BranchList branchList, Actions actions) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(2), 0, activity.dp(2), 0);
        if (branchList == null || branchList.displays == null || branchList.displays.isEmpty()) {
            TextView empty = branchRow("暂无分支", false);
            empty.setTextColor(Color.rgb(120, 124, 121));
            box.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(ROW_HEIGHT_DP)));
            return box;
        }

        for (BranchDisplay display : branchList.displays) {
            TextView row = branchRow(display.label + ". " + display.preview, display.active);
            if (display.active) {
                row.setOnClickListener(v -> actions.navigateToPly(branchList.activeTarget));
                row.setOnLongClickListener(v -> {
                    actions.showBranchActions(branchList.node, -1, true, display.label);
                    return true;
                });
            } else {
                final int variationIndex = display.variationIndex;
                row.setOnClickListener(v -> actions.switchToVariation(branchList.node, variationIndex));
                row.setOnLongClickListener(v -> {
                    actions.showBranchActions(branchList.node, variationIndex, false, display.label);
                    return true;
                });
            }
            box.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(ROW_HEIGHT_DP)));
        }
        return box;
    }

    View buildCommentEditor(Comment comment, Actions actions) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(4), activity.dp(3), activity.dp(4), activity.dp(3));
        activity.setRoundedBackground(box, activity.globalSurfaceFillColor(), 5,
                UiTheme.compositeOver(activity.globalBackgroundColor(), Color.argb(70, 120, 125, 121)));

        commentEdit = new EditText(activity);
        visibleCommentIndex = comment == null ? Integer.MIN_VALUE : comment.targetIndex;
        String value = comment == null ? "" : comment.value;
        boolean enabled = comment != null && comment.enabled;
        commentEdit.setTextSize(13);
        commentEdit.setPadding(activity.dp(6), activity.dp(4), activity.dp(6), activity.dp(4));
        commentEdit.setSingleLine(false);
        commentEdit.setVerticalScrollBarEnabled(true);
        commentEdit.setBackgroundColor(Color.TRANSPARENT);
        commentEdit.setTextColor(activity.globalBackgroundTextColor());
        commentEdit.setFocusable(false);
        commentEdit.setFocusableInTouchMode(false);
        commentEdit.setCursorVisible(false);
        commentEdit.setEnabled(enabled);
        commentEdit.setHint("注释框");
        commentEdit.setHintTextColor(UiTheme.secondaryTextOnBackground(activity));
        commentEdit.setText(value);
        updateCommentAppearance(value);
        if (enabled) {
            final int targetIndex = comment.targetIndex;
            commentEdit.setOnClickListener(v -> actions.editComment(targetIndex));
            box.setOnClickListener(v -> actions.editComment(targetIndex));
        }
        box.addView(commentEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    void updateVisibleComment(int targetIndex, String value) {
        if (commentEdit == null || visibleCommentIndex != targetIndex) return;
        commentEdit.setText(value == null ? "" : value);
        updateCommentAppearance(value);
    }

    void clearViewReferences() {
        commentEdit = null;
        visibleCommentIndex = Integer.MIN_VALUE;
    }

    private TextView branchRow(String text, boolean selected) {
        TextView row = new TextView(activity);
        row.setText(text);
        row.setTextSize(13);
        row.setSingleLine(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(6), 0, activity.dp(4), 0);
        row.setTextColor(selected ? activity.highlightTextColor() : activity.globalBackgroundTextColor());
        activity.setRoundedBackground(row,
                selected ? activity.highlightColor() : activity.globalSurfaceFillColor(),
                5, Color.TRANSPARENT);
        return row;
    }

    private void updateCommentAppearance(String value) {
        if (commentEdit == null) return;
        boolean empty = value == null || value.trim().length() == 0;
        commentEdit.setGravity(empty ? Gravity.CENTER : (Gravity.TOP | Gravity.START));
    }
}

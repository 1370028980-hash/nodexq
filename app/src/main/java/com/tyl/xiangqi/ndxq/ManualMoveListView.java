package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.CornerBadgeMoveView;

import java.util.List;

/** Only visible manual rows own views; the complete move record stays in the model. */
final class ManualMoveListView extends ListView {
    private final MainActivity host;
    private final boolean combined;
    private final MoveAdapter moves = new MoveAdapter();
    // Detached pages must not expose unannounced count changes from the shared model.
    private int moveCount;

    ManualMoveListView(MainActivity host, boolean combined) {
        super(host);
        this.host = host;
        this.combined = combined;
        moveCount = host.readableMoves.size();
        setDivider(null);
        setDividerHeight(0);
        setCacheColorHint(Color.TRANSPARENT);
        setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(8));
        setClipToPadding(false);
        setAdapter(moves);
    }

    void refreshMoves() {
        moveCount = host.readableMoves.size();
        moves.notifyDataSetChanged();
    }

    void showCurrentPly() {
        int ply = Math.max(0, Math.min(host.currentPly, host.readableMoves.size()));
        int row = ply == 0 ? 0 : combined ? ply : (ply + 1) / 2;
        // ListView retains this request until layout, including the first layout after a tab switch.
        setSelectionFromTop(row, Math.max(0, (getHeight() - host.dp(32)) / 2));
    }

    private final class MoveAdapter extends BaseAdapter {
        @Override public int getCount() {
            return 1 + (combined ? moveCount : (moveCount + 1) / 2);
        }

        @Override public Object getItem(int position) { return position; }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            if (position == 0) {
                TextView start = recycled instanceof TextView ? (TextView) recycled
                        : host.manualMoveRow("0. 初始局面", false);
                start.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dp(32)));
                style(start, host.currentPly == 0);
                start.setOnClickListener(v -> host.navigateToPly(0));
                return start;
            }
            LinearLayout row;
            if (recycled instanceof LinearLayout) {
                row = (LinearLayout) recycled;
            } else {
                row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.dp(combined ? 31 : 32)));
                int columns = combined ? 1 : 2;
                for (int column = 0; column < columns; column++) {
                    View cell = host.manualMoveCell("", false, -1, false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, host.dp(31), 1f);
                    if (column == 0 && !combined) lp.rightMargin = host.dp(1);
                    if (column == 1) lp.leftMargin = host.dp(1);
                    row.addView(cell, lp);
                }
            }
            int index = combined ? position - 1 : (position - 1) * 2;
            for (int column = 0; column < row.getChildCount(); column++) {
                bind((CornerBadgeMoveView) row.getChildAt(column), index + column);
            }
            return row;
        }
    }

    private void bind(CornerBadgeMoveView cell, int index) {
        boolean exists = index < host.readableMoves.size();
        boolean selected = exists && host.currentPly == index + 1;
        String text = exists ? host.readableMoves.get(index) : "";
        if (exists && (index & 1) == 0) text = (index / 2 + 1) + ". " + text;
        cell.setText(text);
        style(cell, selected);
        cell.setCornerBadge(exists ? branchIndicator(index) : "", selected);
        cell.setOnClickListener(exists ? v -> host.navigateToPly(index + 1) : null);
        cell.setClickable(exists);
    }

    private void style(TextView cell, boolean selected) {
        cell.setTextColor(selected ? host.highlightTextColor() : host.globalBackgroundTextColor());
        host.setRoundedBackground(cell, selected ? host.highlightColor() : Color.TRANSPARENT,
                6, selected ? Color.TRANSPARENT : Color.rgb(226, 228, 224));
    }

    private String branchIndicator(int node) {
        List<ManualVariation> variations = host.manualVariations.get(node);
        int count = 1;
        if (variations != null) {
            for (ManualVariation variation : variations) {
                if (variation != null && !variation.engineSteps.isEmpty()) count++;
            }
        }
        return count <= 1 ? "" : count + host.activeBranchLabel(node);
    }
}

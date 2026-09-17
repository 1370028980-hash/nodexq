package com.tyl.xiangqi.ndxq;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;

import java.io.File;
import java.util.Map;

/** 编辑棋盘的面板创建、棋子选择和热路径状态刷新。 */
final class ManualEditorController {
    private final MainActivity host;

    ManualEditorController(MainActivity host) {
        this.host = host;
    }

    View buildPanel() {
        clearPanelReferences();
        LinearLayout root = new LinearLayout(host);
        host.editPanelRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(2));

        root.addView(pieceRow(new char[]{'R','N','B','A','K','C','P'}),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(52)));
        root.addView(pieceRow(new char[]{'r','n','b','a','k','c','p'}),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(52)));

        LinearLayout actions1 = new LinearLayout(host);
        actions1.setOrientation(LinearLayout.HORIZONTAL);
        host.editDeleteAction = actionView("删除", host.selectedEditPiece == '-',
                v -> selectPiece(host.selectedEditPiece == '-' ? (char) 0 : '-'));
        actions1.addView(host.editDeleteAction, editLp());
        actions1.addView(actionView("翻转", false, v -> host.reverseBoard()), editLp());
        actions1.addView(actionView("清空", false, v -> host.boardView.clearBoard()), editLp());
        actions1.addView(actionView("初始", false, v -> host.boardView.fillInitialBoard()), editLp());
        root.addView(actions1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));

        LinearLayout actions2 = new LinearLayout(host);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        host.editBlackFirstAction = actionView("黑先", !host.boardView.isRedToMove(), v -> {
            host.boardView.setRedToMove(false);
            refreshPanelState();
        });
        actions2.addView(host.editBlackFirstAction, editLp());
        host.editRedFirstAction = actionView("红先", host.boardView.isRedToMove(), v -> {
            host.boardView.setRedToMove(true);
            refreshPanelState();
        });
        actions2.addView(host.editRedFirstAction, editLp());
        actions2.addView(actionView("完成", false, v -> host.finishEditMode(true)), editLp());
        actions2.addView(actionView("取消", false, v -> host.finishEditMode(false)), editLp());
        root.addView(actions2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(34)));
        return root;
    }

    private View pieceRow(char[] pieces) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        char[][] board = host.boardView.copyBoard();
        for (char piece : pieces) {
            int max = XiangqiRules.maxPieceCount(piece);
            int current = XiangqiRules.countPiece(board, piece);
            int remaining = Math.max(0, max - current);
            row.addView(pieceButton(piece, remaining, max), editLp());
        }
        return row;
    }

    private int bundledPieceDrawableId(char piece) {
        switch (piece) {
            case 'r': return R.drawable.br;
            case 'n': return R.drawable.bn;
            case 'b': return R.drawable.bb;
            case 'a': return R.drawable.ba;
            case 'k': return R.drawable.bk;
            case 'c': return R.drawable.bc;
            case 'p': return R.drawable.bp;
            case 'R': return R.drawable.rr;
            case 'N': return R.drawable.rn;
            case 'B': return R.drawable.rb;
            case 'A': return R.drawable.ra;
            case 'K': return R.drawable.rk;
            case 'C': return R.drawable.rc;
            case 'P': return R.drawable.rp;
            default: return 0;
        }
    }

    private View pieceButton(char piece, int remaining, int max) {
        boolean selected = host.selectedEditPiece == piece;
        boolean paintAvailable = remaining > 0;
        LinearLayout cell = new LinearLayout(host);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        host.setRoundedBackground(cell, selected ? host.highlightColor()
                        : paintAvailable ? Color.rgb(242, 243, 239) : Color.rgb(225, 226, 223),
                5, paintAvailable ? Color.rgb(198, 202, 196) : Color.rgb(210, 212, 209));

        ImageView icon = new ImageView(host);
        File skinDir = MainActivity.DEFAULT_SKIN_NAME.equals(host.currentSkinName)
                ? null : host.skinDirectory(host.currentSkinName);
        android.graphics.Bitmap sharedIcon = ChessBoardView.sharedSkinPieceBitmap(host, skinDir, piece);
        if (sharedIcon != null) {
            icon.setImageBitmap(sharedIcon);
        } else {
            int resId = bundledPieceDrawableId(piece);
            if (resId != 0) icon.setImageResource(resId);
        }
        int iconSize = host.dp(30);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setAlpha(paintAvailable ? 1f : 0.35f);
        cell.addView(icon);

        TextView count = new TextView(host);
        count.setText(remaining + "/" + max);
        count.setTextSize(10);
        count.setGravity(Gravity.CENTER);
        count.setTextColor(selected ? host.highlightTextColor()
                : paintAvailable ? Color.rgb(45, 50, 47) : Color.rgb(145, 148, 146));
        cell.addView(count);

        cell.setEnabled(true);
        cell.setOnClickListener(v -> selectPiece(piece));
        host.editPieceCells.put(piece, cell);
        host.editPieceIcons.put(piece, icon);
        host.editPieceCounts.put(piece, count);
        return cell;
    }

    /** 只刷新数量、高亮和先手按钮，不重建整个编辑面板。 */
    void refreshPanelState() {
        if (host.boardView == null || !host.boardView.isEditMode()
                || host.editPanelRoot == null) return;
        char[][] board = host.boardView.copyBoard();
        for (Map.Entry<Character, View> entry : host.editPieceCells.entrySet()) {
            char piece = entry.getKey();
            int max = XiangqiRules.maxPieceCount(piece);
            int current = XiangqiRules.countPiece(board, piece);
            int remaining = Math.max(0, max - current);
            boolean selected = host.selectedEditPiece == piece;
            boolean paintAvailable = remaining > 0;
            View cell = entry.getValue();
            if (cell != null) {
                host.setRoundedBackground(cell,
                        selected ? host.highlightColor()
                                : paintAvailable ? Color.rgb(242, 243, 239) : Color.rgb(225, 226, 223),
                        5, paintAvailable ? Color.rgb(198, 202, 196) : Color.rgb(210, 212, 209));
            }
            ImageView icon = host.editPieceIcons.get(piece);
            if (icon != null) icon.setAlpha(paintAvailable ? 1f : 0.35f);
            TextView count = host.editPieceCounts.get(piece);
            if (count != null) {
                count.setText(remaining + "/" + max);
                count.setTextColor(selected ? host.highlightTextColor()
                        : paintAvailable ? Color.rgb(45, 50, 47) : Color.rgb(145, 148, 146));
            }
        }
        styleAction(host.editDeleteAction, host.selectedEditPiece == '-');
        styleAction(host.editBlackFirstAction, !host.boardView.isRedToMove());
        styleAction(host.editRedFirstAction, host.boardView.isRedToMove());
    }

    void clearPanelReferences() {
        host.editPanelRoot = null;
        host.editPieceCells.clear();
        host.editPieceIcons.clear();
        host.editPieceCounts.clear();
        host.editDeleteAction = null;
        host.editBlackFirstAction = null;
        host.editRedFirstAction = null;
    }

    void selectPiece(char piece) {
        if (host.boardView == null) return;
        if (host.boardView.removeSelectedPieceInEditMode()) {
            host.selectedEditPiece = 0;
            host.boardView.setEditPaintPiece((char) 0);
            refreshPanelState();
            return;
        }
        if (host.selectedEditPiece == piece && XiangqiRules.isPiece(piece)) {
            host.selectedEditPiece = 0;
            host.boardView.setEditPaintPiece((char) 0);
            refreshPanelState();
            return;
        }
        if (XiangqiRules.isPiece(piece)
                && XiangqiRules.countPiece(host.boardView.copyBoard(), piece)
                >= XiangqiRules.maxPieceCount(piece)) return;
        host.selectedEditPiece = piece;
        host.boardView.setEditPaintPiece(piece);
        refreshPanelState();
    }

    private TextView actionView(String text, boolean selected, View.OnClickListener listener) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        styleAction(view, selected);
        view.setOnClickListener(listener);
        return view;
    }

    private void styleAction(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(selected ? host.highlightTextColor() : Color.rgb(45, 50, 47));
        host.setRoundedBackground(view,
                selected ? host.highlightColor() : Color.rgb(242, 243, 239),
                5, Color.rgb(198, 202, 196));
    }

    private LinearLayout.LayoutParams editLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = host.dp(1);
        lp.rightMargin = host.dp(1);
        lp.topMargin = host.dp(1);
        lp.bottomMargin = host.dp(1);
        return lp;
    }
}

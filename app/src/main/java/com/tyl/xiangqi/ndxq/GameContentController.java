package com.tyl.xiangqi.ndxq;

import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.core.GameReportCalculator;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;

import java.util.Collections;

/** 棋盘页内容区的 View 组装与滚动状态控制。 */
final class GameContentController {
    private final MainActivity host;

    GameContentController(MainActivity host) {
        this.host = host;
    }

    void updateGameContent() {
        if (host.gameContentHost == null) return;
        host.updateDrawButtonState();
        if (host.manualScrollView != null) host.manualScrollY = host.manualScrollView.getScrollY();
        if (host.branchScrollView != null) host.branchScrollY = host.branchScrollView.getScrollY();
        if (host.engineScrollView != null) host.engineScrollY = host.engineScrollView.getScrollY();
        host.gameContentHost.removeAllViews();
        host.clearEditPanelReferences();
        host.manualScrollView = null;
        host.branchScrollView = null;
        host.engineScrollView = null;
        host.manualBranchPanel.clearViewReferences();
        host.engineContentHost = null;
        host.situationPanel.clearViewReferences();

        if (host.evaluationMode && !host.completedDuelGame
                && (host.boardView == null || !host.boardView.isEditMode())) {
            host.gameContentHost.addView(buildManualView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        if (host.boardView != null && host.boardView.isEditMode()) {
            ScrollView editScroll = new ScrollView(host);
            editScroll.setFillViewport(true);
            editScroll.addView(host.buildEditPanel(), new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            host.gameContentHost.addView(editScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            host.boardView.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
            return;
        }
        if (host.selectedGameTab == 1 && !host.combinedManualEngineMode) {
            host.gameContentHost.addView(buildEngineView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (host.selectedGameTab == 2) {
            host.gameContentHost.addView(buildSituationView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            host.gameContentHost.addView(host.combinedManualEngineMode
                            ? buildCombinedManualEngineView() : buildManualView(),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        host.updateRescoreReviewArrows();
    }

    void keepNestedScrollGestures(View child) {
        if (child == null) return;
        child.setOnTouchListener((v, event) -> {
            setParentInterceptDisallowed(v,
                    event.getActionMasked() != android.view.MotionEvent.ACTION_UP
                            && event.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL);
            return false;
        });
    }

    private void setParentInterceptDisallowed(View view, boolean disallow) {
        if (view != null && view.getParent() != null) {
            view.getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /** V18.5 棋谱+引擎二合一布局。 */
    private View buildCombinedManualEngineView() {
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, host.dp(3));
        root.setClipChildren(true);
        root.addView(host.buildManualNavigationBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(32)));

        LinearLayout contentRow = new LinearLayout(host);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setClipChildren(false);

        LinearLayout manualAndNotes = new LinearLayout(host);
        manualAndNotes.setOrientation(LinearLayout.HORIZONTAL);
        manualAndNotes.setClipChildren(false);

        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(8));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        final View[] selectedManualRow = new View[1];
        TextView start = host.manualMoveRow("0. 初始局面", host.currentPly == 0);
        if (host.currentPly == 0) selectedManualRow[0] = start;
        start.setOnClickListener(v -> host.navigateToPly(0));
        list.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, host.dp(30)));
        for (int i = 0; i < host.readableMoves.size(); i++) {
            boolean redMove = (i % 2) == 0;
            String text = redMove ? ((i / 2 + 1) + ". " + host.readableMoves.get(i))
                    : host.readableMoves.get(i);
            View move = host.manualMoveCell(text, host.currentPly == i + 1, i, redMove);
            if (host.currentPly == i + 1) selectedManualRow[0] = move;
            final int target = i + 1;
            move.setOnClickListener(v -> host.navigateToPly(target));
            list.addView(move, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.dp(31)));
        }

        host.manualScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.manualScrollView);
        host.manualScrollView.setFillViewport(false);
        host.manualScrollView.setClipChildren(false);
        host.manualScrollView.setClipToPadding(false);
        host.manualScrollView.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final int restoreY = host.manualScrollY;
        final boolean followCurrentPly = host.manualScrollToCurrentPly || host.autoFollowLatestMove;
        host.manualScrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                if (followCurrentPly && selectedManualRow[0] != null) {
                    host.scrollManualRowIntoView(selectedManualRow[0], list);
                    host.manualScrollToCurrentPly = false;
                    host.autoFollowLatestMove = false;
                } else {
                    host.manualScrollView.scrollTo(0, restoreY);
                }
            }
        });
        manualAndNotes.addView(host.manualScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout notePane = new LinearLayout(host);
        notePane.setOrientation(LinearLayout.VERTICAL);
        notePane.setPadding(host.dp(3), host.dp(2), host.dp(2), host.dp(2));
        host.branchScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.branchScrollView);
        host.branchScrollView.addView(host.buildBranchList(), new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final int restoreBranchY = host.branchScrollY;
        final ScrollView restoreBranchScroll = host.branchScrollView;
        restoreBranchScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                restoreBranchScroll.scrollTo(0, restoreBranchY);
            }
        });
        notePane.addView(host.branchScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(MainActivity.BRANCH_ROW_HEIGHT_DP
                        * MainActivity.MAX_VISIBLE_BRANCH_ROWS)));
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        commentLp.topMargin = host.dp(3);
        notePane.addView(host.buildManualCommentEditor(), commentLp);
        int notePaneWidth = Math.round(host.getResources().getDisplayMetrics().widthPixels * 0.25f);
        manualAndNotes.addView(notePane, new LinearLayout.LayoutParams(
                notePaneWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        contentRow.addView(manualAndNotes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout enginePane = new LinearLayout(host);
        enginePane.setOrientation(LinearLayout.VERTICAL);
        enginePane.setPadding(host.dp(4), host.dp(2), host.dp(2), host.dp(2));
        host.engineScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.engineScrollView);
        host.engineContentHost = new LinearLayout(host);
        host.engineContentHost.setOrientation(LinearLayout.VERTICAL);
        host.engineContentHost.setPadding(host.dp(5), host.dp(4), host.dp(5), host.dp(8));
        host.engineScrollView.addView(host.engineContentHost, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.refreshEngineContentText();
        final int restoreEngineY = host.engineScrollY;
        host.engineScrollView.post(() -> host.engineScrollView.scrollTo(0, restoreEngineY));
        enginePane.addView(host.engineScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams enginePaneLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        enginePaneLp.leftMargin = host.dp(3);
        contentRow.addView(enginePane, enginePaneLp);
        root.addView(contentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildManualView() {
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, host.dp(3));
        root.setClipChildren(true);
        root.addView(host.buildManualNavigationBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(32)));

        LinearLayout contentRow = new LinearLayout(host);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setClipChildren(false);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(8));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        final View[] selectedManualRow = new View[1];
        TextView start = host.manualMoveRow("0. 初始局面", host.currentPly == 0);
        if (host.currentPly == 0) selectedManualRow[0] = start;
        start.setOnClickListener(v -> host.navigateToPly(0));
        list.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, host.dp(30)));
        for (int i = 0; i < host.readableMoves.size(); i += 2) {
            LinearLayout roundRow = new LinearLayout(host);
            roundRow.setOrientation(LinearLayout.HORIZONTAL);
            roundRow.setClipChildren(false);
            int round = i / 2 + 1;
            View redMove = host.manualMoveCell(round + ". " + host.readableMoves.get(i),
                    host.currentPly == i + 1, i, true);
            if (host.currentPly == i + 1) selectedManualRow[0] = redMove;
            final int redTarget = i + 1;
            redMove.setOnClickListener(v -> host.navigateToPly(redTarget));
            LinearLayout.LayoutParams redLp = new LinearLayout.LayoutParams(0, host.dp(31), 1f);
            redLp.rightMargin = host.dp(1);
            roundRow.addView(redMove, redLp);
            if (i + 1 < host.readableMoves.size()) {
                View blackMove = host.manualMoveCell(host.readableMoves.get(i + 1),
                        host.currentPly == i + 2, i + 1, false);
                if (host.currentPly == i + 2) selectedManualRow[0] = blackMove;
                final int blackTarget = i + 2;
                blackMove.setOnClickListener(v -> host.navigateToPly(blackTarget));
                LinearLayout.LayoutParams blackLp = new LinearLayout.LayoutParams(0, host.dp(31), 1f);
                blackLp.leftMargin = host.dp(1);
                roundRow.addView(blackMove, blackLp);
            } else {
                TextView empty = host.manualMoveRow("", false);
                empty.setClickable(false);
                LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(0, host.dp(31), 1f);
                emptyLp.leftMargin = host.dp(1);
                roundRow.addView(empty, emptyLp);
            }
            list.addView(roundRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(32)));
        }
        host.manualScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.manualScrollView);
        host.manualScrollView.setFillViewport(false);
        host.manualScrollView.setClipChildren(false);
        host.manualScrollView.setClipToPadding(false);
        host.manualScrollView.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final int restoreY = host.manualScrollY;
        final boolean followCurrentPly = host.manualScrollToCurrentPly || host.autoFollowLatestMove;
        host.manualScrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                if (followCurrentPly && selectedManualRow[0] != null) {
                    host.scrollManualRowIntoView(selectedManualRow[0], list);
                    host.manualScrollToCurrentPly = false;
                    host.autoFollowLatestMove = false;
                } else {
                    host.manualScrollView.scrollTo(0, restoreY);
                }
            }
        });

        LinearLayout branches = new LinearLayout(host);
        branches.setOrientation(LinearLayout.VERTICAL);
        branches.setPadding(host.dp(4), host.dp(2), host.dp(2), host.dp(2));
        host.branchScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.branchScrollView);
        host.branchScrollView.addView(host.buildBranchList(), new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final int restoreBranchY = host.branchScrollY;
        final ScrollView restoreBranchScroll = host.branchScrollView;
        restoreBranchScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                restoreBranchScroll.scrollTo(0, restoreBranchY);
            }
        });
        branches.addView(host.branchScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(MainActivity.BRANCH_ROW_HEIGHT_DP
                        * MainActivity.MAX_VISIBLE_BRANCH_ROWS)));
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        commentLp.topMargin = host.dp(3);
        branches.addView(host.buildManualCommentEditor(), commentLp);

        int manualPaneWidth = measureManualPaneWidth();
        int availableWidth = host.getResources().getDisplayMetrics().widthPixels - host.dp(16);
        int maxManualWidth = availableWidth - host.dp(160) - host.dp(5);
        if (manualPaneWidth > maxManualWidth) manualPaneWidth = maxManualWidth;
        if (host.evaluationMode && !host.completedDuelGame) {
            branches.setVisibility(View.GONE);
            contentRow.addView(host.manualScrollView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            contentRow.addView(host.manualScrollView, new LinearLayout.LayoutParams(
                    manualPaneWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout.LayoutParams branchPaneLp = new LinearLayout.LayoutParams(
                host.dp(160), ViewGroup.LayoutParams.MATCH_PARENT);
        branchPaneLp.leftMargin = host.dp(3);
        if (!(host.evaluationMode && !host.completedDuelGame)) {
            contentRow.addView(branches, branchPaneLp);
        }
        root.addView(contentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    int measureManualPaneWidth() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(14f * host.getResources().getDisplayMetrics().scaledDensity);
        float widestText = paint.measureText("0. 初始局面");
        for (int i = 0; i < host.readableMoves.size(); i++) {
            String text = (i % 2 == 0) ? ((i / 2 + 1) + ". " + host.readableMoves.get(i))
                    : host.readableMoves.get(i);
            widestText = Math.max(widestText, paint.measureText(text));
        }
        int cellWidth = Math.round(widestText) + host.dp(24);
        cellWidth = host.clamp(cellWidth, host.dp(108), host.dp(150));
        int colWidth = cellWidth * 2 + host.dp(2);
        int screenWidth = host.getResources().getDisplayMetrics().widthPixels;
        int maximum = Math.max(host.dp(170), Math.round(screenWidth * 0.55f));
        return host.clamp(colWidth + host.dp(6), host.dp(170), maximum);
    }

    private View buildEngineView() {
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout navRow = new LinearLayout(host);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.addView(host.buildManualNavigationBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(navRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(32)));
        host.engineScrollView = new ScrollView(host);
        keepNestedScrollGestures(host.engineScrollView);
        host.engineContentHost = new LinearLayout(host);
        host.engineContentHost.setOrientation(LinearLayout.VERTICAL);
        host.engineContentHost.setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(12));
        host.engineScrollView.addView(host.engineContentHost, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.refreshEngineContentText();
        final int restoreY = host.engineScrollY;
        host.engineScrollView.post(() -> host.engineScrollView.scrollTo(0, restoreY));
        root.addView(host.engineScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildSituationView() {
        int endgameRound = host.findFirstEndgameRound();
        GameReportCalculator.Report report = host.buildGameReport(endgameRound);
        return host.situationPanel.build(host.buildManualNavigationBar(),
                host.situationPanelData(endgameRound, report), host.situationPanelActions);
    }
}

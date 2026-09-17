package com.tyl.xiangqi.ndxq;

import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.UiTheme;

/** 皮肤棋盘网格与棋子尺寸的动态校准页面。 */
final class SkinCalibrationController {
    private SkinCalibrationController() {}

    static void show(MainActivity activity, String skinName, int pieceSize, float[] initialGrid,
                     MainActivity.SkinGridCalibrationListener listener) {
        final String selectedSkin = activity.sanitizeSkinName(skinName);
        final float[][] workingGrid = new float[][]{
                initialGrid == null ? ChessBoardView.defaultGridCorners() : initialGrid.clone()
        };

        android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(10));
        page.setBackgroundColor(activity.globalRootBackgroundColor());

        TextView title = new TextView(activity);
        title.setText("动态校准 · " + selectedSkin);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(activity.globalBackgroundTextColor());
        page.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38)));

        TextView status = new TextView(activity);
        status.setText("点击黄色角点选择后可用方向键微调；也可以直接拖动角点或整体网格。");
        status.setTextSize(12);
        status.setTextColor(UiTheme.secondaryTextOnBackground(activity));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(activity.dp(2), 0, activity.dp(2), activity.dp(4));
        page.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        final ChessBoardView preview = new ChessBoardView(activity, false);
        preview.setShowCoordinate(false);
        preview.setPieceShadowEnabled(true);
        preview.setShowArrow(false);
        preview.setBoardScalePercent(100);
        preview.setInputEnabled(false);
        preview.setReversed(false);
        preview.setBoardSideFillColor(activity.globalSurfaceFillColor());
        preview.newGame();
        boolean loaded = MainActivity.DEFAULT_SKIN_NAME.equals(selectedSkin)
                ? preview.loadBundledDefaultSkin(activity.clamp(pieceSize, 55, 125), workingGrid[0])
                : preview.loadSkin(activity.skinDirectory(selectedSkin),
                        activity.clamp(pieceSize, 55, 125), workingGrid[0]);
        if (!loaded) {
            status.setText("皮肤图片不完整或无法读取：" + selectedSkin);
        }
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView calibrateSizeLabel = new TextView(activity);
        calibrateSizeLabel.setText("棋子大小: " + activity.clamp(pieceSize, 55, 125) + "%");
        calibrateSizeLabel.setTextSize(12);
        calibrateSizeLabel.setTextColor(activity.globalBackgroundTextColor());
        calibrateSizeLabel.setPadding(activity.dp(2), activity.dp(6), activity.dp(2), 0);
        content.addView(calibrateSizeLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar calibrateSizeSlider = new SeekBar(activity);
        calibrateSizeSlider.setMax(70);
        calibrateSizeSlider.setProgress(activity.clamp(pieceSize, 55, 125) - 55);
        calibrateSizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = 55 + progress;
                calibrateSizeLabel.setText("棋子大小: " + size + "%");
                preview.setPieceSizePercent(size);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(calibrateSizeSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(38)));

        Button reset = activity.compactButton("恢复默认位置");
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(36));
        resetLp.topMargin = activity.dp(6);
        content.addView(reset, resetLp);

        LinearLayout nudgeArea = new LinearLayout(activity);
        nudgeArea.setOrientation(LinearLayout.HORIZONTAL);
        nudgeArea.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout directionPad = new LinearLayout(activity);
        directionPad.setOrientation(LinearLayout.VERTICAL);
        directionPad.setGravity(Gravity.CENTER);

        LinearLayout nudgeTop = new LinearLayout(activity);
        nudgeTop.setGravity(Gravity.CENTER);
        Button nudgeUp = activity.compactButton("↑");
        nudgeUp.setContentDescription("向上微调所选校准点");
        nudgeTop.addView(nudgeUp, new LinearLayout.LayoutParams(activity.dp(66), activity.dp(40)));
        directionPad.addView(nudgeTop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));

        LinearLayout nudgeBottom = new LinearLayout(activity);
        nudgeBottom.setGravity(Gravity.CENTER);
        Button nudgeLeft = activity.compactButton("←");
        Button nudgeDown = activity.compactButton("↓");
        Button nudgeRight = activity.compactButton("→");
        nudgeLeft.setContentDescription("向左微调所选校准点");
        nudgeDown.setContentDescription("向下微调所选校准点");
        nudgeRight.setContentDescription("向右微调所选校准点");
        nudgeBottom.addView(nudgeLeft, new LinearLayout.LayoutParams(activity.dp(66), activity.dp(40)));
        LinearLayout.LayoutParams middleNudgeLp = new LinearLayout.LayoutParams(activity.dp(66), activity.dp(40));
        middleNudgeLp.leftMargin = activity.dp(6);
        nudgeBottom.addView(nudgeDown, middleNudgeLp);
        LinearLayout.LayoutParams rightNudgeLp = new LinearLayout.LayoutParams(activity.dp(66), activity.dp(40));
        rightNudgeLp.leftMargin = activity.dp(6);
        nudgeBottom.addView(nudgeRight, rightNudgeLp);
        directionPad.addView(nudgeBottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(42)));
        nudgeArea.addView(directionPad, new LinearLayout.LayoutParams(activity.dp(224), activity.dp(86)));

        LinearLayout stepPanel = new LinearLayout(activity);
        stepPanel.setOrientation(LinearLayout.VERTICAL);
        stepPanel.setGravity(Gravity.CENTER_VERTICAL);
        TextView stepLabel = new TextView(activity);
        stepLabel.setText("微调步长(dp)");
        stepLabel.setGravity(Gravity.CENTER);
        stepLabel.setTextSize(11);
        stepPanel.addView(stepLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(30)));
        EditText calibrationStep = new EditText(activity);
        calibrationStep.setSingleLine(true);
        calibrationStep.setGravity(Gravity.CENTER);
        calibrationStep.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        calibrationStep.setText("2");
        calibrationStep.setSelectAllOnFocus(true);
        calibrationStep.setTextSize(13);
        stepPanel.addView(calibrationStep, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(44)));
        LinearLayout.LayoutParams stepPanelLp = new LinearLayout.LayoutParams(0, activity.dp(86), 1f);
        stepPanelLp.leftMargin = activity.dp(8);
        nudgeArea.addView(stepPanel, stepPanelLp);
        content.addView(nudgeArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(90)));

        TextView hint = new TextView(activity);
        hint.setText("动态校准方法：黄色网格实时表示棋子落点。可直接拖拽四个圆形控制点；点击某个角点可选中，再点一次取消，点击另一个角点会切换选择。选中后可用方向键按设定步长微调；拖动网格内部可整体移动。");
        hint.setTextSize(11);
        hint.setTextColor(UiTheme.secondaryTextOnBackground(activity));
        hint.setPadding(activity.dp(2), activity.dp(5), activity.dp(2), activity.dp(8));
        content.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = activity.compactButton("取消");
        Button done = activity.compactButton("完成");
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, activity.dp(42), 1f));
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(0, activity.dp(42), 1f);
        doneLp.leftMargin = activity.dp(8);
        bottom.addView(done, doneLp);
        page.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(46)));

        preview.setGridHandleSelectionListener(handle -> {
            String[] names = new String[]{"左上角", "右上角", "左下角", "右下角"};
            if (handle >= 0 && handle < names.length) {
                status.setText("已选择" + names[handle]
                        + "；可拖动或使用方向键微调，再次点击该点可取消选择。");
            } else {
                status.setText("请点击一个黄色角点选择，或直接拖动角点/网格。");
            }
        });
        preview.startGridAdjustment(corners -> workingGrid[0] = corners);

        View.OnClickListener nudgeListener = v -> {
            Float step = activity.parseCalibrationStepDp(calibrationStep);
            if (step == null) return;
            float dx = 0f, dy = 0f;
            if (v == nudgeUp) dy = -step;
            else if (v == nudgeDown) dy = step;
            else if (v == nudgeLeft) dx = -step;
            else if (v == nudgeRight) dx = step;
            if (!preview.nudgeSelectedGridHandle(dx, dy)) {
                Toast.makeText(activity, "请先点击一个黄色校准点",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            workingGrid[0] = preview.getSkinGridCorners();
        };
        nudgeUp.setOnClickListener(nudgeListener);
        nudgeDown.setOnClickListener(nudgeListener);
        nudgeLeft.setOnClickListener(nudgeListener);
        nudgeRight.setOnClickListener(nudgeListener);

        reset.setOnClickListener(v -> {
            workingGrid[0] = ChessBoardView.defaultGridCorners();
            preview.setSkinGridCorners(workingGrid[0]);
            status.setText("已恢复默认网格位置，可继续调整。");
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        done.setOnClickListener(v -> {
            workingGrid[0] = preview.getSkinGridCorners();
            if (listener != null) listener.onFinished(workingGrid[0].clone(),
                    preview.getPieceSizePercent());
            dialog.dismiss();
        });

        dialog.setContentView(page);
        dialog.setOnDismissListener(ignored -> preview.stopGridAdjustment());
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new ColorDrawable(activity.globalRootBackgroundColor()));
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(activity.globalRootBackgroundColor()));
        }
    }
}

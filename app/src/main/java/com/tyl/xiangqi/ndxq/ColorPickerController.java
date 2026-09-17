package com.tyl.xiangqi.ndxq;


import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.ui.ColorWheelView;
import com.tyl.xiangqi.ndxq.ui.UiTheme;

import java.util.Locale;

/** 颜色选择、HEX 输入和预览的独立控制器。 */
final class ColorPickerController {
    private ColorPickerController() {
    }

    static void show(MainActivity host, String title, int initialColor,
                     MainActivity.HighlightColorListener listener) {
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(8));

        final ColorWheelView wheel = new ColorWheelView(host);
        wheel.setColor(initialColor);
        final int[] selectedColor = new int[]{initialColor};
        final boolean[] syncingSlider = new boolean[]{false};
        body.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(250)));

        TextView preview = new TextView(host);
        preview.setGravity(Gravity.CENTER);
        preview.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        preview.setTextSize(13);
        body.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(36)));

        LinearLayout codeRow = new LinearLayout(host);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView codeLabel = new TextView(host);
        codeLabel.setText("hex色值");
        codeLabel.setTextSize(12);
        codeRow.addView(codeLabel, new LinearLayout.LayoutParams(host.dp(62), host.dp(36)));
        EditText colorCode = new EditText(host);
        colorCode.setSingleLine(true);
        colorCode.setTextSize(13);
        colorCode.setSelectAllOnFocus(true);
        colorCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeRow.addView(colorCode, new LinearLayout.LayoutParams(0, host.dp(36), 1f));
        Button applyCode = host.compactButton("应用");
        LinearLayout.LayoutParams applyCodeLp = new LinearLayout.LayoutParams(
                host.dp(58), host.dp(34));
        applyCodeLp.leftMargin = host.dp(6);
        codeRow.addView(applyCode, applyCodeLp);
        body.addView(codeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(38)));

        TextView codeHint = new TextView(host);
        codeHint.setText("可直接输入 6 位 hex 色值，例如 395E42；也可继续拖动上方色盘。");
        codeHint.setTextSize(10);
        codeHint.setTextColor(Color.rgb(105, 110, 106));
        body.addView(codeHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(26)));

        TextView valueLabel = new TextView(host);
        valueLabel.setText("亮度");
        valueLabel.setTextSize(12);
        body.addView(valueLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(24)));

        SeekBar valueSlider = new SeekBar(host);
        valueSlider.setMax(100);
        valueSlider.setProgress(Math.round(wheel.getValue() * 100f));
        body.addView(valueSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(42)));

        final Runnable refreshPreview = () -> {
            int color = selectedColor[0];
            String code = String.format(Locale.CHINA, "%02X%02X%02X",
                    Color.red(color), Color.green(color), Color.blue(color));
            preview.setText("当前颜色  #" + code);
            preview.setTextColor(UiTheme.textOnHighlight(host, color));
            host.setRoundedBackground(preview, color, 7, Color.TRANSPARENT);
            colorCode.setText(code);
        };
        colorCode.setText(String.format(Locale.CHINA, "%02X%02X%02X",
                Color.red(initialColor), Color.green(initialColor), Color.blue(initialColor)));
        wheel.setListener(color -> {
            selectedColor[0] = color;
            refreshPreview.run();
        });
        valueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                      boolean fromUser) {
                if (syncingSlider[0] || !fromUser) {
                    return;
                }
                wheel.setValue(progress / 100f);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        applyCode.setOnClickListener(v -> {
            Integer parsed = parseHexColorValue(colorCode.getText().toString());
            if (parsed == null) {
                Toast.makeText(host, "hex色值无效，请输入 6 位十六进制，例如 395E42",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            selectedColor[0] = parsed;
            wheel.setColor(parsed);
            syncingSlider[0] = true;
            try {
                valueSlider.setProgress(Math.round(wheel.getValue() * 100f));
            } finally {
                syncingSlider[0] = false;
            }
            colorCode.clearFocus();
            refreshPreview.run();
        });
        refreshPreview.run();

        AlertDialog picker = new AlertDialog.Builder(host)
                .setTitle(title == null || title.trim().isEmpty() ? "选择颜色" : title)
                .setView(body)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        picker.setOnShowListener(ignored -> picker.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    Integer parsed = parseHexColorValue(colorCode.getText().toString());
                    if (parsed == null) {
                        Toast.makeText(host,
                                "hex色值无效，请输入 6 位十六进制，例如 395E42",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 直接输入 HEX 后点击确定时，以输入框内容作为最终颜色。
                    selectedColor[0] = parsed;
                    wheel.setColor(parsed);
                    if (listener != null) listener.onSelected(selectedColor[0]);
                    picker.dismiss();
                }));
        picker.show();
    }

    private static Integer parseHexColorValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("(?i)[0-9a-f]{6}")) return null;
        try {
            return Color.rgb(Integer.parseInt(value.substring(0, 2), 16),
                    Integer.parseInt(value.substring(2, 4), 16),
                    Integer.parseInt(value.substring(4, 6), 16));
        } catch (Exception e) {
            return null;
        }
    }
}

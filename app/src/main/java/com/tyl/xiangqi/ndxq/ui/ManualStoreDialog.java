package com.tyl.xiangqi.ndxq.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

/** XQF / PGN / 东萍 UBB 棋谱保存格式选择弹窗。 */
public final class ManualStoreDialog {
    public interface Listener { void onSelected(int format); }
    private ManualStoreDialog() {}

    public static void show(Activity activity, int currentFormat, Listener listener) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 2));
        final RadioButton[] radios = new RadioButton[]{
                new RadioButton(activity), new RadioButton(activity), new RadioButton(activity)
        };
        String[] labels = new String[]{"XQF", "PGN", "UBB"};
        for (int i = 0; i < radios.length; i++) {
            final RadioButton selected = radios[i];
            selected.setChecked(currentFormat == i);
            selected.setOnCheckedChangeListener((buttonView, checked) -> {
                if (!checked) return;
                for (RadioButton other : radios) if (other != selected) other.setChecked(false);
            });
            box.addView(formatRow(activity, labels[i], selected), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)));
        }
        new AlertDialog.Builder(activity).setTitle("存储棋谱").setView(box)
                .setPositiveButton("选择路径", (dialog, which) -> {
                    int selected = 0;
                    for (int i = 0; i < radios.length; i++) if (radios[i].isChecked()) selected = i;
                    listener.onSelected(selected);
                })
                .setNegativeButton("取消", null).show();
    }

    private static LinearLayout formatRow(Activity activity, String label, RadioButton radio) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 10), 0, dp(activity, 4), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 248, 245));
        bg.setCornerRadius(dp(activity, 6));
        bg.setStroke(dp(activity, 1), Color.rgb(207, 212, 207));
        row.setBackground(bg);
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextSize(15);
        text.setTextColor(Color.rgb(42, 50, 46));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        radio.setText("");
        radio.setGravity(Gravity.CENTER);
        row.addView(radio, new LinearLayout.LayoutParams(dp(activity, 42), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> radio.setChecked(true));
        return row;
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}

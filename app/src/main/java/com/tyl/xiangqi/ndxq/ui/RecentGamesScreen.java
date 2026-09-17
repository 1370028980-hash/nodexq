package com.tyl.xiangqi.ndxq.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.storage.RecentGameStore;

import java.util.List;

/** 最近对局页面的纯 UI 构建器，减少 MainActivity 的页面布局职责。 */
public final class RecentGamesScreen {
    public interface Listener {
        void onBack();
        void onGrantStorage();
        void onOpen(RecentGameStore.Record record);
        void onDelete(RecentGameStore.Record record);
        void onClearAll();
    }

    private RecentGamesScreen() {}

    public static void show(Activity activity, FrameLayout host, boolean storageReady,
                            List<RecentGameStore.Record> records, Listener listener) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        header.setBackgroundColor(Color.rgb(35, 46, 41));
        Button back = compactButton(activity, "返回");
        back.setOnClickListener(v -> listener.onBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 36)));
        TextView title = new TextView(activity);
        title.setText("最近对局");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 36), 1f));
        Button clear = compactButton(activity, "清空");
        clear.setEnabled(storageReady && records != null && !records.isEmpty());
        clear.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setMessage("是否清空所有对局记录？")
                .setPositiveButton("确定", (d, w) -> listener.onClearAll())
                .setNegativeButton("取消", null)
                .show());
        header.addView(clear, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 36)));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 18));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        host.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (!storageReady) {
            TextView message = new TextView(activity);
            message.setText("需要文件访问权限，才能读取和保存：\n/storage/emulated/0/nodexq/recent");
            message.setTextSize(15);
            message.setTextColor(UiTheme.textOnBackground(activity));
            message.setGravity(Gravity.CENTER);
            list.addView(message, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 110)));
            Button grant = largeButton(activity, "授予存储权限");
            grant.setOnClickListener(v -> listener.onGrantStorage());
            list.addView(grant, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));
            return;
        }

        if (records == null || records.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("暂无已完成对局");
            empty.setTextSize(16);
            empty.setTextColor(UiTheme.secondaryTextOnBackground(activity));
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 120)));
            return;
        }
        for (RecentGameStore.Record record : records) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Button item = largeButton(activity, styledText(record));
            item.setTextSize(13);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setPadding(dp(activity, 14), 0, dp(activity, 10), 0);
            item.setOnClickListener(v -> listener.onOpen(record));
            row.addView(item, new LinearLayout.LayoutParams(0, dp(activity, 50), 1f));
            TextView delete = xButton(activity);
            delete.setContentDescription("删除本条对局记录");
            delete.setOnClickListener(v -> new AlertDialog.Builder(activity)
                    .setMessage("是否删除本条对局记录？")
                    .setPositiveButton("确定", (d, w) -> listener.onDelete(record))
                    .setNegativeButton("取消", null)
                    .show());
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30));
            deleteLp.leftMargin = dp(activity, 7);
            row.addView(delete, deleteLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(activity, 50));
            rowLp.bottomMargin = dp(activity, 8);
            list.addView(row, rowLp);
        }
    }

    private static CharSequence styledText(RecentGameStore.Record record) {
        if (record == null) return "";
        String line = record.displayLine == null ? "" : record.displayLine;
        int resultStart = line.indexOf(" " + record.result + " ");
        if (resultStart >= 0) resultStart++;
        int color = "先胜".equals(record.result) ? Color.rgb(196, 52, 40)
                : "先负".equals(record.result) ? Color.rgb(140, 145, 141) : 0;
        if (color == 0 || resultStart < 0 || resultStart + record.result.length() > line.length()) return line;
        SpannableString styled = new SpannableString(line);
        styled.setSpan(new ForegroundColorSpan(color), resultStart, resultStart + record.result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    private static Button compactButton(Activity activity, String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        int highlight = UiTheme.highlight(activity);
        rounded(button, highlight, 7, Color.TRANSPARENT, activity);
        button.setTextColor(UiTheme.textOnHighlight(activity, highlight));
        return button;
    }

    private static Button largeButton(Activity activity, CharSequence text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.BLACK);
        button.setAllCaps(false);
        rounded(button, UiTheme.homeButton(activity), 14, Color.TRANSPARENT, activity);
        return button;
    }

    private static TextView xButton(Activity activity) {
        TextView view = new TextView(activity);
        view.setText("×");
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        rounded(view, Color.rgb(145, 149, 146), 6, Color.TRANSPARENT, activity);
        return view;
    }

    private static void rounded(android.view.View view, int fill, int radiusDp, int stroke,
                                Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(activity, radiusDp));
        if (stroke != Color.TRANSPARENT) bg.setStroke(dp(activity, 1), stroke);
        view.setBackground(bg);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

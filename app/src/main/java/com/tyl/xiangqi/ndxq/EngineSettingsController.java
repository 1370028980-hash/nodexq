package com.tyl.xiangqi.ndxq;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 手动分析和重打分共用的 UCI 选项界面及本地选项持久化。 */
final class EngineSettingsController {
    private final MainActivity host;

    EngineSettingsController(MainActivity host) {
        this.host = host;
    }

    void showOptionsDialog() {
        if (host.manualEngine == null) return;
        final AlertDialog loading = new AlertDialog.Builder(host)
                .setTitle("引擎设置")
                .setMessage("正在读取 UCI 选项……")
                .create();
        loading.show();
        host.manualEngine.loadOptions(new PikafishEngine.OptionsCallback() {
            @Override public void onOptions(final List<PikafishEngine.EngineOption> options,
                                            String rawUciOptions) {
                host.handler.post(() -> {
                    loading.dismiss();
                    buildOptionsDialog(options);
                });
            }

            @Override public void onError(final String message) {
                host.handler.post(() -> {
                    loading.dismiss();
                    String publicMessage = message == null ? "读取引擎选项失败。" : message;
                    int detailAt = publicMessage.indexOf('\n');
                    if (detailAt >= 0) publicMessage = publicMessage.substring(0, detailAt);
                    new AlertDialog.Builder(host)
                            .setTitle("引擎设置")
                            .setMessage(publicMessage)
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        });
    }

    private void buildOptionsDialog(List<PikafishEngine.EngineOption> options) {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(8), host.dp(2), host.dp(8), host.dp(2));
        scroll.addView(panel);

        TextView scopeNote = new TextView(host);
        scopeNote.setText("此设置适用于局面分析、重新打分，以及引擎执红/执黑（与放大镜共用同一个 131 引擎进程）");
        scopeNote.setTextSize(12);
        scopeNote.setTextColor(Color.rgb(104, 110, 106));
        scopeNote.setGravity(Gravity.CENTER_VERTICAL);
        scopeNote.setPadding(host.dp(4), host.dp(5), host.dp(4), host.dp(6));
        panel.addView(scopeNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ArrayList<Runnable> commits = new ArrayList<Runnable>();
        addAppDisplayOptions(panel, commits);

        int visibleOptionCount = 0;
        if (options != null) {
            for (PikafishEngine.EngineOption option : options) {
                if (shouldHideOption(option)) continue;
                panel.addView(makeOptionRow(option, commits));
                visibleOptionCount++;
            }
        }
        if (visibleOptionCount == 0) {
            TextView empty = new TextView(host);
            empty.setText("引擎没有返回可显示的 UCI 选项。");
            empty.setPadding(host.dp(8), host.dp(12), host.dp(8), host.dp(12));
            panel.addView(empty);
        }

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("引擎设置")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    boolean resume = host.analysisMode;
                    if (host.manualEngine != null) host.manualEngine.stopAnalysis();
                    for (Runnable commit : commits) commit.run();
                    host.appendLog("引擎 UCI 选项已保存。\n");
                    if (resume && host.analysisMode) {
                        host.handler.postDelayed(host::startManualAnalysis, 160L);
                    }
                })
                .setNeutralButton("恢复默认", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                .setOnClickListener(v -> new AlertDialog.Builder(host)
                        .setMessage("是否恢复引擎默认设置？")
                        .setPositiveButton("确定", (d, w) -> {
                            dialog.dismiss();
                            restoreDefaults(options);
                        })
                        .setNegativeButton("取消", null)
                        .show()));
        dialog.show();
        if (dialog.getWindow() != null) {
            int maxHeight = Math.round(host.getResources().getDisplayMetrics().heightPixels * 0.82f);
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, maxHeight);
        }
    }

    /** 应用自己的显示选项，不会向引擎发送 UCI setoption。 */
    private void addAppDisplayOptions(LinearLayout panel, ArrayList<Runnable> commits) {
        LinearLayout outputStepsRow = new LinearLayout(host);
        outputStepsRow.setOrientation(LinearLayout.HORIZONTAL);
        outputStepsRow.setGravity(Gravity.CENTER_VERTICAL);
        outputStepsRow.setPadding(0, host.dp(1), 0, host.dp(1));
        TextView outputStepsName = new TextView(host);
        outputStepsName.setText("引擎输出步数（0为不限制）");
        outputStepsName.setTextSize(14);
        outputStepsName.setMaxLines(2);
        outputStepsName.setTextColor(host.globalBackgroundTextColor());
        outputStepsRow.addView(outputStepsName, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        final EditText outputStepsEdit = new EditText(host);
        outputStepsEdit.setSingleLine(true);
        outputStepsEdit.setText(String.valueOf(host.engineOutputMoveLimit));
        outputStepsEdit.setTextSize(14);
        outputStepsEdit.setSelectAllOnFocus(true);
        outputStepsEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        outputStepsRow.addView(outputStepsEdit, new LinearLayout.LayoutParams(
                0, host.dp(38), 0.55f));
        panel.addView(outputStepsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        commits.add(() -> {
            int value = 0;
            try { value = Integer.parseInt(outputStepsEdit.getText().toString().trim()); }
            catch (Exception ignored) {}
            host.engineOutputMoveLimit = host.clamp(value, 0, 999);
            host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(MainActivity.PREF_ENGINE_OUTPUT_STEPS, host.engineOutputMoveLimit)
                    .apply();
        });

        LinearLayout latestRow = new LinearLayout(host);
        latestRow.setOrientation(LinearLayout.HORIZONTAL);
        latestRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView latestName = new TextView(host);
        latestName.setText("只显示最新深度的信息");
        latestName.setTextSize(14);
        latestName.setTextColor(Color.rgb(30, 40, 35));
        latestRow.addView(latestName, new LinearLayout.LayoutParams(0, host.dp(38), 1f));
        CheckBox latestCheck = new CheckBox(host);
        latestCheck.setChecked(host.latestDepthOnly);
        latestRow.addView(latestCheck, new LinearLayout.LayoutParams(host.dp(54), host.dp(38)));
        panel.addView(latestRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.dp(40)));
        commits.add(() -> {
            host.latestDepthOnly = latestCheck.isChecked();
            host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(MainActivity.PREF_LATEST_DEPTH_ONLY, host.latestDepthOnly)
                    .apply();
            if (host.analysisMode) host.updateGameContent();
        });
    }

    private void restoreDefaults(List<PikafishEngine.EngineOption> options) {
        boolean resume = host.analysisMode;
        if (host.manualEngine != null) host.manualEngine.stopAnalysis();
        if (host.rescoreEngine != null) host.rescoreEngine.stopAnalysis();
        SharedPreferences prefs = host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key != null && key.startsWith(MainActivity.MANUAL_UCI_PREFIX)) editor.remove(key);
        }
        editor.putInt(MainActivity.PREF_ENGINE_OUTPUT_STEPS, 0);
        editor.apply();
        host.engineOutputMoveLimit = 0;
        if (host.manualEngine != null) host.manualEngine.clearSessionOptions();
        if (host.rescoreEngine != null) host.rescoreEngine.clearSessionOptions();
        if (options != null) {
            for (PikafishEngine.EngineOption option : options) {
                if (option == null || option.name == null || option.isButton()) continue;
                String value = option.defaultValue == null ? "" : option.defaultValue;
                if (host.manualEngine != null) host.manualEngine.setLocalOption(option.name, value);
                if (host.rescoreEngine != null) host.rescoreEngine.setLocalOption(option.name, value);
            }
        }
        host.appendLog("引擎设置已恢复为引擎声明的默认值。\n");
        Toast.makeText(host, "已恢复默认设置", Toast.LENGTH_SHORT).show();
        if (resume && host.analysisMode) host.handler.postDelayed(host::startManualAnalysis, 160L);
    }

    private boolean shouldHideOption(PikafishEngine.EngineOption option) {
        if (option == null || option.name == null) return false;
        String key = normalizeUciLabel(option.name);
        return "debuglogfile".equals(key)
                || "numapolicy".equals(key)
                || "ponder".equals(key)
                || "moveoverhead".equals(key)
                || "nodestime".equals(key)
                || "skilllevel".equals(key)
                || "ucilimitstrength".equals(key)
                || "ucielo".equals(key)
                || "luoutput".equals(key);
    }

    private String normalizeUciLabel(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) out.append(ch);
        }
        return out.toString();
    }

    private String localizedOptionName(String raw) {
        String key = normalizeUciLabel(raw);
        if ("threads".equals(key)) return "线程数";
        if ("hash".equals(key)) return "哈希值";
        if ("clearhash".equals(key)) return "清理哈希";
        if ("multipv".equals(key)) return "主变数量";
        if ("matethreatdepth".equals(key)) return "中规判断“杀”回合数";
        if ("repetitionrule".equals(key)) return "棋规";
        if ("asianrule".equals(key)) return "亚规";
        if ("chineserule".equals(key)) return "简易中规";
        if ("skyrule".equals(key)) return "天规";
        if ("computerrule".equals(key)) return "象棋程序竞赛规则";
        if ("yitianrule".equals(key)) return "弈天规则";
        if ("allowchase".equals(key)) return "允许长捉";
        if ("nojudgement".equals(key)) return "无规则";
        if ("drawrule".equals(key)) return "和棋棋规";
        if ("drawasblackwin".equals(key)) return "和棋黑胜";
        if ("drawasredwin".equals(key)) return "和棋红胜";
        if ("drawrepasblackwin".equals(key)) return "和棋循环黑胜";
        if ("drawrepasredwin".equals(key)) return "和棋循环红胜";
        if ("sixtymoverule".equals(key)) return "自然限招";
        if ("rule60maxply".equals(key)) return "自然限招步数";
        if ("scoretype".equals(key)) return "显示分数格式";
        if ("evalfile".equals(key)) return "权重路径";
        return raw == null ? "" : raw;
    }

    private String localizedOptionValue(String raw) {
        String localized = localizedOptionName(raw);
        return localized.length() == 0 ? (raw == null ? "" : raw) : localized;
    }

    private View makeOptionRow(final PikafishEngine.EngineOption option,
                               final ArrayList<Runnable> commits) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.dp(1), 0, host.dp(1));
        TextView name = new TextView(host);
        name.setText(localizedOptionName(option.name));
        name.setTextSize(14);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTextColor(Color.rgb(30, 40, 35));
        row.addView(name, new LinearLayout.LayoutParams(0, host.dp(38), 1.35f));

        String current = option.currentValue.length() > 0
                ? option.currentValue : option.defaultValue;
        if (option.isButton()) {
            Button run = host.compactButton("执行");
            run.setOnClickListener(v -> executeButtonOption(option));
            row.addView(run, new LinearLayout.LayoutParams(0, host.dp(34), 0.55f));
        } else if (option.isCheck()) {
            final CheckBox check = new CheckBox(host);
            check.setChecked("true".equalsIgnoreCase(current) || "1".equals(current));
            commits.add(() -> saveUciOption(option.name,
                    check.isChecked() ? "true" : "false"));
            row.addView(check, new LinearLayout.LayoutParams(0, host.dp(34), 0.55f));
        } else if (option.isCombo() && !option.vars.isEmpty()) {
            final Spinner spinner = new Spinner(host);
            ArrayList<String> displayVars = new ArrayList<String>();
            boolean drawRuleOption = "drawrule".equals(normalizeUciLabel(option.name));
            for (String value : option.vars) {
                displayVars.add(drawRuleOption && "none".equals(normalizeUciLabel(value))
                        ? "无" : localizedOptionValue(value));
            }
            spinner.setAdapter(new ArrayAdapter<String>(host,
                    android.R.layout.simple_spinner_dropdown_item, displayVars));
            int index = option.vars.indexOf(current);
            spinner.setSelection(Math.max(0, index));
            commits.add(() -> {
                int position = spinner.getSelectedItemPosition();
                if (position >= 0 && position < option.vars.size()) {
                    saveUciOption(option.name, option.vars.get(position));
                }
            });
            row.addView(spinner, new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        } else {
            final EditText edit = new EditText(host);
            edit.setSingleLine(true);
            edit.setText(current);
            edit.setTextSize(14);
            edit.setSelectAllOnFocus(true);
            if (option.isSpin()) edit.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
            commits.add(() -> saveUciOption(option.name, edit.getText().toString()));
            row.addView(edit, new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        }
        return row;
    }

    private void executeButtonOption(final PikafishEngine.EngineOption option) {
        final boolean resume = host.analysisMode;
        host.manualEngine.executeLocalButton(option.name, new PikafishEngine.Callback() {
            @Override public void onBestMove(String bestMove, String rawInfo) {
                host.handler.post(() -> {
                    Toast.makeText(host, "已执行 " + localizedOptionName(option.name),
                            Toast.LENGTH_SHORT).show();
                    if (resume && host.analysisMode) {
                        host.handler.postDelayed(host::startManualAnalysis, 120L);
                    }
                });
            }

            @Override public void onError(String message) {
                host.handler.post(() -> {
                    Toast.makeText(host, message, Toast.LENGTH_LONG).show();
                    if (resume && host.analysisMode) {
                        host.handler.postDelayed(host::startManualAnalysis, 120L);
                    }
                });
            }
        });
    }

    private void saveUciOption(String name, String value) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.length() == 0) return;
        String safeValue = value == null ? "" : value.trim();
        if ("MultiPV".equalsIgnoreCase(safeName) && host.combinedManualEngineMode) {
            int pv = 1;
            try { pv = Integer.parseInt(safeValue); } catch (Exception ignored) {}
            if (pv > 4) {
                Toast.makeText(host, "二合一之后还选这么多PV，估计没位置显示了。", Toast.LENGTH_LONG).show();
            }
        }
        persistUciOption(safeName, safeValue);
    }

    private void persistUciOption(String safeName, String safeValue) {
        host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putString(MainActivity.MANUAL_UCI_PREFIX + safeName, safeValue)
                .apply();
        host.manualEngine.setLocalOption(safeName, safeValue);
        host.rescoreEngine.setLocalOption(safeName, safeValue);
        if ("MultiPV".equalsIgnoreCase(safeName)) {
            try {
                host.analysisConfiguredMultiPv = Math.max(1, Integer.parseInt(safeValue));
            } catch (Exception ignored) {
                host.analysisConfiguredMultiPv = 1;
            }
            if (host.analysisMode) host.continueManualAnalysisForCurrentPosition(40L);
            host.updateGameContent();
        }
        // 提和使用独立固定配置，不受“引擎设置”影响。
    }
}

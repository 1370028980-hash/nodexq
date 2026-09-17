package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** 已缓存分析数据到引擎页文本/MultiPV 模块的纯展示转换。 */
final class EngineAnalysisPresentation {
    private EngineAnalysisPresentation() {}

    static CharSequence buildSinglePvContent(Context context,
                                             TreeMap<Integer, AnalysisDisplayEntry> entries,
                                             boolean latestDepthOnly, boolean redAtBottom,
                                             String emptyText) {
        if (entries == null || entries.isEmpty()) return emptyText == null ? "" : emptyText;
        SpannableStringBuilder output = new SpannableStringBuilder();
        Iterable<AnalysisDisplayEntry> displayEntries = latestDepthOnly
                ? Collections.<AnalysisDisplayEntry>singleton(entries.lastEntry().getValue())
                : entries.descendingMap().values();
        for (AnalysisDisplayEntry entry : displayEntries) {
            appendFullEntry(context, output, entry, redAtBottom);
        }
        return output;
    }

    static CharSequence buildCompactSinglePvContent(Context context,
                                                    TreeMap<Integer, AnalysisDisplayEntry> entries,
                                                    boolean latestDepthOnly, boolean redAtBottom,
                                                    CharSequence fallback) {
        if (entries == null || entries.isEmpty()) return fallback == null ? "" : fallback;
        SpannableStringBuilder output = new SpannableStringBuilder();
        Iterable<AnalysisDisplayEntry> displayEntries = latestDepthOnly
                ? Collections.<AnalysisDisplayEntry>singleton(entries.lastEntry().getValue())
                : entries.descendingMap().values();
        for (AnalysisDisplayEntry entry : displayEntries) {
            int start = output.length();
            output.append("深度:").append(String.valueOf(entry.depth))
                    .append("  己分:").append(entry.ownScoreText(redAtBottom))
                    .append("  时间:").append(entry.timeText)
                    .append("  NPS:").append(entry.npsText);
            output.setSpan(new ForegroundColorSpan(entry.ownScoreValue(redAtBottom) < 0
                            ? EngineAnalysisPanel.disadvantageColor(context)
                            : EngineAnalysisPanel.advantageColor(context)),
                    start, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            output.append('\n').append(EngineAnalysisPanel.colorMoveUnits(context,
                    entry.cnPv, entry.redToMoveAtRoot)).append("\n\n");
        }
        return output;
    }

    static void appendFullEntry(Context context, SpannableStringBuilder output,
                                AnalysisDisplayEntry entry, boolean redAtBottom) {
        if (entry == null) return;
        int start = output.length();
        output.append("深度:").append(String.valueOf(entry.depth))
                .append("  己分:").append(entry.ownScoreText(redAtBottom))
                .append("  时间:").append(entry.timeText)
                .append("  NPS:").append(entry.npsText)
                .append("  节点数:").append(entry.nodesText);
        if (entry.hashFullText.length() > 0) output.append("  HF:").append(entry.hashFullText);
        output.append("  WDL:").append(entry.ownWdlText(redAtBottom));
        output.append("（").append(String.valueOf(entry.noCaptureMoveCount)).append("）");
        output.setSpan(new ForegroundColorSpan(entry.ownScoreValue(redAtBottom) < 0
                        ? EngineAnalysisPanel.disadvantageColor(context)
                        : EngineAnalysisPanel.advantageColor(context)),
                start, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        output.append('\n').append(EngineAnalysisPanel.colorMoveUnits(context,
                entry.cnPv, entry.redToMoveAtRoot)).append("\n\n");
    }

    static List<EngineAnalysisPanel.DepthModule> buildMultiPvModules(
            TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> entries,
            int expectedMultiPv, boolean latestDepthOnly, boolean redAtBottom,
            List<EngineAnalysisPanel.DepthModule> previousModules) {
        ArrayList<EngineAnalysisPanel.DepthModule> modules =
                new ArrayList<EngineAnalysisPanel.DepthModule>();
        int expected = Math.max(1, expectedMultiPv);
        TreeSet<Integer> depths = completeDepths(entries, expected);
        if (!depths.isEmpty()) {
            Iterable<Integer> displayDepths = latestDepthOnly
                    ? Collections.<Integer>singleton(depths.last()) : depths.descendingSet();
            for (Integer depth : displayDepths) {
                AnalysisDisplayEntry first = entries.get(1).get(depth);
                EngineAnalysisPanel.DepthModule module = new EngineAnalysisPanel.DepthModule(
                        depth, first.timeText, first.npsText, first.nodesText, first.hashFullText);
                for (int pv = 1; pv <= expected; pv++) {
                    AnalysisDisplayEntry entry = entries.get(pv).get(depth);
                    module.pvs.add(new EngineAnalysisPanel.PvItem(pv,
                            entry.ownScoreValue(redAtBottom), entry.ownScoreText(redAtBottom),
                            entry.ownWdlText(redAtBottom), entry.cnPv, entry.redToMoveAtRoot));
                }
                modules.add(module);
            }
        } else if (expected > 1) {
            AnalysisDisplayEntry meta = null;
            int displayDepth = Integer.MAX_VALUE;
            for (int pv = 1; pv <= expected; pv++) {
                TreeMap<Integer, AnalysisDisplayEntry> branch = entries.get(pv);
                if (branch != null && !branch.isEmpty()) {
                    AnalysisDisplayEntry entry = branch.lastEntry().getValue();
                    if (meta == null) meta = entry;
                    displayDepth = Math.min(displayDepth, entry.depth);
                }
            }
            EngineAnalysisPanel.DepthModule module = meta == null
                    ? new EngineAnalysisPanel.DepthModule(0, "-", "-", "-", "")
                    : new EngineAnalysisPanel.DepthModule(
                            displayDepth == Integer.MAX_VALUE ? meta.depth : displayDepth,
                            meta.timeText, meta.npsText, meta.nodesText, meta.hashFullText);
            for (int pv = 1; pv <= expected; pv++) {
                TreeMap<Integer, AnalysisDisplayEntry> branch = entries.get(pv);
                AnalysisDisplayEntry entry = branch == null || branch.isEmpty()
                        ? null : branch.lastEntry().getValue();
                if (entry == null) {
                    module.pvs.add(new EngineAnalysisPanel.PvItem(pv, 0, "-", "-",
                            "等待PV" + pv, true));
                } else {
                    module.pvs.add(new EngineAnalysisPanel.PvItem(pv,
                            entry.ownScoreValue(redAtBottom), entry.ownScoreText(redAtBottom),
                            entry.ownWdlText(redAtBottom), entry.cnPv, entry.redToMoveAtRoot));
                }
            }
            modules.add(module);
        }
        if (!modules.isEmpty()) return modules;
        return previousModules == null ? Collections.<EngineAnalysisPanel.DepthModule>emptyList()
                : previousModules;
    }

    static TreeSet<Integer> completeDepths(
            TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> entries, int expectedMultiPv) {
        TreeSet<Integer> depths = new TreeSet<Integer>();
        TreeMap<Integer, AnalysisDisplayEntry> first = entries.get(1);
        if (first == null || first.isEmpty()) return depths;
        depths.addAll(first.keySet());
        for (int multiPv = 2; multiPv <= Math.max(1, expectedMultiPv); multiPv++) {
            TreeMap<Integer, AnalysisDisplayEntry> branch = entries.get(multiPv);
            if (branch == null || branch.isEmpty()) {
                depths.clear();
                return depths;
            }
            depths.retainAll(branch.keySet());
            if (depths.isEmpty()) return depths;
        }
        return depths;
    }
}

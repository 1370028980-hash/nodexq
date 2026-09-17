package com.tyl.xiangqi.ndxq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 棋谱分支树的标签和嵌套关系维护。
 *
 * <p>本类只修改传入的分支容器；棋盘重建、引擎停止和界面刷新仍由调用方处理。</p>
 */
final class ManualBranchManager {
    private final Map<Integer, List<ManualVariation>> variations;
    private final Map<Integer, String> activeLabels;

    ManualBranchManager(Map<Integer, List<ManualVariation>> variations,
                        Map<Integer, String> activeLabels) {
        this.variations = variations;
        this.activeLabels = activeLabels;
    }

    static String labelForOrdinal(int index) {
        int value = Math.max(0, index) + 1;
        StringBuilder label = new StringBuilder();
        while (value > 0) {
            value--;
            label.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return label.toString();
    }

    static int labelOrdinal(String label) {
        if (label == null || label.length() == 0) return Integer.MAX_VALUE;
        int value = 0;
        for (int i = 0; i < label.length(); i++) {
            char ch = Character.toUpperCase(label.charAt(i));
            if (ch < 'A' || ch > 'Z') return Integer.MAX_VALUE;
            value = value * 26 + (ch - 'A' + 1);
        }
        return value - 1;
    }

    void ensureLabels(int node) {
        List<ManualVariation> branches = variations.get(node);
        String active = activeLabels.get(node);
        if (active == null || active.length() == 0) active = "A";
        TreeSet<String> used = new TreeSet<String>();
        used.add(active);
        if (branches != null) {
            for (ManualVariation variation : branches) {
                if (variation == null) continue;
                String label = variation.label;
                if (label == null || label.length() == 0 || used.contains(label)) {
                    int ordinal = 0;
                    do {
                        label = labelForOrdinal(ordinal++);
                    } while (used.contains(label));
                    variation.label = label;
                }
                used.add(variation.label);
            }
            Collections.sort(branches, (a, b) -> Integer.compare(
                    labelOrdinal(a == null ? null : a.label),
                    labelOrdinal(b == null ? null : b.label)));
        }
        if (branches != null && !branches.isEmpty()) activeLabels.put(node, active);
    }

    void renumberLabels(int node) {
        List<ManualVariation> branches = variations.get(node);
        String active = activeLabels.get(node);
        if (active == null || active.length() == 0) active = "A";
        ArrayList<LabelledBranch> items = new ArrayList<LabelledBranch>();
        items.add(new LabelledBranch(active, null, true));
        if (branches != null) {
            for (ManualVariation variation : branches) {
                if (variation != null) items.add(new LabelledBranch(variation.label, variation, false));
            }
        }
        Collections.sort(items, (a, b) -> Integer.compare(labelOrdinal(a.label), labelOrdinal(b.label)));
        for (int i = 0; i < items.size(); i++) {
            LabelledBranch item = items.get(i);
            String label = labelForOrdinal(i);
            if (item.active) activeLabels.put(node, label);
            else item.variation.label = label;
        }
        if (branches == null || branches.isEmpty()) activeLabels.remove(node);
    }

    String activeLabel(int node) {
        ensureLabels(node);
        String label = activeLabels.get(node);
        return label == null || label.length() == 0 ? "A" : label;
    }

    String nextAvailableLabel(int node) {
        TreeSet<String> used = new TreeSet<String>();
        String active = activeLabels.get(node);
        if (active != null && active.length() > 0) used.add(active);
        List<ManualVariation> branches = variations.get(node);
        if (branches != null) {
            for (ManualVariation variation : branches) {
                if (variation != null && variation.label != null && variation.label.length() > 0) {
                    used.add(variation.label);
                }
            }
        }
        for (int i = 0; ; i++) {
            String candidate = labelForOrdinal(i);
            if (!used.contains(candidate)) return candidate;
        }
    }

    void captureDescendants(ManualVariation target, int node, int endExclusive) {
        if (target == null) return;
        ArrayList<Integer> keys = new ArrayList<Integer>(variations.keySet());
        for (Integer key : keys) {
            if (key == null || key <= node || key >= endExclusive) continue;
            List<ManualVariation> nested = variations.remove(key);
            if (nested != null && !nested.isEmpty()) target.variations.put(key - node, nested);
            String label = activeLabels.remove(key);
            if (label != null && label.length() > 0) {
                target.activeBranchLabels.put(key - node, label);
            }
        }
    }

    void restoreDescendants(ManualVariation source, int node) {
        if (source == null) return;
        for (Map.Entry<Integer, List<ManualVariation>> entry : source.variations.entrySet()) {
            Integer relative = entry.getKey();
            if (relative == null || relative <= 0 || relative >= source.engineSteps.size()
                    || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            int absolute = node + relative;
            variations.put(absolute, entry.getValue());
            String label = source.activeBranchLabels.get(relative);
            if (label != null && label.length() > 0) activeLabels.put(absolute, label);
            ensureLabels(absolute);
        }
        source.variations.clear();
        source.activeBranchLabels.clear();
    }

    void removeDescendants(int node, int endExclusive) {
        ArrayList<Integer> keys = new ArrayList<Integer>(variations.keySet());
        for (Integer key : keys) {
            if (key != null && key > node && key < endExclusive) variations.remove(key);
        }
        keys = new ArrayList<Integer>(activeLabels.keySet());
        for (Integer key : keys) {
            if (key != null && key > node && key < endExclusive) activeLabels.remove(key);
        }
    }

    private static final class LabelledBranch {
        final String label;
        final ManualVariation variation;
        final boolean active;

        LabelledBranch(String label, ManualVariation variation, boolean active) {
            this.label = label;
            this.variation = variation;
            this.active = active;
        }
    }
}

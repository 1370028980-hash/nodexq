#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V18.3 report update (no Android build required)."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq"
MAIN = JAVA / "MainActivity.java"
REPORT = JAVA / "core/GameReportCalculator.java"
CHART = JAVA / "ui/V68SituationChartView.java"
GRADLE = ROOT / "app/build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

main = MAIN.read_text('utf-8')
report = REPORT.read_text('utf-8')
chart = CHART.read_text('utf-8')
gradle = GRADLE.read_text('utf-8')
changelog = CHANGELOG.read_text('utf-8')

assert 'private static final String VERSION_NAME = "V18.3"' in main
assert '节点象棋 V18.3' in main
assert "versionCode 36" in gradle and "versionName '18.3'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V18.3 — 2026-08-11')

# Report controls and completeness tracking.
assert 'private final List<Boolean> scoreKnown' in main
assert 'private boolean initialScoreKnown' in main
assert 'new LinearLayout.LayoutParams(dp(66), dp(28))' in main
assert 'reportButton = compactButton("报告")' in main
assert 'Color.rgb(166, 171, 168)' in main
assert 'reportButton.setEnabled(complete)' in main
assert 'scoreInitialPositionForRescore' in main
assert 'Collections.<String>emptyList()' in main
assert 'root.put("schema", 5)' in main
assert 'root.put("scoreKnown", booleansToJson(scoreKnown))' in main
assert 'root.put("initialScoreKnown", initialScoreKnown)' in main

# Calculator follows supplied piecewise loss mapping / mate handling / phases.
assert 'ERROR_LOSS_THRESHOLD = 1000d' in report
assert 'OPENING_WEIGHT = 0.236d' in report
assert 'MIDDLEGAME_WEIGHT = 0.761d' in report
assert 'ENDGAME_WEIGHT = 0.019d' in report
assert 'if (loss <= 50d) return 100d;' in report
assert 'linear(loss, 50d, 150d, 100d, 95d)' in report
assert 'linear(loss, 150d, 500d, 95d, 80d)' in report
assert 'linear(loss, 500d, 1000d, 80d, 55d)' in report
assert 'linear(capped, 1000d, 9999d, 55d, 0d)' in report
assert 'mateDiscount(beforeMate)' in report
assert 'Math.max(11, endgameRound)' in report
assert 'if (!Boolean.TRUE.equals(scoreKnown.get(i))) return Report.incomplete();' in report
assert 'weightSum <= 0d ? Double.NaN : weighted / weightSum' in report
assert 'initialRedToMove ? (i % 2) == 0 : (i % 2) != 0' in report

# Exact endgame material rule: each side has <=2 rooks/knights/cannons.
assert 'return red <= 2 && black <= 2;' in main

# Error annotations only arrive through a complete report.
assert 'report.complete ? report.errorPlies : Collections.<Integer>emptyList()' in main
assert 'drawErrorMarkers' in chart
assert 'canvas.drawText("错"' in chart
assert re.search(r'canvas\.drawLine\(x, y, x - arrow', chart)

# Live update path from analysis -> scoreKnown -> chart/report refresh.
assert 'scoreKnown.set(index, true);' in main
assert 'refreshOpenReportDialog(report);' in main
assert 'if (selectedGameTab == 2) refreshSituationChart();' in main

# Basic XML sanity.
ET.parse(MANIFEST)

# Basic Java lexical sanity: braces/quoted strings while ignoring comments.
def balanced_java(text: str) -> bool:
    state = 'code'
    escaped = False
    depth = 0
    i = 0
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ''
        if state == 'code':
            if ch == '"': state, escaped = 'string', False
            elif ch == "'": state, escaped = 'char', False
            elif ch == '/' and nxt == '/': state, i = 'line', i + 1
            elif ch == '/' and nxt == '*': state, i = 'block', i + 1
            elif ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth < 0: return False
        elif state in ('string', 'char'):
            if ch == '\n': return False
            if escaped: escaped = False
            elif ch == '\\': escaped = True
            elif (state == 'string' and ch == '"') or (state == 'char' and ch == "'"):
                state = 'code'
        elif state == 'line':
            if ch == '\n': state = 'code'
        elif state == 'block' and ch == '*' and nxt == '/':
            state, i = 'code', i + 1
        i += 1
    return depth == 0 and state in ('code', 'line')

for java in JAVA.rglob('*.java'):
    assert balanced_java(java.read_text('utf-8')), f'unbalanced Java source: {java}'

print('V18.3 source/resource assertions passed.')

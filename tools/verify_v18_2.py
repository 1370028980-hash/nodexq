#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V18.2 update (no Android build required)."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq"
MAIN = JAVA / "MainActivity.java"
RESCORE = JAVA / "ui/RescoreOptionsDialog.java"
ANALYSIS = JAVA / "ui/EngineAnalysisPanel.java"
GRADLE = ROOT / "app/build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

main = MAIN.read_text('utf-8')
rescore = RESCORE.read_text('utf-8')
analysis = ANALYSIS.read_text('utf-8')
gradle = GRADLE.read_text('utf-8')
changelog = CHANGELOG.read_text('utf-8')

assert 'private static final String VERSION_NAME = "V18.2"' in main
assert "versionCode 35" in gradle and "versionName '18.2'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V18.2 — 2026-08-10')

# MultiPV: no horizontal scroll/fixed 108dp column; all PV columns use equal weight.
assert 'import android.widget.HorizontalScrollView' not in analysis
assert 'new HorizontalScrollView' not in analysis
assert re.search(r'pvRow\.addView\(pvColumn\(activity, module\.pvs\.get\(p\), p > 0\),\s*new LinearLayout\.LayoutParams\(0,\s*ViewGroup\.LayoutParams\.WRAP_CONTENT, 1f\)\)', analysis, re.S)
assert 'PvColumnLayout' in analysis
assert 'MoveSequenceView' in analysis
assert 'widest > available' in analysis
assert "out.append('\\n')" in analysis
assert 'keepMoveUnitsTogether' in analysis
assert 'EngineAnalysisPanel.keepMoveUnitsTogether(entry.cnPv)' in main

# Rescore dialog: aligned rows; no initial time selection; click selects all.
assert 'LABEL_WIDTH_DP = 124' in rescore
assert 'timeInput.setSelectAllOnFocus(false)' in rescore
assert 'timeInput.setOnClickListener(v -> timeInput.selectAll())' in rescore
assert 'panel.requestFocus()' in rescore
assert 'timeInput.requestFocus()' not in rescore
assert re.search(r'directionGroup\.addView\(forward, new RadioGroup\.LayoutParams\(\s*0,.*?1f\)\)', rescore, re.S)
assert re.search(r'directionGroup\.addView\(backward, new RadioGroup\.LayoutParams\(\s*0,.*?1f\)\)', rescore, re.S)

# Basic XML sanity.
ET.parse(MANIFEST)

# Basic Java lexical sanity: braces while ignoring comments and quoted literals.
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
    assert balanced_java(java.read_text('utf-8')), f'unbalanced braces: {java}'

print('V18.2 source/resource assertions passed.')

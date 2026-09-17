#!/usr/bin/env python3
"""Lightweight source assertions for the V17.4 maintenance update."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main_path = root / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java"
engine_path = root / "app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java"
main = main_path.read_text(encoding="utf-8")
engine = engine_path.read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V17.4"' in main
assert 'versionCode 28' in gradle and "versionName '17.4'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.4 — 2026-07-23')

# Menu is left of New and menu actions follow the requested order.
toolbar = main[main.index('private View buildGameToolbar()'):main.index('private void showGameActionMenu()')]
assert toolbar.index('Icon.MENU') < toolbar.index('Icon.PAPER')
menu = main[main.index('private void showGameActionMenu()'):main.index('private Button menuActionButton')]
ordered = ['menuActionButton("提和"', 'menuActionButton("认输"', 'menuActionButton("悔棋"',
           'menuActionButton("编辑"', 'menuActionButton("打开文件"',
           'menuActionButton("保存棋谱"', 'menuActionButton("设置"']
positions = [menu.index(x) for x in ordered]
assert positions == sorted(positions), positions
assert 'ScrollView' not in menu

# Immediate move never executes an interim PV and only acts on final bestmove.
assert 'IMMEDIATE_MOVE_TARGET_DEPTH = 15' in main
assert 'IMMEDIATE_MOVE_MIN_THINK_MS = 180L' in main
assert 'IMMEDIATE_MOVE_MAX_THINK_MS = 700L' in main
immediate = main[main.index('private void immediateMove()'):main.index('private void forceAlternativeMove()')]
assert 'getCurrentAnalysisElapsedMs()' in immediate
assert 'requestCurrentAnalysisBestMove()' in immediate
assert 'latestAnalysisDepth >= IMMEDIATE_MOVE_TARGET_DEPTH' in immediate
assert 'executeImmediateAnalysisMove();' not in main[main.index('public void onInfo'):main.index('@Override public void onBestMove', main.index('public void onInfo'))]
assert 'normalizedBestMove.length() >= 4' in main
assert '采用分析引擎最终着法' in immediate

# Engine timestamps the real go command and distinguishes requested stop from compatibility cycles.
assert 'private volatile long analysisSearchStartedAtMs;' in engine
assert 'private volatile boolean analysisBestMoveRequested;' in engine
assert 'public long getCurrentAnalysisElapsedMs()' in engine
assert 'analysisSearchStartedAtMs = System.currentTimeMillis();' in engine
assert 'analysisBestMoveRequested = true;' in engine
assert '&& !requestedBestMove' in engine

# Lightweight Java lexical sanity check without Android SDK.
def balanced_java_braces(text):
    stack = []
    i = 0
    state = 'code'
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ''
        if state == 'code':
            if ch == '/' and nxt == '/': state = 'line'; i += 2; continue
            if ch == '/' and nxt == '*': state = 'block'; i += 2; continue
            if ch == '"': state = 'string'; i += 1; continue
            if ch == "'": state = 'char'; i += 1; continue
            if ch == '{': stack.append(ch)
            elif ch == '}':
                if not stack: return False
                stack.pop()
            i += 1
        elif state == 'line':
            if ch == '\n': state = 'code'
            i += 1
        elif state == 'block':
            if ch == '*' and nxt == '/': state = 'code'; i += 2
            else: i += 1
        else:
            quote = '"' if state == 'string' else "'"
            if ch == '\\': i += 2
            elif ch == quote: state = 'code'; i += 1
            else: i += 1
    return not stack and state in ('code', 'line')

for java_path in (root / 'app/src/main/java').rglob('*.java'):
    assert balanced_java_braces(java_path.read_text(encoding='utf-8')), java_path

# Existing V17 file/manual and activity integration remain intact.
assert 'XqfManualUtils.parse' in main and 'XqfManualUtils.exportSimpleXqf' in main
manifest_path = root / 'app/src/main/AndroidManifest.xml'
ET.parse(manifest_path)
for xml in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
print('V17.4 source assertions passed.')

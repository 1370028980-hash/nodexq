#!/usr/bin/env python3
"""Lightweight source assertions for the V17.5 responsiveness update."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main_path = root / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java'
engine_path = root / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java'
main = main_path.read_text(encoding='utf-8')
engine = engine_path.read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V17.5"' in main
assert 'versionCode 29' in gradle and "versionName '17.5'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.5 — 2026-07-23')

# Immediate move: no fixed time/depth gate; stop is requested as soon as the search is active.
assert 'IMMEDIATE_MOVE_TARGET_DEPTH' not in main
assert 'IMMEDIATE_MOVE_MIN_THINK_MS' not in main
assert 'IMMEDIATE_MOVE_MAX_THINK_MS' not in main
immediate = main[main.index('private void immediateMove()'):main.index('private void forceAlternativeMove()')]
assert 'getCurrentAnalysisElapsedMs()' not in immediate
assert 'requestCurrentAnalysisBestMove()' in immediate
assert 'handler.postDelayed(immediateMoveStopCheck, IMMEDIATE_MOVE_RETRY_MS)' in immediate
assert 'executeImmediateAnalysisMove();' not in main[main.index('public void onInfo'):main.index('@Override public void onBestMove', main.index('public void onInfo'))]
assert 'normalizedBestMove.length() >= 4' in main

# Next position starts without the old 20ms artificial gap.
move_commit = main[main.index('private void commitNewMove'):main.index('@Override\n    public void onMessage')]
assert 'scheduleManualAnalysisRestart(0L)' in move_commit

# Dense info callbacks are batched, while bestmove remains a direct handler callback.
assert 'ANALYSIS_UI_BATCH_MS = 16L' in main
assert 'enqueueAnalysisUiUpdate(generation, positionKey, redToMove, info);' in main
assert 'private void drainAnalysisUiBatch()' in main
assert 'pendingAnalysisUiUpdates.add' in main
assert 'handler.post(() -> {' in main[main.index('@Override public void onBestMove'):main.index('@Override public void onError', main.index('@Override public void onBestMove'))]

# Normal continuous analysis no longer pays an isready round-trip on every position.
start = engine[engine.index('public synchronized void startAnalysis(final String baseFen'):engine.index('private List<String> sanitizeSearchMoves')]
assert 'consumeNewGameIfNeeded();' in start
normal_transition = start[start.index('consumeNewGameIfNeeded();'):start.index('sendPosition(baseFen, moves);')]
assert 'syncReady(' not in normal_transition
assert 'if (cmd.equals(lastSentEvalFileCmd)) return;' in engine
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

assert 'XqfManualUtils.parse' in main and 'XqfManualUtils.exportSimpleXqf' in main
ET.parse(root / 'app/src/main/AndroidManifest.xml')
for xml in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
print('V17.5 source assertions passed.')

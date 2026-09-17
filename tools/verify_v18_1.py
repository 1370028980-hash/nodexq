#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V18.1 update (no Android build required)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq"
MAIN = JAVA / "MainActivity.java"
RESCORE = JAVA / "ui/RescoreOptionsDialog.java"
STORE = JAVA / "ui/ManualStoreDialog.java"
ANALYSIS = JAVA / "ui/EngineAnalysisPanel.java"
FILE_IO = JAVA / "storage/ManualFileIo.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"

main = MAIN.read_text('utf-8')
rescore = RESCORE.read_text('utf-8')
store = STORE.read_text('utf-8')
analysis = ANALYSIS.read_text('utf-8')
file_io = FILE_IO.read_text('utf-8')
manifest = MANIFEST.read_text('utf-8')
gradle = GRADLE.read_text('utf-8')
changelog = CHANGELOG.read_text('utf-8')

assert 'private static final String VERSION_NAME = "V18.1"' in main
assert "namespace 'com.tyl.xiangqi.ndxq'" in gradle
assert "versionCode 34" in gradle and "versionName '18.1'" in gradle
assert not (JAVA / 'core/CbrCblManualUtils.java').exists()
for text in (main, rescore, store, analysis, file_io, manifest):
    assert not re.search(r'(?i)\\bcbr\\b|\\bcbl\\b', text)
assert 'application/x-chess-pgn' in file_io and 'application/x-xqf' in file_io
assert 'text/plain' not in re.search(r'createSaveIntent.*?return intent;', file_io, re.S).group(0)
assert 'return hasAlternative ? activeBranchLabel(node) : "";' in main
assert '从前往后' in rescore and '从后往前' in rescore
assert 'crossButton' not in rescore and 'selectAll()' in rescore
assert 'EngineAnalysisPanel.render' in main
assert '节点数:' in analysis and 'DashedDivider' in analysis
assert '己分:' in analysis and 'WDL:' in analysis
assert 'redAtBottom' in main and 'ownScoreText' in main and 'ownWdlText' in main
assert changelog.startswith('# 节点象棋更新日志\n\n## V18.1 — 2026-08-10')

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

print('V18.1 source/resource assertions passed.')

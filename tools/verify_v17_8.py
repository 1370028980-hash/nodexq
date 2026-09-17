#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V17.8 update."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java"
BOARD = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java"
ARROW = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ThoughtArrowRenderer.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"

main = MAIN.read_text(encoding="utf-8")
board = BOARD.read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
changelog = CHANGELOG.read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V17.8"' in main
assert "versionCode 32" in gradle and "versionName '17.8'" in gradle
assert changelog.startswith("# 节点象棋更新日志\n\n## V17.8 — 2026-08-06")
assert 'DifficultyProfile.nodes("市县好手", "duf", 8, 2, 15000)' in main

assert 'NODE_ROOT_DIR_NAME = "nodexq"' in main
assert 'RECENT_DIR_NAME = "recent"' in main
assert 'Environment.getExternalStorageDirectory()' in main
assert 'saveCompletedDuelToRecent(type)' in main
assert 'buildStoredManualBytes(STORE_FORMAT_XQF)' in main
assert 'new SimpleDateFormat("yyMMddHHmmss", Locale.CHINA)' in main
assert 'setMessage("分析模式已有棋谱，是否覆盖？")' in main
assert 'setMessage("对弈模式已有未完成对局，是否覆盖？")' in main
assert 'if (completedDuelGame)' in main and 'showLauncherScreen();' in main

launcher_order = [main.index('largeButton("分析模式"'),
                  main.index('largeButton("最近对局"'),
                  main.index('largeButton("设置"')]
assert launcher_order == sorted(launcher_order)
assert 'menuActionButton(selfAnalysisMode ? "进入对弈模式" : "进入分析模式"' in main
assert re.search(r'if \(selfAnalysisMode\) \{\s*text = "";', main)

for forbidden in ("ThoughtArrowRenderer", "AnalysisArrow", "setAnalysisArrows",
                  "setShowArrow", "棋盘显示箭头", "showEngineArrows"):
    assert forbidden not in main
    assert forbidden not in board
assert not ARROW.exists()

assert 'android.permission.MANAGE_EXTERNAL_STORAGE' in manifest
assert 'android.permission.WRITE_EXTERNAL_STORAGE' in manifest
assert 'android:requestLegacyExternalStorage="true"' in manifest
ET.parse(MANIFEST)

# Fast lexical smoke check for accidentally unbalanced Java delimiters.
def balanced(text: str) -> bool:
    stack = []
    pairs = {'}': '{', ')': '(', ']': '['}
    i = 0
    quote = None
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ''
        if quote:
            if c == '\\':
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in ('"', "'"):
            quote = c
        elif c == '/' and n == '/':
            i = text.find('\n', i + 2)
            if i < 0:
                return not stack
            continue
        elif c == '/' and n == '*':
            end = text.find('*/', i + 2)
            if end < 0:
                return False
            i = end + 2
            continue
        elif c in '{([':
            stack.append(c)
        elif c in '})]':
            if not stack or stack.pop() != pairs[c]:
                return False
        i += 1
    return quote is None and not stack

assert balanced(main)
assert balanced(board)
print("V17.8 source/resource assertions passed.")

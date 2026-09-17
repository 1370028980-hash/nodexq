#!/usr/bin/env python3
"""Lightweight source assertions for the V17 feature update."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
board = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
toolbar = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ToolbarIconButton.java').read_text(encoding='utf-8')
xqf = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/XqfManualUtils.java').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
manifest_path = root / 'app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text(encoding='utf-8')
changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V17"' in main
assert 'versionCode 24' in gradle and "versionName '17'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17 — 2026-07-21')

assert 'XqfManualUtils.parse' in main and 'XqfManualUtils.exportSimpleXqf' in main
assert 'ACTION_OPEN_DOCUMENT' in main and 'ACTION_CREATE_DOCUMENT' in main
assert 'XQF 格式' in main and 'PGN 格式' in main and '选择路径' in main
assert 'format == STORE_FORMAT_PGN ? "text/plain" : "application/octet-stream"' in main
assert 'android.intent.action.VIEW' in manifest and 'application/x-xqf' in manifest
assert 'android:launchMode="singleTop"' in manifest
assert '.*\\\\.xqf' in manifest and '.*\\\\.pgn' in manifest

assert 'return Math.min(currentPly - 1' in main
assert 'ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f' in main
assert 'ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f' in main
assert 'buildManualCommentEditor()' in main and 'moveComments' in main

assert 'setOnLongClickListener' in main and '复制局面", "复制棋谱", "粘贴' in main
assert 'ViewConfiguration.getLongPressTimeout()' in board and 'performLongClick()' in board
assert 'OPEN, SAVE' in toolbar and 'drawOpen' in toolbar and 'drawSave' in toolbar

assert '棋盘显示箭头' in main and 'show_engine_arrows' in main
assert 'boardView.setShowArrow(true)' not in main and 'boardView.setShowArrow(false)' not in main
assert 'showCenteredToast("重新打分 ' in main
assert 'buildSituationView()' in main and 'buildManualNavigationBar()' in main
assert 'buildBoardScalePreviewManualArea()' in main
assert 'ViewGroup.LayoutParams.MATCH_PARENT, dp(66)' in main
assert 'ViewGroup.LayoutParams.MATCH_PARENT, dp(36)' in main

assert 'public static PgnManualUtils.ParsedManual parse' in xqf
assert 'public static byte[] exportSimpleXqf' in xqf
assert 'out.hasExplicitFen = true' in xqf
ET.parse(manifest_path)
print('V17 source assertions passed.')

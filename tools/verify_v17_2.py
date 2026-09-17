#!/usr/bin/env python3
"""Lightweight source assertions for the V17.2 maintenance update."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java").read_text(encoding="utf-8")
board = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java").read_text(encoding="utf-8")
toolbar = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ToolbarIconButton.java").read_text(encoding="utf-8")
pgn = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/core/PgnManualUtils.java").read_text(encoding="utf-8")
xqf = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/core/XqfManualUtils.java").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
manifest_path = root / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V17.2"' in main
assert 'versionCode 26' in gradle and "versionName '17.2'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.2 — 2026-07-21')

# Keep the V17/V17.1 file and branch features intact.
assert 'XqfManualUtils.parse' in main and 'XqfManualUtils.exportSimpleXqf' in main
assert 'ACTION_OPEN_DOCUMENT' in main and 'ACTION_CREATE_DOCUMENT' in main
assert 'XQF 格式' in main and 'PGN 格式' in main and '选择路径' in main
assert 'android:launchMode="singleTask"' in manifest
assert 'android:documentLaunchMode="never"' in manifest
assert 'onNewIntent' in main and 'handleIncomingManualIntent(intent)' in main
assert r'.*\\.xqf' in manifest and r'.*\\.pgn' in manifest
assert '"第" + currentPly + "步的分支"' in main
assert 'captureDescendantBranches' in main and 'restoreDescendantBranches' in main
assert 'branchScrollY = branchScrollView.getScrollY()' in main
assert 'restoreBranchScroll.scrollTo(0, restoreBranchY)' in main
assert 'final Map<Integer, List<VariationLine>> variations' in pgn
assert 'parseDirectVariations' in xqf
assert 'setOnLongClickListener' in main and '复制局面", "复制棋谱", "粘贴' in main
assert 'OPEN, SAVE' in toolbar

# Comment editing uses a popup and the inline field cannot summon IME.
assert 'showManualCommentDialog' in main
assert 'manualCommentEdit.setFocusable(false)' in main
assert 'SOFT_INPUT_ADJUST_RESIZE' in main
assert 'keyboard.showSoftInput(editor' in main
assert 'private String initialComment = "";' in main
assert 'saved.initialComment = root.optString("initialComment", "")' in main

# Board scaling uses the real game page; obsolete preview builders are gone.
assert 'handleLauncherAnalysisEntry();' in main
assert 'boardView.setBoardScalePercent(boardScalePercent)' in main
assert 'buildBoardScalePreviewManualArea' not in main
assert 'buildBoardScalePreviewToolbar' not in main
assert '.setCancelable(false)' in main

# One Toast instance is reused and branch switch chatter is removed.
assert 'if (rescoreProgressToast == null)' in main
assert 'rescoreProgressToast.setText(rescoreProgressMessage)' in main
assert 'rescoreProgressToast = Toast.makeText(this, "", Toast.LENGTH_LONG)' in main
assert 'rescoreToastKeepAlive' in main and 'dismissRescoreProgressToast()' in main
assert '已切换到分支 ' not in main

# Requested UCI options are filtered.
for key in ('debuglogfile', 'numapolicy', 'ponder', 'moveoverhead', 'nodestime'):
    assert key in main

# Large boards leave the lower panel reachable without sacrificing child gestures.
assert 'gamePageScroll = new ScrollView(this)' in main
assert 'Math.max(dp(180), available)' in main
assert 'keepNestedScrollGestures(manualScrollView)' in main
assert 'keepNestedScrollGestures(branchScrollView)' in main
assert 'requestDisallowInterceptTouchEvent(true)' in board

ET.parse(manifest_path)
print('V17.2 source assertions passed.')

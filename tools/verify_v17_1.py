#!/usr/bin/env python3
"""Lightweight source assertions for the V17.1 maintenance update."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
pgn = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/PgnManualUtils.java').read_text(encoding='utf-8')
xqf = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/XqfManualUtils.java').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
manifest_path = root / 'app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text(encoding='utf-8')
changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V17.1"' in main
assert 'versionCode 25' in gradle and "versionName '17.1'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.1 — 2026-07-21')

# Current-step-only branch display and delayed nested branches.
assert '"第" + currentPly + "步的分支"' in main
assert 'formatBranchMove' in main and 'formatLinePreview' not in main
assert 'final Map<Integer, List<VariationLine>> variations' in pgn
assert 'parseDirectVariations' in xqf and 'line.variations.put(relativePly + 1' in xqf
assert 'captureDescendantBranches' in main and 'restoreDescendantBranches' in main

# Branch list scroll is restored instead of jumping to the top.
assert 'private int branchScrollY;' in main
assert 'branchScrollY = branchScrollView.getScrollY()' in main
assert 'restoreBranchScroll.scrollTo(0, restoreBranchY)' in main

# External file opening reuses the app task.
assert 'android:launchMode="singleTask"' in manifest
assert 'android:documentLaunchMode="never"' in manifest
assert 'onNewIntent' in main and 'handleIncomingManualIntent(intent)' in main

# Initial-position comment is editable, persisted and round-tripped.
assert 'private String initialComment = "";' in main
assert '初始局面、棋谱简介' in main
assert 'saved.initialComment = root.optString("initialComment", "")' in main
assert 'out.initialComment = sanitizeRootComment' in xqf
assert 'String initialComment' in pgn
assert 'initialComment, exportVariationsForXqf()' in main

ET.parse(manifest_path)
print('V17.1 source assertions passed.')

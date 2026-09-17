#!/usr/bin/env python3
"""Lightweight source assertions for the V17.3 maintenance update."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main_path = root / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java"
engine_path = root / "app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java"
toolbar_path = root / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ToolbarIconButton.java"
main = main_path.read_text(encoding="utf-8")
engine = engine_path.read_text(encoding="utf-8")
toolbar = toolbar_path.read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
manifest_path = root / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V17.3"' in main
assert 'versionCode 27' in gradle and "versionName '17.3'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.3 — 2026-07-23')

# Compact one-row toolbar and a non-scrolling, all-visible text menu.
toolbar_block = main[main.index('private View buildGameToolbar()'):main.index('private void showGameActionMenu()')]
assert 'ToolbarIconButton.Icon.MENU' in toolbar_block
for moved_icon in ('Icon.PEN', 'Icon.OPEN', 'Icon.SAVE', 'Icon.UNDO', 'Icon.WRENCH', 'Icon.GEAR'):
    assert moved_icon not in toolbar_block
for label in ('"编辑"', '"提和"', '"认输"', '"悔棋"', '"设置"', '"打开文件"', '"保存棋谱"'):
    assert label in main
menu_block = main[main.index('private void showGameActionMenu()'):main.index('private Button menuActionButton')]
assert 'new LinearLayout[4]' in menu_block
assert 'ScrollView' not in menu_block
assert 'dp(34 + 30 + 30 + 36)' in main
assert 'MENU' in toolbar and 'drawMenu' in toolbar

# Comment pane uses one third and empty comments have no inline prompt.
assert 'branches.addView(branchScrollView, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));' in main
assert 'branches.addView(buildManualCommentEditor(), commentLp);' in main
assert 'manualCommentEdit.setHint("")' in main
assert 'manualCommentEdit.setMaxLines(12)' in main
assert '点击弹窗编辑' not in main

# Rescoring has one stop control plus inline progress and no Toast progress UI.
assert 'rescoreProgressTextView' in main
assert 'compactButton("停止")' in main
assert 'updateRescoreProgressText()' in main
assert 'rescoreProgressToast' not in main
assert 'showCenteredToast' not in main
assert '打分中' not in main

# Analysis->duel is ineligible for records, and record method has a guard.
analysis_to_duel = main[main.index('private void enterDuelModeFromAnalysis()'):main.index('private void reverseBoard()')]
assert 'competitiveResultEligible = false;' in analysis_to_duel
assert 'resultRecordedForCurrentGame = false;' in analysis_to_duel
assert 'if (selfAnalysisMode || !competitiveResultEligible) return;' in main

# Only the in-game settings menu retains board-size adjustment.
assert main.count('"调整棋盘大小"') == 1
assert 'String[] items = new String[]{"日志信息", "引擎设置", "调整棋盘大小"};' in main

# End dialog contains only textual winner result, without the old victory badge.
end_dialog = main[main.index('private void showUnifiedGameEndDialog'):main.index('private Button endDialogButton')]
assert '"红方胜！"' in end_dialog and '"黑方胜！"' in end_dialog
assert 'badge' not in end_dialog
assert 'setText("胜")' not in end_dialog

# Analysis display is updated in place and rapid immediate move uses stop->bestmove.
assert 'manualAnalysisStarter = Executors.newSingleThreadExecutor()' in main
assert 'engineContentTextView.setText(engineContent)' in main
assert 'manualEngine.requestCurrentAnalysisBestMove()' in main
assert 'immediateMovePending && normalizedBestMove.length() >= 4' in main
assert 'public synchronized boolean requestCurrentAnalysisBestMove()' in engine
assert 'send("stop")' in engine[engine.index('requestCurrentAnalysisBestMove'):engine.index('public synchronized void stopAnalysis()')]
assert '正在分析，请等待出现主要变例后再点击立即出招' not in main

# The selected tab survives mode transitions and manual loading.
assert '.putInt("selected_game_tab", selectedGameTab)' in main
assignments = re.findall(r'^\s*selectedGameTab\s*=\s*([^;]+);', main, re.MULTILINE)
assert len(assignments) == 2, assignments
assert any('sp.getInt("selected_game_tab", 0)' in value for value in assignments)
assert any('clamp(index, 0, 2)' in value for value in assignments)

assert '特别鸣谢：GPT5.6，DeepSeekV4pro。' in main
assert '特别鸣谢：ChatGPT5.6' not in main

# Previous file/manual features remain intact.
assert 'XqfManualUtils.parse' in main and 'XqfManualUtils.exportSimpleXqf' in main
assert 'ACTION_OPEN_DOCUMENT' in main and 'ACTION_CREATE_DOCUMENT' in main
assert 'android:launchMode="singleTask"' in manifest
assert 'android:documentLaunchMode="never"' in manifest
assert 'setOnLongClickListener' in main and '复制局面", "复制棋谱", "粘贴' in main

ET.parse(manifest_path)
for xml in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
print('V17.3 source assertions passed.')

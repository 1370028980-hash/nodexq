#!/usr/bin/env python3
"""Lightweight V18.5 source/resource assertions. Intentionally does not compile Android code."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
PANEL = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/EngineAnalysisPanel.java').read_text(encoding='utf-8')
BOARD = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
CHART = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java').read_text(encoding='utf-8')
REPORT = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/GameReportCalculator.java').read_text(encoding='utf-8')
TOOLBAR = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ToolbarIconButton.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')
SCORE_DOC = (ROOT / '打分算法.txt').read_text(encoding='utf-8')

# Version + release notes.
assert 'private static final String VERSION_NAME = "V18.5"' in MAIN
assert 'versionCode 38' in GRADLE and "versionName '18.5'" in GRADLE
assert CHANGELOG.startswith('# 节点象棋更新日志\n\n## V18.5 — 2026-08-13')

# 1. MultiPV state and placeholder skeleton survive page/navigation rebuilds.
assert 'analysisConfiguredMultiPv' in MAIN
assert 'lastMultiPvModules' in MAIN
assert 'if (multiPv > analysisExpectedMultiPv) continue;' in MAIN
assert '"等待PV" + pv' in MAIN
assert 'new EngineAnalysisPanel.DepthModule(0, "-", "-", "-", "")' in MAIN
assert 'analysisDisplayFrozen' in MAIN
assert 'freezeAnalysisDisplay();' in MAIN
assert 'clearFrozenAnalysisDisplay();' in MAIN
assert 'modulesAtStop = buildMultiPvModules();' in MAIN
assert 'frozenMultiPvModules = copyDepthModules(modulesAtStop);' in MAIN
assert 'if (analysisDisplayFrozen)' in MAIN
assert '停止分析后引擎内容冻结修复' in CHANGELOG

# 2. Situation scoring stays on virtual slot 131 and only adds ComputerRule.
needle = MAIN[MAIN.index('// V18.5 修正：局势图评分继续严格固定虚拟位 131'):MAIN.index('if (!has131)', MAIN.index('// V18.5 修正：局势图评分继续严格固定虚拟位 131'))]
assert 'situationEngine.setVirtualEngineSlot("131")' in needle
assert 'situationEngine.setSessionOption("Repetition Rule", "ComputerRule")' in needle
assert 'situationEngine.setVirtualEngineSlot("")' not in needle

# 3. Error marker is a circle; old error arrow implementation removed.
assert 'final float radius = dp(5.5f);' in CHART
assert 'canvas.drawCircle(x, y, radius, errorPaint);' in CHART
assert 'redBlunder ? Color.rgb(220, 35, 35) : Color.BLACK' in CHART
assert 'initialScoreRed' in CHART and 'initialRedToMove' in CHART
assert 'drawErrorArrow(' not in CHART

# 4. Read-only comment frame fills weighted height and has no max-lines cap.
comment = MAIN[MAIN.index('private View buildManualCommentEditor()'):MAIN.index('private void showManualCommentDialog', MAIN.index('private View buildManualCommentEditor()'))]
assert 'setVerticalScrollBarEnabled(true)' in comment
assert 'setMaxLines' not in comment
assert 'ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f' in comment

# 5. Severe blunder guard is phase-independent; original phase weights stay exact.
for literal in ['0.23567d', '0.76047d', '0.01906d', 'ERROR_LOSS_THRESHOLD = 1000d']:
    assert literal in REPORT
assert 'worstSevereScore' in REPORT
assert 'double severeFactor = 0.70d + 0.30d * (worstSevereScore / 100d);' in REPORT
assert 'V18.5默认τ=15' in SCORE_DOC and '0.70 + 0.30×S/100' in SCORE_DOC

# 6/7. Sound + combined mode menu and compact engine view.
assert '"当前声音:开"' in MAIN and '"当前声音:关"' in MAIN
assert 'menuStayButton' in MAIN and 'combinedManualEngineMode' in MAIN
assert '"棋谱+引擎"' in MAIN
assert 'buildCombinedManualEngineView' in MAIN
assert '二合一之后还选这么多PV，估计没位置显示了。' in MAIN
assert 'renderCompact' in PANEL
assert '深度:' in PANEL and '己分:' in PANEL and '时间:' in PANEL and 'NPS:' in PANEL

# 8. Duel-only independent push board.
assert 'ToolbarIconButton.Icon.PUSH' in MAIN and 'PUSH' in TOOLBAR
assert 'if (!selfAnalysisMode)' in MAIN[MAIN.index('private View buildGameToolbar()'):MAIN.index('private void showGameActionMenu()', MAIN.index('private View buildGameToolbar()'))]
assert 'private void enterPushMode()' in MAIN
assert 'final ChessBoardView pushBoard = new ChessBoardView(this);' in MAIN
assert '退出推演' in MAIN
assert 'if (pushModeActive || selfAnalysisMode' in MAIN

# 9. Move-side coloring exists for normal + MultiPV views.
assert 'colorMoveUnits' in PANEL
assert 'redToMoveAtRoot' in PANEL
assert 'new ForegroundColorSpan(SCORE_RED)' in PANEL
assert 'Color.BLACK' in PANEL
assert 'EngineAnalysisPanel.colorMoveUnits' in MAIN

# 10. Initial score + terminal backfill.
assert 'requestInitialSituationScore' in MAIN
assert 'backfillCompletedDuelScores' in MAIN
assert 'reportBackfillPass >= 3' in MAIN
assert 'initialScoreKnown = true;' in MAIN

# 11. Arrow cycle 0..4 and light continuation arrows; MultiPV remains two-step arrows.
assert 'arrowStepCount = clamp' in MAIN
assert 'arrowStepCount >= 4 ? 0 : arrowStepCount + 1' in MAIN
assert '"箭头显示" + arrowStepCount + "步"' in MAIN
assert 'appendAnalysisArrows(arrows, branch.lastEntry().getValue(), multiPv, 2);' in MAIN
assert 'public final boolean continuation;' in BOARD
assert 'continuationArrowColor' in BOARD

print('V18.5 source/resource assertions passed (no compilation performed).')

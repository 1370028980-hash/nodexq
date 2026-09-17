#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java'
ENGINE = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java'
TOOLBAR = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ToolbarIconButton.java'
PGN = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/PgnManualUtils.java'
GRADLE = ROOT / 'app/build.gradle'

main = MAIN.read_text('utf-8')
engine = ENGINE.read_text('utf-8')
toolbar = TOOLBAR.read_text('utf-8')
pgn = PGN.read_text('utf-8')
gradle = GRADLE.read_text('utf-8')
changelog = (ROOT / 'CHANGELOG.md').read_text('utf-8')

assert 'private static final String VERSION_NAME = "V10.0"' in main
assert 'versionCode 10' in gradle
assert "versionName '10.0'" in gradle

# Three-page navigation and rescore lock.
assert main.count('buildAlignedNavigationRow()') >= 3
assert 'boolean navigationEnabled = !isRescoring;' in main
assert 'button.setEnabled(enabled);' in main

# MultiPV displays only complete same-depth groups.
assert 'completeMultiPvDepths()' in main
assert '正在等待同一深度的' in main
assert 'for (int multiPv = 1; multiPv <= analysisExpectedMultiPv; multiPv++)' in main

# Separate persistent sessions and restore flows.
for token in [
    'SAVED_ANALYSIS_RECORD', 'SAVED_GAME_RECORD', 'persistCurrentSession()',
    'readSavedSession(false)', 'readSavedSession(true)',
    '上次还有未完成的对局，难度为', '新的对局', '继续对局',
    '确定返回初始界面吗？当前棋谱会保留。',
]:
    assert token in main
assert 'protected void onPause()' in main

# 131 scoring and 60-move draw.
assert 'situationEngine.setVirtualEngineSlot("131")' in main
assert 'situationEngine.setSessionOption("Threads", "4")' in main
assert 'PikafishEngine.SearchLimit.movetime(100)' in main
assert 'quiet == 119' in main
assert '60回合不吃子，和棋！' in main
assert 'GameEndType.NO_CAPTURE_DRAW' in main
assert 'root.put("sixtyArm", sixtyMoveDrawArmedPly)' in main

# Unified result dialog and statistics.
for token in ['showUnifiedGameEndDialog', '再来一局', '新的一局',
              '历史战绩', '重置战绩', '确定重置战绩吗？',
              'wins + "胜" + draws + "和" + losses + "负"']:
    assert token in main

# About dialog and gear icon.
assert '感谢 Pikafish、Duffish、PikafishHCE 以及 Tchess' in main
assert 'prepareAboutDialog()' in main
assert 'ToolbarIconButton.Icon.GEAR' in main
assert 'case GEAR:' in toolbar and 'drawGear' in toolbar

assert changelog.index('## V10.0') < changelog.index('## V9.0')
assert '覆盖安装更新后仍保留' in changelog

# V9 terminal scoring, rescore isolation, navigation follow and empty-back behavior.
for token in [
    'resultRecordedForCurrentGame',
    'applyTerminalSituationScore(type)',
    'terminalRedScoreAtPly(index + 1)',
    '重新打分仅更新评分数据，不触发自动走棋或历史战绩结算。',
    'navigateWithManualFollow',
    'scrollManualRowIntoView',
    'if (engineMoves.isEmpty()) {',
]:
    assert token in main
assert 'handler.postDelayed(this::maybeAutoMove, 180L);' not in main[main.index('private void finishRescore'):main.index('private int findFirstEndgameRound')]
assert '红方胜' in (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java').read_text('utf-8')

# V9 follow-up: analysis results are not competitive, and pasted records are exact/atomic.
for token in [
    'competitiveResultEligible',
    'finishAnalysisPosition',
    '本棋谱后续仅作为分析记录，不计入历史战绩',
    'root.put("competitiveEligible", competitiveResultEligible)',
    '粘贴完成后不会自动续走',
    'validatePastedPosition',
]:
    assert token in main
assert 'showUnifiedGameEndDialog(message, type);' in main
assert main.index('if (selfAnalysisMode || !competitiveResultEligible)') < main.index('showUnifiedGameEndDialog(message, type);')

for token in [
    'parseExactLegalMove',
    'XiangqiRules.generateLegalMoves(board, redToMove)',
    '手无法唯一识别或不合法',
    'hasExplicitFen',
    'expandMoveToken',
]:
    assert token in pgn
assert 'if (move == null) continue;' not in pgn
assert '粘贴棋谱或 FEN 后不再自动触发电脑续走' in changelog

# Preserve engine availability repairs.
assert 'hasUsableEngine()' in engine
assert 'boundedCompatibilityMode' in engine
assert '分析过程中引擎已退出' in engine


# V10 direct-new and editor constraints.
for token in [
    '对弈模式下已直接新建对局，难度与先后手保持不变',
    'finishEditMode(true);',
    '将/帅位置不对！',
    'pieceName(piece) + "\\n" + remaining + "/" + max',
    "selectedEditPiece == '-' ? (char) 0 : '-'",
    'editActionView("取消"',
]:
    assert token in main
board_view = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text('utf-8')
rules = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/XiangqiRules.java').read_text('utf-8')
assert "board[r][c] != 'k' && board[r][c] != 'K'" in board_view
assert '数量已达到上限' in board_view
assert 'maxPieceCount' in rules and "case 'p': return 5" in rules
assert '只显示同一深度已收齐的完整 PV1…PVN 组' in changelog

# Lightweight Java lexical sanity check even when javalang is unavailable.
def balanced_java_braces(text):
    stack = []
    i = 0
    state = 'code'
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ''
        if state == 'code':
            if ch == '/' and nxt == '/':
                state = 'line'; i += 2; continue
            if ch == '/' and nxt == '*':
                state = 'block'; i += 2; continue
            if ch == '"':
                state = 'string'; i += 1; continue
            if ch == "'":
                state = 'char'; i += 1; continue
            if ch == '{': stack.append(ch)
            elif ch == '}':
                if not stack: return False
                stack.pop()
            i += 1
        elif state == 'line':
            if ch == '\n': state = 'code'
            i += 1
        elif state == 'block':
            if ch == '*' and nxt == '/':
                state = 'code'; i += 2
            else:
                i += 1
        else:
            quote = '"' if state == 'string' else "'"
            if ch == '\\': i += 2
            elif ch == quote:
                state = 'code'; i += 1
            else:
                i += 1
    return not stack and state in ('code', 'line')

assert balanced_java_braces(main)
assert balanced_java_braces((ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java').read_text('utf-8'))

# Parse all Java files when javalang is available.
try:
    import javalang
except ImportError:
    javalang = None
if javalang is not None:
    java_files = sorted((ROOT / 'app/src/main/java').rglob('*.java'))
    for path in java_files:
        javalang.parse.parse(path.read_text('utf-8'))
    parsed = len(java_files)
else:
    parsed = 0

print(f'V10.0 static verification passed; parsed Java files: {parsed}')

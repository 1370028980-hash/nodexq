#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V17.7 update."""
from pathlib import Path
import wave
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
chart = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V17.7"' in main
assert 'versionCode 31' in gradle and "versionName '17.7'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V17.7 — 2026-07-31')

# Audio resources are the exact WAV files supplied in the source root.
for name in ('move.wav', 'check.wav'):
    source = root / name
    packaged = root / 'app/src/main/res/raw' / name
    assert source.read_bytes() == packaged.read_bytes(), name
    with wave.open(str(packaged), 'rb') as wav:
        assert wav.getnframes() > 0 and wav.getnchannels() in (1, 2)
assert 'R.raw.move' in main and 'R.raw.check' in main
assert 'XiangqiRules.isInCheck(boardView.copyBoard(), redToMoveNow)' in main

# Later-ply navigation plays; earlier-ply navigation does not.
navigate = main[main.index('private void navigateToPly(int target)'):
                main.index('private void rebuildBoardToPly', main.index('private void navigateToPly(int target)'))]
assert 'int previousPly = currentPly;' in navigate
assert 'if (target > previousPly)' in navigate
assert 'target < previousPly' not in navigate

# Existing branch first moves are selected before the new-branch/overwrite paths.
on_move = main[main.index('public void onMoveMade'):
               main.index('private void deleteAllBranchesAtOrAfter', main.index('public void onMoveMade'))]
assert 'findVariationIndexByFirstStep(currentPly, step)' in on_move
assert on_move.index('findVariationIndexByFirstStep(currentPly, step)') < on_move.index('captureFutureAsVariation(currentPly)')
assert on_move.index('findVariationIndexByFirstStep(currentPly, step)') < on_move.index('新走法会覆盖旧走法')
finder = main[main.index('private int findVariationIndexByFirstStep'):
              main.index('private void truncateListsTo', main.index('private int findVariationIndexByFirstStep'))]
assert 'variation.engineSteps.get(0)' in finder and 'equalsIgnoreCase' in finder

# Branch title is gone; container is exactly three branch rows high and scrollable.
branch = main[main.index('private View buildBranchList()'):
              main.index('private void showBranchLongPressDialog', main.index('private View buildBranchList()'))]
assert '初始局面的分支' not in branch and '步的分支' not in branch
assert 'BRANCH_ROW_HEIGHT_DP * MAX_VISIBLE_BRANCH_ROWS' in main
assert 'branchScrollView = new ScrollView(this);' in main

# Comment box has no step title and uses only a centered gray placeholder when empty.
comment = main[main.index('private View buildManualCommentEditor()'):
               main.index('private void showManualCommentDialog', main.index('private View buildManualCommentEditor()'))]
assert '第" + currentPly + "步注释' not in comment
assert 'setHint("注释框")' in comment
assert 'setHintTextColor' in comment
assert 'empty ? Gravity.CENTER : (Gravity.TOP | Gravity.START)' in main

# Chart keeps raw engine scores for labels while still clipping only the Y coordinate.
assert 'this.redScore = redScore;' in chart
assert 'Math.abs(point.redScore) == MATE_LIMIT' in chart
assert 'Math.abs(point.redScore) >= MATE_LIMIT' not in chart
assert 'Math.min(MATE_LIMIT, Math.abs(score))' in chart

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

ET.parse(root / 'app/src/main/AndroidManifest.xml')
for xml in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
print('V17.7 source/resource assertions passed.')

#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
BOARD = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
RECENT = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/storage/RecentGameStore.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

checks = []
def ok(name, condition): checks.append((name, bool(condition)))

ok('versionCode 45', 'versionCode 45' in GRADLE)
ok('versionName 19.2', "versionName '19.2'" in GRADLE)
ok('MainActivity V19.2', 'VERSION_NAME = "V19.2"' in MAIN)
ok('CHANGELOG V19.2', '## V19.2 — 2026-08-16' in CHANGELOG)

# 1. Quality badge must be painted after selection/calibration overlays.
pos_selection = BOARD.find('drawSelection(canvas);')
pos_calibration = BOARD.find('drawCalibrationOverlay(canvas);')
pos_badge_loop = BOARD.find('for (int i = 0; i < topQualityBadges.size(); i++)')
ok('quality badge top-layer collection', 'topQualityBadges.add(a);' in BOARD)
ok('quality badge paints after selection', 0 <= pos_selection < pos_badge_loop)
ok('quality badge paints after calibration', 0 <= pos_calibration < pos_badge_loop)
ok('quality badge remains at piece upper-left', 'ex - piece * 0.43f' in BOARD and 'ey - piece * 0.43f' in BOARD)

# 2. Accessible calibration: selectable four points + directional nudge + editable dp step.
ok('persistent selected calibration handle', 'selectedGridHandle' in BOARD and 'getSelectedGridHandle()' in BOARD)
ok('same point toggles selected state', 'selectedGridHandle == releasedHandle ? -1 : releasedHandle' in BOARD)
ok('selected point visibly thickens', 'selected ? 1.55f : 1f' in BOARD and 'selected ? 1.16f : 1f' in BOARD)
ok('dp nudge API exists', 'nudgeSelectedGridHandle(float dxDp, float dyDp)' in BOARD and 'density / boardDst.width()' in BOARD)
ok('four arrow buttons exist', all(x in MAIN for x in ['compactButton("↑")', 'compactButton("←")', 'compactButton("↓")', 'compactButton("→")']))
ok('calibration default step 5dp', 'calibrationStep.setText("5")' in MAIN and '微调步长(dp)' in MAIN)
ok('calibration step validation', 'value <= 0f || value > 100f' in MAIN)

# 3. RGB direct input with validation and Toast.
ok('RGB input added', 'codeLabel.setText("RGB代码")' in MAIN and '例如 395E42' in MAIN)
ok('RGB parser accepts six hex digits', 'value.matches("(?i)[0-9a-f]{6}")' in MAIN)
ok('RGB invalid Toast exists', MAIN.count('颜色代码无效，请输入 6 位十六进制 RGB，例如 395E42') >= 2)
ok('invalid RGB does not auto-dismiss', '.setPositiveButton("确定", null)' in MAIN and 'picker.dismiss();' in MAIN)

# 4. Recent games show rounds after timestamp, using XQF mainline ply count.
ok('recent record has turn count', 'public final int turnCount;' in RECENT)
ok('recent XQF mainline parsed', 'XqfManualUtils.parse' in RECENT and 'manual.moves.size()' in RECENT)
ok('plies converted to rounds', 'turns = (plies + 1) / 2' in RECENT)
ok('round count appended after timestamp', 'displayStamp\n                + (turns > 0 ? "  " + turns + "回合" : "")' in RECENT)

# 5. DrawRule none localized only for that combo; raw option value remains untouched.
ok('DrawRule none shown as 无', 'drawRuleOption && "none".equals(normalizeUciLabel(value))' in MAIN and '? "无" : localizedEngineOptionValue(value)' in MAIN)
ok('combo saves original UCI var', 'option.vars.get(position)' in MAIN)

# 6. Magnifier preserves and refreshes review overlay instead of clearing it.
ok('analysis arrows merge review arrows', 'arrows.addAll(buildRescoreReviewArrows());' in MAIN)
ok('magnifier restores review immediately', 'updateAnalysisArrows();\n        startReviewRefreshForCurrentPly(generation, positionKey);' in MAIN)
ok('complete review remains valid while live after-score changes', 'boolean refreshExistingReview = hasCompleteRescoreReview(index);' in MAIN and 'rescoreScoreKnown.set(index, true)' in MAIN)
ok('previous-position PV1 short refresh exists', 'startReviewRefreshForCurrentPly' in MAIN and 'PikafishEngine.SearchLimit.movetime(1000)' in MAIN)
ok('previous-position refresh only accepts PV1', 'Math.max(1, info.multiPv) != 1' in MAIN)
ok('live refresh updates recommendation', 'rescoreRecommendedMoves.set(index, recommended);' in MAIN)

# 7. Edit mode double tap delete without removing normal tap handler.
ok('double-tap timeout used', 'ViewConfiguration.getDoubleTapTimeout()' in BOARD)
ok('double-tap delete helper', 'deleteEditPieceAt(cell[0], cell[1]);' in BOARD and 'private void deleteEditPieceAt' in BOARD)
ok('normal tap still proceeds', 'handleCellTap(cell[0], cell[1]);' in BOARD)
ok('normal same-cell deselect remains', 'if (selectedRow == row && selectedCol == col)' in BOARD)

failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(('OK   ' if passed else 'FAIL ') + name)
if failed:
    raise SystemExit('\nV19.2 static verification failed: ' + ', '.join(failed))
print(f'\nV19.2 static verification passed ({len(checks)} checks). No Android build performed.')

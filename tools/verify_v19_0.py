#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
BOARD = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
REPORT = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/GameReportCalculator.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')
THEME = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/UiTheme.java').read_text(encoding='utf-8')
WHEEL = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ColorWheelView.java').read_text(encoding='utf-8')

checks = []
def ok(name, condition):
    checks.append((name, bool(condition)))

ok('versionCode 43', 'versionCode 43' in GRADLE)
ok('versionName 19.0', "versionName '19.0'" in GRADLE)
ok('MainActivity V19.0', 'VERSION_NAME = "V19.0"' in MAIN)
ok('CHANGELOG V19.0', '## V19.0 — 2026-08-16' in CHANGELOG)

ok('skin dialog removes duplicate current skin row', '当前皮肤:' not in MAIN)
ok('skin dialog has global highlight color', '全局高亮色' in MAIN and 'showHighlightColorPicker' in MAIN)
ok('HSV color wheel exists', 'Color.HSVToColor' in WHEEL and 'Math.atan2' in WHEEL)
ok('theme preference exists', 'KEY_HIGHLIGHT_COLOR' in THEME)
ok('theme applied in MainActivity', MAIN.count('highlightColor()') >= 10)

ok('rescore recommendation storage', 'rescoreRecommendedMoves' in MAIN and 'applyRescoreRecommendation' in MAIN)
ok('PV1 filter retained', 'Math.max(1, info.multiPv) == 1' in MAIN)
ok('rescore source-completeness flags', 'rescoreScoreKnown' in MAIN and 'initialRescoreScoreKnown' in MAIN)
ok('review arrow updater exists', 'updateRescoreReviewArrows()' in MAIN)
ok('actual and recommended arrows differ', '!recommended.equals(actual)' in MAIN)
ok('quality thresholds', all(x in MAIN for x in ['loss <= 50d', 'loss <= 150d', 'loss <= 500d', 'loss <= 1000d']))
ok('quality labels', all(x in MAIN for x in ['quality = "★"', 'quality = "优"', 'quality = "中"', 'quality = "差"', 'quality = "错"']))
ok('mate/loss logic shared with report', 'GameReportCalculator.moveLoss' in MAIN and 'public static double moveLoss' in REPORT)
ok('board endpoint labels', 'endpointLabel' in BOARD and 'drawEndpointLabel' in BOARD)
ok('move hint JSON preserves placeholders', 'jsonToMoveHints' in MAIN and 'out.add(value.length() >= 4 ? value : "")' in MAIN)

ok('vertical-only manual clipping view', 'class VerticalClipScrollView extends ScrollView' in MAIN)
ok('both manual scroll layouts use clip view', MAIN.count('manualScrollView = new VerticalClipScrollView(this);') == 2)
ok('session schema 6', 'root.put("schema", 6)' in MAIN)

res = ROOT / 'app/src/main/res/drawable-nodpi'
for name in ['board','br','bn','bb','ba','bk','bc','bp','rr','rn','rb','ra','rk','rc','rp']:
    ok(f'bundled default resource {name}', any((res / f'{name}{ext}').exists() for ext in ('.png','.webp','.jpg','.jpeg')))

failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(('OK   ' if passed else 'FAIL ') + name)
if failed:
    raise SystemExit('\nV19.0 static verification failed: ' + ', '.join(failed))
print(f'\nV19.0 static verification passed ({len(checks)} checks). No compilation performed.')

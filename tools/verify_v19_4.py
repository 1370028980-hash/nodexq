#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
BOARD = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
CHART = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

checks = []
def ok(name, condition): checks.append((name, bool(condition)))

ok('versionCode 47', 'versionCode 47' in GRADLE)
ok('versionName 19.4', "versionName '19.4'" in GRADLE)
ok('MainActivity V19.4', 'VERSION_NAME = "V19.4"' in MAIN)
ok('CHANGELOG V19.4', '## V19.4 — 2026-08-16' in CHANGELOG)

# Review arrows: recommendation follows the same live side color as board analysis arrows;
# actual played move keeps only the quality badge anchor.
ok('review recommendation uses live side color', 'boardView.getSideArrowColor(redMover)' in MAIN)
ok('fixed green review arrow removed', 'Color.rgb(43, 154, 83)' not in MAIN)
ok('badge-only actual move supported', 'public final boolean drawArrow;' in BOARD and 'if (a.drawArrow) drawMoveArrow' in BOARD)
ok('actual review arrow hidden', 'moverArrowColor, quality, qualityColor, false' in MAIN)

# Skin settings order and calibration copy/default.
calibration = MAIN.find('calibrationTitle.setText("动态校准")')
hint = MAIN.find('hint.setText("动态校准方法：屏幕上会出现一个黄色网格')
highlight = MAIN.find('colorLabel.setText("全局高亮色")')
piece = MAIN.find('sizeLabel.setText("棋子大小: "')
arrows = MAIN.find('arrowTitle.setText("红黑双方箭头颜色")')
situation = MAIN.find('situationTitle.setText("局势图优势线条颜色")')
ok('skin setting order', -1 not in (calibration, hint, highlight, piece, arrows, situation)
   and calibration < hint < highlight < piece < arrows < situation)
ok('calibration default 2dp', 'calibrationStep.setText("2")' in MAIN)
ok('plain-language calibration copy', '/storage/emulated/0/nodexq/pic/ 这个文件夹里' in MAIN
   and '支持的图片格式有 PNG、WEBP 和 JPG' in MAIN)

# Color picker/RGB persistence.
ok('generic RGB color picker', 'private void showColorPicker(String title, int initialColor' in MAIN
   and 'RGB代码' in MAIN and 'parseRgbColorCode' in MAIN)
ok('arrow color prefs', 'PREF_RED_ARROW_COLOR' in MAIN and 'PREF_BLACK_ARROW_COLOR' in MAIN)
ok('situation color prefs', 'PREF_SITUATION_RED_COLOR' in MAIN and 'PREF_SITUATION_BLACK_COLOR' in MAIN)
ok('continuation shades follow custom colors', 'lightenArrowColor(redColor)' in BOARD and 'lightenArrowColor(blackColor)' in BOARD)
ok('situation chart custom curves', 'public void setAdvantageColors(int redColor, int blackColor)' in CHART
   and 'situationChartView.setAdvantageColors' in MAIN)

# Keep V19.3 edit hot-path fixes intact.
ok('persistent edit panel refs', 'private View editPanelRoot;' in MAIN and 'editPieceCells' in MAIN and 'editPieceCounts' in MAIN)
ok('local edit refresh method', 'private void refreshEditPanelState()' in MAIN)
ok('old posted full rebuild remains removed', 'gameContentHost.post(this::updateGameContent)' not in MAIN)
ok('shared edit piece bitmap cache intact', 'public static Bitmap sharedSkinPieceBitmap' in BOARD)

failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(('OK   ' if passed else 'FAIL ') + name)
if failed:
    raise SystemExit('\nV19.4 static verification failed: ' + ', '.join(failed))
print(f'\nV19.4 static verification passed ({len(checks)} checks). No Android build performed.')

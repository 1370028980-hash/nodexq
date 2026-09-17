#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
BOARD = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

checks = []
def ok(name, condition):
    checks.append((name, bool(condition)))

ok('versionCode 44', 'versionCode 44' in GRADLE)
ok('versionName 19.1', "versionName '19.1'" in GRADLE)
ok('MainActivity V19.1', 'VERSION_NAME = "V19.1"' in MAIN)
ok('CHANGELOG V19.1', '## V19.1 — 2026-08-16' in CHANGELOG)

# V19.1 manual scrolling regression fix.
ok('custom VerticalClipScrollView removed', 'VerticalClipScrollView' not in MAIN)
ok('two manual views use normal ScrollView', MAIN.count('manualScrollView = new ScrollView(this);') == 2)
ok('two manual roots clip sibling boundaries', MAIN.count('root.setClipChildren(true);') >= 2)
ok('manual content rows retain horizontal overflow', MAIN.count('contentRow.setClipChildren(false);') >= 2)
ok('normal manual navigation stays 32dp', MAIN.count('ViewGroup.LayoutParams.MATCH_PARENT, dp(32)') >= 2)

# V19.1 quality badge position.
ok('quality badge renderer exists', 'drawMoveQualityBadge' in BOARD)
ok('old endpoint renderer removed', 'drawEndpointLabel' not in BOARD)
ok('badge anchored to destination piece', 'cellCenterX(move.toRow, move.toCol)' in BOARD and 'cellCenterY(move.toRow, move.toCol)' in BOARD)
ok('badge follows piece size', 'piece = cell * pieceSizePercent / 100f' in BOARD)
ok('badge offsets upper-left', 'ex - piece * 0.43f' in BOARD and 'ey - piece * 0.43f' in BOARD)
ok('badge clamped to board bitmap', 'boardDst.left + radius' in BOARD and 'boardDst.top + radius' in BOARD)
ok('all quality labels retained', all(x in MAIN for x in ['quality = "★"', 'quality = "优"', 'quality = "中"', 'quality = "差"', 'quality = "错"']))


# V19.1 startup/new-game jank hotfix.
ok('lazy chessboard constructor exists', 'ChessBoardView(Context context, boolean loadDefaultImmediately)' in BOARD)
ok('main boards skip constructor skin decode', MAIN.count('new ChessBoardView(this, false)') >= 4)
ok('bundled skin process cache exists', 'bundledBoardBitmap' in BOARD and 'preloadBundledDefaultSkin' in BOARD)
ok('external skin single-cache exists', 'cachedExternalSkinPath' in BOARD and 'cachedExternalSkinSignature' in BOARD and 'preloadSkin(File skinDir)' in BOARD)
ok('launcher preloads current skin off main thread', '"Skin-preload"' in MAIN and 'ChessBoardView.preloadSkin(skinDirectory(startupSkin))' in MAIN)
ok('same-mode new game reuses visible board', 'resetVisibleGameScreenForNewSession' in MAIN and 'if (reuseGameScreen)' in MAIN)
ok('saved-session clear is asynchronous', '.remove(savedSessionKey(analysis)).apply();' in MAIN)
ok('changelog documents jank fix', '修复进入棋盘/新建对局时的短暂卡顿' in CHANGELOG)
ok('analysis stop avoids long engine monitor wait', 'public void stopAnalysis()' in (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java').read_text(encoding='utf-8') and 'analysisStopGeneration' in (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java').read_text(encoding='utf-8'))

failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(('OK   ' if passed else 'FAIL ') + name)
if failed:
    raise SystemExit('\nV19.1 static verification failed: ' + ', '.join(failed))
print(f'\nV19.1 static verification passed ({len(checks)} checks). No compilation performed.')

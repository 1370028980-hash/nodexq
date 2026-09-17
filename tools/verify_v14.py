from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
board = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V14.0"' in main
assert 'versionCode 14' in gradle and "versionName '14.0'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V14.0')

# 编辑棋盘：真实选中态与绿色框同步；上一手标记不在编辑模式绘制。
assert 'public boolean hasSelectedPieceInEditMode()' in board
assert 'if (!editMode && lastMove != null)' in board
assert 'if (editMode) lastMove = null;' in board
assert 'if (!hasSelectedPieceInEditMode()) return false;' in board

# 14 个棋子按钮始终接收点击；数量已满时，无选中棋子的普通放置由 selectEditPiece 拦截。
assert 'boolean clickable = true;' in main
assert 'if (clickable) cell.setOnClickListener(v -> selectEditPiece(piece));' in main
assert 'if (boardView.removeSelectedPieceInEditMode()) {' in main
assert '>= XiangqiRules.maxPieceCount(piece)) {' in main

# 浅绿色统一。
assert 'private static final int GAME_LIGHT_GREEN = Color.rgb(231, 233, 230);' in main
assert 'private static final int BOARD_SIDE_FILL_COLOR = Color.rgb(231, 233, 230);' in board
assert 'canvas.drawColor(BOARD_SIDE_FILL_COLOR);' in board
assert 'topInfoRow.setBackgroundColor(GAME_LIGHT_GREEN);' in main
assert 'tabs.setBackgroundColor(GAME_LIGHT_GREEN);' in main
assert main.count(': GAME_LIGHT_GREEN,') >= 2

print('VERIFY_V14_OK')

#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
board = (root / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')

required_main = [
    'private static final String VERSION_NAME = "V13.0"',
    'private boolean completedDuelGame;',
    '确定返回初始界面吗？已下完的棋局不会保存！',
    'setNeutralButton("退出但保留棋谱"',
    'persistCompletedDuelSession(true)',
    '新走法会覆盖旧走法，是否继续？',
    '检测到棋谱存在不同分支，进入对弈模式仅保留主分支，是否进入？',
    '此举动会删除本分支及其之后的衍生分支，是否删除？',
    '悔棋会删除之后所有衍生分支，是否悔棋？',
    '.setPositiveButton("删除"',
    '.setNegativeButton("取消", null)',
    'private String branchLabelForOrdinal(int index)',
    'activeBranchLabels',
    'resignButton.setEnabled(enabled)',
    'undoButton.setEnabled(enabled)',
    'drawButton.setEnabled(enabled)',
    'boardView.removeSelectedPieceInEditMode()',
]
for token in required_main:
    assert token in main, f'MainActivity 缺少关键实现：{token}'

assert '"主线. "' not in main, '分支列表仍残留“主线”标签框'
assert 'versionCode 13' in gradle and "versionName '13.0'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V13.0')
assert 'public boolean removeSelectedPieceInEditMode()' in board
assert 'board[selectedRow][selectedCol] = \' \';' in board

# 验证 A..Z, AA, AB... 编号规则。
def label(index: int) -> str:
    value = max(0, index) + 1
    out = ''
    while value > 0:
        value -= 1
        out = chr(ord('A') + value % 26) + out
        value //= 26
    return out

expected = {0:'A', 25:'Z', 26:'AA', 27:'AB', 51:'AZ', 52:'BA', 701:'ZZ', 702:'AAA'}
for idx, text in expected.items():
    assert label(idx) == text, (idx, label(idx), text)

# 终局状态必须参与所有关键禁用/防重复入口。
for signature in ['private void maybeAutoMove()', 'private void computerMove()',
                  'private void offerDraw()', 'private void confirmResign()',
                  'private void undoToPreviousHumanTurn()', 'private void immediateMove()',
                  'private void forceAlternativeMove()']:
    start = main.index(signature)
    body = main[start:start + 700]
    assert 'completedDuelGame' in body, f'{signature} 未检查正式终局状态'

print('VERIFY_V13_OK')

from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java").read_text(encoding="utf-8")
engine = (root / "app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V16.1"' in main
assert 'versionCode 23' in gradle and "versionName '16.1'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V16.1')
assert 'ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))' in main
assert 'FrameLayout topInfoRow = new FrameLayout(this);' in main
assert 'topInfoRow.addView(rowModeToggleButton, modeLp);' in main
assert '第三行只在“设置”按钮正下方' not in main
assert 'duf 使用 libduf.so 内嵌 NNUE，未发送 EvalFile' in engine
assert 'if (usesEmbeddedDufNetwork()) return null;' in engine
assert "exclude '**/xiangqi-agg20260512.nnue.so'" in gradle
assert not (root / 'app/src/main/assets/books/rp/book.obk').exists()
print('V16.1 source assertions passed.')

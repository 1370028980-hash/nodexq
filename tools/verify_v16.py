from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
engine = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java').read_text(encoding='utf-8')
gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V16.0"' in main
assert 'versionCode 22' in gradle and "versionName '16.0'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V16.0')
assert 'boardView != null && boardView.isEditMode()' in main
assert 'finishEditMode(false);' in main
assert 'modeToggleButton = modeToggleButton();' in main
assert "return 'libxiangqi-agg20260512.nnue.so'" in gradle
assert 'private static final String DUF_NNUE_FILE = "libxiangqi-agg20260512.nnue.so";' in engine
assert 'LEGACY_DUF_NNUE_FILE' in engine
assert 'searchFailureDiagnostics(e, raw)' in engine
assert '准备搜索时 EvalFile 已不存在' in engine

print('VERIFY_V16_OK')

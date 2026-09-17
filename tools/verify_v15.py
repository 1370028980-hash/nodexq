from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
engine = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/engine/PikafishEngine.java').read_text(encoding='utf-8')
gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
changelog = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V15.0"' in main
assert 'versionCode 15' in gradle and "versionName '15.0'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V15.0')
assert "fileName.equalsIgnoreCase('pikafish.nnue.so') ? 'libpikafish.nnue.so'" in gradle
assert 'private static final String NNUE_FILE = "libpikafish.nnue.so";' in engine
assert 'removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);' in engine
assert 'sessionOptions.put(actualName, selected);' in engine
assert 'sendActiveEvalFileIfConfigured();' in engine
assert '[Debug] NNUE安装目录检查=' in engine
assert 'if (isEvalFileOption(actualName)) lastSentEvalFileCmd = cmd;' in engine
assert 'addUniqueName(names, "lib" + base + ".so")' in engine

print('VERIFY_V15_OK')

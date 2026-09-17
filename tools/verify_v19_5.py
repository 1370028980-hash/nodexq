#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java'
BOARD_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java'
THEME_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/UiTheme.java'
ENGINE_PANEL_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/EngineAnalysisPanel.java'
RECENT_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/RecentGamesScreen.java'
CHART_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/V68SituationChartView.java'
RESCORE_DIALOG_PATH = ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/ui/RescoreOptionsDialog.java'
GRADLE_PATH = ROOT / 'app/build.gradle'
CHANGELOG_PATH = ROOT / 'CHANGELOG.md'

MAIN = MAIN_PATH.read_text(encoding='utf-8')
BOARD = BOARD_PATH.read_text(encoding='utf-8')
THEME = THEME_PATH.read_text(encoding='utf-8')
ENGINE_PANEL = ENGINE_PANEL_PATH.read_text(encoding='utf-8')
RECENT = RECENT_PATH.read_text(encoding='utf-8')
CHART = CHART_PATH.read_text(encoding='utf-8')
RESCORE_DIALOG = RESCORE_DIALOG_PATH.read_text(encoding='utf-8')
GRADLE = GRADLE_PATH.read_text(encoding='utf-8')
CHANGELOG = CHANGELOG_PATH.read_text(encoding='utf-8')

checks = []
def ok(name, condition):
    checks.append((name, bool(condition)))

# Version / changelog.
ok('versionCode 48', 'versionCode 48' in GRADLE)
ok('versionName 19.5', "versionName '19.5'" in GRADLE)
ok('MainActivity V19.5', 'VERSION_NAME = "V19.5"' in MAIN)
ok('CHANGELOG V19.5', '## V19.5 — 2026-08-17' in CHANGELOG)

# Global base color and readable text.
ok('single global background pref', 'KEY_BACKGROUND_COLOR = "global_background_color"' in THEME
   and 'DEFAULT_BACKGROUND_COLOR = Color.rgb(246, 244, 238)' in THEME)
ok('dynamic text contrast for base', 'textOnBackground(Context context)' in THEME
   and 'secondaryTextOnBackground(Context context)' in THEME
   and 'textForOpaqueColor' in THEME)
ok('transparent highlight contrast composites over base', 'compositeOver(opaque(base), color)' in THEME)
ok('player rows use global surface', 'setRoundedBackground(label, globalSurfaceFillColor(), 0, Color.TRANSPARENT)' in MAIN)
ok('player row compact 24dp', 'PLAYER_ROW_HEIGHT_DP = 24' in MAIN)
ok('comment box follows global surface', 'setRoundedBackground(box, globalSurfaceFillColor(), 5,' in MAIN
   and 'manualCommentEdit.setTextColor(globalBackgroundTextColor())' in MAIN)
ok('manual move text follows base contrast', 'view.setTextColor(selected ? highlightTextColor() : globalBackgroundTextColor())' in MAIN
   and 'move.setTextColor(selected ? highlightTextColor() : globalBackgroundTextColor())' in MAIN)

# Skin setting order: choose skin -> piece size -> calibration -> base -> highlight -> arrows -> situation.
choose_skin = MAIN.find('chooseLabel.setText("选择皮肤")')
piece = MAIN.find('sizeLabel.setText("棋子大小: "')
calibration = MAIN.find('calibrationTitle.setText("动态校准")')
background = MAIN.find('backgroundLabel.setText("全局底色")')
highlight = MAIN.find('colorLabel.setText("全局高亮色")')
arrows = MAIN.find('arrowTitle.setText("红黑双方箭头颜色")')
situation = MAIN.find('situationTitle.setText("局势图优势线条颜色")')
ok('skin setting order', -1 not in (choose_skin, piece, calibration, background, highlight, arrows, situation)
   and choose_skin < piece < calibration < background < highlight < arrows < situation)
ok('hex wording in color input', 'codeLabel.setText("hex色值")' in MAIN and '6 位 hex 色值' in MAIN)
ok('old RGB-code UI wording removed', 'RGB代码' not in MAIN and 'RGB 代码' not in MAIN)

# Highlight alpha + fixed defaults / restore button.
ok('highlight alpha pref and 0-100 slider', 'KEY_HIGHLIGHT_ALPHA = "global_highlight_alpha"' in THEME
   and 'highlightAlphaSlider.setMax(100)' in MAIN
   and 'selectedHighlightAlpha[0] = clamp(progress, 0, 100)' in MAIN)
ok('0 percent means fully transparent', 'Math.round(highlightAlphaPercent(context) * 255f / 100f)' in THEME)
ok('defaults frozen from current colors', 'DEFAULT_HIGHLIGHT_COLOR = Color.rgb(42, 92, 70)' in THEME
   and 'DEFAULT_HIGHLIGHT_ALPHA = 100' in THEME
   and 'DEFAULT_RED_ARROW_COLOR = Color.rgb(214, 40, 40)' in MAIN
   and 'DEFAULT_BLACK_ARROW_COLOR = Color.rgb(40, 100, 214)' in MAIN
   and 'DEFAULT_SITUATION_RED_COLOR = Color.rgb(238, 42, 42)' in MAIN
   and 'DEFAULT_SITUATION_BLACK_COLOR = Color.rgb(25, 102, 235)' in MAIN)
ok('restore default colors and alpha button', '恢复默认色值与透明度' in MAIN
   and 'selectedBackground[0] = UiTheme.DEFAULT_BACKGROUND_COLOR' in MAIN
   and 'selectedHighlightAlpha[0] = UiTheme.DEFAULT_HIGHLIGHT_ALPHA' in MAIN)

# Optional skin background image + caching/performance.
ok('back image lookup', 'findSkinImageFile(skinDirectory(skin), "back")' in MAIN
   and 'new String[]{".png", ".webp", ".jpg", ".jpeg"}' in MAIN)
ok('sampled background decode', 'decodeSampledGlobalBackground(File file)' in MAIN
   and 'bounds.inJustDecodeBounds = true' in MAIN and 'options.inSampleSize' in MAIN)
ok('background decode uses bounded single-thread executor', 'globalBackgroundLoader = Executors.newSingleThreadExecutor()' in MAIN
   and 'globalBackgroundLoader.execute(() ->' in MAIN
   and 'globalBackgroundRequestGeneration' in MAIN)
ok('background signature cache', 'cachedGlobalBackgroundSignature' in MAIN
   and 'file.length() * 31L + file.lastModified()' in MAIN)
ok('cache-hit refresh avoids content rebuild', 'if (generation == globalBackgroundRequestGeneration) applyGlobalBackgroundToAppRoot();' in MAIN
   and 'applyGlobalSurfaceBackgroundOnly();' in MAIN)
ok('background loader shutdown', 'globalBackgroundLoader.shutdownNow();' in MAIN)
ok('back image makes base surfaces transparent', 'hasActiveGlobalBackgroundImage() ? Color.TRANSPARENT : globalBackgroundColor()' in MAIN)
ok('board side fill preserves transparency', 'if (boardSideFillColor == color) return;' in BOARD
   and 'boardSideFillColor = color;' in BOARD
   and 'canvas.drawColor(boardSideFillColor);' in BOARD)

# No decoding in onDraw hot paths (simple scoped checks around each source onDraw).
def decode_near_ondraw(text):
    for m in re.finditer(r'\bonDraw\s*\(', text):
        chunk = text[m.start():m.start()+3500]
        if 'BitmapFactory.decode' in chunk:
            return True
    return False
ok('no bitmap decode in ChessBoard onDraw', not decode_near_ondraw(BOARD))
ok('no bitmap decode in Main onDraw', not decode_near_ondraw(MAIN))
ok('existing shared skin bitmap cache retained', 'sharedSkinPieceBitmap' in BOARD
   and 'cachedExternalSkinSignature' in BOARD)
ok('old edit full-rebuild post remains removed', 'gameContentHost.post(this::updateGameContent)' not in MAIN)

# Large font protection.
ok('large fontScale capped', 'protected void attachBaseContext(Context newBase)' in MAIN
   and 'current.fontScale > 1.18f' in MAIN and 'adjusted.fontScale = 1.18f' in MAIN)
ok('engine output row can wrap', 'outputStepsName.setMaxLines(2)' in MAIN
   and 'ViewGroup.LayoutParams.WRAP_CONTENT' in MAIN[MAIN.find('outputStepsName.setText'):MAIN.find('final EditText outputStepsEdit')])

# Engine display PV limit: app-only, applied to both primary and temporary analysis.
ok('engine output move pref', 'PREF_ENGINE_OUTPUT_STEPS = "engine_output_steps"' in MAIN)
ok('engine option label exact', '引擎输出步数（0为不限制）' in MAIN)
ok('engine display option is not UCI option', '这是应用自己的显示选项，不向引擎发送任何 UCI setoption' in MAIN)
ok('pv truncation helper', 'private List<String> pvForDisplay(List<String> pv)' in MAIN
   and 'pv.subList(0, engineOutputMoveLimit)' in MAIN)
ok('pv display limit used in both flows', MAIN.count('List<String> displayPv = pvForDisplay(info.pv);') >= 2)
ok('full PV still stored for arrows/logic', 'new AnalysisDisplayEntry(info, cnPv, update.redToMove)' in MAIN
   and 'latestAnalysisInfo = copyInfo(info)' in MAIN)
ok('engine output default reset is unlimited', 'editor.putInt(PREF_ENGINE_OUTPUT_STEPS, 0)' in MAIN
   and 'engineOutputMoveLimit = 0' in MAIN)

# Rescore time remembers the last valid confirmed value, with 0.1 as first-use fallback.
ok('rescore time memory pref', 'PREF_LAST_RESCORE_SECONDS = "rescore_last_seconds"' in RESCORE_DIALOG
   and '.getString(PREF_LAST_RESCORE_SECONDS, "0.1")' in RESCORE_DIALOG
   and '.putString(PREF_LAST_RESCORE_SECONDS, secondsText)' in RESCORE_DIALOG)
ok('rescore time no longer hard resets each open', 'timeInput.setText("0.1")' not in RESCORE_DIALOG
   and 'timeInput.setText(lastSeconds)' in RESCORE_DIALOG)

# V19.4 review-arrow behavior retained.
ok('review recommendation uses live side color', 'boardView.getSideArrowColor(redMover)' in MAIN)
ok('actual review arrow remains hidden', 'moverArrowColor, quality, qualityColor, false' in MAIN)
ok('badge-only actual move supported', 'public final boolean drawArrow;' in BOARD
   and 'if (a.drawArrow) drawMoveArrow' in BOARD)

# Existing custom arrow/chart colors remain.
ok('custom arrow prefs retained', 'PREF_RED_ARROW_COLOR' in MAIN and 'PREF_BLACK_ARROW_COLOR' in MAIN)
ok('custom situation colors retained', 'PREF_SITUATION_RED_COLOR' in MAIN and 'PREF_SITUATION_BLACK_COLOR' in MAIN
   and 'setAdvantageColors(int redColor, int blackColor)' in CHART)

# Lexical delimiter check: strips strings/chars/comments, then verifies (), {}, [].
def balanced_java(text, label):
    out = []
    i = 0
    state = 'code'
    quote = ''
    while i < len(text):
        c = text[i]
        n = text[i+1] if i+1 < len(text) else ''
        if state == 'code':
            if c == '/' and n == '/': state = 'line'; i += 2; continue
            if c == '/' and n == '*': state = 'block'; i += 2; continue
            if c in ('"', "'"): state = 'string'; quote = c; i += 1; continue
            out.append(c); i += 1; continue
        if state == 'line':
            if c == '\n': state = 'code'; out.append('\n')
            i += 1; continue
        if state == 'block':
            if c == '*' and n == '/': state = 'code'; i += 2; continue
            i += 1; continue
        if state == 'string':
            if c == '\\': i += 2; continue
            if c == quote: state = 'code'
            i += 1; continue
    pairs = {')':'(', '}':'{', ']':'['}
    stack = []
    for c in out:
        if c in '({[': stack.append(c)
        elif c in ')}]':
            if not stack or stack.pop() != pairs[c]: return False
    return not stack and state != 'block'

for path, text in [(MAIN_PATH, MAIN), (BOARD_PATH, BOARD), (THEME_PATH, THEME),
                   (ENGINE_PANEL_PATH, ENGINE_PANEL), (RECENT_PATH, RECENT),
                   (RESCORE_DIALOG_PATH, RESCORE_DIALOG)]:
    ok('balanced delimiters ' + path.name, balanced_java(text, path.name))

failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(('OK   ' if passed else 'FAIL ') + name)
if failed:
    raise SystemExit('\nV19.5 static verification failed: ' + ', '.join(failed))
print(f'\nV19.5 static verification passed ({len(checks)} checks). No Android build performed.')

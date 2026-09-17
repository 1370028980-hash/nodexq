#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
paths = {
    'MAIN': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java',
    'THEME': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/ui/UiTheme.java',
    'DIFF': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/ui/SegmentedDifficultyView.java',
    'RECENT': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/ui/RecentGamesScreen.java',
    'BOARD': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java',
    'RESCORE': ROOT/'app/src/main/java/com/tyl/xiangqi/ndxq/ui/RescoreOptionsDialog.java',
    'GRADLE': ROOT/'app/build.gradle',
    'CHANGELOG': ROOT/'CHANGELOG.md',
}
T={k:p.read_text(encoding='utf-8') for k,p in paths.items()}
MAIN,THEME,DIFF,RECENT,BOARD,RESCORE,GRADLE,CHANGELOG=(T[k] for k in ['MAIN','THEME','DIFF','RECENT','BOARD','RESCORE','GRADLE','CHANGELOG'])
checks=[]
def ok(name, cond): checks.append((name,bool(cond)))

# Version / delivery metadata.
ok('versionCode 49', 'versionCode 49' in GRADLE)
ok('versionName 19.6', "versionName '19.6'" in GRADLE)
ok('MainActivity V19.6', 'VERSION_NAME = "V19.6"' in MAIN)
ok('CHANGELOG V19.6', '## V19.6 — 2026-08-17' in CHANGELOG)

# Global background alpha.
ok('background alpha pref', 'KEY_BACKGROUND_ALPHA = "global_background_alpha"' in THEME)
ok('background alpha default', 'DEFAULT_BACKGROUND_ALPHA = 100' in THEME)
ok('background RGB kept separate', 'public static int backgroundRgb(Context context)' in THEME)
ok('background ARGB uses alpha', 'return withAlpha(backgroundRgb(context), backgroundAlphaPercent(context));' in THEME)
ok('background alpha slider', 'backgroundAlphaSlider.setMax(100)' in MAIN and '全局底色透明度' in MAIN)
ok('background alpha persisted', '.putInt(UiTheme.KEY_BACKGROUND_ALPHA, selectedBackgroundAlpha[0])' in MAIN)
ok('opaque root fallback', 'private int globalRootBackgroundColor()' in MAIN and 'return UiTheme.backgroundRgb(this);' in MAIN)
ok('surface uses alpha base', 'return globalBackgroundColor();' in MAIN[MAIN.find('private int globalSurfaceFillColor'):MAIN.find('private void requestGlobalBackgroundRefresh')])
ok('back decode remains async', 'globalBackgroundLoader = Executors.newSingleThreadExecutor()' in MAIN and 'globalBackgroundLoader.execute(() ->' in MAIN)
ok('no decode in draw path', 'BitmapFactory.decode' not in MAIN[MAIN.find('private void styleTab'):MAIN.find('private TextView manualMoveRow')])

# Home button theme.
ok('home button color pref', 'KEY_HOME_BUTTON_COLOR = "home_button_color"' in THEME)
ok('home button alpha pref', 'KEY_HOME_BUTTON_ALPHA = "home_button_alpha"' in THEME)
ok('home default color', 'DEFAULT_HOME_BUTTON_COLOR = Color.rgb(239, 239, 233)' in THEME)
ok('home default alpha', 'DEFAULT_HOME_BUTTON_ALPHA = 100' in THEME)
ok('home setting label', 'homeButtonLabel.setText("首页按钮色")' in MAIN)
ok('home setting after highlight alpha', MAIN.find('homeButtonLabel.setText("首页按钮色")') > MAIN.find('highlightAlphaSlider.setMax(100)'))
ok('home alpha slider', 'homeButtonAlphaSlider.setMax(100)' in MAIN and '首页按钮透明度' in MAIN)
ok('home prefs persisted', '.putInt(UiTheme.KEY_HOME_BUTTON_COLOR, selectedHomeButton[0])' in MAIN and '.putInt(UiTheme.KEY_HOME_BUTTON_ALPHA, selectedHomeButtonAlpha[0])' in MAIN)
ok('restore covers new alpha/color', 'selectedBackgroundAlpha[0] = UiTheme.DEFAULT_BACKGROUND_ALPHA' in MAIN and 'selectedHomeButton[0] = UiTheme.DEFAULT_HOME_BUTTON_COLOR' in MAIN and 'selectedHomeButtonAlpha[0] = UiTheme.DEFAULT_HOME_BUTTON_ALPHA' in MAIN)

# Launcher styling.
ok('start uses launcher button', 'Button start = launcherButton("开始对弈")' in MAIN)
ok('analysis uses launcher button', 'Button analysisEntry = launcherButton("分析模式")' in MAIN)
ok('recent uses launcher button', 'Button recent = launcherButton("最近对局")' in MAIN)
ok('settings uses launcher button', 'Button settings = launcherButton("设置")' in MAIN)
ok('launcher button black text/home fill', 'button.setTextColor(Color.BLACK);' in MAIN[MAIN.find('private Button launcherButton'):MAIN.find('private Button launcherSmallButton')] and 'homepageButtonColor()' in MAIN[MAIN.find('private Button launcherButton'):MAIN.find('private Button launcherSmallButton')])
ok('selector unselected uses home color', 'selected ? highlightColor() : homepageButtonColor()' in MAIN)
ok('selector text black', 'view.setTextColor(Color.BLACK);' in MAIN[MAIN.find('private void styleSelectorOption'):MAIN.find('private Button launcherButton')])
ok('difficulty unselected uses home color', 'UiTheme.homeButton(getContext())' in DIFF)
ok('difficulty text black', DIFF.count('paint.setColor(Color.BLACK);') >= 2)
ok('history area uses home color', 'setRoundedBackground(row, homepageButtonColor(), 10, Color.TRANSPARENT)' in MAIN and 'setRoundedBackground(launcherStatsText, homepageButtonColor(), 7, Color.TRANSPARENT)' in MAIN)
ok('history reset uses home color', 'Button reset = launcherSmallButton("重置战绩")' in MAIN)
ok('recent records use home color', 'rounded(button, UiTheme.homeButton(activity), 14, Color.TRANSPARENT, activity)' in RECENT)
ok('recent record text black', 'button.setTextColor(Color.BLACK);' in RECENT[RECENT.find('private static Button largeButton'):RECENT.find('private static TextView xButton')])
ok('homepage version black', 'version.setTextColor(Color.BLACK);' in MAIN)
ok('homepage section title black', 'title.setTextColor(Color.BLACK);' in MAIN[MAIN.find('private TextView sectionTitle'):MAIN.find('private TextView selectorOption')])
ok('history texts black', MAIN[MAIN.find('private View buildLauncherStatsRow'):MAIN.find('private String statsKey')].count('setTextColor(Color.BLACK)') >= 2)

# Navigation/actions/branches.
ok('manual nav arrows', 'manualNavButton("←"' in MAIN and 'manualNavButton("→"' in MAIN)
ok('temporary nav arrows', 'pushNavButton("←"' in MAIN and 'pushNavButton("→"' in MAIN)
ok('no old nav labels', 'manualNavButton("前"' not in MAIN and 'manualNavButton("后"' not in MAIN and 'pushNavButton("前"' not in MAIN and 'pushNavButton("后"' not in MAIN)
nav_block=MAIN[MAIN.find('private TextView manualNavButton'):MAIN.find('private LinearLayout.LayoutParams squareNavLp')]
ok('nav background always base', 'setRoundedBackground(button, globalSurfaceFillColor(), 5, Color.TRANSPARENT);' in nav_block)
ok('nav disabled only text gray', 'enabled ? globalBackgroundTextColor() : Color.rgb(145, 150, 147)' in nav_block)
tmp_block=MAIN[MAIN.find('private void updateTemporaryNavButtonState'):MAIN.find('private LinearLayout.LayoutParams pushNavLp')]
ok('temporary nav background base', 'setRoundedBackground(button, globalSurfaceFillColor(), 5, Color.TRANSPARENT);' in tmp_block)
ok('rescore/report/error use surface buttons', 'surfaceActionButton("重新打分")' in MAIN and 'surfaceActionButton("报告")' in MAIN and 'surfaceActionButton("错")' in MAIN)
report_block=MAIN[MAIN.find('private void refreshReportButtonState'):MAIN.find('private GameReportCalculator.Report buildGameReport')]
ok('report background base', 'setRoundedBackground(reportButton, globalSurfaceFillColor(), 7, Color.TRANSPARENT)' in report_block)
ok('report disabled only grey text', 'complete ? globalBackgroundTextColor() : Color.rgb(145, 150, 147)' in report_block)
branch_block=MAIN[MAIN.find('private TextView branchRow'):MAIN.find('private View buildEngineView')]
ok('unselected branch base', 'selected ? highlightColor() : globalSurfaceFillColor()' in branch_block)

# Tabs: no highlight background, short black line.
tab_block=MAIN[MAIN.find('private void styleTab'):MAIN.find('private TextView manualMoveRow')]
ok('tab has no highlight fill', 'highlightColor()' not in tab_block and 'setRoundedBackground' not in tab_block)
ok('tab short black line', 'paint.setColor(Color.BLACK)' in tab_block and 'lineWidth' in tab_block and 'drawRoundRect' in tab_block)
ok('tab drawable only', 'tab.setBackground(new android.graphics.drawable.Drawable()' in tab_block)

# Existing requested behavior retained.
ok('hex wording retained', 'codeLabel.setText("hex色值")' in MAIN and 'RGB代码' not in MAIN)
ok('piece size still before calibration', MAIN.find('sizeLabel.setText("棋子大小: "') < MAIN.find('calibrationTitle.setText("动态校准")'))
ok('rescore time memory retained', 'PREF_LAST_RESCORE_SECONDS = "rescore_last_seconds"' in RESCORE and '.putString(PREF_LAST_RESCORE_SECONDS, secondsText)' in RESCORE)
ok('engine output steps retained', 'PREF_ENGINE_OUTPUT_STEPS = "engine_output_steps"' in MAIN and '引擎输出步数（0为不限制）' in MAIN)
ok('review actual arrow hidden retained', 'moverArrowColor, quality, qualityColor, false' in MAIN and 'public final boolean drawArrow;' in BOARD)

# Basic lexical delimiter checker (no Android compilation by request).
def balanced_java(text):
    out=[]; i=0; state='code'; quote=''
    while i<len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ''
        if state=='code':
            if c=='/' and n=='/': state='line'; i+=2; continue
            if c=='/' and n=='*': state='block'; i+=2; continue
            if c in ('"', "'"): state='string'; quote=c; i+=1; continue
            out.append(c); i+=1; continue
        if state=='line':
            if c=='\n': state='code'; out.append(c)
            i+=1; continue
        if state=='block':
            if c=='*' and n=='/': state='code'; i+=2; continue
            i+=1; continue
        if state=='string':
            if c=='\\': i+=2; continue
            if c==quote: state='code'
            i+=1; continue
    pairs={')':'(', '}':'{', ']':'['}; stack=[]
    for c in out:
        if c in '({[': stack.append(c)
        elif c in ')}]':
            if not stack or stack.pop()!=pairs[c]: return False
    return not stack and state!='block'

for key in ['MAIN','THEME','DIFF','RECENT','BOARD','RESCORE']:
    ok('balanced '+paths[key].name, balanced_java(T[key]))

failed=[n for n,p in checks if not p]
for n,p in checks: print(('OK   ' if p else 'FAIL ')+n)
if failed:
    raise SystemExit('\nV19.6 static verification failed: '+', '.join(failed))
print(f'\nV19.6 static verification passed ({len(checks)} checks). No Android build performed.')

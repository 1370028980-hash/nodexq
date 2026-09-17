#!/usr/bin/env python3
"""Lightweight source/resource assertions for the V18.4 report refinement (no Android build)."""
from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq"
MAIN = JAVA / "MainActivity.java"
REPORT = JAVA / "core/GameReportCalculator.java"
CHART = JAVA / "ui/V68SituationChartView.java"
GRADLE = ROOT / "app/build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"
ALGO = ROOT / "打分算法.txt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

main = MAIN.read_text('utf-8')
report = REPORT.read_text('utf-8')
chart = CHART.read_text('utf-8')
gradle = GRADLE.read_text('utf-8')
changelog = CHANGELOG.read_text('utf-8')
algo = ALGO.read_text('utf-8')

assert 'private static final String VERSION_NAME = "V18.4"' in main
assert '节点象棋 V18.4' in main
assert "versionCode 37" in gradle and "versionName '18.4'" in gradle
assert changelog.startswith('# 节点象棋更新日志\n\n## V18.4 — 2026-08-11')

# Exact V18.4 report constants and unchanged total normalization.
assert 'OPENING_WEIGHT = 0.23567d' in report
assert 'MIDDLEGAME_WEIGHT = 0.76047d' in report
assert 'ENDGAME_WEIGHT = 0.01906d' in report
assert 'HIGH_SCORE_ERROR_IGNORE_THRESHOLD = 5500' in report
assert 'PHASE_SOFTMIN_TAU = 20d' in report
assert 'weightSum <= 0d ? Double.NaN : weighted / weightSum' in report
assert 'Math.exp(-bounded / PHASE_SOFTMIN_TAU)' in report
assert '-PHASE_SOFTMIN_TAU * Math.log(averageExp)' in report

# Mate equivalent value and corrected discount direction.
assert 'Math.max(0d, 9000d - (Math.max(1, matePly) - 1) * 300d)' in report
for token in ('return 0.90d;', 'return 0.70d;', 'return 0.50d;', 'return 0.35d;', 'return 0.20d;'):
    assert token in report
assert 'M30=300，M31 及以上=0' in report

# High-score error suppression is separate from phase scoring.
assert 'side.add(phase, quality.score);' in report
assert 'quality.errorEligible && quality.loss > ERROR_LOSS_THRESHOLD' in report
assert 'sameDirectionHighNonMateZone' in report
assert 'if (beforeIsMate || afterIsMate) return false;' in report
assert 'Integer.signum(before) == Integer.signum(after)' in report

# Chart annotations avoid the curve instead of covering it with white backing/halo.
assert 'labelBackgroundPaint' not in chart
assert 'errorHaloPaint' not in chart
assert 'drawBackedText' not in chart
assert 'canvas.drawRoundRect(' not in chart
assert 'chooseLabelPlacement(' in chart
assert 'chooseErrorPlacement(' in chart
assert 'addCurveNormalCandidates(' in chart
assert 'curveRectPenalty(' in chart
assert 'guideRectPenalty(' in chart
assert 'curveLinePenalty(' in chart
assert 'occupiedPenalty(' in chart
assert 'drawErrorArrow(canvas, placement.anchorX, placement.anchorY, x, y)' in chart
assert 'float tipX = pointX - ux * dp(2.5f);' in chart
assert 'float[] gaps = new float[]{dp(7), dp(15), dp(23)};' in chart
assert 'float[] gaps = new float[]{dp(9), dp(15), dp(21)};' in chart

# Algorithm document synchronized.
for token in (
    'M30对应300，M31及以上对应0',
    'M1时α=0.9',
    'M2-M3时α=0.7',
    'M4-M6时α=0.5',
    'M7-M10时α=0.35',
    'M10以上α=0.2',
    '|普通分|>5500',
    'V18.4暂定τ=20',
    '开局0.23567，中局0.76047，残局0.01906',
):
    assert token in algo

# Reference arithmetic checks for the chosen tau and normalized weights.
def softmin(values, tau=20.0):
    return -tau * math.log(sum(math.exp(-v / tau) for v in values) / len(values))
assert abs(softmin([100.0] * 20) - 100.0) < 1e-9
assert 91.0 < softmin([100.0] * 19 + [50.0]) < 91.2
assert 57.4 < softmin([100.0] * 19 + [0.0]) < 57.7
weights = [0.23567, 0.76047, 0.01906]
assert abs(sum(w * 100.0 for w in weights) / sum(weights) - 100.0) < 1e-9

def suppress_high_error(before, after, before_mate=False, after_mate=False):
    return (not before_mate and not after_mate
            and abs(before) > 5500 and abs(after) > 5500
            and (before > 0) == (after > 0))
assert suppress_high_error(8000, 6500)
assert suppress_high_error(-8000, -6500)
assert not suppress_high_error(7000, 2500)       # crosses back under 5500
assert not suppress_high_error(2500, -7000)      # threshold crossing + advantage flip
assert not suppress_high_error(8000, 6000, after_mate=True)

# The user's new M1->M2 factor gives L=270; unchanged piecewise mapping puts it in 150..500.
loss = 300.0 * 0.9
score = 95.0 + (80.0 - 95.0) * ((loss - 150.0) / (500.0 - 150.0))
assert 89.8 < score < 89.9

# Basic XML sanity.
ET.parse(MANIFEST)

# Basic Java lexical sanity: braces/quoted strings while ignoring comments.
def balanced_java(text: str) -> bool:
    state = 'code'
    escaped = False
    depth = 0
    i = 0
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ''
        if state == 'code':
            if ch == '"': state, escaped = 'string', False
            elif ch == "'": state, escaped = 'char', False
            elif ch == '/' and nxt == '/': state, i = 'line', i + 1
            elif ch == '/' and nxt == '*': state, i = 'block', i + 1
            elif ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth < 0: return False
        elif state in ('string', 'char'):
            if ch == '\n': return False
            if escaped: escaped = False
            elif ch == '\\': escaped = True
            elif (state == 'string' and ch == '"') or (state == 'char' and ch == "'"):
                state = 'code'
        elif state == 'line':
            if ch == '\n': state = 'code'
        elif state == 'block' and ch == '*' and nxt == '/':
            state, i = 'code', i + 1
        i += 1
    return depth == 0 and state in ('code', 'line')

for java in JAVA.rglob('*.java'):
    assert balanced_java(java.read_text('utf-8')), f'unbalanced Java source: {java}'

print('V18.4 source/resource assertions passed.')
print(f'SoftMin tau=20 sample: 19x100 + 1x50 => {softmin([100.0]*19+[50.0]):.3f}')
print(f'M1->M2 with alpha=0.9 under unchanged loss mapping => L={loss:.0f}, score={score:.3f}')

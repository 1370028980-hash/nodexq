#!/usr/bin/env python3
"""Lightweight V18.6 XQF compatibility assertions. Intentionally does not compile Android code."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java').read_text(encoding='utf-8')
XQF = (ROOT / 'app/src/main/java/com/tyl/xiangqi/ndxq/core/XqfManualUtils.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
CHANGELOG = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')

assert 'private static final String VERSION_NAME = "V18.6"' in MAIN
assert 'versionCode 39' in GRADLE and "versionName '18.6'" in GRADLE
assert CHANGELOG.startswith('# 节点象棋更新日志\n\n## V18.6 — 2026-08-14')

# High-version piece positions must rotate even when all keys are zero.
assert 'if (version > 0x0A && keys != null && !legacyRawZeroKey)' in XQF
assert 'pos[(keys.keyXY + i + 1) & 0x1F] = man[i]' in XQF
assert 'version > 0x0A && keys != null && !keys.zeroKeys' not in XQF

# Zero-key v18 export must use the inverse slot rotation.
assert 'writeInitialPiecePositions(header, initialFen, true)' in XQF
assert 'header[0x10 + i] = logical[(i + 1) & 0x1F]' in XQF

# Old NodeChess malformed zero-key v18 files remain readable.
for marker in ['chooseZeroKeyFen', 'hasLegalFirstMove', 'fixedPiecePlacementScore',
               'legacyRawZeroKey', 'inPalace', 'isElephantPoint']:
    assert marker in XQF
assert 'V18.5 及更早节点象棋的历史 raw 候选' in CHANGELOG

# Fixture from the three supplied samples: P/old Node raw logical slots and
# RedShark v18 standard high-version slots differ by exactly one inverse rotation.
logical = [0,10,20,30,40,50,60,70,80,12,72,3,23,43,63,83,
           9,19,29,39,49,59,69,79,89,17,77,6,26,46,66,86]
redshark_v18 = [10,20,30,40,50,60,70,80,12,72,3,23,43,63,83,9,
                19,29,39,49,59,69,79,89,17,77,6,26,46,66,86,0]
encoded = [logical[(i + 1) & 31] for i in range(32)]
assert encoded == redshark_v18
recovered = [255] * 32
for i, value in enumerate(encoded):
    recovered[(i + 1) & 31] = value
assert recovered == logical

print('V18.6 XQF source/resource assertions passed (no compilation performed).')

#!/usr/bin/env python3
"""Lightweight source/resource assertions for V18.9. Intentionally does not compile Android code."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq/MainActivity.java"
BOARD_PATH = ROOT / "app/src/main/java/com/tyl/xiangqi/ndxq/ui/ChessBoardView.java"
MAIN = MAIN_PATH.read_text(encoding="utf-8")
BOARD = BOARD_PATH.read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CHANGELOG = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")

assert 'private static final String VERSION_NAME = "V18.9"' in MAIN
assert "versionCode 42" in GRADLE and "versionName '18.9'" in GRADLE
assert CHANGELOG.startswith("# 节点象棋更新日志\n\n## V18.9 — 2026-08-16")

# Rescore keeps the configured MultiPV but only PV1 may update chart scores.
for signature in ("private void scoreInitialPositionForRescore", "private void processRescoreStep"):
    start = MAIN.index(signature)
    end = MAIN.find("\n    private ", start + len(signature))
    block = MAIN[start:end if end >= 0 else None]
    assert "Math.max(1, info.multiPv) == 1" in block, signature

# Manual, combined manual+engine, and situation pages use the same full-width navigation row.
for signature in ("private View buildManualView()", "private View buildCombinedManualEngineView()",
                  "private View buildSituationView()"):
    start = MAIN.index(signature)
    end = MAIN.find("\n    private ", start + len(signature))
    block = MAIN[start:end if end >= 0 else None]
    assert "buildManualNavigationBar()" in block
    assert "ViewGroup.LayoutParams.MATCH_PARENT, dp(32)" in block
situation = MAIN[MAIN.index("private View buildSituationView()"):
                 MAIN.index("private void refreshSituationChart()")]
assert situation.index("buildManualNavigationBar()") < situation.index('compactButton("重新打分")')

# Bundled default skin + optional external skins.
for marker in ('PIC_DIR_NAME = "pic"', 'DEFAULT_SKIN_NAME = "default"',
               'menuActionButton("皮肤设置"', '"当前皮肤: "', '"棋子大小: "',
               'loadBundledDefaultSkin', 'names.add(DEFAULT_SKIN_NAME)',
               'DEFAULT_SKIN_NAME.equals(selectedSkin[0])'):
    assert marker in MAIN or marker in BOARD, marker
assert 'R.drawable.board' in BOARD and 'R.drawable.br' in BOARD and 'R.drawable.rp' in BOARD
assert 'startGridAdjustment' in BOARD and 'updateGridAdjustment' in BOARD
assert '拖动四角圆点' in MAIN and '拖动网格内部' in MAIN
assert '四点校准' not in MAIN and 'startGridCalibration' not in BOARD

required = {"board.png", "br.png", "bn.png", "bb.png", "ba.png", "bk.png", "bc.png", "bp.png",
            "rr.png", "rn.png", "rb.png", "ra.png", "rk.png", "rc.png", "rp.png"}
resource_dir = ROOT / "app/src/main/res/drawable-nodpi"
provided = {p.name for p in resource_dir.iterdir() if p.is_file()}
assert required <= provided
assert not any(p.name == "board_pc.png" or p.name.startswith("piece_") for p in resource_dir.iterdir())
assert not (ROOT / "pic/default").exists()
assert "board_pc" not in MAIN and "board_pc" not in BOARD

# Requested Chinese UCI labels; LUOutput is not shown.
for label in ("线程数", "哈希值", "清理哈希", "主变数量", "中规判断“杀”回合数", "棋规", "亚规", "简易中规",
              "天规", "象棋程序竞赛规则", "弈天规则", "允许长捉", "无规则", "和棋棋规", "和棋黑胜", "和棋红胜",
              "和棋循环黑胜", "自然限招", "自然限招步数", "显示分数格式", "权重路径"):
    assert label in MAIN, label
assert '"luoutput".equals(key)' in MAIN

# Changelog includes the follow-up corrections.
assert "默认皮肤恢复内置并保持新命名" in CHANGELOG
assert "皮肤动态拖动校准" in CHANGELOG

print("VERIFY_V18_9_OK")

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""申鹤动画文件的中文 key → 英文 key 改名（幂等）。

为什么要有这个脚本：`shenhe.animation.json` 里有 7 个中文动画 key（来自手工命名的
素材），而动画文件按项目约定**只允许 ASCII key** —— 中文 key 会让外部工具链
（资源包校验、跨平台文件系统、YSM 侧导入导出）在别处出问题，也不好被 Java 常量引用。

改名表（旧 → 新，纯 ASCII、与文件里其它 key 的命名风格一致）：

    下落初期   → fall_early      0.5s / 30 骨 / 一次性   滞空下落的前段
    跳跃缓冲   → landing_heavy   1.0606s / 25 骨 / 一次性  重落地收势（真跳、摔落）
    落地小     → landing_light   0.125s / 20 骨 / 一次性   轻落地（挪台阶、缓降）
    落地翻滚   → landing_roll    0.5833s / 21 骨 / 一次性  翻滚落地（当前不接）
    急停       → run_stop        1.25s / 39 骨 / 一次性    疾跑停下来那一下
    急停4      → run_stop_alt    1.5s / 13 骨 / 一次性     急停的另一个变体（当前不接）
    点头       → nod             1.5s / 13 骨 / 一次性     打招呼点头（当前不接）

只动 `animations` 这一层的 key，骨骼名与通道内容一字不改（骨骼名本来就是 ASCII）。
重复跑安全：第二次会打印 `[skip]`。

用法：
    python -B tools/rename-shenhe-anim-keys.py [--force]
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ANIM = REPO / "src/main/resources/assets/minegenshin/character/shenhe/shenhe.animation.json"

RENAME = {
    "下落初期": "fall_early",
    "跳跃缓冲": "landing_heavy",
    "落地小": "landing_light",
    "落地翻滚": "landing_roll",
    "急停": "run_stop",
    "急停4": "run_stop_alt",
    "点头": "nod",
}


def main() -> int:
    force = "--force" in sys.argv
    text = ANIM.read_text(encoding="utf-8")
    data = json.loads(text)
    anims = data["animations"]

    todo = {old: new for old, new in RENAME.items() if old in anims}
    if not todo:
        print("[skip] 没有中文 key 了，文件已符合 ASCII 约定")
        return 0

    if not force:
        # 目标名撞车检查：撞了必须改名表，不能悄悄覆盖。
        for old, new in todo.items():
            if new in anims:
                print(f"[abort] 目标名已存在：{old} → {new}")
                return 1

    # 文本级替换（`"旧名":` → `"新名":`）而不是重排 JSON：这样 459 KB 里
    # 所有浮点数的写法一字不变，diff 里只看得到 key 本身。
    # 引号 + 冒号做锚点，`"急停4"` 不会被 `"急停"` 误伤。
    for old, new in todo.items():
        anchor = f'"{old}":'
        if text.count(anchor) != 1:
            print(f"[abort] {old} 的锚点出现 {text.count(anchor)} 次，预期 1 次")
            return 1
        text = text.replace(anchor, f'"{new}":')

    json.loads(text)  # 改完必须还是合法 JSON，再落盘
    ANIM.write_text(text, encoding="utf-8", newline="\n")

    for old, new in todo.items():
        print(f"[ok] {old} → {new}")
    print(f"[done] {ANIM} ({ANIM.stat().st_size:,} B)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

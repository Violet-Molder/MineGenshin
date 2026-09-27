# -*- coding: utf-8 -*-
"""把「木偶套件里那次出招的节奏」写进申鹤的普攻素材：屏幕/齿轮什么时候出现、
剑什么时候消失复位。

改写对象是 **YSM 搬过来的原素材**
``src/main/resources/assets/minegenshin/character/shenhe/shenhe.animation.json``
（这个文件被 .gitignore 排除，是本地母本、构建期才收进整包），只动
``sword_idle_attack_01`` / ``sword_idle_attack_02`` 两条，别的动画一律不碰。

----------------------------------------------------------------------------
一、前两段普攻的剑：打完就收，别在半空里定住
----------------------------------------------------------------------------
原素材里 ``RightSword``（剑的挂点，刀刃网格全是它的子树）只写到出手那一帧：

* ``_01``：rotation/position 最后一帧在 0.25s，scale 到 0.2917s 就没下文了；
* ``_02``：position 最后一帧在 0.25s，scale 到 0.375s 就没下文了。

而这两段动画各有 1.0s 长（引擎侧 COMBO_DURATIONS 是 20 刻）——GeckoLib 对
「动画里不再写关键帧的骨骼」就是停在最后一帧不动，于是剑会在挥砍结束之后
**在身前定格 0.6~0.7 秒**，等到引擎硬切下一段才跳走。玩家口径：
「动作1和动作2攻击完成后剑很快就消失复位。现在停留时间太长了。」

补法与 ``_03`` 已有的收法对齐（``_03`` 就是 0.5833 → 0.625 把剑缩没的）：

1. 出手之后 40ms 内把 ``scale`` 收到 ``[0,0,0]``（剑消失）；
2. 消失在前面之后，用一对 ``pre``/``post`` 关键帧把位移/旋转**瞬跳**回本段的起手值
   （复位）。跳到复位姿势的那一瞬间 scale 已经是 0，所以画面上看不到这一跳。

----------------------------------------------------------------------------
二、屏幕与齿轮的出场：齿轮先亮，屏幕后亮（照着参考视频的节奏）
----------------------------------------------------------------------------
模型里那两块浮游屏（``ysmGlow_texiao`` = 外框 + 内屏）与右上角的齿轮
（``ysmGlow_texiao2``）是**顶层骨骼**，常态由 ``CharacterPropBones`` 整组藏掉；
哪一段动作能看到它们由 ``ShenheResources`` 里那三张状态名单决定
（齿轮：三段普攻 + 收尾 + 重击；屏幕：第二段开始 + 收尾 + 重击；
坐骑/法吉偶：只有重击）。

名单只决定「这一段允许出现」，**怎么出现**写在动画里 —— 原素材给这两根骨骼
留的关键帧时间点正好就是出/入场的节奏点，只是值全是 1（= 一直可见）：

* ``_01``：齿轮有 0.25 / 0.7083 / 0.75 三个关键帧 → 第一刀收招（0.7083s）时齿轮亮起；
* ``_02``：屏幕有 0.2083 / 0.25 / 0.75 / 0.7917 四个 → 第二刀起手把屏幕推向前方
  （0.25s 的 position ``[0,0,-11]``）的同时，屏幕在 0.2083s 亮起；
* ``_03``：屏幕 scale 本来就是 1（可见）→ 第三段整段都亮着。

齿轮是小圆环，亮相就是「瞬跳 + 15% 过冲」：
``pre=[0,0,0] / post=[1.15,1.15,1.15]``。

**屏幕不是这么出现的** —— 参考视频里它是「先弹出一条竖窄条，再横向展开」：

* 亮起那一帧：``pre=[0,0,0]``（还不存在）→ ``post=[0.14,1,1]``
  —— X 只有 14%、高度已经满格的**竖窄条**；
* 之后 0.2 秒里把 X 展开到 ``1.12``（过冲）再落回 ``1``，Y/Z 一直是 1。

（视频里 10.35s→10.55s 那 4 帧：竖条 → 半开 → 满幅；
消失则是垂直方向压成一条亮线，写在收尾动画里。见下面「熄灭」。）

收尾那段的「熄灭」写在 ``shenhe_puppet.animation.json`` 的
``sword_idle_attack_end`` 里（生成器 ``tools/gen-shenhe-recovery.ps1``）。

用法（在本仓库根目录）：``python tools/patch-shenhe-props.py``
重复跑是安全的：只覆盖脚本自己写的那几个关键帧，跑几次结果一样。
"""

from __future__ import annotations

import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(
    ROOT,
    "src", "main", "resources", "assets", "minegenshin",
    "character", "shenhe", "shenhe.animation.json",
)
# 备份写在 build/ 下：那是仓库的产物目录（.gitignore 里），不会被当成资源收进整包
BACKUP = os.path.join(ROOT, "build", "shenhe-anim-backup",
                      "shenhe.animation.json")

# 过冲：出场的瞬间先放大 15%，下一帧回到 1 —— 和 _03 里剑显形（0.9,1.4,1 → 1）同一种手感
OVERSHOOT = 1.15

# 屏幕出场的「竖窄条」：X 只占 14%、高度满格，之后横向展开（见模块docstring 第二节）
SCREEN_STRIP = [0.14, 1.0, 1.0]

# 屏幕横向展开到满幅要多久（秒）—— 参考视频里从竖条到满幅约 0.2 秒
SCREEN_UNFURL = 0.2083


def post(vec):
    """一段动画里的普通关键帧（catmullrom，向后插值）。"""
    return {"post": {"vector": [float(v) for v in vec]}, "lerp_mode": "catmullrom"}


def jump(before, after):
    """同一时刻的瞬时跳变：到这一帧之前是 before，之后是 after。"""
    return {
        "pre": {"vector": [float(v) for v in before]},
        "post": {"vector": [float(v) for v in after]},
        "lerp_mode": "catmullrom",
    }


def constant(vec):
    """整段不变的通道（原素材里 pre_parallel2 藏屏幕用的就是这个写法）。"""
    return {"vector": [float(v) for v in vec]}


def patch_attack_01(anim, log):
    bones = anim["bones"]

    sword = bones["RightSword"]
    start_pos = [32, 10, -24]
    strike_pos = [-6, -16, -30]
    sword_rotation = sword["rotation"]["0.0"]["post"]["vector"]

    # 剑：出手姿势保持到 0.2917（原素材的最后一帧），0.3333 缩没
    sword["scale"] = {
        "0.0": post([1, 1, 1]),
        "0.0417": post([1, 1, 1]),
        "0.25": post([1, 1, 1]),
        "0.2917": post([1, 1, 1]),
        "0.3333": post([0, 0, 0]),
    }
    # 剑：缩没的同一刻把位移瞬跳回起手（rotation 这一段本来就是常量，不用补）
    sword["position"] = {
        "0.0": post(start_pos),
        "0.25": post(strike_pos),
        "0.3333": jump(strike_pos, start_pos),
    }
    sword["rotation"] = {
        "0.0": post(sword_rotation),
        "0.25": post(sword_rotation),
        "0.3333": jump(sword_rotation, sword_rotation),
    }
    log.append("_01 RightSword：0.2917 保持出手姿势 → 0.3333 缩没 + 瞬跳回起手位移")

    # 屏幕：第一段不出现（名单里也没有它，这里补一条常量 0 兜底）
    bones["ysmGlow_texiao"] = {"scale": constant([0, 0, 0])}
    log.append("_01 ysmGlow_texiao：整段 scale 0（第一刀不亮屏幕）")

    # 齿轮：第一刀收招（0.7083s）亮起
    bones["ysmGlow_texiao2"]["scale"] = {
        "0.0": post([0, 0, 0]),
        "0.25": jump([0, 0, 0], [0, 0, 0]),
        "0.7083": jump([0, 0, 0], [OVERSHOOT] * 3),
        "0.75": post([1, 1, 1]),
    }
    log.append("_01 ysmGlow_texiao2：0.7083s 亮起（pre 0 / post 1.15）→ 0.75 回到 1")


def patch_attack_02(anim, log):
    bones = anim["bones"]

    sword = bones["RightSword"]
    start_pos = [-17, -5, 1]
    start_rot = [87.5, 67.4999, 207.50011]
    strike_pos = [22, -5, -7]
    strike_rot = [1.1429, -89.0433, -61.333]

    # 剑：出手姿势保持到 0.375（原素材的最后一帧），0.42 缩没，0.45 瞬跳回起手
    sword["scale"] = {
        "0.0": post([1, 1, 1]),
        "0.25": post([1, 1, 1]),
        "0.3333": post([1, 1, 1]),
        "0.375": post([1, 1, 1]),
        "0.42": post([0, 0, 0]),
    }
    sword["position"] = dict(
        sword["position"],
        **{"0.45": jump(strike_pos, start_pos)},
    )
    sword["rotation"] = dict(
        sword["rotation"],
        **{"0.45": jump(strike_rot, start_rot)},
    )
    log.append("_02 RightSword：0.375 保持出手姿势 → 0.42 缩没 → 0.45 瞬跳回起手位移/旋转")

    # 屏幕：第二段起手把屏幕推向身前（原素材 0.25s 的 position [0,0,-11]）时出场。
    # 出场不是整体弹出，而是「先一条竖窄条 → 0.2 秒横向展开」，见模块 docstring 第二节。
    strip_at = 0.2083
    full_at = round(strip_at + SCREEN_UNFURL, 4)
    bones["ysmGlow_texiao"]["scale"] = {
        "0.0": post([0, 0, 0]),
        "%.4f" % strip_at: jump([0, 0, 0], SCREEN_STRIP),
        "%.4f" % full_at: post([OVERSHOOT, 1, 1]),
        "%.4f" % round(full_at + 0.0417, 4): post([1, 1, 1]),
        "0.75": post([1, 1, 1]),
        "0.7917": post([1, 1, 1]),
    }
    log.append("_02 ysmGlow_texiao：%.4fs 弹出竖窄条（X=0.14）→ %.4fs 横向展开到 1.12 → "
               "%.4fs 落回 1，之后整段保持可见"
               % (strip_at, full_at, round(full_at + 0.0417, 4)))


def main():
    if not os.path.isfile(TARGET):
        print("找不到素材文件：" + TARGET)
        return 1

    if not os.path.isfile(BACKUP):
        os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
        shutil.copyfile(TARGET, BACKUP)
        print("已备份原素材到 " + BACKUP)

    with open(TARGET, encoding="utf-8") as handle:
        data = json.load(handle)

    animations = data["animations"]
    log = []
    for name, patcher in (
        ("sword_idle_attack_01", patch_attack_01),
        ("sword_idle_attack_02", patch_attack_02),
    ):
        if name not in animations:
            print("素材里没有动画 " + name)
            return 1
        patcher(animations[name], log)

    text = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    with open(TARGET, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)

    print("已写入 " + TARGET + "（%d 字节）" % len(text.encode("utf-8")))
    for line in log:
        print("  - " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main())

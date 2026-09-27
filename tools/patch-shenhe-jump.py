# 申鹤（木偶）「起跳 / 下落 / 落地」动画修补 —— 幂等补丁。
#
# 背景：shenhe.animation.json 里的 `jump` 是一条**坏素材**：
#   LeftArm 从 0° 一路线性涨到 137.7°、末帧不回起手帧，本质是「慢慢把双手举过头顶」，
#   却标着 loop = true。引擎把它当「上升中的循环」播，观感就是
#   「一边上升一边举手施法」，而且每 2 秒重新起手一次。
#
# 这套素材里真正合格的滞空姿势是 `jump_down`：双臂外展、屈膝收腿，
# 且 `landing_heavy` 的首帧就是它 —— 落地接得上。
# 但它自己也不是全闭合：FrontSkirt.rotation 首帧 -30°、末帧 -27.5°，
# 循环一圈会弹一下 —— 第二步顺手焊死。
#
# 所以新 `jump` 的做法是：**以 `jump_down` 为母本**（骨骼集合、波形、服装姿态全部继承），
# 把时间轴整体压到一半（1.0s → 0.5s，波形仍走满一整圈，首末帧因此严格相等），
# 再把两条腿多收一点（大腿前抬更多、膝弯更深）做出「起跳收腿」的区分，
# 最后给 AllBody 加一发整圈正弦的轻微浮沉。
#
# 这样起跳（jump）↔ 下落（jump_down）是同族姿势，硬切/插值都不会跳形。
#
# 三步各自幂等：
#   ① 重写 jump（判据：0.5s 循环 + 有 AllBody.position + 与 jump_down 的接缝已是 0）
#   ② 焊死 jump_down 自己的循环（判据：所有通道首末帧已一致 = 无事可做）
#   ③ 抹掉重落地缓冲（landing_heavy）首帧的整体上抬（判据：首键 y 已经是 0）
#
# ── 为什么收腿增量要按时间收放（本轮修的接缝跳形）────────────────────────────
# 上一版把 LEG_DELTA 当常量逐帧加上去，于是 `jump` 的末帧 = 母本末帧 + delta，
# 而 `jump_down` 的首帧 = 母本首帧（delta = 0）—— 两条动画的接缝处腿部就差
# 一个 LEG_DELTA（实测 20°~34°），起跳转下落那一下腿会弹。现在 delta 乘上
# sin(π·t/T)：起末两端严格为 0，收腿只在循环中段读出来，接缝与母本逐字相等。
#
# 用法： python tools/patch-shenhe-jump.py
#        python tools/patch-shenhe-jump.py --force      # 忽略幂等判据，重写一遍

import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets",
                    "minegenshin", "character", "shenhe", "shenhe.animation.json")

SOURCE = "jump_down"        # 母本
TARGET = "jump"             # 被重写的
DESCENT = "jump_down"       # 只修它自己的循环闭合
LANDING = "landing_heavy"    # 重落地缓冲：抹掉首帧的整体上抬
NEW_LENGTH = 0.5            # 新的循环长度（秒）

# 收腿增量（加在母本关键帧上，所以母本自己的波形原样保留）。
# 这些是 .animation.json 里的**原始**分量 —— 引擎烘培时对 rotation 取反 x、y，
# 所以「原始 x 变小」= 大腿往前抬得更多、膝弯得更深。
# 值由 swing() 按时间收放：起末为 0，循环中点最大 —— 见文件头。
LEG_DELTA = {
    "LeftLeg": (-28.0, 0.0, 0.0),
    "RightLeg": (-25.0, 0.0, 0.0),
    "LeftLowerLeg": (34.0, 0.0, 0.0),
    "RightLowerLeg": (20.0, 0.0, 0.0),
}

# 滞空浮沉：AllBody.position 的 y 走一整圈正弦（起末为 0，循环严格闭合）。
FLOAT_AMPLITUDE = 1.0       # 原始单位（1 单位 = 1/16 格）
FLOAT_STEPS = 16            # 半圈内的采样段数 → 32 个关键帧


def stepped_keys(channel):
    """通道里的时间键（浮点、已排序），排除 'vector' 这种常量写法。"""
    return sorted(float(k) for k in channel if k != "vector")


def retime(channel, ratio):
    """把通道的时间轴按 ratio 压缩，值原样搬过去。"""
    out = {}
    for key in stepped_keys(channel):
        out[fmt(key * ratio)] = channel[fmt(key)]
    return out


def fmt(value):
    """键名写法：python 的 repr 就已经是最短形式（0.0 / 0.03125）。"""
    return repr(round(float(value), 6))


def unwrap(value):
    """键帧值可能是 [x,y,z]、{'vector': [...]} 或 {'pre':…, 'post':…}；取出向量。"""
    while isinstance(value, dict):
        for key in ("vector", "post", "pre"):
            if key in value:
                value = value[key]
                break
        else:
            return None
    return [float(v) for v in value] if isinstance(value, list) else None


def swing(t, length):
    """收腿增量的时间包络：0 → 1（循环中点）→ 0。

    起末两端严格为 0，是「接缝与母本逐字相等」的关键 —— 常量增量会让
    jump 的末帧带着 delta、jump_down 的首帧不带，接缝处腿弹一下。
    """
    if length <= 0.0:
        return 0.0
    t = min(max(float(t), 0.0), length)
    return math.sin(math.pi * t / length)


def bake_bone(name, body, ratio, length):
    """按 ratio 压缩一根骨骼下所有通道的时间轴，并给指定骨骼加收腿增量。"""
    delta = LEG_DELTA.get(name)
    out = {}
    for channel_name, channel in body.items():
        if "vector" in channel:          # 常量通道，压不压时间都一样
            out[channel_name] = channel
            continue
        moved = retime(channel, ratio)
        if delta and channel_name == "rotation":
            for key, value in moved.items():
                factor = swing(float(key), length)
                moved[key] = bump(value, [d * factor for d in delta])
        out[channel_name] = moved
    return out


def bump(value, delta):
    """关键帧值可能是 [x,y,z]、{'vector':…} 或 {'pre'/'post':…}；只动里面的向量。"""
    if isinstance(value, dict):
        return {k: bump(v, delta) for k, v in value.items()}
    return [round(float(v) + d, 5) for v, d in zip(value, delta)]


def float_channel(length):
    """AllBody 的整圈正弦浮沉；起末严格为 0，保证循环闭合。"""
    keys = {}
    steps = FLOAT_STEPS * 2
    for i in range(steps + 1):
        t = length * i / steps
        y = FLOAT_AMPLITUDE * math.sin(2.0 * math.pi * i / steps)
        keys[fmt(t)] = {"vector": [0.0, round(y, 5), 0.0]}
    return keys


def close_loop(body, length):
    """把每条通道的末帧拉回起帧 —— 母本 jump_down 的 FrontSkirt 差了 2.5°，
    循环一圈就会弹一下；新 jump 既然要求真循环，这里统一焊死。"""
    first_key, last_key = "0.0", fmt(length)
    for channel in body.values():
        if not isinstance(channel, dict) or "vector" in channel:
            continue
        keys = stepped_keys(channel)
        if len(keys) < 2:
            continue
        channel[last_key] = json.loads(json.dumps(channel[fmt(keys[0])]))


def copy(value):
    return json.loads(json.dumps(value))


def seam_open(anims):
    """jump 末帧与 jump_down 首帧之间还差多少（度）。接缝闭合时返回 0 附近的小数。"""
    target = anims.get(TARGET, {}).get("bones", {})
    mother = anims.get(SOURCE, {}).get("bones", {})
    worst = 0.0
    for name, body in target.items():
        for channel_name, channel in body.items():
            if not isinstance(channel, dict) or "vector" in channel:
                continue
            keys = stepped_keys(channel)
            if not keys:
                continue
            last = unwrap(channel[fmt(keys[-1])])
            other = mother.get(name, {}).get(channel_name)
            if last is None or not isinstance(other, dict) or "vector" in other:
                continue
            other_keys = stepped_keys(other)
            if not other_keys:
                continue
            first = unwrap(other[fmt(other_keys[0])])
            if first is None:
                continue
            worst = max(worst, max(abs(a - b) for a, b in zip(last, first)))
    return worst


def rebuild_jump(anims, force):
    """① 把坏掉的 jump 重写成「以 jump_down 为母本、压到一半时长」的起跳循环。"""
    # 幂等判据不往 json 里塞标记（GeckoLib 的动画定义不认自定义字段），
    # 直接看结构：改完的 jump 一定是 0.5s 循环 + AllBody 带 position 通道，
    # 且与 jump_down 的接缝已经闭合（旧版把 LEG_DELTA 当常量加，接缝差 20°~34°）。
    current = anims.get(TARGET, {})
    if (not force
            and abs(float(current.get("animation_length") or 0) - NEW_LENGTH) < 1e-9
            and "position" in current.get("bones", {}).get("AllBody", {})
            and seam_open(anims) < 1e-6):
        print("[skip] %s 已经是重写后的版本（%.4fs 循环，接缝已闭合），无需改动"
              % (TARGET, NEW_LENGTH))
        return False

    if SOURCE not in anims:
        print("[err] 母本 %s 不存在" % SOURCE, file=sys.stderr)
        raise SystemExit(1)

    mother = anims[SOURCE]
    old_length = float(mother.get("animation_length") or 1.0)
    ratio = NEW_LENGTH / old_length

    rebuilt = {"animation_length": NEW_LENGTH, "loop": True, "bones": {}}
    for name, body in mother.get("bones", {}).items():
        rebuilt["bones"][name] = bake_bone(name, body, ratio, NEW_LENGTH)

    # 母本没有 AllBody.position（滞空原地不动），这里补一发浮沉，
    # 让「腿在收、人在轻微上浮」两件事一起读出来。
    all_body = rebuilt["bones"].setdefault("AllBody", {})
    all_body.setdefault("rotation", {"vector": [0, 0, 0]})
    all_body["position"] = float_channel(NEW_LENGTH)

    for body in rebuilt["bones"].values():
        close_loop(body, NEW_LENGTH)

    anims[TARGET] = rebuilt

    print("[ok] %s 重写完成：母本 %s（%.4fs）→ %.4fs 循环，骨骼 %d 根，收腿 %s"
          % (TARGET, SOURCE, old_length, NEW_LENGTH, len(rebuilt["bones"]),
             ", ".join(sorted(LEG_DELTA))))
    return True


def close_descent_loop(anims):
    """② 把 jump_down 自己的循环焊死（FrontSkirt 差 2.5°）。天然幂等：没差就不动。"""
    animation = anims.get(DESCENT)
    if animation is None:
        print("[err] %s 不存在" % DESCENT, file=sys.stderr)
        raise SystemExit(1)

    fixed = []
    for name, body in animation.get("bones", {}).items():
        for channel_name, channel in body.items():
            if not isinstance(channel, dict) or "vector" in channel:
                continue
            keys = stepped_keys(channel)
            if len(keys) < 2:
                continue
            first, last = channel[fmt(keys[0])], channel[fmt(keys[-1])]
            if copy(first) != copy(last):
                fixed.append("%s.%s" % (name, channel_name))
                channel[fmt(keys[-1])] = copy(first)

    if not fixed:
        print("[skip] %s 的循环本来就闭合，无需改动" % DESCENT)
        return False
    print("[ok] %s 焊循环：%d 条通道（%s）末帧拉回起帧"
          % (DESCENT, len(fixed), ", ".join(fixed)))
    return True


def flatten_landing_entry(anims):
    """③ 抹掉重落地缓冲（landing_heavy）首帧的整体上抬。

    母本 jump_down 根本没有 AllBody.position（滞空原地不动），而落地缓冲的首键
    是 [0, 3.4, 0] —— 接上的那一帧人整体往上弹 3.4 个单位（1/16 格），下一帧
    再砸到 -3.5，观感就是「落地先跳一下」。把首键的 y 归零，接缝即与 jump_down
    的静止姿态重合；后面 -11.5 的屈膝下沉与末键的 0 原样保留。
    幂等判据：首键 y 已经是 0。
    """
    animation = anims.get(LANDING)
    if animation is None:
        print("[err] 落地缓冲 %s 不存在" % LANDING, file=sys.stderr)
        raise SystemExit(1)

    channel = animation.get("bones", {}).get("AllBody", {}).get("position")
    if not isinstance(channel, dict):
        print("[skip] %s 没有 AllBody.position，无需改动" % LANDING)
        return False

    keys = stepped_keys(channel)
    if not keys:
        print("[skip] %s 的 AllBody.position 是常量，无需改动" % LANDING)
        return False

    first_key = fmt(keys[0])
    value = channel[first_key]
    vector = unwrap(value)
    if vector is None:
        print("[skip] %s 的首键不是向量，跳过" % LANDING)
        return False
    if abs(vector[1]) < 1e-6:
        print("[skip] %s 首帧整体位移已经是 0，无需改动" % LANDING)
        return False

    if isinstance(value, dict):
        channel[first_key] = {k: bump(v, [0.0, -vector[1], 0.0]) for k, v in value.items()}
    else:
        channel[first_key] = bump(value, [0.0, -vector[1], 0.0])

    print("[ok] %s 首帧 AllBody.position 的 y 由 %.4f 归零" % (LANDING, vector[1]))
    return True


def main():
    force = "--force" in sys.argv[1:]
    with open(ANIM, "r", encoding="utf-8") as handle:
        doc = json.load(handle)

    anims = doc["animations"]
    changed = rebuild_jump(anims, force)
    changed = close_descent_loop(anims) or changed
    changed = flatten_landing_entry(anims) or changed

    if not changed:
        return 0

    with open(ANIM, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, ensure_ascii=False, separators=(",", ":"))
    print("[ok] 已写回 %s" % os.path.relpath(ANIM, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

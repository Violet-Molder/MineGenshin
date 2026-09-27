# 申鹤「轻落地」（`landing_light`，素材里原名 `落地小`）首帧焊接 —— 幂等补丁。
#
# 背景：引擎现在有两档落地（见 LocomotionAnims#landingLight 与 PlayerAnimationController）：
#   重档 = `landing_heavy`（真跳/摔下去），轻档 = `landing_light`（挪台阶、缓降着地）。
#
# `landing_light` 只有 0.125 秒、20 根骨骼、每条通道就两个键（起 + 止），内容是
# 「蹭地一下 → 站直」。它的**止帧是站姿**，可**起帧跟滞空姿势差得很远**：
# 实测 LeftArm 差 134.45°、RightLowerLeg 差 56.97°、LeftSkirt 差 20.51°、
# Head 差 13.65° —— 从 `jump_down` 硬切进去，人会在落地那一帧**跳一下形**，
# 正是用户报的「落地看着不对」的第二半。
#
# 所以这里做**只改首帧**的焊接：把 `landing_light` 里与 `jump_down` 同名的 (骨骼, 通道)
# 的首键值，直接替换成 `jump_down` 首键的值。这样：
#   * 滞空姿势 → `landing_light` 的接缝逐字相等，0 刻硬切也不跳形；
#   * 第二个键（0.125s）原样保留，所以「蹭地 → 站直」这段动作一点没动。
#
# 幂等判据：所有共有通道的首帧都已经等于 `jump_down` 首帧（不需要往 json 里塞标记，
# GeckoLib 的动画定义不认自定义字段）。
#
# 用法： python tools/patch-shenhe-landing.py
#        python tools/patch-shenhe-landing.py --force

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets",
                    "minegenshin", "character", "shenhe", "shenhe.animation.json")

LIGHT = "landing_light"  # 被焊的：轻落地那一档（素材原名 `落地小`）
MOTHER = "jump_down"    # 母本：滞空姿势（落地那一帧的起点）


def copy(value):
    return json.loads(json.dumps(value))


def stepped_keys(channel):
    """通道里的时间键（浮点、已排序），排除 'vector' 这种常量写法。"""
    return sorted(float(k) for k in channel if k != "vector")


def fmt(value):
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


def first_key_of(channel):
    keys = stepped_keys(channel)
    return channel[fmt(keys[0])] if keys else None


def weld_entry(anims, force):
    light = anims.get(LIGHT)
    if light is None:
        print("[err] %s 不存在" % LIGHT, file=sys.stderr)
        raise SystemExit(1)
    mother = anims.get(MOTHER)
    if mother is None:
        print("[err] 母本 %s 不存在" % MOTHER, file=sys.stderr)
        raise SystemExit(1)

    mother_bones = mother.get("bones", {})
    welded = []
    aligned = True
    orphan = []
    worst = 0.0

    for name, body in light.get("bones", {}).items():
        mother_body = mother_bones.get(name, {})
        for channel_name, channel in body.items():
            if not isinstance(channel, dict) or "vector" in channel:
                continue
            keys = stepped_keys(channel)
            if not keys:
                continue
            source = mother_body.get(channel_name)
            if not isinstance(source, dict) or "vector" in source:
                orphan.append("%s.%s" % (name, channel_name))
                continue
            wanted = first_key_of(source)
            if wanted is None:
                orphan.append("%s.%s" % (name, channel_name))
                continue
            here = channel[fmt(keys[0])]
            if copy(here) == copy(wanted):
                continue
            aligned = False
            before, after = unwrap(here), unwrap(wanted)
            if before and after:
                worst = max(worst, max(abs(x - y) for x, y in zip(before, after)))
            welded.append("%s.%s" % (name, channel_name))
            channel[fmt(keys[0])] = copy(wanted)

    if aligned:
        print("[skip] %s 的首帧已经与 %s 逐字一致，无需改动" % (LIGHT, MOTHER))
        return False

    print("[ok] %s 首帧焊接：%d 条共有通道对齐到 %s（改前最大差 %.2f 度/单位）"
          % (LIGHT, len(welded), MOTHER, worst))

    if orphan:
        print("[note] 这 %d 条通道在 %s 里没有对应项，保持原样：%s"
              % (len(orphan), MOTHER, ", ".join(sorted(orphan))))
    return True


def main():
    force = "--force" in sys.argv[1:]
    with open(ANIM, "r", encoding="utf-8") as handle:
        doc = json.load(handle)

    anims = doc["animations"]
    changed = weld_entry(anims, force)

    if not changed:
        return 0

    with open(ANIM, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, ensure_ascii=False, separators=(",", ":"))
    print("[ok] 已写回 %s" % os.path.relpath(ANIM, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

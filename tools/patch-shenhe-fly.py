# 申鹤「飞行三态」素材 —— 从 `fly` 派生出 `fly_up` / `fly_down`。幂等补丁。
#
# 背景（用户口径）：正常水平飞行就用 `fly` 那套（就是**座椅**那套：人坐在 `MFly` 飞椅上），
# 上升 / 下降要有**额外的一小段**，不能跟水平飞行一个样。
#
# 素材里原本只有 `fly`（2.0 秒循环、38 根骨骼）。这里不新画动作，只做**参数化派生**：
# 整条 `fly` 原样复制成 `fly_up` / `fly_down`，只把「姿态量」按一条时间包络加进去：
#
#   包络 = sin(π·t/T)：t = 0 与 t = T 时严格为 0，循环中点最大。
#
# 于是：
#   * 三段的首末帧**逐字相等**，两两硬切、各自循环都不会跳形；
#   * 中段姿态不同，观感上三段能读出来：
#       `fly_up`   —— 躯干后仰（原始 x 变小 = 抬头往上飞）+ 整体略微上浮
#       `fly_down` —— 躯干前压（原始 x 变大 = 低头往下飞）+ 整体略微下沉
#   * 素材里那些服装、座椅、手臂的波形全部保留，不动。
#
# 方向说明：这套 json 里的 `rotation` 用的是**原始分量**，引擎烘培时对 x、y 取反，
# 所以「原始 x 变大」= 躯干往前压（`jump_down` 用 UpperBody = 10 做前压姿势可对照）。
#
# 幂等判据：两个 key 都在、长度与骨骼集合与 `fly` 一致、首末帧与 `fly` 逐字相等，
# 且中段躯干偏移方向正确 —— 满足就跳过（不往 json 里塞标记，GeckoLib 不认自定义字段）。
#
# 用法： python tools/patch-shenhe-fly.py
#        python tools/patch-shenhe-fly.py --force

import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets",
                    "minegenshin", "character", "shenhe", "shenhe.animation.json")

MOTHER = "fly"

# 姿态增量：键 = "骨骼.通道"，值 = 原始单位下的 [x, y, z] 增量（乘包络后加在原值上）。
POSES = {
    "fly_up": {
        "UpBody.rotation": (-6.0, 0.0, 0.0),
        "UpperBody.rotation": (-9.0, 0.0, 0.0),
        "AllBody.position": (0.0, 0.7, 0.0),
    },
    "fly_down": {
        "UpBody.rotation": (8.0, 0.0, 0.0),
        "UpperBody.rotation": (11.0, 0.0, 0.0),
        "AllBody.position": (0.0, -0.7, 0.0),
    },
}

def copy(value):
    return json.loads(json.dumps(value))


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


def stepped_keys(channel):
    return sorted(float(k) for k in channel if k != "vector")


def bump(value, delta):
    """关键帧值可能是 [x,y,z] 或 {'pre'/'post':…}；只动里面的向量。"""
    if isinstance(value, dict):
        return {k: bump(v, delta) for k, v in value.items()}
    return [round(float(v) + d, 5) for v, d in zip(value, delta)]


def envelope(t, first, last):
    """0 → 1（通道中点）→ 0 的时间包络 —— 首个键与末个键严格为 0。

    按**通道自己的键跨度**归一化，而不是按 `animation_length`：这批素材的通道
    只铺到 1.9667s（不是 2.0s），按长度归一化会让末键带着 5% 的增量，
    三段的接缝就不再逐字相等了。
    """
    span = last - first
    if span <= 0.0:
        return 0.0
    t = min(max(float(t), first), last)
    return math.sin(math.pi * (t - first) / span)


def shift(animation, delta):
    """按包络把 delta 加进已有通道的每个关键帧；常量通道原样不动。"""
    bones = animation.get("bones", {})
    touched = []
    for bone, channel in sorted(tuple(entry.split(".", 1)) for entry in delta):
        vector = delta["%s.%s" % (bone, channel)]
        body = bones.get(bone)
        if body is None or channel not in body:
            continue
        source = body[channel]
        if not isinstance(source, dict) or "vector" in source:
            continue
        keys = stepped_keys(source)
        if len(keys) < 2:
            continue
        first, last = keys[0], keys[-1]
        shifted = {}
        for key, value in source.items():
            factor = envelope(float(key), first, last)
            if abs(factor) < 1e-9:
                # 起末帧原样搬过来（不经过 round），保证与母本**逐字**相等
                shifted[key] = copy(value)
            else:
                shifted[key] = bump(value, [d * factor for d in vector])
        body[channel] = shifted
        touched.append("%s.%s" % (bone, channel))
    return touched


def build(anims, name, delta, force):
    mother = anims.get(MOTHER)
    if mother is None:
        print("[err] 母本 %s 不存在" % MOTHER, file=sys.stderr)
        raise SystemExit(1)

    length = float(mother.get("animation_length") or 2.0)
    current = anims.get(name)

    if current is not None and not force and looks_built(mother, current, delta):
        print("[skip] %s 已经是派生版本（%.4fs，骨骼 %d 根），无需改动"
              % (name, length, len(current.get("bones", {}))))
        return False

    rebuilt = copy(mother)
    rebuilt["loop"] = True
    rebuilt["animation_length"] = length
    touched = shift(rebuilt, delta)
    anims[name] = rebuilt

    print("[ok] %s 派生完成：母本 %s（%.4fs，骨骼 %d 根），调了 %d 条通道（%s）"
          % (name, MOTHER, length, len(rebuilt.get("bones", {})), len(touched),
             ", ".join(touched)))
    return True


def looks_built(mother, current, delta):
    """幂等判据：长度/骨骼集合与母本一致 + 所有通道首末帧与母本逐字相等。"""
    if abs(float(current.get("animation_length") or 0) - float(mother.get("animation_length") or 0)) > 1e-9:
        return False
    if set(current.get("bones", {})) != set(mother.get("bones", {})):
        return False

    for name, body in current.get("bones", {}).items():
        mother_body = mother.get("bones", {}).get(name, {})
        for channel_name, channel in body.items():
            if not isinstance(channel, dict) or "vector" in channel:
                continue
            keys = stepped_keys(channel)
            if len(keys) < 2:
                continue
            source = mother_body.get(channel_name)
            if not isinstance(source, dict) or "vector" in source:
                return False
            mother_keys = stepped_keys(source)
            if not mother_keys:
                return False
            if (copy(channel[fmt(keys[0])]) != copy(source[fmt(mother_keys[0])])
                    or copy(channel[fmt(keys[-1])]) != copy(source[fmt(mother_keys[-1])])):
                return False

    # 中段姿态得真的动了，而且方向对
    for entry, vector in delta.items():
        bone, channel = entry.split(".", 1)
        keys = stepped_keys(current.get("bones", {}).get(bone, {}).get(channel, {}))
        if not keys:
            return False
        key = fmt(keys[len(keys) // 2])
        here = unwrap(current["bones"][bone][channel][key])
        there = unwrap(mother["bones"][bone][channel][key])
        if here is None or there is None:
            return False
        if vector[0] != 0.0 and (here[0] - there[0]) * vector[0] <= 0.0:
            return False
        if vector[1] != 0.0 and (here[1] - there[1]) * vector[1] <= 0.0:
            return False
    return True


def main():
    force = "--force" in sys.argv[1:]
    with open(ANIM, "r", encoding="utf-8") as handle:
        doc = json.load(handle)

    anims = doc["animations"]
    changed = False
    for name in ("fly_up", "fly_down"):
        changed = build(anims, name, POSES[name], force) or changed

    if not changed:
        return 0

    with open(ANIM, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, ensure_ascii=False, separators=(",", ":"))
    print("[ok] 已写回 %s" % os.path.relpath(ANIM, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

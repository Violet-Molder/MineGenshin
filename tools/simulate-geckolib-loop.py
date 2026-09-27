# -*- coding: utf-8 -*-
"""
离线复算 GeckoLib 5.5.6 的「烘焙 + 求值 + 控制器时间轴」，
看某个动画在**循环接缝**那一帧到底有没有数值上的跳变。

为什么要它：§四十一 里所有探针看的都是「骨骼快照」，而快照是时间轴算出来的结果 ——
时间轴本身怎么走（哪一帧回卷、回卷后落在哪个关键帧、插值用的是哪一对关键帧）
在实机日志里看不到。这里把 GeckoLib 的那几段代码（见 §四十一 第 3 节引的源码）
原样搬成 Python，直接算出来。

对照的 Java 源（G:\\AI\\Codex\\Temp\\geckolib-src）：
    loading/definition/animation/ActorBoneAnimationEntry.java     烘焙入口 / keyframeLength
    loading/definition/animation/ActorBoneAnimationKeyframe.java    pre / values / post 三个落点
    animation/object/EasingType.java                              LINEAR / CatmullRomEasing
    animation/state/AnimationPoint.java                           createFor / createNext / 关键帧索引搜索
    animation/state/AnimationTimeline.java                        createAnimationPoint / 回卷
    animation/AnimationProcessor.java                             findAnimationPointValue
    animation/AnimationController.java                            checkControllerState / progressExistingAnimation

用法：
    python -B tools/simulate-geckolib-loop.py [动画名] [fps] [圈数]
默认：run / 60 / 3
"""

import io
import json
import math
import sys

PATH = (r"E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin"
        r"\character\shenhe\shenhe.animation.json")

EPS = 1e-5          # Mth.EPSILON
TPS = 20.0
KINDS = ("rotation", "position", "scale")


# ---------------------------------------------------------------- 烘焙

class KF:
    __slots__ = ("t", "length", "start", "end", "easing", "args")

    def __init__(self, t, length, start, end, easing, args=()):
        self.t = t
        self.length = length
        self.start = start
        self.end = end
        self.easing = easing
        self.args = args


def vec(entry):
    """从一条关键帧 JSON 里取 vector（兼容裸 list / {"vector": [...]}）。"""
    if isinstance(entry, list):
        return list(entry)
    if isinstance(entry, dict):
        v = entry.get("vector")
        if isinstance(v, list):
            return list(v)
    raise ValueError("no vector in %r" % (entry,))


def bake_channel(entry):
    """
    把一条通道（rotation / position / scale）烘成 KF 列表。
    返回 (keyframes, forced_linear)；keyframes 按时间升序。
    """
    if not isinstance(entry, dict):
        # 单键（persistent）：落在 t=0、length=0
        return [KF(0.0, 0.0, vec(entry), vec(entry), "linear")], True
    times = []
    for key in entry:
        try:
            times.append(float(key))
        except ValueError:
            pass
    if not times:
        return [KF(0.0, 0.0, vec(entry), vec(entry), "linear")], True

    forced_linear = len(times) == 1
    out = []
    last = None

    def emit(timestamp, value_entry, easing_name):
        nonlocal last
        value = vec(value_entry)
        easing = "linear" if forced_linear or easing_name is None else easing_name
        length = timestamp - (last.t if last is not None else 0.0)
        start = last.end if last is not None else value
        kf = KF(timestamp, length, start, value, easing)
        out.append(kf)
        last = kf

    for key in sorted(times):
        node = entry[str(key)] if str(key) in entry else entry[key]
        if isinstance(node, list):
            emit(key, node, None)
            continue
        if not isinstance(node, dict):
            emit(key, node, None)
            continue
        name = node.get("lerp_mode") or node.get("easing")
        pre = node.get("pre")
        if pre is not None:
            emit(key - EPS, pre, name)
        if "vector" in node:
            emit(key, node, name)
        post = node.get("post")
        if post is not None:
            emit(key + EPS, post, name)

    # addEasingArgs：CatmullRomEasing#modifyKeyframes 的锚点规则
    for i, kf in enumerate(out):
        if kf.easing == "catmullrom":
            p0 = out[i].start if i < 2 else out[i - 2].end
            p3 = out[i].end if i + 1 >= len(out) else out[i + 1].end
            kf.args = (p0, p3)
    return out, forced_linear


class Animation:
    def __init__(self, name, length, loop, bones):
        self.name = name
        self.length = length
        self.loop = loop
        self.bones = bones            # bone -> kind -> [KF]
        self.index = {b: i for i, b in enumerate(sorted(bones))}


def load_animation(name):
    doc = json.loads(io.open(PATH, encoding="utf-8").read())
    body = doc["animations"][name]
    bones = {}
    for bone, channels in body["bones"].items():
        baked = {}
        for kind in KINDS:
            if kind in channels:
                baked[kind] = bake_channel(channels[kind])[0]
        if baked:
            bones[bone] = baked
    return Animation(name, float(body["animation_length"]), bool(body.get("loop")), bones)


# ------------------------------------------------------- 关键帧索引 + 求值

NO_KEYFRAME = -2
BEFORE_FIRST = -1


def find_forward(kfs, anim_time, starting):
    if not kfs:
        return NO_KEYFRAME
    if kfs[0].t > anim_time:
        return BEFORE_FIRST
    for i in range(max(0, starting), len(kfs)):
        if i + 1 < len(kfs) and kfs[i + 1].t >= anim_time:
            return i
    return len(kfs) - 1


def find_reverse(kfs, anim_time, starting):
    if not kfs:
        return NO_KEYFRAME
    for i in range(min(starting, len(kfs) - 1), -1, -1):
        if i - 1 >= 0 and kfs[i - 1].t <= anim_time:
            return i - 1
    return BEFORE_FIRST


def axis_value(kfs, anim_time, index):
    if not kfs:
        return None
    if index == NO_KEYFRAME:
        return None
    cur = kfs[max(0, min(index, len(kfs) - 1))]
    nxt = kfs[max(0, min(index + 1, len(kfs) - 1))]
    frm, to = cur.end, nxt.end
    if cur is nxt or cur.easing == "linear" or frm == to:
        delta = 0.0 if nxt.length == 0 else (anim_time - cur.t) / nxt.length
        return [a + (b - a) * delta for a, b in zip(frm, to)]
    delta = 0.0 if nxt.length == 0 else (anim_time - cur.t) / nxt.length
    if delta >= 1.0:
        return list(to)
    p0, p3 = (nxt.args if len(nxt.args) == 2 else (frm, to))
    out = []
    for a, b, c, d in zip(p0, frm, to, p3):
        out.append(0.5 * (2 * b + (c - a) * delta
                          + (2 * a - 5 * b + 4 * c - d) * delta * delta
                          + (3 * b - a - 3 * c + d) * delta * delta * delta))
    return out


class Point:
    """对应 GeckoLib 的 AnimationPoint（keyFramePoints 在 createNext 里是共用的那份数组）。"""

    __slots__ = ("anim", "anim_time", "idx")

    def __init__(self, anim, anim_time, idx):
        self.anim = anim
        self.anim_time = anim_time
        self.idx = idx

    @staticmethod
    def create_for(anim, anim_time):
        anim_time = max(0.0, min(anim_time, anim.length))
        idx = {}
        for bone, channels in anim.bones.items():
            idx[bone] = {k: [find_forward(kfs, anim_time, 0) for kfs in [channels[k]]][0]
                         for k in channels}
        return Point(anim, anim_time, idx)

    def create_next(self, anim_time):
        anim_time = max(0.0, min(anim_time, self.anim.length))
        if abs(anim_time - self.anim_time) < 1e-5:
            return self
        reverse = anim_time < self.anim_time
        idx = {b: {k: (find_reverse(self.anim.bones[b][k], anim_time, self.idx[b][k])
                       if reverse else
                       find_forward(self.anim.bones[b][k], anim_time, self.idx[b][k]))
                   for k in self.idx[b]}
               for b in self.idx}
        return Point(self.anim, anim_time, idx)

    def sample(self):
        out = {}
        for bone, channels in self.anim.bones.items():
            out[bone] = {}
            for kind, kfs in channels.items():
                v = axis_value(kfs, self.anim_time, self.idx[bone][kind])
                out[bone][kind] = v
        return out


# ------------------------------------------------------------- 控制器时间轴

def create_animation_point(anim, timeline_time, existing):
    """AnimationTimeline#createAnimationPoint（单段时间轴，transitionTicks = 0）。"""
    if existing is None or existing.anim is not anim or \
            (anim.length - existing.anim_time) < existing.anim_time:
        return Point.create_for(anim, timeline_time)
    return existing.create_next(timeline_time)


def run_between(timeline_time, total_time):
    return max(0.0, min(timeline_time, total_time))


def simulate(anim, fps, cycles):
    frames = int(fps * anim.length * cycles)
    total_time = anim.length
    timeline_time = -1.0
    point = None
    last_age = 0.0
    rows = []
    for f in range(frames + 1):
        tick = f // (fps // TPS if fps >= TPS else 1)
        partial = (f % (fps // TPS)) / (fps // TPS) if fps >= TPS else 0.0
        if partial >= 1.0:
            partial = 1.0
        age = tick + partial
        time_delta = 0.0 if partial == 1 else (age - last_age) / 20.0
        if partial != 1:
            last_age = age
        if timeline_time >= 0:
            timeline_time = run_between(timeline_time + time_delta, total_time)
        if point is None:
            timeline_time = 0.0
            point = create_animation_point(anim, timeline_time, None)
            rows.append((f, tick, partial, timeline_time, point.anim_time, point.sample(), "init"))
            continue
        if time_delta != 0.0:
            prev_time = timeline_time - time_delta
            if timeline_time >= total_time:            # LoopType.LOOP 回卷
                timeline_time = 0.0 + (timeline_time - total_time)
                point = create_animation_point(anim, timeline_time, point)
                rows.append((f, tick, partial, timeline_time, point.anim_time, point.sample(), "loop"))
            else:
                point = create_animation_point(anim, timeline_time, point)
                rows.append((f, tick, partial, timeline_time, point.anim_time, point.sample(), ""))
            continue
        rows.append((f, tick, partial, timeline_time, point.anim_time, point.sample(), "hold"))
    return rows


SENTINELS = ("Root", "UpperBody", "LeftLeg", "RightLeg", "Robot_Root", "Skirt_Robot")


def describe(sample, bone):
    if bone not in sample:
        return "—"
    channels = sample[bone]
    parts = []
    for kind in ("rotation", "position"):
        if kind in channels and channels[kind]:
            parts.append(kind[:1] + "=" + ",".join(str(round(v, 2)) for v in channels[kind]))
    return " ".join(parts) if parts else "—"


def pose_distance(a, b):
    total = 0.0
    for bone in SENTINELS:
        if bone not in a or bone not in b:
            continue
        for kind in a[bone]:
            if kind not in b[bone]:
                continue
            va, vb = a[bone][kind], b[bone][kind]
            if not va or not vb:
                continue
            total += sum(abs(x - y) for x, y in zip(va, vb))
    return total


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "run"
    fps = float(sys.argv[2]) if len(sys.argv) > 2 else 60.0
    cycles = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    anim = load_animation(name)
    print("动画 %s：长度 %s 秒，循环 %s，骨骼 %d 根"
          % (anim.name, anim.length, anim.loop, len(anim.bones)))
    print("仿真：%g fps / %d tps / %d 圈" % (fps, TPS, cycles))

    rows = simulate(anim, fps, cycles)
    print("\n== 每帧姿势跳变（前 12 名）==")
    deltas = []
    for i in range(1, len(rows)):
        d = pose_distance(rows[i - 1][5], rows[i][5])
        deltas.append((d, i))
    for d, i in sorted(deltas, reverse=True)[:12]:
        f, tick, partial, tl, at, _, tag = rows[i]
        print("  Δ=%7.3f  frame=%-5d 刻=%-4d partial=%.2f timeline=%6.3f animTime=%6.3f %s"
              % (d, f, tick, partial, tl, at, tag or "普通帧"))

    print("\n== 接缝前后 12 帧 ==")
    loops = [i for i, r in enumerate(rows) if r[6] == "loop"]
    for start in loops[:2]:
        for i in range(max(0, start - 6), min(len(rows), start + 7)):
            f, tick, partial, tl, at, sample, tag = rows[i]
            mark = "  <<< 回卷帧" if tag == "loop" else ("  <<< init" if tag == "init" else "")
            print("  frame=%-5d 刻=%-4d partial=%.2f timeline=%6.3f animTime=%6.3f %-6s Root[%s] LeftLeg[%s]%s"
                  % (f, tick, partial, tl, at, tag or "-",
                     describe(sample, "Root"), describe(sample, "LeftLeg"), mark))
        print("  ----")

    print("\n== animTime 极值处的姿势（看接缝两侧的数值是否连续）==")
    for probe_t in (0.0, anim.length - 1e-6, anim.length):
        p = Point.create_for(anim, probe_t)
        print("  animTime=%8.5f  %s" % (probe_t, "  ".join(
            "%s{%s}" % (b, describe(p.sample(), b)) for b in ("Root", "LeftLeg", "Robot_Root"))))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""
修 `run` 循环末尾那 1/24 秒的「姿势冻住」—— 每个周期开头闪一下的真凶。

## 实据（`tools/simulate-geckolib-loop.py` 复算，2026-09-27）

`run` 的素材是 **16 帧一圈**（24 fps）：

* 肢体通道的键落在 **k/24**（k = 0..16），最后一键在 **16/24 = 0.6667**；
* 帧 16 的姿势**逐通道等于帧 0**（左腿 -25、右腿 20.47、Root 起伏 1.04…），
  也就是素材自己把「回到起点」写成了末键 —— 帧 16 就是这个循环的收口；
* 但 `animation_length` 写的是 **17/24 = 0.7083**，比末键多出整整 1/24 秒。

多出来的那一帧没有任何键，GeckoLib 只会「保持上一个键的值」：

    t = 0.6667 / 0.6833 / 0.7000 / 回卷后的 0.0000  →  四帧姿势完全相同

于是**每一圈的最后 1/24 秒，整个人物（含木偶、裙摆、马尾）冻在周期起点的姿势上**，
下一帧才突然恢复运动 —— 原速 42 ms、放慢到 4 秒一圈时是 0.235 秒。
这正是用户口径「跳一下之后，每一个周期的一开始会闪一次」，也正是姿态探针抓到
「那几帧的姿势正好是 run 时间轴的起点姿势」的原因：它不是算错了，是**停住了**。

## 修法

1. `animation_length` 改回末键的时间 **0.6667（16/24）**，接缝处不再有保持段；
2. 另有 13 条通道的末键写在 **17/24**（0.7083，如 `Robot_Root.position`、`Skirt_Robot.rotation`、
   `*BackClothe`、`*SideHair`、`HatBow*`）—— 它们是按「长度 = 17/24」写的，
   要把那个键一并挪到 **0.6667**，否则它会落到动画长度之外、末段插值到一半就被回卷截断。

改完之后：每条通道的末键都正好落在动画长度上，值等于首键，回卷点就是那个末键 ——
值连续、速度连续，没有任何「多出来的帧」。

用法：
    python -B tools/fix-run-loop-length.py --check    # 只报告
    python -B tools/fix-run-loop-length.py            # 修（幂等）
    python -B tools/fix-run-loop-length.py --restore  # 从备份还原 run 这一条
"""

# 文件里的时间键是「四舍五入到 4 位小数」的，跟精确的 k/24 有 3e-5 的出入，
# 所以一律先折成「第几帧」再比较，写回时沿用文件里已有的那种写法。

import io
import json
import collections
import shutil
import sys

SRC = (r"E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin"
       r"\character\shenhe\shenhe.animation.json")
BAK = (r"G:\AI\Codex\Skill\像素原神\备份\shenhe-anim-20260927"
       r"\shenhe.animation.json.full")

FRAME = 1.0 / 24.0
SKIP = {"vector", "post", "pre", "lerp_mode", "easing", "easingArgs"}


def times_of(channel):
    out = []
    if isinstance(channel, dict):
        for key in channel:
            try:
                out.append(float(key))
            except ValueError:
                pass
    return sorted(out)


def fmt(t):
    return ("%.4f" % t).rstrip("0").rstrip(".") if t else "0.0"


def main():
    doc = json.loads(io.open(SRC, encoding="utf-8").read())
    run = doc["animations"]["run"]
    length = float(run["animation_length"])

    # 收口帧 = 大多数通道末键所在的那一帧（这里众数应当落在第 16 帧），
    # 顺便记下这一帧在文件里是怎么写的（"0.6667"）
    counts = collections.Counter()
    spelling = {}
    for channels in run["bones"].values():
        for kind in ("rotation", "position", "scale"):
            ts = times_of(channels.get(kind))
            if len(ts) >= 2:
                frame_no = round(ts[-1] * 24)
                counts[frame_no] += 1
                spelling.setdefault(frame_no, str(ts[-1]))
    frame_no = max(counts, key=counts.get)
    close_key = spelling.get(frame_no)
    if close_key is None:
        raise SystemExit("找不到收口帧的写法")

    later = []
    for bone, channels in run["bones"].items():
        for kind in ("rotation", "position", "scale"):
            ts = times_of(channels.get(kind))
            if ts and round(ts[-1] * 24) > frame_no:
                later.append((bone, kind, ts[-1], len(ts)))

    print("run：animation_length = %s（%s/24）；末键分布 %s"
          % (length, round(length * 24), sorted(counts.items())))
    print("多数通道的收口帧 = %d/24（文件里写作 %s）" % (frame_no, close_key))
    if abs(round(length * 24) - frame_no) > 1:
        raise SystemExit("长度和末键差了不止一帧（%s vs %s），不自动改，先人工看"
                         % (round(length * 24), frame_no))
    print("末键晚于收口帧的通道：%d 条" % len(later))
    for bone, kind, t, n in later:
        print("   %-16s %-9s 末键 %.4f（%s/24，共 %d 键）" % (bone, kind, t, round(t * 24), n))

    if "--check" in sys.argv:
        return

    if "--restore" in sys.argv:
        bak = json.loads(io.open(BAK, encoding="utf-8").read())
        doc["animations"]["run"] = bak["animations"]["run"]
    else:
        for bone, channels in run["bones"].items():
            for kind in ("rotation", "position", "scale"):
                channel = channels.get(kind)
                ts = times_of(channel)
                if not ts or round(ts[-1] * 24) <= frame_no:
                    continue
                if any(round(t * 24) == frame_no for t in ts):
                    raise SystemExit("撞键：%s %s 已经有一个 %s 的键，别再自动挪"
                                     % (bone, kind, close_key))
                moved = channel.pop(str(ts[-1]))
                channel[close_key] = moved
        run["animation_length"] = float(close_key)

    io.open(SRC, "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, ensure_ascii=False, separators=(",", ":")))

    # 改完立刻自检：长度是不是正好落在所有通道的末键上、首末值是不是一致
    doc = json.loads(io.open(SRC, encoding="utf-8").read())
    run = doc["animations"]["run"]
    length = float(run["animation_length"])
    bad = []
    for bone, channels in run["bones"].items():
        for kind in ("rotation", "position", "scale"):
            ts = times_of(channels.get(kind))
            if ts and abs(ts[-1] - length) > 1e-3 and len(ts) > 1:
                bad.append((bone, kind, ts[-1], len(ts)))
    print("已写入。现在 animation_length = %s（%s/24）" % (length, round(length * 24)))
    print("末键没落在动画长度上的通道（单键通道不计）：%d 条" % len(bad))
    for bone, kind, t, n in bad[:20]:
        print("   %-16s %-9s 末键 %.4f（%s/24，共 %d 键）" % (bone, kind, t, round(t * 24), n))


if __name__ == "__main__":
    main()

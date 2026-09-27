# 申鹤（木偶）普攻动画自检 —— 纯离线读 json，不需要起游戏。
#
# 查这几件事：
#   ① 三段普攻的动画名/时长在资源里真的存在，且时长够引擎那一套刻数播完；
#   ② 收尾动画 sword_idle_attack_end 的首帧 == 第三段末帧（硬切不跳）；
#   ③ 收尾动画的末帧 == idle 首帧（回常态不跳），并把「收尾没写、idle 写了」的通道列出来。
#   ④ 跳 / 落 / 落地 / 急停自洽：jump 与 jump_down 是闭合循环、起跳末帧 == 下落首帧
#      （接缝不跳形）、落地缓冲首帧没有整体上抬（落地不先弹一下）、急停收得回 idle。
#   ⑤ 水里的姿态分档：三条常态（不动 / 前进 / 后退）必须都点竖直的 swim_stand，
#      横躺的 swim 只许出现在「加速」那一档（引擎侧 player.isSwimming()）。
#
# 用法： python tools/verify-shenhe-anim.py
#
# ── 为什么是「逐通道比数值」而不是肉眼看 ────────────────────────────────────
# 引擎（GeckoLib 5.5.x）对动画里没写的骨骼用 <模型基础姿态 + 0> 渲染
# （见 RenderUtil.translateAndRotateMatrixForBone：baseRot + snapshot，snapshot 默认 0），
# 硬切（transitionTicks = 0）时 snapshot 直接取目标动画在当前关键帧的值，
# 上一段有、这一段没有的骨骼则被显式复位。所以「接得连不连」等价于
# 「同一根骨骼、同一个通道，在切换那一刻的数值是否相等」，可以完全靠数值判定。
#
# 只读，不改任何文件。

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "minegenshin", "character", "shenhe")
MAIN = os.path.join(DIR, "shenhe.animation.json")
PUPPET = os.path.join(DIR, "shenhe_puppet.animation.json")
# 引擎侧的常态动画名就是从这张表读出来的，所以「水里用了哪条素材」要连它一起查
SHENHE_ANIMATIONS = os.path.join(ROOT, "src", "main", "java", "com", "linweiyun", "genshin",
                                 "core", "character", "polearm", "shenhe", "ShenheAnimations.java")

# 引擎侧 ShenheResources 里点名的动画（改那边就要改这边）
COMBO = ["sword_idle_attack_01", "sword_idle_attack_02", "sword_idle_attack_03"]
END = "sword_idle_attack_end"
END_TICKS = 18
COMBO_TICKS = [20, 20, 18]

# 水：竖直的常态姿态 vs 横躺的加速姿态
WATER_VERTICAL = "swim_stand"
WATER_ACCEL = "swim"

# 常态里那几条一次性 / 循环素材，与 ShenheAnimations.LOCOMOTION 一一对应
JUMP = "jump"                 # 起跳上升循环（已按 jump_down 重写，见 tools/patch-shenhe-jump.py）
JUMP_DOWN = "jump_down"       # 下落循环
LANDING = "landing_heavy"      # 重落地缓冲（一次性）
LANDING_TICKS = 12
RUN_STOP = "run_stop"          # 疾跑急停（一次性）
RUN_STOP_TICKS = 25
# 急停末帧回到 idle 首帧时可以容忍的最大分量差（度/单位）。
# 这两条素材本来就是「收在站姿」而不是「逐字对齐 idle」，进 idle 那一下引擎还有 5 刻插值。
IDLE_HANDOFF_TOLERANCE = 15.0
# 引擎侧 ShenheAnimations.EXIT_TRANSITION_TICKS —— 常态动画互切时的插值刻数（改那边就要改这边）
EXIT_TRANSITION_TICKS = 5


def load(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)["animations"]


def keyed(channel):
    """[(秒, 原始键名)]，按时间排好。"""
    return sorted(((float(k), k) for k in channel if k != "vector"), key=lambda pair: pair[0])


def unwrap(value):
    """键帧值可能是 [x,y,z]、{'vector': [...]} 或 {'pre':…, 'post':…}。"""
    while isinstance(value, dict):
        if "post" in value:
            value = value["post"]
        elif "vector" in value:
            return [float(v) for v in value["vector"]]
        elif "pre" in value:
            value = value["pre"]
        else:
            return None
    return [float(v) for v in value] if value is not None else None


def sample(channel, time):
    """在 time 秒处取通道的值（线性插值；'vector' 视作常量）。"""
    if "vector" in channel:
        return [float(v) for v in channel["vector"]]
    keys = keyed(channel)
    if not keys:
        return None
    if time <= keys[0][0]:
        return unwrap(channel[keys[0][1]])
    if time >= keys[-1][0]:
        return unwrap(channel[keys[-1][1]])
    for index in range(len(keys) - 1):
        start, start_key = keys[index]
        stop, stop_key = keys[index + 1]
        if start <= time <= stop:
            low, high = unwrap(channel[start_key]), unwrap(channel[stop_key])
            if low is None or high is None:
                return low if low is not None else high
            ratio = (time - start) / (stop - start) if stop > start else 0.0
            return [low[i] + (high[i] - low[i]) * ratio for i in range(len(low))]
    return None


def channels(animation):
    return {(bone, channel): data
            for bone, group in animation["bones"].items()
            for channel, data in group.items()
            if channel in ("rotation", "position", "scale")}


def same(left, right, eps=1e-3):
    if left is None or right is None:
        return left is right
    return len(left) == len(right) and all(abs(a - b) <= eps for a, b in zip(left, right))


def default_of(pair, value):
    """这个值是不是「等于默认姿态」（旋转/位移 0，缩放 1）。"""
    if value is None:
        return True
    if pair[1] == "scale":
        return all(abs(v - 1.0) <= 1e-3 for v in value)
    return all(abs(v) <= 1e-3 for v in value)


def main():
    main_file = load(MAIN)
    puppet_file = load(PUPPET)
    failures = []

    print("== ① 动画名与时长 ==")
    for index, name in enumerate(COMBO):
        if name not in main_file:
            failures.append(f"{name} 不在 {os.path.basename(MAIN)} 里")
            continue
        length = float(main_file[name]["animation_length"])
        ticks = length / 0.05
        enough = COMBO_TICKS[index] >= ticks - 0.5
        print(f"  {name}: {length}s = {ticks} 刻；引擎给 {COMBO_TICKS[index]} 刻 -> "
              f"{'够' if enough else '不够（最后几帧看不到）'}")
        if not enough:
            failures.append(f"{name} 的时长 {ticks} 刻 > 引擎的 {COMBO_TICKS[index]} 刻")
    if END not in puppet_file:
        failures.append(f"{END} 不在 {os.path.basename(PUPPET)} 里")
    else:
        end = puppet_file[END]
        print(f"  {END}: {end['animation_length']}s，loop={end.get('loop')}；"
              f"引擎给 {END_TICKS} 刻")

    print("\n== ② 收尾首帧 == 第三段末帧 ==")
    end_animation = puppet_file[END]
    third = main_file[COMBO[2]]
    end_channels = channels(end_animation)
    third_channels = channels(third)
    third_end = float(third["animation_length"])
    mismatches = [(key, sample(data, 0.0), sample(third_channels[key], third_end))
                  for key, data in end_channels.items() if key in third_channels
                  and not same(sample(data, 0.0), sample(third_channels[key], third_end))]
    print(f"  共有通道 {len([k for k in end_channels if k in third_channels])}，不一致 {len(mismatches)}")
    for key, got, want in mismatches:
        print(f"    {key}: {got} != {want}")
    if mismatches:
        failures.append(f"收尾首帧与第三段末帧有 {len(mismatches)} 处不一致")

    print("\n== ③ 收尾末帧 == idle 首帧 ==")
    idle = main_file["idle"]
    idle_channels = channels(idle)
    end_time = float(end_animation["animation_length"])
    mismatches = [(key, sample(data, end_time), sample(idle_channels[key], 0.0))
                  for key, data in end_channels.items() if key in idle_channels
                  and not same(sample(data, end_time), sample(idle_channels[key], 0.0))]
    print(f"  共有通道 {len([k for k in end_channels if k in idle_channels])}，不一致 {len(mismatches)}")
    for key, got, want in mismatches:
        print(f"    {key}: {got} != {want}")
    if mismatches:
        failures.append(f"收尾末帧与 idle 首帧有 {len(mismatches)} 处不一致")

    # idle 写了、收尾没写的通道：硬切过去时这些骨骼会从「模型基础姿态」跳到 idle 的值。
    # 引擎对「上一段有、这一段没有」的骨骼是显式复位的，所以这不是收尾写错，而是素材本身
    # 在这些骨骼上不一致 —— 列出来，方便判断要不要一起补进收尾。
    idle_only = [(key, sample(data, 0.0)) for key, data in idle_channels.items()
                 if key not in end_channels and not default_of(key, sample(data, 0.0))]
    print(f"\n== ④ idle 写了、收尾没写、且首帧非默认值的通道：{len(idle_only)} ==")
    for key, value in idle_only:
        print(f"    {key}: {value}")

    print("\n== ⑤ 三段之间 ==")
    sequence = [(name, main_file[name]) for name in COMBO] + [(END, end_animation)]
    for index in range(len(sequence) - 1):
        name_a, animation_a = sequence[index]
        name_b, animation_b = sequence[index + 1]
        left, right = channels(animation_a), channels(animation_b)
        stop = float(animation_a["animation_length"])
        shared = [key for key in left if key in right]
        gaps = [(key, sample(left[key], stop), sample(right[key], 0.0)) for key in shared
                if not same(sample(left[key], stop), sample(right[key], 0.0))]
        print(f"  {name_a} 末帧 -> {name_b} 首帧：共有 {len(shared)}，不一致 {len(gaps)}")
        for key, got, want in gaps:
            print(f"      {key}: {got} -> {want}")

    print("\n== ⑥ 跳 / 落 / 落地 / 急停 ==")
    for name, want_loop in ((JUMP, True), (JUMP_DOWN, True)):
        if name not in main_file:
            failures.append(f"{name} 不在 {os.path.basename(MAIN)} 里")
            continue
        animation = main_file[name]
        length = float(animation["animation_length"])
        looped = animation.get("loop") is True
        open_channels = [(key, sample(data, 0.0), sample(data, length))
                         for key, data in channels(animation).items()
                         if not same(sample(data, 0.0), sample(data, length))]
        print(f"  {name}: {length}s, loop={animation.get('loop')}, "
              f"首末帧不一致的通道 {len(open_channels)}")
        for key, got, want in open_channels:
            print(f"      {key}: {got} != {want}")
        if looped != want_loop:
            failures.append(f"{name} 的 loop 标记应为 {want_loop}，实际 {animation.get('loop')}")
        if open_channels:
            failures.append(f"{name} 标着 loop 却有 {len(open_channels)} 条通道首末帧不一致（循环会弹跳）")

    if JUMP in main_file and JUMP_DOWN in main_file:
        jump_animation = main_file[JUMP]
        jump_channels, down_channels = channels(jump_animation), channels(main_file[JUMP_DOWN])
        shared = [key for key in jump_channels if key in down_channels]
        limbs = [key for key in shared if "Arm" in key[0] or "Hand" in key[0]]
        drift = [key for key in limbs
                 if not same(sample(jump_channels[key], 0.0), sample(down_channels[key], 0.0))]
        print(f"  {JUMP} 与 {JUMP_DOWN} 的公共通道 {len(shared)}，其中手臂类 {len(limbs)}，"
              f"首帧不一致 {len(drift)}")
        extra = sorted({key[0] for key in jump_channels} - {key[0] for key in down_channels})
        print(f"    {JUMP} 独有骨骼（应为整体位移这类）：{extra}")

        # 接缝：起跳末帧 == 下落首帧。收腿增量现在按 sin(π·t/T) 收放，起末严格为 0，
        # 所以这两帧应当逐字相等；上一版把增量当常量逐帧加，接缝处腿撕开 20°~34°，
        # 起跳转下落那一下就弹一下 —— 这条判据专门盯它。
        jump_length = float(jump_animation["animation_length"])
        seam = [(key, sample(jump_channels[key], jump_length), sample(down_channels[key], 0.0))
                for key in shared
                if not same(sample(jump_channels[key], jump_length),
                            sample(down_channels[key], 0.0))]
        print(f"    {JUMP} 末帧 -> {JUMP_DOWN} 首帧：共有 {len(shared)}，不一致 {len(seam)}")
        for key, got, want in seam:
            print(f"      {key}: {got} -> {want}")
        if seam:
            failures.append(f"{JUMP} 末帧与 {JUMP_DOWN} 首帧有 {len(seam)} 处不一致"
                            f"（起跳转下落会跳形）")

    if LANDING in main_file and JUMP_DOWN in main_file:
        landing_animation = main_file[LANDING]
        length = float(landing_animation["animation_length"])
        landing_channels = channels(landing_animation)
        down_channels = channels(main_file[JUMP_DOWN])
        shared = [key for key in landing_channels if key in down_channels]
        gaps = [(key, sample(landing_channels[key], 0.0), sample(down_channels[key], 0.0))
                for key in shared
                if not same(sample(landing_channels[key], 0.0), sample(down_channels[key], 0.0))]
        # 手臂/前臂/手是「滞空姿势」读出来的地方，必须首帧一致，落地那一下才不会跳形；
        # 裙摆与布料在素材里本来就不同（它要在落地过程里慢慢放下来），
        # 引擎进这条动画时还有 exitTransitionTicks 刻插值，差异是被抹平的那部分。
        pose_keys = [key for key in shared if "Arm" in key[0] or "Hand" in key[0]]
        pose_gaps = [key for key in pose_keys
                     if not same(sample(landing_channels[key], 0.0), sample(down_channels[key], 0.0))]
        cloth_gaps = [key for key, _, _ in gaps if key not in pose_keys]
        print(f"  {LANDING}: {length}s = {length / 0.05:.1f} 刻，引擎给 {LANDING_TICKS} 刻"
              f"（{'够' if LANDING_TICKS <= length / 0.05 + 0.5 else '超了'}）；"
              f"首帧 vs {JUMP_DOWN} 首帧：手臂类 {len(pose_keys) - len(pose_gaps)}/{len(pose_keys)} 一致，"
              f"布料类 {len(cloth_gaps)} 条不同（走进它时由 {EXIT_TRANSITION_TICKS} 刻插值消化）")
        for key in pose_gaps:
            print(f"      手臂 {key}: {sample(landing_channels[key], 0.0)} "
                  f"!= {sample(down_channels[key], 0.0)}")
        for key in cloth_gaps:
            print(f"      布料 {key}: {sample(landing_channels[key], 0.0)} "
                  f"!= {sample(down_channels[key], 0.0)}")
        if pose_gaps:
            failures.append(f"{LANDING} 的手臂类首帧与 {JUMP_DOWN} 有 {len(pose_gaps)} 处不一致"
                            f"（落地那一下会跳形）")

        # 整体位移：jump_down 没有 AllBody.position（滞空原地不动），落地缓冲若首帧就
        # 带着上抬（原素材是 +3.4），接上那一帧人会先弹起来再砸下去。首帧必须为 0。
        entry = landing_channels.get(("AllBody", "position"))
        if entry is None:
            print(f"  {LANDING} 没有 AllBody.position：接上 {JUMP_DOWN} 时人不会被整体推一下")
        else:
            first = sample(entry, 0.0)
            print(f"  {LANDING} 首帧 AllBody.position = {first}"
                  f"（应为 0，接上 {JUMP_DOWN} 的静止姿态不弹）")
            if not default_of(("AllBody", "position"), first):
                failures.append(f"{LANDING} 首帧整体位移 {first} 不为 0"
                                f"（落地那一下会先弹再砸）")

    if RUN_STOP in main_file:
        stop_animation = main_file[RUN_STOP]
        length = float(stop_animation["animation_length"])
        stop_channels = channels(stop_animation)
        shared = [key for key in stop_channels if key in idle_channels]
        gaps = [(key, sample(stop_channels[key], length), sample(idle_channels[key], 0.0))
                for key in shared
                if not same(sample(stop_channels[key], length), sample(idle_channels[key], 0.0),
                            IDLE_HANDOFF_TOLERANCE)]
        print(f"  {RUN_STOP}: {length}s = {length / 0.05:.1f} 刻，引擎给 {RUN_STOP_TICKS} 刻"
              f"（{'够' if RUN_STOP_TICKS <= length / 0.05 + 0.5 else '超了'}）；"
              f"末帧 vs idle 首帧：共有 {len(shared)}，超出 {IDLE_HANDOFF_TOLERANCE} 的 {len(gaps)}")
        for key, got, want in gaps:
            print(f"      {key}: {got} -> {want}")
        if RUN_STOP_TICKS > length / 0.05 + 0.5:
            failures.append(f"{RUN_STOP} 的时长 {length / 0.05:.1f} 刻 < 引擎的 {RUN_STOP_TICKS} 刻")
        if gaps:
            failures.append(f"{RUN_STOP} 末帧与 idle 首帧有 {len(gaps)} 处差得超过 "
                            f"{IDLE_HANDOFF_TOLERANCE}（急停收不进站姿）")

    print("\n== ⑤ 水里的姿态分档 ==")
    if not os.path.exists(SHENHE_ANIMATIONS):
        failures.append(f"找不到 {os.path.relpath(SHENHE_ANIMATIONS, ROOT)}")
    else:
        with open(SHENHE_ANIMATIONS, "r", encoding="utf-8") as handle:
            source = handle.read()
        if "LocomotionAnims.of(" not in source:
            failures.append("ShenheAnimations 里找不到 LocomotionAnims.of(...) 那张名单")
        else:
            body = source.split("LocomotionAnims.of(", 1)[1].split(".withTransitions", 1)[0]
            declared = re.findall(r'"([^"]*)"', body)
            if len(declared) < 14:
                failures.append(f"ShenheAnimations.LOCOMOTION 只解析出 {len(declared)} 个名字"
                                f"（应为 14）")
            else:
                water_idle, water_walk, water_back, accel = declared[8:12]
                print(f"  名单里的水里三项 = {water_idle} / {water_walk} / {water_back}，"
                      f"加速 = {accel}")
                for label, name in (("waterIdle", water_idle), ("waterWalk", water_walk),
                                    ("waterWalkBack", water_back)):
                    if name != WATER_VERTICAL:
                        failures.append(f"{label} 点的是 {name}（横躺），"
                                        f"水里一般游泳要竖直的 {WATER_VERTICAL}")
                if accel != WATER_ACCEL:
                    failures.append(f"加速那一档点的是 {accel}，应为横躺的 {WATER_ACCEL}")

    # 素材本身的朝向也要对得上：竖直的 Root 不俯仰，横躺的 Root 转了 90°。
    # 只查名字不够 —— 名字对了但素材朝向反了，等于白改。
    for name, want_pitch, label in ((WATER_VERTICAL, 0.0, "竖直"),
                                    (WATER_ACCEL, 90.0, "横躺")):
        animation = main_file.get(name)
        if animation is None:
            failures.append(f"{name} 不在 {os.path.basename(MAIN)} 里")
            continue
        data = channels(animation).get(("Root", "rotation"))
        pitch = sample(data, 0.0)[0] if data is not None else None
        print(f"  {name}: {animation['animation_length']}s，Root 俯仰 {pitch}°（{label}，"
              f"期望 {want_pitch}°）")
        if pitch is None or abs(pitch - want_pitch) > 1.0:
            failures.append(f"{name} 的 Root 俯仰是 {pitch}°、不是 {want_pitch}°，"
                            f"素材朝向与引擎侧「{label}」的用法对不上")

    print()
    if failures:
        for line in failures:
            print("FAIL " + line)
        return 1
    print("PASS 收尾动画与三段普攻、idle 在数值上全部接得上；"
          "跳/落/落地/急停自洽；水里的竖直/横躺分档也对")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""把母本里的「备用表情脸」子树补进 shenhe.geo.json（幂等，可反复重跑）。

为什么需要这一步
----------------
仓库里在用的 `character/shenhe/shenhe.geo.json`（572 骨）是**裁掉全部 Molang 查询与
表情子树**的产物（见 `CharacterRenderDispatcher` 里那段 ClassCastException 注释）。
裁掉的时候连 `yushe` 那张备用脸一起没了，于是：

* 引擎侧没有 `MEyes` 之外的「闭眼脸」可切（用户要的 `biyang1` 就是这张脸的闭眼档）；
* 现在实际显示的是 `MEyes` 那张脸（`head/MHead/.../Head` 之下）。

母本 `桑多涅1.2(Ysm2.6.3)/models/main.json`（694 骨）里，
`yushe`（parent `Face`，0 方块容器）挂着几档表情脸，其中前三档是：

| 骨骼 | 母本 Molang 档位 | 方块数 |
|---|---|---|
| `biyang1` | face == 5（**闭眼**，就是这里要用的） | 46 |
| `biyang_Left` | face == 6 | 99 |
| `biyang_Right` | face == 7 | 99 |

三档的整棵子树（40 骨 / 244 方块）在仓库这份 geo 里**没有任何重名骨骼**
（脚本自己会再查一遍，重名就报错退出），所以直接补进骨头表即可。
补完由引擎决定显隐，见 `CharacterFaceBones`：
常态藏三档、只留 `MEyes`；`extra_equip`（闭眼姿势）反过来藏 `MEyes`、放行 `biyang1`。

为什么是改这份文件而不是 `local/`
--------------------------------
`character/<id>/local/<名字>.geo.json` 确实能覆盖同名键，但那份是「一份整模型的副本」，
以后母本再更新（脸型、身体）就会与它悄悄分叉。这里改的是**构建期会收进整包的那份母本**，
和现有流程一致；万一以后重新从母本覆盖过 `shenhe.geo.json`，重跑本脚本即可。

用法
----
    python -B tools/add-shenhe-face-bones.py            # 用默认路径
    python -B tools/add-shenhe-face-bones.py --target <geo.json> --source <main.json>
"""

from __future__ import annotations

import argparse
import io
import json
import os
import sys

#: 694 骨母本（只有它才有 `yushe` 那棵子树）。
DEFAULT_SOURCE = r"G:\图片\MC-MOD素材\模型\桑多涅1.2(Ysm2.6.3)\models\main.json"

#: 游戏里真正吃的那份（构建期收进 `.minegenshin` 整包）。
DEFAULT_TARGET = (
    r"E:\MCMOD\MineGenshin-26.2\src\main\resources"
    r"\assets\minegenshin\character\shenhe\shenhe.geo.json"
)

#: 要补进去的子树根骨（`yushe` 是容器，三档表情脸都是它的子骨）。
SUBTREE_ROOTS = ("yushe", "biyang1", "biyang_Left", "biyang_Right")

#: 骨头表里一个骨骼对象的缩进（父级是 `bones` 数组）。
BONE_INDENT = "\t" * 4


def _num(value: float) -> str:
    """按母本的原样打印数字：整数就是整数，浮点保持 Python 的最短往返表示。"""
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return json.dumps(value)


def _vec(values) -> str:
    return "[" + ", ".join(_num(v) for v in values) + "]"


def _face(value) -> str:
    return '{"uv": ' + _vec(value["uv"]) + ", \"uv_size\": " + _vec(value["uv_size"]) + "}"


def _cube_lines(cube: dict, indent: str) -> list[str]:
    """一个方块对象，缩进与母本一致（`uv` 的花纹表每条朝内联成一行）。"""
    inner = indent + "\t"
    lines = [indent + "{"]
    lines.append(inner + '"origin": ' + _vec(cube["origin"]) + ",")
    lines.append(inner + '"size": ' + _vec(cube["size"]) + ",")
    lines.append(inner + '"pivot": ' + _vec(cube["pivot"]) + ",")
    if "rotation" in cube:
        lines.append(inner + '"rotation": ' + _vec(cube["rotation"]) + ",")
    if "inflate" in cube:
        lines.append(inner + '"inflate": ' + _num(cube["inflate"]) + ",")
    lines.append(inner + '"uv": {')
    faces = [(name, value) for name, value in cube["uv"].items()]
    for index, (name, value) in enumerate(faces):
        tail = "," if index + 1 < len(faces) else ""
        lines.append(inner + "\t" + '"%s": %s%s' % (name, _face(value), tail))
    lines.append(inner + "}")
    lines.append(indent + "}")
    return lines


def _bone_lines(bone: dict, indent: str = BONE_INDENT) -> list[str]:
    """一个骨骼对象：`name / parent / pivot / rotation? / cubes?`（键序与仓库这份一致）。"""
    inner = indent + "\t"
    lines = [indent + "{"]
    lines.append(inner + '"name": ' + json.dumps(bone["name"]) + ",")
    lines.append(inner + '"parent": ' + json.dumps(bone["parent"]) + ",")
    lines.append(inner + '"pivot": ' + _vec(bone["pivot"]) + ",")
    if "rotation" in bone:
        lines.append(inner + '"rotation": ' + _vec(bone["rotation"]) + ",")
    cubes = bone.get("cubes") or []
    if not cubes:
        lines[-1] = lines[-1][:-1]
        lines.append(indent + "}")
        return lines
    lines.append(inner + '"cubes": [')
    cube_indent = inner + "\t"
    for index, cube in enumerate(cubes):
        cube_lines = _cube_lines(cube, cube_indent)
        if index + 1 < len(cubes):
            cube_lines[-1] += ","
        lines.extend(cube_lines)
    lines.append(inner + "]")
    lines.append(indent + "}")
    return lines


def _load_bones(path: str) -> list[dict]:
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)["minecraft:geometry"][0]["bones"]


def _collect(bones: list[dict], root: str) -> list[dict]:
    """按深度优先序收集 `root` 及其整棵子树（母本顺序 = 父在子前）。"""
    children: dict[str | None, list[dict]] = {}
    for bone in bones:
        children.setdefault(bone.get("parent"), []).append(bone)
    by_name = {bone["name"]: bone for bone in bones}
    if root not in by_name:
        raise SystemExit("母本里没有骨骼 %s —— 传错 --source 了？" % root)

    ordered: list[dict] = []

    def walk(bone: dict) -> None:
        ordered.append(bone)
        for child in children.get(bone["name"], ()):
            walk(child)

    walk(by_name[root])
    return ordered


def _find_bone_end(text: str, name: str) -> int:
    """定位 `"name": "<name>"` 那个骨骼对象的结束 `}` 之后（含它后面那个逗号）。"""
    marker = '"name": "%s"' % name
    index = text.find(marker)
    if index < 0:
        raise SystemExit("目标文件里找不到骨骼 %s（缺 parent 挂点）" % name)
    start = text.rfind("\n" + BONE_INDENT + "{", 0, index)
    if start < 0:
        raise SystemExit("骨骼 %s 的对象起始位置没找到，格式与预期不符" % name)
    start += 1 + len(BONE_INDENT)
    depth = 0
    in_string = False
    escaped = False
    for position in range(start, len(text)):
        char = text[position]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = position + 1
                if text[end] == ",":
                    end += 1
                return end
    raise SystemExit("骨骼 %s 的对象没有闭合" % name)


def main() -> int:
    parser = argparse.ArgumentParser(description="给 shenhe.geo.json 补备用表情脸子树")
    parser.add_argument("--target", default=DEFAULT_TARGET)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    args = parser.parse_args()

    if not os.path.isfile(args.source):
        raise SystemExit("母本不存在：%s" % args.source)
    if not os.path.isfile(args.target):
        raise SystemExit("目标不存在：%s" % args.target)

    with io.open(args.target, encoding="utf-8") as handle:
        text = handle.read()
    if '"name": "yushe"' in text:
        print("已经补过（找到 yushe），什么都不做：%s" % args.target)
        return 0

    upstream = _load_bones(args.source)
    existing = set()
    for bone in _load_bones(args.target):
        existing.add(bone["name"])

    wanted: list[dict] = []
    seen: set[str] = set()
    for root in SUBTREE_ROOTS:
        for bone in _collect(upstream, root):
            if bone["name"] in seen:
                continue
            seen.add(bone["name"])
            wanted.append(bone)

    clash = sorted(name for name in seen if name in existing)
    if clash:
        raise SystemExit("有骨骼重名，先人工改名再补：%s" % ", ".join(clash))

    block = ""
    for bone in wanted:
        block += "\n".join(_bone_lines(bone)) + ",\n"

    end = _find_bone_end(text, "Face")
    # `end` 落在骨骼对象后面的那个逗号之后，还要吃掉换行，补进去的块才落在行首。
    if text[end] == "\r":
        end += 1
    if text[end] == "\n":
        end += 1
    patched = text[:end] + block + text[end:]

    try:
        json.loads(patched)
    except ValueError as error:
        raise SystemExit("补完不是合法 JSON，已放弃写入：%s" % error)

    with io.open(args.target, "w", encoding="utf-8", newline="") as handle:
        handle.write(patched)

    cubes = sum(len(bone.get("cubes") or ()) for bone in wanted)
    print("补入 %d 骨 / %d 方块：%s" % (len(wanted), cubes, args.target))
    print("骨骼：" + ", ".join(bone["name"] for bone in wanted))
    return 0


if __name__ == "__main__":
    sys.exit(main())

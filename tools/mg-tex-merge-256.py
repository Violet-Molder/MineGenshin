# -*- coding: utf-8 -*-
"""
把角色贴图 + 武器贴图合成一张 **256×256**（1:1）的图，并同步工程文件与 geo。

为什么要 256×256：原先是「角色 128 + 武器 128」拼成 256×128（长宽比 2:1），
不是 1:1；用户口径是 MC 这边必须 1:1。

做法（**内容像素位置与 UV 全部不动**，只换画布尺寸）：
    · 画布 256×256，角色贴图贴在 (0,0)、武器贴图贴在 (128,0)，下半张透明；
    · geo 的 texture_width/height 改成 256×256；
    · 武器骨骼的面 UV 已经在右半边（u≥128）——本脚本对「还没右移」的自动 +128，已右移的跳过（幂等）。

因为 UV 是**像素坐标**、图片宽度与声明宽度都是 256，采样到的像素位置和改之前完全一致；
只有高度 128→256（图片高度与声明高度一起变），下半张没有 UV 用到，所以画面不变。

输入（都在素材目录里，是用户自己的工程）：
    G:\\图片\\MC-MOD素材\\assets\\minegenshin\\character\\default\\deafult.bbmodel      （工程，内嵌角色/武器两张 128×128 贴图）
    仓库里的 linweiyun.geo.json                                                      （线上模型，就地补 UV/尺寸）

输出：
    G:\\AI\\Codex\\Skill\\像素原神\\工程文件\\deafult.merged.256.bbmodel               （256×256 的工程）
    G:\\AI\\Codex\\Skill\\像素原神\\工程文件\\default.merged.256.png                   （256×256 合成贴图）
    G:\\图片\\MC-MOD素材\\assets\\minegenshin\\character\\default\\deafult.merged.256.bbmodel（同工程，放回素材目录）
    仓库 src/main/resources/.../character/linweiyun/textures/linweiyun.png            （线上贴图 = 合成图）

用法： python -B tools/mg-tex-merge-256.py
"""

import base64
import io
import json
import os
import shutil
import sys

sys.path.insert(0, r"G:\AI\Codex\Temp")
import mg_png  # noqa: E402  （Temp 里的 PNG 读写小工具）

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BB_IN = r"G:\图片\MC-MOD素材\assets\minegenshin\character\default\deafult.bbmodel"
MATERIALS_DIR = r"G:\图片\MC-MOD素材\assets\minegenshin\character\default"
OUT_DIR = r"G:\AI\Codex\Skill\像素原神\工程文件"

GEO_LIVE = os.path.join(ROOT, "src", "main", "resources", "assets", "minegenshin",
                        "character", "linweiyun", "linweiyun.geo.json")
PNG_LIVE = os.path.join(ROOT, "src", "main", "resources", "assets", "minegenshin",
                        "character", "linweiyun", "textures", "linweiyun.png")

WEAPON_BONES = {"long", "sword", "claymore", "bow", "magic"}
SHIFT = 128       # 武器贴图放在右半边
SIZE = 256        # 合成后的正方形边长


def png_bytes(data_url):
    return base64.b64decode(data_url.split(",", 1)[1])


def blend(dst_rows, src_w, src_h, src_rows, ox, oy):
    for y in range(src_h):
        row = src_rows[y]
        drow = dst_rows[oy + y]
        for x in range(src_w):
            a = row[x * 4 + 3]
            if a == 0:
                continue
            i = (ox + x) * 4
            if a == 255:
                drow[i:i + 4] = row[x * 4:x * 4 + 4]
            else:
                for k in range(3):
                    drow[i + k] = (row[x * 4 + k] * a + drow[i + k] * (255 - a)) // 255
                drow[i + 3] = max(drow[i + 3], a)


def shift_face_uvs(uv_dict, moved):
    """把一个 cube 的 uv 字典里所有面右移 SHIFT（已经移过的跳过 → 幂等）。"""
    n = 0
    for face in uv_dict.values():
        uv = face.get("uv")
        if not uv:
            continue
        if len(uv) == 4:
            if max(uv[0], uv[2]) < SHIFT:      # 还没右移
                uv[0] += SHIFT
                uv[2] += SHIFT
                n += 1
        elif uv[0] < SHIFT:
            uv[0] += SHIFT
            n += 1
    return n


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # ---------- 1. 工程内嵌的两张贴图 -> 256×256 ----------
    bb = json.loads(io.open(BB_IN, encoding="utf-8").read())
    texs = bb["textures"]
    print("工程内贴图:", [(t["name"], t.get("width"), t.get("height")) for t in texs])
    char_png, wep_png = png_bytes(texs[0]["source"]), png_bytes(texs[1]["source"])
    io.open(os.path.join(r"G:\AI\Codex\Temp", "_char256.png"), "wb").write(char_png)
    io.open(os.path.join(r"G:\AI\Codex\Temp", "_wep256.png"), "wb").write(wep_png)
    cw, ch, crows = mg_png.read(os.path.join(r"G:\AI\Codex\Temp", "_char256.png"))
    ww, wh, wrows = mg_png.read(os.path.join(r"G:\AI\Codex\Temp", "_wep256.png"))
    print("角色贴图 %dx%d，武器贴图 %dx%d，合成画布 %dx%d" % (cw, ch, ww, wh, SIZE, SIZE))

    canvas = [bytearray(SIZE * 4) for _ in range(SIZE)]
    blend(canvas, cw, ch, crows, 0, 0)
    blend(canvas, ww, wh, wrows, SHIFT, 0)

    merged_png = os.path.join(OUT_DIR, "default.merged.256.png")
    mg_png.write(merged_png, SIZE, SIZE, [bytes(r) for r in canvas])
    print("写出合成贴图:", merged_png)

    # ---------- 2. 线上贴图 = 这张合成图 ----------
    os.makedirs(os.path.dirname(PNG_LIVE), exist_ok=True)
    shutil.copyfile(merged_png, PNG_LIVE)
    print("写出线上贴图:", PNG_LIVE)

    # ---------- 3. 线上 geo：尺寸改 256，武器面 UV 右移（幂等） ----------
    geo = json.loads(io.open(GEO_LIVE, encoding="utf-8").read())
    g = geo["minecraft:geometry"][0]
    old = (g["description"].get("texture_width"), g["description"].get("texture_height"))
    g["description"]["texture_width"] = SIZE
    g["description"]["texture_height"] = SIZE
    moved = 0
    for bone in g.get("bones", []):
        if bone.get("name") not in WEAPON_BONES:
            continue
        for cube in (bone.get("cubes") or []):
            moved += shift_face_uvs(cube.get("uv") or {}, moved)
    io.open(GEO_LIVE, "w", encoding="utf-8", newline="\n").write(
        json.dumps(geo, ensure_ascii=False, indent="\t") + "\n")
    print("线上 geo: 贴图尺寸 %sx%s -> %dx%d，移动武器面 UV %d 个" % (old[0], old[1], SIZE, SIZE, moved))

    # ---------- 4. 工程文件：一张合成贴图 + 分辨率 256×256 + 武器元素 UV 右移 ----------
    merged_b64 = "data:image/png;base64," + base64.b64encode(
        io.open(merged_png, "rb").read()).decode("ascii")
    first = json.loads(json.dumps(texs[0]))
    first["name"] = "default.png"
    first["width"] = SIZE
    first["height"] = SIZE
    if "uv_width" in first:
        first["uv_width"] = SIZE
    if "uv_height" in first:
        first["uv_height"] = SIZE
    first["source"] = merged_b64
    bb["textures"] = [first]
    bb["resolution"] = {"width": SIZE, "height": SIZE}

    grp = {n["uuid"]: n["name"] for n in (bb.get("groups") or [])}
    els = {e["uuid"]: e for e in bb["elements"]}
    bone_of = {}

    def walk(node, bone):
        if isinstance(node, str):
            if node in els:
                bone_of[node] = bone
            return
        name = grp.get(node.get("uuid"), bone)
        for child in (node.get("children") or []):
            walk(child, name)

    for node in bb["outliner"]:
        walk(node, grp.get(node.get("uuid")))

    n_el = 0
    for uuid, element in els.items():
        if bone_of.get(uuid) not in WEAPON_BONES:
            continue
        n_el += 1
        shift_face_uvs(element.get("faces") or {}, 0)
        for face in (element.get("faces") or {}).values():
            face["texture"] = 0

    bb_out = os.path.join(OUT_DIR, "deafult.merged.256.bbmodel")
    io.open(bb_out, "w", encoding="utf-8", newline="\n").write(json.dumps(bb, ensure_ascii=False))
    shutil.copyfile(bb_out, os.path.join(MATERIALS_DIR, "deafult.merged.256.bbmodel"))
    print("写出工程文件: %s（并复制回素材目录）" % bb_out)
    print("工程: 合并为 1 张 %dx%d 贴图，武器元素 %d 个 UV 右移" % (SIZE, SIZE, n_el))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""
从 Gradle 本地构建缓存里，把「源码已经丢了、但当时确实编译过」的 .class 捞出来。

背景（2026-09-27）：一批只在磁盘上、从没进过 git 的新源码被删/被截断
（`core/character/allweapon/`、`CharacterAppearanceOptionBones` 等）。
`build/classes` 又已经被清掉，唯一还留着这些类的地方是：

* `build/libs/minegenshin-1.0.1.jar`（09-27 01:50 的构建，**比那些新类还早**，只能救一部分）；
* Gradle 本地构建缓存 `~/.gradle/caches/build-cache-1/`（`org.gradle.caching=true`），
  那里按哈希存着每次 `compileJava` 的**全部输出**，只要那次编译成功过就还在。

缓存的每个条目 = 一小段头部 + gzip(tar)。这里扫全部条目，找出含目标类的那个，
把类文件解到 `G:\\AI\\Codex\\Temp\\recovered-classes\\`。

用法：
    python -B tools/recover-from-gradle-cache.py            # 找出并解出目标类
    python -B tools/recover-from-gradle-cache.py --list     # 只列出缓存里有哪些 com/linweiyun 的类
"""

import gzip
import io
import os
import sys
import tarfile

CACHE_DIR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "build-cache-1")
OUT_DIR = r"G:\AI\Codex\Temp\recovered-classes"

# 需要救回来的类（按名字匹配成员路径）
WANTED = (
    "AllWeaponArtifactInventory",
    "CharacterAppearanceData",
    "CharacterWeaponClass",
    "AssetFallback",
    "CharacterPropBones",
    "CharacterAppearanceOptionBones",
    "LocalPlayerSprintMixin",
    "MultiPackResourceManagerBundleMixin",
    "Linweiyun",
)


def open_entry(path):
    """缓存条目 = 头部 + gzip(tar)，从头里找 gzip 魔数再当 tar 读。"""
    with open(path, "rb") as handle:
        raw = handle.read()
    start = raw.find(b"\x1f\x8b")
    if start < 0:
        return None
    try:
        return tarfile.open(fileobj=io.BytesIO(raw[start:]), mode="r:gz")
    except Exception:
        return None


def main():
    if not os.path.isdir(CACHE_DIR):
        raise SystemExit("没有构建缓存目录：%s" % CACHE_DIR)
    only_list = "--list" in sys.argv

    entries = [n for n in os.listdir(CACHE_DIR)
               if os.path.isfile(os.path.join(CACHE_DIR, n)) and n != "gc.properties"]
    print("缓存条目 %d 个，开始扫…" % len(entries))

    found = {}          # class 简单名 -> (条目路径, 成员名)
    compile_entries = []  # 含 com/linweiyun 的条目
    scanned = 0
    for name in entries:
        path = os.path.join(CACHE_DIR, name)
        tar = open_entry(path)
        if tar is None:
            continue
        scanned += 1
        try:
            members = [m.name for m in tar.getmembers() if m.isfile()]
        except Exception:
            continue
        hit = [m for m in members if "com/linweiyun" in m and m.endswith(".class")]
        if hit:
            compile_entries.append((path, hit))
        for member in members:
            if not member.endswith(".class") or "com/linweiyun" not in member:
                continue
            simple = os.path.basename(member)[:-6].split("$")[0]
            if simple in WANTED and simple not in found:
                found[simple] = (path, member)
        tar.close()
    print("能当 tar 打开的条目 %d 个，其中含本模组 class 的 %d 个\n" % (scanned, len(compile_entries)))

    if only_list:
        for path, members in compile_entries[:5]:
            print("%s（%d 个类）" % (os.path.basename(path), len(members)))
            for m in members[:10]:
                print("   " + m)
        return

    print("== 目标类命中情况 ==")
    for want in WANTED:
        if want in found:
            path, member = found[want]
            print("  [找到] %-38s ← %s :: %s" % (want, os.path.basename(path), member))
        else:
            print("  [没有] %-38s" % want)

    os.makedirs(OUT_DIR, exist_ok=True)
    written = 0
    for want, (path, member) in found.items():
        simple = os.path.basename(member)[:-6].split("$")[0]
        tar = open_entry(path)
        for m in tar.getmembers():
            if not m.isfile() or "com/linweiyun" not in m.name or not m.name.endswith(".class"):
                continue
            base = os.path.basename(m.name)[:-6].split("$")[0]
            if base != simple:
                continue
            target = os.path.join(OUT_DIR, m.name.replace("/", os.sep))
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "wb") as out, tar.extractfile(m) as src:
                out.write(src.read())
            written += 1
        tar.close()
    print("\n已解出 %d 个 class 文件 -> %s" % (written, OUT_DIR))


if __name__ == "__main__":
    main()

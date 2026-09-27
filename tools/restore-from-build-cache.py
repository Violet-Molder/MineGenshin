# -*- coding: utf-8 -*-
"""
用 Gradle 构建缓存里的 class 把「当前源码比它旧 / 缺成员 / 文件没了」的类还原回源码。

## 为什么需要它（2026-09-27 事故）

* `bb62ed2「改动前」` 其实是**更早的快照**：它是用当时**过期的索引**提交的
  （HEAD 里连 `PGCharacter.hasLegShoes`、`LogGroup.ANIMATION`、`PerformanceConfig.DEBUG_DISABLE_BONE_UPDATERS`
  都没有，而 08:55 编译出来的 class 里都有）。
* 之后 `git reset --hard bb62ed2` 把**已跟踪**文件退回了那个旧快照；
  09:04:18 又有一次覆盖把 19 个**未跟踪**文件换成了旧内容、并删掉了一批没进 git 的新文件。
* 结果：工作区变成「旧一半 + 新一半」，`gradlew compileJava` 报一堆「找不到符号」。
* 唯一还留着「新的一半」的地方，是 Gradle 本地构建缓存
  （`~/.gradle/caches/build-cache-1/`，`org.gradle.caching=true`）：每次成功编译的**全部 class** 都在里面。

## 这一版做了什么

1. 自动挑「最新、且含本模组 class」的那个缓存条目（默认按文件时间）；
2. 跑一遍 `gradlew compileJava`，把报错涉及的源码文件收集起来；
3. 从缓存条目里取出这些类的 class（含内部类），交给 Vineflower 反编译；
4. 按 `package` 声明写回 `src/main/java/...`（文件头加注释标明是反编译还原的）；
5. 重复 2–4，直到编译通过或不再有新文件。

⚠️ 反编译会丢掉注释与排版。原稿如果还在 IDEA 的本地历史里
（`%LOCALAPPDATA%\\JetBrains\\IntelliJIdea2025.3\\LocalHistory`，界面里「右键 → Local History → Show History」），
拿到之后直接覆盖本工具还原出来的文件即可。

用法：
    python -B tools/restore-from-build-cache.py --list                # 只看缓存里有哪些条目可选
    python -B tools/restore-from-build-cache.py --rounds 4            # 自动收敛（推荐）
    python -B tools/restore-from-build-cache.py --files a.java b.java  # 只还原指定文件
"""

import gzip
import io
import os
import re
import shutil
import subprocess
import sys
import tarfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")
TEMP = r"G:\AI\Codex\Temp"
WORK = os.path.join(TEMP, "buildcache-restore")
CACHE = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "build-cache-1")
VINEFLOWER = ""

HEADER = ("// 本文件由反编译还原（2026-09-27）：新的一半只存在于 Gradle 构建缓存里的 class，\n"
          "// 源码被旧快照覆盖 / 删除过。行为与原来一致，但注释、排版、局部变量名与原稿不同 ——\n"
          "// 拿到 IDEA 本地历史里的原稿后请覆盖本文件。工具：tools/restore-from-build-cache.py\n")


def find_vineflower():
    base = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1", "org.vineflower")
    hits = []
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.startswith("vineflower-") and f.endswith(".jar"):
                hits.append(os.path.join(dirpath, f))
    if not hits:
        raise SystemExit("找不到 vineflower（反编译器）")
    return sorted(hits)[-1]


def entries():
    out = []
    for name in os.listdir(CACHE):
        p = os.path.join(CACHE, name)
        if os.path.isfile(p) and name != "gc.properties":
            out.append((os.path.getmtime(p), p))
    out.sort(reverse=True)
    return out


def open_tar(path):
    raw = open(path, "rb").read()
    i = raw.find(b"\x1f\x8b")
    if i < 0:
        return None
    return tarfile.open(fileobj=io.BytesIO(raw[i:]), mode="r:gz")


def pick_entry():
    """挑最新、且含本模组 class 的条目。"""
    for _, path in entries():
        tar = open_tar(path)
        if tar is None:
            continue
        try:
            for m in tar.getmembers():
                if "com/linweiyun" in m.name and m.name.endswith(".class"):
                    tar.close()
                    return path
        finally:
            try:
                tar.close()
            except Exception:
                pass
    raise SystemExit("缓存里没有含本模组 class 的条目")


def extract(entry_path):
    """把条目里的 class 全解到 WORK/classes。"""
    out = os.path.join(WORK, "classes")
    if os.path.isdir(out):
        shutil.rmtree(out)
    tar = open_tar(entry_path)
    n = 0
    for m in tar.getmembers():
        if not m.isfile() or "com/linweiyun" not in m.name or not m.name.endswith(".class"):
            continue
        rel = m.name.split("com/linweiyun", 1)[1].lstrip("/")
        target = os.path.join(out, "com", "linweiyun", rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as fh, tar.extractfile(m) as src:
            fh.write(src.read())
        n += 1
    tar.close()
    print("从 %s 解出 %d 个 class" % (os.path.basename(entry_path), n))
    return out


def compile_errors():
    """跑一次 compileJava，返回 [(源码绝对路径, 行号, 说明)]。"""
    cmd = [os.path.join(ROOT, "gradlew.bat"), "compileJava", "--console=plain"]
    env = dict(os.environ, TEMP=TEMP, TMP=TEMP)
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
    out = (proc.stdout or "") + (proc.stderr or "")
    errs = []
    for line in out.splitlines():
        m = re.match(r"^(.*?\.java):(\d+): (?:错误|error): (.*)$", line.strip())
        if m:
            errs.append((m.group(1), int(m.group(2)), m.group(3)))
    return proc.returncode, errs, out


def decompile(class_root, java_files):
    """把给定源码文件对应的类反编译，返回 {源码绝对路径: 反编译文本}。"""
    stage = os.path.join(WORK, "stage")
    if os.path.isdir(stage):
        shutil.rmtree(stage)
    wanted = set()
    for jf in java_files:
        rel = os.path.relpath(jf, SRC)
        # class_root 下面已经是 com/linweiyun/genshin/... 了，别再补前缀
        cls = os.path.join(class_root, rel[:-5] + ".class")
        if not os.path.exists(cls):
            print("  [缓存里没有这个类] %s  (找过: %s)" % (rel, cls))
            continue
        folder = os.path.dirname(rel)
        simple = os.path.basename(rel)[:-5]
        srcdir = os.path.join(class_root, folder)
        if os.path.isdir(srcdir):
            for f in os.listdir(srcdir):
                if f == simple + ".class" or (f.startswith(simple + "$") and f.endswith(".class")):
                    dst = os.path.join(stage, f)
                    os.makedirs(stage, exist_ok=True)
                    shutil.copyfile(os.path.join(srcdir, f), dst)
                    wanted.add(rel)
    if not wanted:
        return {}
    outdir = os.path.join(WORK, "out")
    if os.path.isdir(outdir):
        shutil.rmtree(outdir)
    cmd = ["java", "-jar", VINEFLOWER, "-dgs=1", "-din=1", "-rsy=1", "-hdc=0", stage, outdir]
    subprocess.run(cmd, cwd=WORK, capture_output=True)
    result = {}
    for dirpath, _, files in os.walk(outdir):
        for f in files:
            if not f.endswith(".java"):
                continue
            text = io.open(os.path.join(dirpath, f), encoding="utf-8", errors="replace").read()
            m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
            if not m:
                continue
            target = os.path.join(SRC, m.group(1).replace(".", os.sep), f)
            result[target] = text
    return result


def main():
    global VINEFLOWER
    VINEFLOWER = find_vineflower()
    if "--list" in sys.argv:
        for t, p in entries()[:20]:
            import time
            print("  %s  %s" % (time.strftime("%m-%d %H:%M:%S", time.localtime(t)), os.path.basename(p)))
        return
    os.makedirs(WORK, exist_ok=True)

    entry = pick_entry()
    class_root = extract(entry)

    if "--files" in sys.argv:
        i = sys.argv.index("--files")
        targets = [os.path.join(ROOT, f) if not os.path.isabs(f) else f for f in sys.argv[i + 1:]]
        got = decompile(class_root, targets)
        for target, text in got.items():
            os.makedirs(os.path.dirname(target), exist_ok=True)
            io.open(target, "w", encoding="utf-8", newline="\n").write(HEADER + text)
            print("  写回 %s" % os.path.relpath(target, ROOT))
        return

    rounds = 4
    if "--rounds" in sys.argv:
        rounds = int(sys.argv[sys.argv.index("--rounds") + 1])
    for rnd in range(1, rounds + 1):
        code, errs, out = compile_errors()
        if code == 0:
            print("第 %d 轮：编译通过 ✅" % rnd)
            return
        files = []
        for path, _, msg in errs:
            if path not in files:
                files.append(path)
        print("第 %d 轮：编译失败，%d 个错误涉及 %d 个文件" % (rnd, len(errs), len(files)))
        for f in files:
            print("     " + os.path.relpath(f, ROOT))
        got = decompile(class_root, files)
        if not got:
            print("  这些文件在缓存里都找不到对应类，停止。完整输出见 %s" % os.path.join(WORK, "last-compile.txt"))
            io.open(os.path.join(WORK, "last-compile.txt"), "w", encoding="utf-8").write(out)
            return
        for target, text in got.items():
            os.makedirs(os.path.dirname(target), exist_ok=True)
            io.open(target, "w", encoding="utf-8", newline="\n").write(HEADER + text)
            print("  写回 %s" % os.path.relpath(target, ROOT))
    print("轮数用尽，请再看一次编译输出")


if __name__ == "__main__":
    main()

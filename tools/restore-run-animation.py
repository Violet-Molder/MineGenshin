# -*- coding: utf-8 -*-
"""
只把 shenhe.animation.json 里的 **run 一条** 还原成备份里的原始版本。

为什么不用 `slow-run-4s.py --restore`：那几个 `--restore` 都是整份文件覆盖，
会把后面所有改动（FJO scale=0 补丁等）一起退掉。排查态只动过 run 这一条，
所以这里只替换 `animations.run` 一个块，其余字节照原样写回。

写之前先把当前整份文件拷到 `G:\\AI\\Codex\\Temp\\`（时间戳后缀），随时可以退回来。

用法：
    python -B tools/restore-run-animation.py
"""

import datetime
import io
import json
import os
import shutil

SRC = (r"E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin"
       r"\character\shenhe\shenhe.animation.json")
BAK = (r"G:\AI\Codex\Skill\像素原神\备份\shenhe-anim-20260927"
       r"\shenhe.animation.json.full")
TEMP_DIR = r"G:\AI\Codex\Temp"


def main():
    doc = json.loads(io.open(SRC, encoding="utf-8").read())
    bak = json.loads(io.open(BAK, encoding="utf-8").read())

    cur = doc["animations"]["run"]
    org = bak["animations"]["run"]
    print("当前 run：length=%s bones=%d" % (cur.get("animation_length"), len(cur.get("bones", {}))))
    print("备份 run：length=%s bones=%d" % (org.get("animation_length"), len(org.get("bones", {}))))

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    keep = os.path.join(TEMP_DIR, "shenhe.animation.json.before-run-restore-%s" % stamp)
    shutil.copyfile(SRC, keep)
    print("当前整份文件已备份到：%s" % keep)

    doc["animations"]["run"] = json.loads(json.dumps(org))

    io.open(SRC, "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, ensure_ascii=False, separators=(",", ":")))
    print("已把 run 还原成备份里的原始版本")


if __name__ == "__main__":
    main()

# MineGenshin

把《原神》的核心玩法机制移植到 Minecraft 的 NeoForge Mod：可收集角色、元素附着与反应、圣遗物与武器养成、祈愿、怪物等级与护盾、战斗动作系统。

| | |
|---|---|
| 当前版本 | **1.0.1** |
| Minecraft | 26.2 |
| NeoForge | 26.2.0.88 |
| Java | 25 |
| 依赖 | LowDragLib2 26.2.2.39、GeckoLib 5.5.6 |

## 玩法内容

| 系统 | 你能做什么 |
|---|---|
| 角色 | 收集原神角色（薇斯娜、申鹤、阿蕾奇诺、雷电将军、哥伦比娅、沃雅妮莎），升级、突破、天赋与编队 |
| 元素 | 元素附着与量级（强/弱附着）、附着冷却 |
| 元素反应 | 蒸发、融化、冻结、超导等，倍率可配置 |
| 战斗 | 普攻/战技/闪避/爆发四键动作系统、攻击判定、伤害管线与索敌 |
| 伤害表现 | 屏幕空间伤害飘字（元素配色、暴击、反应名） |
| 养成 | 圣遗物（主/副词条、升级、套装）、武器（等级、精炼） |
| 世界 | 入侵玩法开关、怪物等级与防御注入、额外掉落 |
| 其它 | 祈愿抽卡、护盾、状态附着（含方块与物品）、HUD |

## 构建与运行

```bash
./gradlew build        # 构建，产物在 build/libs/
./gradlew runClient    # 开发环境启动客户端
./gradlew runServer    # 开发环境启动服务端
./gradlew runData      # 重新生成数据资源（写入 src/generated/）
```

客户端与服务端都需要安装相同版本的依赖 Mod（LowDragLib2、GeckoLib）。

发行物只有一个文件：`build/libs/minegenshin-<版本>.jar`。把它与依赖 Mod 一起放进 `mods/` 即可，
没有任何附加文件、附加目录，也没有需要单独下载的资源。

## 文档

文档站由 [`web/`](web/) 模块提供（Spring Boot + Java，独立于 Mod 构建）。启动后访问 <http://localhost:8081/>；
文档内容是仓库里的 Markdown，由后端实时渲染。启动方式见 [web/README.md](web/README.md)。

| 文档 | 内容 |
|---|---|
| [首页](/) | 文档站首页（卡片导航，进入下面各篇） |
| 文档站「系统详解」分组 | **技术文档**：注册、角色、附件同步、战斗（攻击/动作）、元素反应、属性效果、掉落与怪物等级、护盾与状态、资源渲染、网络事件、性能优化——每篇含关键类、数据流、扩展步骤与常见坑 |
| [/entity-development.html](/entity-development.html) | **实体开发文档**：注册、实体类、属性、AI、同步、渲染、投射物范例与检查清单 |
| [/doc/entity-ai](/doc/entity-ai) | 实体 AI 深入指南：原版 Goal 原理与清单、自定义 Goal、Brain/Behavior 对照 |
| [/doc/character-system](/doc/character-system) | 角色系统详解：动作时序、技能数值、渲染与骨骼替换 |
| [/doc/character-implementations](/doc/character-implementations) | 角色实现清单 |
| [/doc/changelog](/doc/changelog) | 更新日志（从 1.0.0 起） |
| [/doc/port-targeting](/doc/port-targeting) | 索敌系统移植参考 |

## 开源协议

本项目采用 **CC BY-NC-SA 4.0**（署名 — 非商业性使用 — 相同方式共享 4.0 国际）。

- 允许：以非商业目的使用、修改、分发本项目
- 要求：必须署名，标明是否做过修改，修改版本必须以相同协议发布
- 禁止：任何商业用途

完整条款见 [`LICENSE.txt`](LICENSE.txt)，或访问 <https://creativecommons.org/licenses/by-nc-sa/4.0/>。

MineGenshin 的原创美术资源、音频资源和文本资源受版权保护，未经版权所有者明确书面许可，不得提取、复制或用于任何其他项目。

# 角色系统

## 这个系统负责什么

玩家可收集的角色：等级、突破、天赋、命座、当前 HP、装备（武器与圣遗物）、队伍编成，以及"当前出战角色"的切换与数据同步。

## 数据模型

| 类 | 生命周期 | 说明 |
|---|---|---|
| `core/character/PGCharacter` | 一个角色实例 | 持有 `PGCharacterData`，提供 `tick`、`addExp`、元素类型、当前 HP 等 |
| `core/character/PGCharacterData` | 随角色持久化 | 属性容器、圣遗物栏、武器、天赋等级、`dirty` 脏标记 |
| `core/attachment/PlayerCharactersAttachment` | 挂在玩家身上 | 持有角色列表、编队 4 格、当前出战索引；负责与客户端同步 |
| `core/system/registry/register/ModCharacters` | 全局注册表 | UUID → 角色定义，`getByUUID(135001)` 之类按 ID 取 |
| `core/character/CharacterHelper` | 工具 | 按 UUID 从玩家附件里查角色实例 |

角色实现按**武器类型**分包（这是本项目的分类维度）：

```
core/character/
├── sword/vesna/                  单手剑 · 薇斯娜
├── polearm/shenhe/               长柄 · 申鹤
├── polearm/raiden_shogun/        长柄 · 雷电将军
├── polearm/arlecchino/           长柄 · 阿蕾奇诺
├── catalyst/columbina/           法器 · 哥伦比娅
├── catalyst/vodyanitsa/          法器 · 沃雅妮莎
└── allweapon/linweiyun/          全武器类 · 林薇云（兜底角色）
```

每个角色包内通常有三类东西：角色类本体、资源/动画常量（如 `VesnaAnimations`、`VesnaResources`）、天赋与技能数据（`VesnaTalent`）。武器角色基类在 `core/character/<武器类型>/XxxCharacter`（`SwordCharacter`、`ClaymoreCharacter`、`PolearmCharacter`、`CatalystCharacter`、`BowCharacter`、`FistCharacter`），第七档「全武器类」是 `allweapon/AllWeaponCharacter`。

> **武器类型 ≠ 武器角色**：武器类型是武器的属性，标准的六种；武器角色是角色的分类，比它多一档
> 「全武器类」。判断"这个角色现在算不算单手剑角色"不要写 `instanceof SwordCharacter`，
> 问 `PGCharacter#currentWeaponType()` / `isSwordCharacter()` 一族；全武器类角色会跟着
> 外观里选中的武器种类回答（细节见[资源、渲染与界面](/doc/sys-render-asset)）。

## 一次"角色每 tick"的完整链

监听点是 `core/character/CharacterTickHandler`（`PlayerTickEvent.Post`，服务端），它在一个方法里按顺序做四件事——**顺序敏感，不要拆成多个监听类**：

1. 索敌锁校验：`CombatTargeting.tick(player)`（客户端那份由动作状态机驱动，服务端这份不校验就会永久锁住）
2. 推进队伍角色：对 `attachment.getPartyCharacterUUIDs()` 里的每个角色调 `character.tick(player)`
3. 原神模式"全灭"判定：4 格全死则关闭 genshin mode 并同步给客户端
4. 脏数据同步：`PGCharacterData.isDirty()` → `clearDirty()` → `syncSingleCharacterToPlayer(...)`

注意第 2–4 步整体被"入侵状态"守卫：未入侵时直接返回（第 1 步在守卫之前，始终执行）。

## 数值从哪来

| 数据 | 位置 |
|---|---|
| 等级经验 | `config/minegenshin/exp.toml` |
| 各角色各等级 HP/ATK/DEF | `config/minegenshin/attribute.toml`（含突破跃升） |
| 天赋倍率 | `config/character/<角色>TalentConfig` → 生成的 `character.toml` |
| 元素类型与突破属性 | 角色类本体（`Shenhe`、`Vesna` 等） |

调数值不需要改代码：改 TOML 即可（见角色系统详解 1.2 节）。改结构才动 Java。

## 加一个新角色要动哪几行

1. 在 `core/character/<武器类型>/<角色名>/` 下新建角色类，继承对应的 `XxxCharacter`。
2. 在 `ModCharacters` 注册（分配 UUID 与名字 key）。
3. 语言文件补 `character.name.<id>`（中英各一条）。
4. 资源目录 `assets/minegenshin/character/<角色id>/`（模型、动画、贴图）。
5. 动作与技能：`<角色>Animations` / `<角色>Resources` 声明动作数据，在 `CharacterAnimationRegistry` 里挂一行。
6. 数值默认值写进对应 `config/character/*Config`。

> 外观与兜底：角色可以给一份自己的外观数据（`CharacterAppearanceData` 的子类，决定掩码怎么读、
> 配置页里列出哪几项）；不给时用默认那套。模型 / 贴图 / 动画 / 配置页 / 天赋缺项时会回退到
> 林薇云（兜底角色）—— 见[资源、渲染与界面](/doc/sys-render-asset)。

完整示例与每一步的真实代码见 [角色系统详解（薇斯娜）](/doc/character-system)。

## 常见坑

| 现象 | 原因 |
|---|---|
| 切人后技能不生效 | 服务端与客户端的"当前出战角色"不一致，检查 `PlayerCharactersAttachment` 的同步 |
| 数值改了没变化 | TOML 在首次运行后生成，改的是旧文件，或改的是默认值却没删缓存配置 |
| 属性叠加异常 | 修饰器分"固定/百分比""临时/永久"，检查叠加顺序与来源 |
| 客户端看不到新角色 | 资源目录名与角色 id 不一致，或动画注册里没挂 |
| 模型错位/武器挂点不对 | 骨骼替换配置（`CharacterBoneMount`）与模型骨骼名不匹配 |

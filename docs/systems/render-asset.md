# 资源、渲染与界面

## 资源路径规则

MineGenshin 不用 GeckoLib 默认目录，而是把资源按"类别 + id"统一布局，由 `core/asset/` 解析。

| 类 | 职责 |
|---|---|
| `GenshinAssets` | 客户端 setup 时 `installDefaults()`，安装路径规则 |
| `AssetPathResolver` | 按类别与 id 解析出实际资源路径 |
| `ModAssetPaths` | 本 MOD 的资源根与约定 |
| `AssetCategory` / `AssetSet` | 类别枚举（entity / item / character …）与资源集合 |
| `ItemIcons` | 物品 GUI 图标路径规则（GeckoLib 物品的贴图特殊处理） |

统一布局（**入口按对象，对象内部按类型**）—— 这套布局已经落到磁盘，不再是计划：

```
assets/minegenshin/
├── character/<角色id>/            一个角色 = 一个自包含文件夹，内部再按类型分
│      <id>.animation.json         主动画；<id>_fp.animation.json 等附加动画（模型/动画留对象根）
│      textures/<id>.png           角色模型贴图（缺项时共用 character/linweiyun/textures/linweiyun.png）
│      textures/avatar.png         列表头像
│      textures/avatar_hud.png     HUD / 圣遗物佩戴者叠加头像
│      textures/pose_prepare.png   编队立绘（可选中的角色）
│      textures/pose_already.png   编队立绘（已在队伍）
│      textures/skill.png / burst.png   元素战技 / 元素爆发图标
│      sounds.json / sounds/*.ogg  音效定义与音频（ogg 丢进 sounds/ 就生效）
│      local/<名字>.geo.json       自己的模型 / 动画放这里：明文直读，同名时优先
├── entity/<实体id>/     <id>.geo.json | <id>.animation.json | textures/<id>.png
├── item/<物品id>/       definition.json | model.json                     （原版入口文件）
│                        textures/texture.png | icon.png | <id>.png       （平面贴图 / 图标 / geo 贴图）
├── block/<方块id>/      blockstate.json | model.json                     （原版入口文件）
│                        textures/texture.png | icon.png | <id>.png       （图集贴图 / 图标 / geo 贴图）
│                        blockitem/{definition,model}.json | blockitem/textures/…
├── gui/<名字>.png                 共用界面贴图（血条 / 遮罩 / 边框 / 按钮 / 占位图）
├── icon/<分类>/<名字>.png         跨对象图标（elemental 元素图等）
├── lss/                           界面样式表
└── lang/                          原版语言文件（唯一剩下的原版入口层，见下）
```

### 模型与动画：资源包 + `local/` 明文目录

自带角色的模型与动画**不再以一份份可读 JSON 出现在仓库和发行包里**，而是收进一个资源包
（整包一个文件；它放在哪个目录属实现细节，不在本文展开）。对外只承诺三件事：

- **逻辑路径没有变**：代码里照旧写 `character/vesna/vesna.geo.json` 这样的路径，
  后缀判断、缓存键、目录索引全部沿用本文档后面描述的规则，读取侧只换了「字节从哪来」；
- **自己的模型 / 动画放 `local/`**：`character/<id>/local/vesna.geo.json`、
  `entity/<id>/local/<id>.animation.json` 这一类路径**明文直读**，放进对象目录重启即生效，
  不需要任何额外步骤；同名时**`local/` 里那份优先于资源包**；
- **磁盘优先于资源包**：同一个逻辑路径在仓库里也有文件时以文件为准（方便临时对照调试）。

`local/` 只影响「资源从哪读」和「同名谁优先」，**不改变资源身份**：
`character/vesna/local/vesna.geo.json` 与 `character/vesna/vesna.geo.json` 是同一个缓存键，
所以 `CharacterRenderData` 之类的配置一个字都不用改。

**明文放在哪、谁进仓库。** 自己手上的明文模型 / 动画放在它们正常的资源路径下就行
（`character/<角色id>/vesna.geo.json` 这一类），改完构建一次即可生效；
这些路径在仓库里由 `.gitignore` 排除，所以提交进仓库的**只有资源包那一个文件**，
唯一的明文口子是 `local/`。发行形态同样是单文件：模型与动画跟着 jar 一起发，
玩家把 jar 与依赖 Mod 放进 `mods/` 即可 —— 没有附加文件，也不需要单独下载资源。

### 角色动画的约定

常态动画的名字由 `LocomotionAnims` 决定。名字写错不会崩，但会**静默退回 `idle`**
（`AnimationAvailability` 拦下来），所以下面这些是硬约定：

| 槽位 | 动画名 | 什么时候播 |
|---|---|---|
| 站 / 走 / 跑 / 倒走 | `idle` `walk` `run` `walk_back` | 地面移动 |
| 蹲 / 蹲走 / 睡 / 爬 | `crouch` `crouch_walk` `sleep` `climb` | 蹲伏、睡觉、爬梯 |
| 水中 | `water` `water_walk` `water_walk_back` `swim` | 水中待机 / 移动 |
| 跳 / 下落 | `jump` `jump_down` | 上升 / 下落（状态机按竖直位移自动切） |
| 落地（可选） | `landing` / `landingLight` | 落地那一瞬播**一次**，要显式接线 |

两个容易做错的地方：

- `jump` 与 `jump_down` 在**上升 / 下落期间是循环播放**的，素材要写成"起跳动作 → 稳定腾空姿态"
  或"下落姿态 + 轻微浮动"，写成一次性动作会在半空中反复重播。
- 落地是一次性（`OneShot`，刻数在接线时给）。末帧应当是站直姿势，交回 `idle` 才不跳；
  默认模型接的是 `withTransitions("landing", 9, null, 0)`（0.45s ≈ 9 刻）。没接这一档的角色，
  落地直接回 `idle`，行为跟以前一样。

做角色动画时踩过的几条，每一条都是实机可见的：

- **一个人物同时只能用一张贴图**：角色贴图与武器贴图必须合并成一张。本项目的做法是
  256×128 —— 角色占左 128 一格不动，武器放右 128，**武器骨骼的面 UV 统一 +128**；
  `texture_width/height` 与 Blockbench 工程的 resolution 一起改成 256×128。
- **常态动画要把武器藏起来**：对武器骨骼写 `scale = 0`（默认模型的四把是
  `long` / `sword` / `claymore` / `bow`；另有 `magic` 也用武器贴图，按需一起藏）。
  不藏的话，空手跑动的手臂会一直保持"拎着东西"的角度。
- **左右对称的规则不一样**：四肢是"左侧的镜像 + 半周期相移"（左右交替摆），
  头发是"镜像但**同相**"——发丝跟着身体一起甩，错了半周期会变成左右对扭。
- **前发不能跟身体一起甩**：前发挂在头发父骨骼下面，只要动画动了父骨骼，前发就会离开
  头皮、露出头皮。做法是父骨骼**一律不写通道**，把它的旋转摊到四根侧 / 后发骨骼上
  （`left` `right` `back_left` `back_right`），前发就只跟头走。
- **来源骨骼的静止角不能忽略**：从别的模型搬动画值时，若那根骨骼自带 `rotation`
  （静止角），它的动画值是写在那个**旋转过的坐标系**里的，搬到没有静止角的骨骼上意思
  完全不同（本项目踩到过：两根裙摆骨骼带 ±90° 静止角）。这类骨骼要么别搬，要么按矩阵换算。

### 按武器种类分的飞行动画

一个模型里有**六套飞行姿态**，按"现在拿的是哪一类武器"选一套播：

| 动画名 | 什么时候播 |
|---|---|
| `fly` | 基础飞行（空手 / 拳头，也是别的几套缺失时的落点） |
| `fly_sword` `fly_polearm` `fly_claymore` `fly_catalyst` `fly_bow` | 装备对应武器类型时 |

**约定：六套都写完整姿态，只让"这一类该亮的武器骨骼"有值**（其余武器的骨骼在这条动画里
保持常态隐藏的写法，即 `scale = 0`）。这样切武器类型时不用去补骨骼隐藏逻辑 ——
素材自己就决定了观众看到哪把武器。

> 现状：素材已备好（`character/linweiyun/linweiyun.animation.json`），**接线还没做**
> （按 `WeaponAppearance` 选哪一条）。在接线之前，飞行统一播 `fly`。

### 兜底角色与共用资源目录

本项目只有一套角色模型，它的所有者是**林薇云**，同时她也是**兜底角色**：
别的角色缺某一项资源时用她那一份。共用目录因此就是她自己的目录：

```
character/linweiyun/linweiyun.geo.json          共用模型
character/linweiyun/textures/linweiyun.png      共用贴图
character/linweiyun/linweiyun.animation.json    共用动画（可选）
```

**三项各自独立判断**，每个角色的读取顺序都是：

1. 自己的目录 `character/<角色id>/`；
2. 角色数据里声明的那条路径（与第 1 条不同时才有意义）；
3. 共用目录 `character/linweiyun/`。

所以「自己的模型 + 共用的贴图」这种组合是正常状态：把文件放进角色自己的目录就自动切换，
不需要开关或注册。判据与实现在 `AssetFallback`（模型 / 动画问 `GenshinGeoCache`，
贴图问资源管理器，两者都把整包算在内）。

**配置页与天赋也有兜底**：角色没有自己的 `ICharacterConfigUI` 时用通用配置页
（`core/character/configui/CharacterConfigUI`，外观区与角色面板都按"当前这个角色"的数据生成），
没有自己的天赋时 `getTalent()` 给 `TalentBase.DEFAULT`（空实现）。

### 武器角色与武器类型是两件事

写战斗、装备、界面代码时会遇到"这个角色是不是单手剑角色"这类问题。别去写
`character instanceof SwordCharacter`，也别自己比 `getAllowedWeaponClass()` —— 问角色自己：

| 概念 | 是什么 | 枚举 / 方法 |
|---|---|---|
| **武器类型** | **武器**的属性：标准的六种（单手剑 / 大剑 / 长柄 / 法器 / 弓 / 拳头），外加"认不出来" | `WeaponPoiseTable.WeaponClass` |
| **武器角色** | **角色**的分类，比武器多第七档：六种武器角色 ＋ `ALL_WEAPON`「全武器类」（目前只有林薇云） | `CharacterWeaponClass` |

角色侧的入口全在 `PGCharacter` 上，全武器类角色会把它们接管成"跟当前选中的武器种类走"：

| 方法 | 回答 |
|---|---|
| `characterWeaponClass()` / `isAllWeaponCharacter()` | 这个角色属于哪一类武器角色 |
| `currentWeaponType()` | 现在按哪一类武器算（削韧 / 冲击 / 伤害查表读它） |
| `isWeaponCharacter(type)` / `isSwordCharacter()` … `isFistCharacter()` | 现在算不算某类武器角色 |
| `canEquipWeapon(stack)` | 这把武器能不能装到**现在这一格** |

两个容易混淆的点：

- **全武器类角色的"当前类型"不是它的分类**：它的分类永远是 `ALL_WEAPON`，而
  `currentWeaponType()` 跟着外观里选中的种类变（选大剑 → `CLAYMORE`）。
  所以"选了大剑就按大剑算削韧/冲击/特性"在数据层就成立。
- **`canEquipWeapon` 与"能拿什么"不是一回事**：单武器角色两者相同；全武器类角色
  "六种都能拿"，但**现在这一格**只吃当前选中的那一类（一个类型一个槽，
  选了大剑就不该把弓塞进大剑那一格）。武器选择列表的筛选项也走它。

### 外观掩码的位布局与"角色专属段往后排"

外观是**一个 int**（存档键 `leg_appearance`），按位段取字段；每个角色用哪几段由它自己的
外观数据类（`CharacterAppearanceData` 的子类）声明。当前布局：

| 位 | 含义 | 谁在用 |
|---|---|---|
| 0–1 / 3–4 | 左 / 右腿袜子（0 裸腿、1 白丝、2 黑丝） | 申鹤 |
| 2 / 5 | 左 / 右鞋 | 申鹤 |
| 6 | 猫耳隐藏（1 = 藏） | 申鹤 |
| 7 | **常态（走 / 跑）是否显示武器**（1 = 显示） | 公共位，所有角色 |
| 8–10 | 武器种类（0 拳头 / 1 单手剑 / 2 长柄 / 3 大剑 / 4 法器 / 5 弓） | 全武器类角色共用（`AllWeaponAppearanceData`） |

两条硬约定：

- **角色专属段从 bit 8 起往后排**（11 之后继续往后加）。理由：`CharacterAppearanceBones#forMask`
  是**按掩码全局**套一遍腿部与猫耳规则的，谁踩了 bit 0–6 就等于改了自己的腿 ——
  这一项第一版把武器种类放在低 3 位，实测选一次武器左腿的袜子与鞋会跟着变。
  "位段可以按角色复用"只对**数据读法**成立，渲染这张表不是按角色分的。
- **位数上限 20 位，缓存必须稀疏**：`CharacterAppearanceBones` 的隐藏器缓存用
  `ConcurrentHashMap` 按需建。按位数开定长数组在 20 位下是一百多万个槽，会在类加载时把内存拖死。

### 原版入口层由重定向层供料

原版有四处资源入口是写死路径的。**本 MOD 不再把它们留在原版根目录，而是由 `AssetRedirects` 在读取时改写**：
`FileToIdConverterRedirectMixin` 注入 `FileToIdConverter#listMatchingResources` /
`listMatchingResourceStacks`，往返回的文件表里补进我们布局里的文件（只补 `minegenshin` 命名空间，
且原版已有同名真实文件时不覆盖，方便临时做 A/B 对照）。

| 原版入口 | 我们布局里的真身 |
|---|---|
| `blockstates/<方块id>.json` | `block/<方块id>/blockstate.json` |
| `items/<物品id>.json` | `item/<物品id>/definition.json`（方块物品回落 `block/<方块id>/blockitem/definition.json`） |
| `models/<路径>.json` | `<路径>.json`（去掉 `models/` 前缀） |
| `textures/<路径>.png` | `<路径>.png`（去掉 `textures/` 前缀；只接管 `textures/item` 与 `textures/block` 两个图集目录源） |

> 对象目录里的 `textures/` 是**布局**（对象内部按类型分类），不是资源身份的一部分：
> `item/<id>/textures/texture.png` 镜像给原版时会把这一层去掉，sprite id 仍是
> `minegenshin:item/<id>/texture` —— 所以**模型 JSON 与数据生成都不用因为这次调整而改动**。

实测这四条通道**都**经由 `FileToIdConverter` —— `BlockStateModelLoader`（blockstates）、
`ClientItemInfoLoader`（items）、`ModelManager`（models）、图集的 `DirectoryLister`
（`assets/minecraft/atlases/items.json` 的 `source: item` 与 `blocks.json` 的 `source: block`）——
所以一个注入点就让四条通道同时生效：原版加载逻辑一行未改，GeckoLib 也完全没碰。

实测证据（客户端 `run/logs/debug.log`）：

```
[AssetRedirects] 原版目录 'blockstates' 注入 1 条虚拟入口：minegenshin:blockstates/test_block.json
[AssetRedirects] 原版目录 'items'       注入 24 条虚拟入口：…
[AssetRedirects] 原版目录 'models'      注入 24 条虚拟入口：…
[AssetRedirects] 原版目录 'textures/item'  注入 25 条虚拟入口：…
[AssetRedirects] 原版目录 'textures/block' 注入 1 条虚拟入口：…
```

### 还剩下的原版硬性项

| 资源 | 读取方 | 路径自由度 |
|---|---|---|
| GeckoLib 模型 / 动画 / 贴图（角色、实体、geo 物品） | 本 MOD 自己的缓存 | 完全自由 |
| 代码里直接给 `Identifier` 的贴图（LDLib `SpriteTexture`、`guiGraphics.blit`） | 本项目代码 | 完全自由，Identifier 与文件路径**一字不差**（不带 `textures/` 前缀） |
| `lang/<语言>.json` | 原版 | 完全固定（一种语言一个文件，不做重定向） |

数据生成按同一套规则落盘：`ModModeProvider` 把物品定义与平面模型写进
`item/<物品id>/definition.json` 与 `item/<物品id>/model.json`，模型里的 `layer0` 指向
`item/<物品id>/texture.png`（sprite id `minegenshin:item/<物品id>/texture`）。
**改布局时要同步三处**：`AssetRedirects` 的规则表、`ModAssetPaths`/`AssetSet` 的路径助手、数据生成器的产出路径。

注意：`textures/item/*.png` 这类「原版根目录下的文件」在本 MOD 已全部搬空，
`items/`、`models/`、`blockstates/`、`textures/` 四个目录在 `assets/minegenshin/` 下**不再存在**。

### 还没做的（需要单独决定）

| 项 | 为什么没做 |
|---|---|
| 非角色音效 `sound/<分类>/` | 与 `CharacterSounds` 同形的扫描器还没写；仓库目前也没有 ogg，先立约定 |
| 实机观感 | 本环境只能跑到标题界面：背包/装备界面的物品图标、HUD、角色立绘需要人工看一眼（重定向层本身已由 `runClient` 日志验证注入正确、零 ERROR） |
| 第一次加真方块时的回归 | 方块通道已用临时 `test_block` 探针验证过（`blockstates` / `textures/block` / `models` 三处注入成功），探针已回退；等第一个真方块落地时按同一份日志再看一眼即可 |

> `block/<方块id>/blockstate.json` 曾经列为「做不了」，**第三轮已解决**：由 `AssetRedirects` +
> `FileToIdConverterRedirectMixin` 在读取时把 `blockstates/<id>.json` 改写到我们的布局，不需要数据生成复制，也不需要资源包垫片。

## GeckoLib 接管层

| 类 | 位置 | 职责 |
|---|---|---|
| `GenshinGeoCache` | `client/render/geo/` | **全局资源重载监听器**：扫自己目录、烘培模型与动画 |
| `CategoryGeoModel` | 同上 | "类别 + id" 驱动的模型：解析模型、动画与贴图，含可读的失败提示 |
| `AssetGeoCache` | 同上 | 统一布局资源的解析缓存（**已注册为客户端资源重载监听器**；异常全兜住，空扫描不落锚、最多重扫 5 次） |
| `GenshinGeoModel` / `GenshinItemGeoModel` | 同上 | 角色/物品模型的 GeckoLib 适配 |
| `AttachmentHelper` | `client/render/character/` | 挂件（武器/特效）的骨骼挂点计算 |

### 为什么 `AssetGeoCache` 不做全局资源重载监听器

它曾经挂在 `AddClientReloadListenersEvent` 上：**一旦抛异常，整个客户端资源重载失败**，于是所有资源驱动的表现（角色动作、动画、贴图）一起失效，表现是"某些角色左键没反应、动画也不播"，排查时完全指不到它。现在改为客户端 setup 预热 + 首次查询时同步扫一次，影响范围被限制在"读统一布局资源"这一件事上。

新增资源缓存时沿用这条经验：**不要顺手把它挂到全局重载事件上**。

## 客户端渲染分层

| 位置 | 内容 |
|---|---|
| `client/render/character/` | 角色渲染接管：`FirstPersonCharacterRenderer`（第一人称手臂）、`GenshinReplacedPlayer`、`CharacterRenderDispatcher` |
| `client/render/entity/` | 实体渲染器（`ElementalOrbRenderer`、`IceBlockProjectileRenderer`、`ThunderCloudRenderer` 等），在 `MinegenshinClient.registerEntityRenderers` 注册 |
| `client/damage/` | 伤害飘字：`DamageIndicator`（动画状态）、`DamageIndicatorManager`（活跃列表）、`DamageIndicatorRenderer`（HUD 构建） |
| `client/render/gui/hud/` | HUD 层：`DamageIndicatorHudRegistration`、`MGHud`、`MobHealthBarHud`、`VesnaEnergyHud`、`DebugInfoScreen`、`MyModularHudLayer`、`HealthBarTrail` |
| `client/render/gui/screen/` | 大界面：角色信息、背包、升格、编队、祈愿、圣遗物装备，`ScreenNavigator` / `GUIClientHelperGIM` 负责打开与数据准备 |
| `client/render/gui/component/` | 可复用控件（进度条、图标、状态绑定组件；原 `components/` 与 `state_bind_com/` 已合并到本包） |
| `client/render/gui/menu/` | 背包 UI 与排序（`BackpackUI`、`ArtifactSortMethod`、`LockedResourceHandler`）；容器菜单 `CharacterInfoMenu` 已移到 `core/menu/` |

### HUD 层的注册方式

每个 HUD 自己监听 `RegisterGuiLayersEvent` 并注册自己的层（`@EventBusSubscriber(Dist.CLIENT)`），因此**同一个事件有多个监听点**——这是允许的：每个 HUD 管自己的一块区域。`DamageIndicatorHudRegistration` 用的就是 LDLib2 的 `ModularHudLayer` + LSS 样式表（`lss/hud/damage_indicator.lss`）。

## 加一个界面 / 一个渲染器

1. 实体渲染器：写渲染器类放 `client/render/entity/`，在 `MinegenshinClient.registerEntityRenderers` 加一行，资源按 `entity/<id>/` 布局。
2. HUD 层：新建类实现自己的注册监听（参考 `MobHealthBarHud`），或复用 LDLib2 的 ModularUI + LSS。
3. 大界面：`Screen` 子类放 `client/render/gui/screen/`，需要容器数据时配 `AbstractContainerMenu`（容器菜单放 `core/menu/`，如 `CharacterInfoMenu`）+ 在 `ModMenus` 注册 + 在 `MinegenshinClient.registerMenuScreens` 绑定界面类。
4. 样式：优先用 LSS/样式表，而不是在代码里堆颜色与尺寸。

## 常见坑

| 现象 | 原因 |
|---|---|
| 模型/动画不加载 | 资源不在统一布局里；或 `AssetGeoCache` 未预热、`GenshinGeoCache` 没扫到 |
| 动画解析失败但有文件 | `CategoryGeoModel` 的提示会区分"文件没扫到"与"键名对不上"，先看日志给出的可用动画名 |
| 界面打开是空的 | 菜单没在 `ModMenus` 注册，或 `registerMenuScreens` 没绑定 |
| 专用服务器崩溃 | 渲染/界面类被公共侧引用；所有 `client`/`render` 类只能在客户端侧被引用 |
| 贴图错位/物品图标空白 | 物品图标走 `ItemIcons` 规则，geo 物品的贴图路径与普通物品不同 |
| 切角色 / 血条归零时崩渲染线程（`Scissor size must be >0, was 0x8`） | 带 `Clip.SCISSOR` 的裁剪层宽度按比例算，LDLib2 会把裁剪框四舍五入到物理像素，比例趋近 0 时取整成 0 宽，而 `RenderPass#enableScissor` 对宽或高 ≤ 0 无条件抛异常。修法是让裁剪层永远不带着 0 宽的框进入绘制：按 `MIN_CLIP_PIXELS` 判「够不够一个物理像素」，判据**实时读 `layer.isDisplayed()`**、不缓存成布尔字段（缓存分不清"还没判过"与"判成不可见"）。`HPProgressBar`、`MobHealthBar`、`MobPoiseBar` 三处同款 |

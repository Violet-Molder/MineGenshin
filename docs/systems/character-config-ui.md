# 角色配置界面系统（Character Config UI）

## 概述

本系统管理所有**角色配置屏幕**（按 N 键打开）与**角色装备屏幕**（按 U 键打开）的 Java 逻辑和 LSS 样式。

整个 UI 基于 **LDLib2** 的 `ModularUI` 框架构建，样式系统使用自研的 **LSS**（类似 CSS 的声明式样式表）。

---

## 一、核心架构

### 1.1 整体分工

| 层级 | 职责 | 关键类 |
|---|---|---|
| **接口层** | 定义配置屏幕的契约 | `ICharacterConfigUI` |
| **骨架层** | 面板排版、页签系统、预览区、属性面板、外观区骨架、技能倍率页 | `CharacterConfigScreen`（抽象基类） |
| **工具层** | 3D 预览构建、属性行构建、外观行构建、滚动区处理 | `CharacterConfigPage`（静态工具类） |
| **默认实现** | 最简配置页，只有属性页 + 基类外观 | `CharacterConfigUI` |
| **角色子类** | 重写外观项和技能倍率表 | `ShenheConfigUI`、`LinweiyunConfigUI` |

### 1.2 界面层级

```
#cc-root (100% x 100%, 全屏遮罩)
  └── #cc-window (72% 宽, 居中, 带外框贴图)
        └── #cc-body (88% x 86%, 内容层)
              ├── #cc-titlebar (标题栏)
              │     ├── #cc-title (角色名字)
              │     └── (子类填充按钮等)
              ├── #cc-title-line (分隔线)
              └── #cc-content (主体: 左预览 + 右配置)
                    ├── #cc-preview (48%, 3D 模型预览)
                    │     ├── #cc-scene (3D 场景)
                    │     ├── #cc-credit (模型作者)
                    │     └── #cc-preview-hint (操作提示)
                    └── #cc-right (48%, 配置区)
                          ├── #cc-appearance (48% 高, 外观)
                          │     ├── .cc-section-title (标题)
                          │     └── #cc-appearance-scroller (滚动区)
                          │           └── #cc-appearance-list
                          │                 ├── .cc-leg-row (每行: 名字 + 控件)
                          │                 └── ...
                          └── #cc-info (47% 高, 属性/倍率)
                                ├── #cc-info-tabs (页签)
                                │     └── .cc-tab (页签按钮)
                                ├── #cc-stats-scroller (属性页)
                                │     └── #cc-stats-list
                                │           ├── .cc-group-title (分组标题)
                                │           └── .cc-stat-row (属性行)
                                └── #cc-talent-scroller (倍率页, OP 可见)
                                      └── #cc-talent-list
                                            ├── .cc-group-title
                                            └── .cc-talent-row (倍率行)
```

---

## 二、Java 代码结构

### 2.1 接口层：`ICharacterConfigUI`

[ICharacterConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/ICharacterConfigUI.java)

```java
public interface ICharacterConfigUI {
    ModularUI createConfigUI(Player player, PGCharacter character);
    Component title();

    /** 只取外观那一块，供装备页复用 */
    default UIElement buildAppearanceSection(Player player, PGCharacter character, int[] previewMask) {
        return new UIElement();
    }
}
```

- `createConfigUI()` — 创建完整配置界面
- `title()` — 窗口标题
- `buildAppearanceSection()` — 外观区块，被 U 键装备页复用（避免两份代码）

### 2.2 骨架层：`CharacterConfigScreen`（抽象类）

[CharacterConfigScreen.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigScreen.java)

这是核心骨架类，子类只需重写少量方法。

#### 子类可重写的方法

| 方法 | 默认行为 | 子类用途 |
|---|---|---|
| `title()` | 抽象方法，必须实现 | 返回窗口标题 |
| `titlebarText(character)` | 调用 `title()` | 标题栏文字（可含角色名） |
| `fillTitlebar(titlebar, player, character)` | 空方法 | 在标题栏添加按钮 |
| `previewAnimation()` | 返回 `"extra48"` | 设置预览动画名 |
| `previewBones()` | 返回 `null` | 设置预览骨骼修正 |
| `appearanceTitleKey()` | `"gui...appearance_section"` | 外观区块标题 |
| `fillAppearance(rows, player, character, previewMask)` | 自动遍历外观数据 + 武器类型行 | 填充外观项 |
| `talentConfig()` | 返回 `null`（无倍率页） | 声明技能倍率表 |
| `infoPages(player, character)` | 构建属性页 +（如有权限）倍率页 | 自定义信息页签 |

#### 关键流程

```java
// 1. createConfigUI 总入口
public final ModularUI createConfigUI(Player player, PGCharacter character) {
    // 构建完整 UI 树
    root -> window -> body(titlebar + line + content(preview + right))
    // 挂载 LSS 样式表
    Stylesheet stylesheet = StylesheetManager.getStylesheetSafe(CharacterConfigPage.STYLESHEET);
    return ModularUI.of(UI.of(root, stylesheet), player);
}
```

```java
// 2. infoPages 决定信息区域内容
protected List<InfoPage> infoPages(Player player, PGCharacter character) {
    UIElement stats = CharacterConfigPage.buildStatsScroller(character);
    TalentConfigSource talents = this.talentConfig();
    if (talents == null || !hasCheatPermission(player)) {
        return List.of(new InfoPage(null, stats));  // 只有属性页
    }
    return List.of(
        new InfoPage("tab.stats", stats),
        new InfoPage("tab.talent", this.buildTalentScroller(talents))
    );
}
```

```java
// 3. buildTalentScroller 构建倍率页
protected UIElement buildTalentScroller(TalentConfigSource source) {
    // 遍历分组 -> 分组标题 -> 遍历 key -> 倍率行(名字 + 数值输入框)
    // 改动自动调用 NetworkManager.setTalentMultiplierToServer(key, value)
}
```

#### InfoPage 记录

```java
public record InfoPage(@Nullable String titleKey, UIElement content) {
    // titleKey = null 时只有一页，不显示页签
}
```

### 2.3 工具层：`CharacterConfigPage`

[CharacterConfigPage.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigPage.java)

纯静态工具类，提供以下能力：

#### 3D 预览

```java
public static UIElement buildPreview(Player player, PGCharacter character,
        int[] previewMask, GenshinPreviewPlayer animatable,
        @Nullable BoneUpdater<GeoRenderState> extraBones)
```

- 创建 `Scene` 元素，设置镜头参数
- 通过 `CharacterRenderDispatcher` 渲染角色模型
- 支持装备页的骨骼回调（`extraBones`）
- 模型作者署名（可点击打开 URL）

#### 属性面板

```java
public static UIElement buildStatsScroller(PGCharacter character)
```

读取 `PGCharacterData` 的属性值，构建三组属性行：

| 分组 | 属性 |
|---|---|
| **基础** | 生命值上限、攻击力、防御力、元素精通、体力上限 |
| **进阶** | 暴击率、暴击伤害、治疗加成、受治疗加成、元素充能效率、冷却缩减、护盾强效 |
| **元素** | 火/水/草/雷/风/冰/岩元素伤害加成 + 抗性 + 物理伤害加成/抗性 |

每行格式：`名字(26%) + 占位(32%) + 总值-白字(11%) + 基础值-黄字(11%) + 额外值-绿字(12%)`

#### 外观行

```java
public static UIElement optionRow(PGCharacter character, int index, int[] previewMask)
public static UIElement optionChoice(CharacterAppearanceData appearance, PGCharacter character, int index, int[] previewMask)
```

支持两种外观控件：
- **TOGGLE** — 开关（显示/隐藏）
- **CHOICE** — 选择器（多选一）

#### 装备/预览同步

```java
public static void applyPreview(PGCharacter character, int[] previewMask) {
    previewMask[0] = character.getAppearance();
    attachment.syncSingleCharacterToServer(character);
}
```

### 2.4 默认实现：`CharacterConfigUI`

[CharacterConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigUI.java)

```java
public class CharacterConfigUI extends CharacterConfigScreen {
    public static final CharacterConfigUI INSTANCE = new CharacterConfigUI();
    // 不重写 talentConfig() -> 没有倍率页
    // 不重写 fillAppearance() -> 使用基类默认外观
}
```

适用于"暂时没什么要单独配"的角色。只有一个属性页。

### 2.5 角色子类示例

#### `ShenheConfigUI` — 复杂的定制

[ShenheConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/polearm/shenhe/ShenheConfigUI.java)

特点：
- **自定义外观选项**：左右腿（穿鞋/袜子类型选择）、猫耳显示（手写行）
- **技能倍率表**：`ShenheTalentConfig.SOURCE`
- **预览骨骼修正**：胸屏区域（`ysmGlow_texiao` / `ysmGlow_texiao2` 骨骼）在预览中缩小并前移

申鹤的外观数据（[ShenheAppearanceData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/appearance/ShenheAppearanceData.java)）声明 5 项：

| 索引 | 名称 | 控件类型 | 手写 |
|---|---|---|---|
| 0 | 左腿 | CHOICE（袜子类型） | ✅ |
| 1 | 右腿 | CHOICE（袜子类型） | ✅ |
| 2 | 猫耳 | TOGGLE | ✅ |
| 3 | FJO | TOGGLE | ❌（自动） |
| 4 | 武器显示 | TOGGLE | ❌（自动） |

#### `LinweiyunConfigUI` — 简洁的定制

[LinweiyunConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/allweapon/linweiyun/LinweiyunConfigUI.java)

特点：
- **外观数据**：`AllWeaponAppearanceData`（武器形态 + 显示武器，全自动）
- **技能倍率表**：`LinweiyunTalentConfig.SOURCE`
- **预览动画**：`"idle"`（没有 `extra48` 动画）

林薇云的外观数据（[AllWeaponAppearanceData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/allweapon/AllWeaponAppearanceData.java)）：

| 索引 | 名称 | 控件类型 | 说明 |
|---|---|---|---|
| 0 | 武器形态 | CHOICE | 拳/剑/长柄/大剑/法器/弓 |
| 1 | 显示武器 | TOGGLE | 常态是否看到武器模型 |

### 2.6 技能倍率系统

#### 接口：`TalentConfigSource`

[TalentConfigSource.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/TalentConfigSource.java)

```java
public interface TalentConfigSource {
    List<String> groups();
    List<String> keysOf(String group);
    Double getByKey(String key);
    boolean setByKey(String key, double value);
}
```

#### 全局注册：`TalentConfigs`

[TalentConfigs.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/TalentConfigs.java)

所有倍率表注册在一个 `LinkedHashSet` 中，服务端按 key 全局查表。Key 必须全局唯一（加角色前缀）。

#### 配置类示例：`ShenheTalentConfig`

[LinweiyunTalentConfig.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/LinweiyunTalentConfig.java) — 两者结构相同

```java
public class LinweiyunTalentConfig {
    public static StringDoubleValue NA_BASE_1, NA_BASE_2, ...;
    public static StringDoubleValue SKILL_PRESS_BASE, ...;
    public static final TalentConfigSource SOURCE = new TalentConfigSource() { ... };

    static void register(ModConfigSpec.Builder builder) {
        builder.push("talent");
        builder.push("normal-attack");
        NA_BASE_1 = define(builder, GROUP_NA, "lwy-nab1", 0.433);
        // ... key 带 lwy- 前缀
    }
}
```

#### 数据流向

```
配置页 TextField 输入
    -> source.setByKey(key, value)                                 // 写本地 ConfigValue
    -> NetworkManager.setTalentMultiplierToServer(key, value)       // 同步服务端
        -> TalentConfigs.setByKeyGlobal(key, value)                 // 服务端查找并写入
```

### 2.7 外观系统

`CharacterAppearanceData` 是外观数据的抽象基类，外观值编码为一个 `int` 位掩码。

```java
public abstract class CharacterAppearanceData {
    public abstract int optionCount();
    public abstract String optionNameKey(int index);
    public boolean optionHandWritten(int index);
    public OptionKind optionKind(int index);        // TOGGLE / CHOICE
    public int optionValueCount(int index);
    public String optionValueNameKey(int index, int value);
    public int optionValue(int mask, int index);
    public int withOptionValue(int mask, int index, int value);
    public Map<String, Boolean> normalStateBones(int mask, WeaponPoiseTable.WeaponClass weaponType);
    public Map<String, String> flightBones();       // 飞行时武器骨骼映射
}
```

### 2.8 界面导航

[ScreenNavigator.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/client/render/gui/screen/ScreenNavigator.java)

| 方法 | 触发键 | 用途 |
|---|---|---|
| `openCharacterConfigScreen()` | N | 角色配置页 |
| `openArtifactEquipScreen()` | U | 角色装备页 |
| `openCharacterPartyScreen()` | O | 队伍编辑 |
| `openCharacterSelectScreen()` | - | 角色选择 |

```java
public static void openCharacterConfigScreen(Player player) {
    PGCharacter character = CharacterHelper.getCurrentCharacter(player);
    ICharacterConfigUI own = character.getConfigUI();
    ICharacterConfigUI configUI = own != null ? own : CharacterConfigUI.INSTANCE;
    ModularUI modularUI = configUI.createConfigUI(player, character);
    Minecraft.getInstance().setScreenAndShow(new ScreenCharacterConfig(modularUI, configUI.title()));
}
```

---

## 三、LSS 样式系统

### 3.1 LSS 文件清单

| 文件 | 用途 |
|---|---|
| `character_config.lss` | N 键配置页（科技风蓝框） |
| `character_equip.lss` | U 键装备页（放大版） |
| `character_select.lss` | 角色选择屏幕 |
| `character_party.lss` | 队伍编辑屏幕 |
| `character_info.lss` | 角色信息屏幕 |
| `ascension.lss` | 突破界面 |

### 3.2 `character_config.lss` 详解

#### 整体设计风格

科技风：深蓝半透明背景 + 蓝色外框贴图 + 花纹/齿轮装饰。

外框贴图派生自模型胸屏贴图（`shenhe.png` 的 `(347,33) 422x248` 区域），四个齿轮缩小到 80%。

#### 窗口尺寸体系

```lss
#cc-window {
  width: 72%;
  min-width: 300;
  max-height: 88%;
  aspect-rate: 1.7016;          /* = 422/248 */
  transform: translate(2.36%, -1.85%);  /* 补偿外框美术偏心 */
}
```

内容区：
```lss
#cc-body {
  width: 88%;
  height: 86%;  /* 从 92% 收到 86%，避免内框线压到控件 */
}
```

> **尺寸规则**：全部用显式百分比，禁止用 flex 的 grow/shrink/basis 自动增减。

#### 配色体系

| 用途 | 颜色值 |
|---|---|
| 标题文字 | `#9BE8FF`（亮青） |
| 面板底色 | `#4512263C`（深紫黑） |
| 面板边框 | `#7A6FD8F5`（紫蓝） |
| 内凹面板 | `#59101E30` + `border(1, #404F8FBF)` |
| 属性名 | `#C9DCE8`（灰白） |
| 总值（白字） | `#EAF4FF` |
| 基础值（黄字） | `#F0CE6E`（原神黄） |
| 额外值（绿字） | `#A8E86B`（原神绿） |
| 外框贴图缩放 | `scale(1.32913, 1.30871)` |

#### 关键样式技巧

**1. 外框溢出渲染**：Sprite 缩放后溢出元素矩形，利用 `Clip.NONE`（默认）渲染到元素外。

**2. 内建控件皮肤覆盖**：`.__toggle_button__`、`.__selector_dialog__` 等 LDLib2 内部类名的默认皮肤是 `DEFAULT(0)`，LSS 的 `STYLESHEET(2)` 优先级压过去。

**3. 开关勾染色**：使用 `sprite(gdp_icons.png)` 配合 `color(#9BE8FF)` 染色。

**4. 滚动条定制**：轨道压暗、滑块半透明蓝，隐藏两端箭头按钮。

### 3.3 `character_equip.lss` 详解

装备页是配置页的**放大版兄弟**，三处改动：
1. 窗口 `72% -> 88%`（塞三栏）
2. 内容层 `88%x86% -> 93%x91%`（提高可用区）
3. 新增 `.ce-*` 类（与 `.cc-*` 命名空间隔离）

#### 布局结构

```
#ce-window (88% 宽)
  -> #ce-body (93% x 91%)
       -> #ce-topbar (标题 + 角色头像横向滚动条)
       -> #ce-main (三栏)
            -> #ce-menu (左页签, 宽 62)
            -> #ce-stage (中预览, flex:1)
            -> #ce-panel (右详情, 宽 196)
```

#### 装备页特有控件

| 控件 | 用途 | 样式特点 |
|---|---|---|
| `.ce-avatar` | 角色头像 (22x22) | `overlay: border()` 描边，避免被背景盖掉 |
| `.ce-tab` | 左侧页签按钮 | 通过 `base/hover/pressed-background` 控制三态 |
| `.ce-slot` | 圣遗物格子 (28x28) | `overlay` 描边 + `transform` 缩放动画 |
| `.ce-bar-track` | 经验进度条 | `flex-direction: row` 让预升级绿条跟在当前蓝条后面 |
| `.ce-modal` | 升级确认弹窗 | 全屏遮罩 + 居中面板 |
| `.ce-orb` | 模型周围圣遗物 (30x30) | `position: absolute` 由 Java 计算位置 |
| `.ce-con-dot` | 命座节点 (10x10) | 解锁亮青、锁着暗灰 |
| `.ce-action` | 操作按钮 | 三态背景，禁用时 `.ce-action-off` |

### 3.4 其他 LSS 文件

**`character_select.lss`**：角色选择网格屏，`flex-wrap: wrap` 自动换行，头像 `17%` 宽（一行 5 个），悬浮缩放动画。

**`character_party.lss`**：队伍编辑 4 格横向，`justify-content: space-evenly` 均匀分布。

**`ascension.lss`**：突破界面，300 宽窗口固定，使用 `sdf` 圆角填充背景。

### 3.5 `character_equip.lss` vs `character_config.lss` 对照

| 项目 | config (N 键) | equip (U 键) |
|---|---|---|
| 窗口宽度 | 72% | 88% |
| 内容层 | 88% x 86% | 93% x 91% |
| 布局 | 左预览 + 右配置 | 左页签 + 中预览 + 右详情 |
| 样式命名 | `.cc-*` | `.ce-*` |
| 共享内容 | — | 复用 `#cc-appearance` |
| 预览区域 | 48% 宽 | flex:1（自适应） |
| 功能 | 外观 + 属性/倍率 | 全角色管理 + 装备/升级 |

---

## 四、扩展指南

### 4.1 添加新角色的配置页

| 情形 | 需要做的 |
|---|---|
| A: 只要属性页 | 什么都不做（自动回退 `CharacterConfigUI.INSTANCE`） |
| B: 需要私有的外观项 | 创建 `CharacterAppearanceData` 子类，设置 `appearanceData` 字段 |
| C: 外观 + 技能倍率 | 创建 `*ConfigUI` 继承 `CharacterConfigScreen`，重写 `talentConfig()` / `fillAppearance()`，设置 `configUI` 字段 |
| D: 自动外观 + 倍率 | 类似 `LinweiyunConfigUI`，重写 `talentConfig()` 和 `previewAnimation()` |

### 4.2 添加新的技能倍率表

1. 创建 `*TalentConfig` 类（参照 `ShenheTalentConfig`）
2. 在 `register()` 中用 `define()` 注册所有字段
3. 提供 `SOURCE` 静态 `TalentConfigSource` 实例
4. 确保 key 全局唯一（加角色前缀）
5. 在配置总入口调用 `*TalentConfig.register(builder)`

### 4.3 常见陷阱

| 现象 | 原因 |
|---|---|
| 配置页首次打开错版 | 根元素尺寸需在 Java INLINE 和 LSS 各写一份（首帧时 LSS 未结算） |
| 外观开关点了没反应 | `optionValue()`/`withOptionValue()` 未处理所有索引 |
| 选择框 NPE | 必须在 `setCandidateUIProvider` 前调用 `setSelected`（LDLib2 构造期立即求值） |
| 数值列对不齐 | 必须用固定百分比宽度，不用 flex 分配 |
| 滚动条不动 | 需设置 `min-scroll: 0` 和 `max-scroll: 10000` |
| 第一行列边框被吃 | 需在滚动内容加 `padding` |
| 服务端倍率没更新 | 需调 `NetworkManager.setTalentMultiplierToServer()` |
| 模型预览错位 | 检查 `previewBones` 和预览动画是否存在 |

---

## 五、文件索引

### Java 源文件

| 文件 | 路径 |
|---|---|
| 接口定义 | [ICharacterConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/ICharacterConfigUI.java) |
| 基类骨架 | [CharacterConfigScreen.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigScreen.java) |
| 工具类 | [CharacterConfigPage.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigPage.java) |
| 默认实现 | [CharacterConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/configui/CharacterConfigUI.java) |
| 申鹤配置 | [ShenheConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/polearm/shenhe/ShenheConfigUI.java) |
| 林薇云配置 | [LinweiyunConfigUI.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/allweapon/linweiyun/LinweiyunConfigUI.java) |
| 倍率接口 | [TalentConfigSource.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/TalentConfigSource.java) |
| 倍率注册 | [TalentConfigs.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/TalentConfigs.java) |
| 申鹤倍率 | [ShenheTalentConfig.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/ShenheTalentConfig.java) |
| 林薇云倍率 | [LinweiyunTalentConfig.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/config/character/LinweiyunTalentConfig.java) |
| 外观基类 | [CharacterAppearanceData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/appearance/CharacterAppearanceData.java) |
| 申鹤外观 | [ShenheAppearanceData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/appearance/ShenheAppearanceData.java) |
| 全武外观 | [AllWeaponAppearanceData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/allweapon/AllWeaponAppearanceData.java) |
| 导航 | [ScreenNavigator.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/client/render/gui/screen/ScreenNavigator.java) |
| 按键 | [KeyInputHandler.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/client/keybindings/KeyInputHandler.java) |
| 角色基类 | [PGCharacter.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/PGCharacter.java) |
| 角色数据 | [PGCharacterData.java](file:///E:/MCMOD/MineGenshin/src/main/java/com/linweiyun/genshin/core/character/PGCharacterData.java) |

### LSS 样式文件

| 文件 | 路径 |
|---|---|
| 配置页 | [character_config.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/character_config.lss) |
| 装备页 | [character_equip.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/character_equip.lss) |
| 角色选择 | [character_select.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/character_select.lss) |
| 队伍编辑 | [character_party.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/character_party.lss) |
| 突破界面 | [ascension.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/ascension.lss) |
| 角色信息 | [character_info.lss](file:///E:/MCMOD/MineGenshin/src/main/resources/assets/minegenshin/lss/character_info.lss) |
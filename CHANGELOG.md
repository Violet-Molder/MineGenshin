# 更新日志

> **本日志自 1.0.0 重新建立。** 1.0.0 之前的 0.x 记录不再维护（需要时见 git 历史）。
>
> 记录约定：**每个版本的条目只在该版本发布时写一次**，后续版本只写"新增 / 变更 / 修复"，不重复 1.0.0 已记录的内容。新增版本时复制文末模板。

---

## 26.2.0.2 — 2026-10-06（兼容版本）

**状态：当前更改（未发布）** —— 版本号进了 release 渠道才改标「已发布」，在此之前本节改动一直挂在 26.2.0.2 下。

本版把元素与韧性收进 elementlib 的通用模块层，并新增方块韧性。
版本命名从这里改为「MC 版本 + mod 版本」；本版为兼容版本，旧存档仍可读。

### 新增

- 韧性注册为模块 `minegenshin:toughness`（实体 / 角色）；方块韧性为 `minegenshin:block_toughness`；
- 方块韧性：原神模式左键交互一次扣一笔削韧，归零按钻石镐等价破坏（`BlockDropsEvent` 可取消）；
  换算 `韧性 = 硬度 × 179`（石头 268.5，大剑两刀消耗 80%），`-1` / 硬度 ≤ 0 / 水与岩浆不参与；
- 表现：复用原版裂纹，按已消耗比例推进；
- 文档 `docs/systems/module-system.md`。

### 变更

- 元素容器搬进模块层：实体 `elementlib:modules`、方块 `elementlib:chunk_modules`、角色 `PGCharacterData.moduleContainer`；
- 攻击收口到 `ElibAttackPipeline`：动作伤害点、方块左键、实体左键走同一个入口；
- 依赖 elementlib `26.2.0.4`（联调期走本地开发仓库）。

### 兼容

- 旧 `elementlib:status_container` 与 `chunk_elements` 数据按格迁移，只读不写；
- 旧 `minegenshin:status_container`（PGCharacterData 字段）首次访问时迁移进模块容器。

### 事件网络接入（2026-10-07 追加）

事实在发生的那一刻广播一次，消费方改成订阅；攻击链路「攻击 → 命中 → 造成伤害」三层分家。

**新增**

- 游戏事件：`minegenshin:damage_calculated`（算完没落血）、`minegenshin:damage_dealt`（血量真的掉了，
  盾全挡 / 元素免疫不发）、`character_enter_field` / `character_leave_field` / `character_switched`（带切换原因）、
  `elemental_skill_cast` / `elemental_burst_cast`、`party_member_joined` / `party_member_left`；
  全部实现 elementlib 的 `ElibIdentifiedEvent`，走 `ElibEvents.post` 广播（即 NeoForge 事件总线）；
- `DamageOutcome`：一次伤害的两阶段结果（`hit` / `damaged` / `finalDamage` / `blockReason` / `killed`），
  飘字与事件同读这一份；`NormalAttackOrbProducer`：普通攻击产球改由伤害管线在确认真的掉血后**直接调用**
  （每次 50% 一颗元素微粒），不再靠旁观者扫伤害；
- `GenshinEvents`：本模组唯一的事件登记入口，事件定义在 `event/game`、订阅在 `event/listener/server`。

**变更**

- `PlayerCharactersAttachment` 换人收口到 `switchTo(index, cause)`，由它广播退场 → 登场 → 切换三条事件；
  重登 / 死亡重生走 `restoreCurrentIndex`，不广播；
- 千岩牢固四件套、武器被动改为订阅事件（原先由伤害入口硬编码回调）；
- 日志组新增 `EVENT`，`ElibEvents.setEventLogger` 接上本模组的日志。

**文档**

- `docs/systems/combat-attack.md` 改口径：攻击相关处理一律订阅事件网络，不再「都挤进一个入口」。

### IB 联动与星见雅（2026-10-09 追加）

把 1.21.1 线的 IB 联动整套与这两天的行为修复移植过来。

**新增**

- **IB 联动地基**：`IBCharacter`（联动角色标记）、`IBLink`（对方在不在 / 读对方的角色定义 /
  取对方的模型字节）、`IBRenderDefinition`（对方 `basics.json` + `renderer/render.json` 的归拢）、
  `IBDecryptBridge`（反射调对方的模型解密入口 `ModelDataPack#getResource`，只调不改）；
- **角色资源来源表**：`assets/minegenshin/character/<id>/resources.json` 逐项声明
  模型 / 动画 / 贴图 / 头像 / 立绘 / 额外动画从哪里读，取值支持 `@ib`、`@ib-item`、`namespace:path`；
  还能声明模型骨骼约定（`bones.body_root` / `weapon` / `hide`）—— 对方的模型不能改，
  武器挂在哪根骨骼必须由本 MOD 声明；界面头像 / 立绘也改走来源表，立绘缺失时退到头像；
- **第一个联动角色：星见雅**（五星单手剑，UID 115201，冰元素），模型 / 动画 / 贴图 / 头像取自 IB，
  立绘归本 MOD；角色注册、动画登记、抽卡池都以「对方在」为前提；
  - **普攻五段**：`attack_1..attack_5`，时长取素材那五段（40 / 48 / 25 / 30 / 50 刻）；
  - **战技**：`skill_energy`（25 刻）+ 收尾段 `skill_energy_continue`（60 刻）；
  - **重击**：`heavy_1`（15 刻），长按 6 刻起手；
  - **大招**：`final`（100 刻）；
  - 动作音效沿用素材的音效事件（`imaginary_branch:miyabi_*`）；
  - **星超导户口**：按自身攻击力给全队星超导基础伤害提升（只声明，转化未实现）；
- **星见雅的重击特效**：素材里她的重击是一枚**斩击模型**，本 MOD 用自己的实体
  `minegenshin:miyabi_slash` 复现：正前一道 + 左右各偏 0.6 共三道，**只画模型、不结算伤害**
  （伤害走本 MOD 自己的动作管线）；模型 / 贴图 / 动画按来源表从 IB 读，不需要 Photon；
- **星见雅的伤害结算**：普攻五段 / 重击 / 战技两段 / 大招 / 下落攻击各自在自己的伤害点打一下，
  全是冰元素范围伤害；倍率、附着、衰减走本 MOD 那一套；多段伤害一律写成多个 `hits[]`；
- **近战判定盒修宽**：从玩家自己的碰撞箱沿视线扫出去再外扩（横向 1.0~1.5 格、上下 1.5 格）；
- **星见雅的下落攻击姿态**：素材里没有下劈那一段，借普攻第二段动画的中间姿态，
  下落期间状态名走 `plunge`、由动画别名指到 `attack_2`，时间轴钉在 `0.3 × 20` 刻；
- **新游戏规则 `minegenshin:attack_breaks_blocks`**（默认**关闭**）：关闭时只有被点名的方块
  （`#minegenshin:attack_breakable` 标签、或代码里 `BlockToughnessRules.register` 登记过的）
  参与韧性；打开后恢复「所有有硬度的方块都参与」；
- **飘字阴影不透明度**：新配置 `performance.shadow_alpha`（默认 0.45）。

**变更**

- `GenshinGeoCache` 在联动模组加载时额外扫描对方的 `ib_character`（角色模型 / 动画）与
  `geckolib/models|animations`（例如斩击 `miyabi_slash`）；
- `GenshinAssets.fromModelPath / fromAnimationPath / fromTexturePath` 支持 `namespace:path` 写法；
- `ActionStep.comboEndAnim`（后续动画名）不再只对连招最后一段生效：战技 / 重击 / 大招也会读它
  （`ResourceDrivenActionHandler.queueFollowUp`）；
- 开发运行把临时目录固定到项目内的 `build/dev-temp`（Codex 会话给 C 盘 `%TEMP%` 加了沙箱 ACL，
  JDK 在那里建不出 NIO selector 的自连接管道，表现是打开单人存档时崩在 `Load world`）；
- `build.gradle` 增加**本地联调**段（仅开发运行环境）：把 IB 26.2 的产物与它的必需前置 Curios
  挂进 `runClient / runServer / runData`，不进产物、不影响发布构建；候选产物逐个读 jar 里的
  minecraft 版本区间核对，本地依赖 jar 放在项目内的 `libs/`（不进仓库，见 `.gitignore`）。

**修复**

- **下落攻击**：状态名 `plunge` 由动画别名指到 `attack_2`；每刻压制创造飞行与滑翔；
  中途作废时发 `characterPlungeCancelRPCPacket` 清服务端 `PlungeState`（否则那条状态会挂到
  玩家下次落地，「正常落地」也会按下落攻击结算）；姿态用动画时间轴钉在目标刻来冻结；
- **重击清连击段数**：客户端与服务端两份都要清，否则下一次普攻会接着上一段往下算；
- **单手剑 / 长柄武器的重击开头那一下普攻只在「该是第 1 段」时打**
  （`deferNormalAttackOnPress` 判据 + `comboStage > 1`）；

### 文档（2026-10-10 追加）

文档站改成双线：渲染教程在 26.2 之外各补一份 1.21.1 版，页面右上角加版本切换。

**新增**

- 新增 `docs/rendering-photon2-reference-1.21.1.md`（**Minecraft 1.21.1 渲染与 Photon2 完全参考**，约 960 行，12 章）；
- 新增 `docs/rendering-and-photon2-1.21.1.md`（**渲染与 Photon2 特效（1.21.1）**，约 870 行，10 章）。

**变更**

- 文档站：`DocCatalog` 增加版本与对应篇字段，页面右上角加 `#versionbar` 版本切换（26.2 / 1.21.1），
  侧边栏与首页显示版本标签；`MarkdownRenderer` 补两篇新文档的互链映射；`web/README.md` 记下这套约定；
- 两篇 26.2 渲染文档顶部加「1.21.1 版是另一篇」的指引。

内容按本机 source jar 重新核对：NeoForge `21.1.250`、Photon `2.2.8`、LDLib2 `2.2.42`、GeckoLib `4.9.3`。
**Photon 2.2.8 里没有 GeckoLib 集成**（sources jar 与 all.jar 里 `geckolib` / `bernie` 命中数都是 0），
1.21.1 线的 GeckoLib 是 4.9.3；文里也写明 1.21.1 的 `GeoRenderIntercept` 仍是空实现，
26.2 的 GPU 蒙皮与几何接管结论不适用。

---

## 1.0.7 — 2026-09-28（内部版本）

本版只有文档与文档站：新增一篇按 26.2 源码逐条核过的「渲染 + Photon2」参考，
并把文档站在手机上的体验补齐。

> 内部版本号只进日志：`gradle.properties` 的 `mod_version`、`version.json` 的公开标记
> 与站点首页的版本标签都仍停在 1.0.1，发布时再一起抬。

### 文档

- 新增 `docs/rendering-photon2-reference.md`（**Minecraft 26.2 渲染与 Photon2 完全参考**，约 5400 行，
  站点页 `/doc/rendering-photon2-reference`）。全文对着本机源码核对：一帧的「提取 → 提交 → 绘制」
  与「提交时 `PoseStack` 原点是相机」这条关键结论、Blaze3D 的 API 地图、着色器加载与 `#moj_import`、
  自定义 `RenderPipeline` 的注册与 std140 常量缓冲、实体渲染与 `RenderState` 体系、GeckoLib 5 的
  渲染层与骨骼快照、坐标 / 矩阵 / 四元数、GPU 蒙皮与性能预算，以及 Photon2 从编辑器资产到运行时
  的全链路（Java API、`RuntimeValue` 注入、网络 payload、打包与排错）。
- **Photon2 部分按官方文档站（48 个专题页）整站对照重写**：心智模型与「它不是光影包」的区分、
  编辑器与三种项目文件、对象层级与 Transform/模拟空间、发射器与全部模块（发射与形状、生命周期与
  速度、运动与力场、物理/噪声/光照/UV、拖尾与子发射器、Trail/Beam/AraTrail 三类发射器）、
  数值函数、材质与网格与合批、Timeline 轨道与信号、后处理、着色器图与附加 GPU 数据、
  Java API 与运行时注入、网络触发、分发打包。每个主题都按「这是什么 → 参数怎么用 → 案例 → 坑」
  组织，并新增案例与清单：三段式起手特效、受击屏幕脉冲、生命期渐变发光、六个项目实战范例的
  验收清单、从卡到不卡的性能优化实战、三分钟定位法。
- 该文含一张**过时 API 对照表**：`RenderSystem.setShader`、`Tesselator`、`BufferUploader`、`ShaderInstance`
  与 Core Shader JSON 在 26.2 的现状（多数已删除或改名），`RenderType.create` 则改了签名而非消失 —— 
  照抄 1.21 时代的渲染教程会直接编译不过。
- 文档站手机端：代码块加「复制」按钮（触屏常显、桌面悬停显示）、新增「回到顶部」悬浮键、
  锚点跳转给固定顶栏留出高度、顶栏底色加不透明兜底；手写页 `entity-development.html` 补齐与模板
  一致的移动端外壳（顶栏、抽屉、遮罩、无障碍属性）；`MarkdownRenderer` 对同名标题做锚点去重
  （第二处起补 `-1`），避免长文里的「常见坑」互相串高亮。
- 文档站宽屏排版修正：正文原先被硬限在 1080px，宽屏上右侧留一大片空白、稍宽的表格就得横向滚动；
  现在正文用满可用宽度（超宽屏 2200px 上限并居中），表格在桌面端铺满并正常折行。
  实测 2560px 宽屏：正文 2200px、**122 张表格全部无需横向滚动**；1440px 下同样为 0；
  手机窄屏仍保留「列宽按内容走 + 外层横滚」，并把这条规则限定在 `max-width: 900px` 内。
  同时修了两个渲染缺陷：标题里的英文引号会让锚点出现 `quot` 噪音（先还原 HTML 实体再生成 id）、
  手机端表格会把 `duration` 折成 `duratio n`。
- **代码块语法高亮（对齐 IntelliJ IDEA 的 Dark 配色）**：`docs.js` 内置一个轻量分词器，
  按语言（Java / GLSL / JSON / PowerShell / mcfunction）区分关键字、字符串、数字、注释、
  方法、字段与常量、类名、注解、预处理指令；配色直接取自本机 IDEA 2025.3 的
  `themes/expUI/expUI_darkScheme.xml`（关键字 `#CF8E6D`、字符串 `#6AAB73`、数字 `#2AACB8`、
  方法 `#56A8F5`、字段/常量 `#C77DBB`、注解 `#B3AE60`），注释沿用该机器 IDEA 的自定义值；
  代码块底色改为 IDEA 的 `#1E1F22`，行距收紧到 1.55。高亮在浏览器本地完成，不引入任何第三方库或网络请求。

---

## 1.0.6 — 2026-09-27（内部版本）

本版主线是**兜底角色林薇云**：新增角色，并让她承下"别的角色缺模型 / 贴图 / 配置页 / 天赋 /
动画"时的那一份；顺带把外观做成可扩展的数据族、给她六种武器形态与一套 11 格背包。

> 内部版本号只进日志：本版尚未公开发布，站点首页与 `version.json` 的公开标记、
> `gradle.properties` 的 `mod_version` 都仍停在 1.0.1，发布时再一起抬上去。

### 新增

- **新角色「林薇云」**（风元素，全武器类，六种武器都能装备）。她同时是**兜底角色**：
  别的角色缺模型 / 贴图 / 动画 / 配置页 / 天赋时用她那一份（资源侧的「共用目录」就是
  `character/linweiyun/`）。原 `character/default/` 目录已改名到她名下。
- **外观数据族**（`CharacterAppearanceData` 基类 + 默认 / 申鹤 / 林薇云三份）：配置页的外观区
  改读它，按 `optionCount()` / `optionKind()` 通用生成。申鹤的腿部变体与猫耳读写从角色数据
  搬进 `ShenheAppearanceData`，对外接口不变。
- **武器类型「拳头」**：与其它类型同构（拳头武器类、拳头角色基类、韧性 / 冲击查表的第六档）。
  数值暂按单手剑复制，等正式数值。
- **林薇云的 11 格背包**：5 件圣遗物 + 6 个武器槽（一个武器种类一格，见下）。
- **通用角色配置页**（按键 N）：左边 3D 预览、上方外观区、右下角色面板。没有自己配置页的
  角色现在也能打开这一页（原来是提示「还没有配置页」）。
- **飞行素材五套**：`fly` 与 `fly_sword` / `fly_polearm` / `fly_claymore` / `fly_catalyst` /
  `fly_bow`，按武器种类点亮对应骨骼（接线见文末「还没接的」）。
- **鞘翅自由飞行**：原神模式下**二连跳**进入飞行（走原版创造飞行那套开关），
  先播一段角色的**起飞前摇**再真正起飞；前摇期间悬停、锁死移动输入，被打断才作废。
  生存模式需要背上有滑翔装备（鞘翅这类），飞行按
  `gameplay.toml` 的倍率（默认 **2.5×**）扣它的耐久；飞行期间**不能换角色**、
  **不能放技能 / 大招**（门禁写在技能基类，允许空中放技能的角色覆盖即可）。
- **按武器形态分的飞行动画**：`CharacterAnimations` 新增「按玩家现状挑常态动画」与
  「水平档再细分」两条钩子；林薇云六种形态各有飞行片段，长柄还分
  悬停 / 往前 / 上升 / 下降 / 冲刺五条。本地玩家的选择会**随现成的动画状态包广播**，
  远端照播。
- **下落攻击**：基类实现（`SkillBase#plungingAttack`），**所有角色都自带** ——
  坠落中（坠空 > 2 格）或**飞行中**按普攻触发，加速下坠、落地对身周敌人结算伤害，
  并且这一摔按「下落攻击」那条更宽的免伤曲线算。
- **武器脱手的放飞骨骼**：坐骑式飞行时武器不再跟着手走 —— 模型里复制一根挂在身上的骨骼
  （林薇云长柄为 `polearm_fly`），常态藏它、飞行时反过来，前摇期间只藏手骨。

### 变更

- **武器角色的判断收敛成方法**：「这个角色算不算某类武器角色」（原来靠
  `character instanceof SwordCharacter` 这类硬判、或各自去比 `getAllowedWeaponClass()`）现在统一问
  `PGCharacter#characterWeaponClass()` / `currentWeaponType()` / `isSwordCharacter()` 一族；
  换武器校验与武器列表筛选项走 `canEquipWeapon(ItemStack)`。角色分类比武器类型多第七档
  —— `ALL_WEAPON`「全武器类」（只有角色才有，武器类型仍是标准的六种）。
- **林薇云：选武器种类 = 真换武器**。外观里的「武器种类」同时决定她身上那一把是哪一格、
  吃哪一套武器特性、装备页武器栏对着哪一格；这一项与"当前算哪一类武器角色"是**全武器类角色的
  共用特性**（`AllWeaponAppearanceData` + `AllWeaponCharacter`，子类只提供自己的招式内容）：
  - **大剑**：重击是持续型（按住进状态、边转边结算，松手或到时结束），并且按住左键
    **不**先出一段普攻（单击照旧是普攻）；
  - **单手剑**：一次性重击，普攻段数为 0 —— 按一下只出一段普攻，不成连段；
  - 拳头 / 长柄 / 法器 / 弓暂按「一次性重击 + 1 段普攻」占位。
- **外观掩码上限放宽到 20 位**，隐藏器缓存改成按需建（稀疏）—— 大位数下不再于类加载时
  造一百多万个槽。
- **拳头在每一处都按第六种武器处理**（不再有"掉进默认分支"的地方）：武器类型名、
  武器 UID 的类型位、认类表都补上它那一档；装备页那条「类型」文字也不再默认回落到「单手剑」，
  认不出来时老实写「未知」。
- 缺配置页的角色不再只弹提示，而是打开通用配置页（外观区只有「常态显示武器」一项，
  武器类型一行只显示本类、不可选）。
- **飞行期间的行为收口**：不能更换队伍角色（客户端拦 V、服务端也拒）、不能放技能 / 大招
  （门禁在技能基类，按角色可覆盖）、普通攻击改成**直接转下落攻击**。
- **飞行升降的判定改成看按键**（原来按竖直速度，飞行有惯性，松手后要等近一秒才切回悬停；
  而且只有「升/降 → 悬停」这一向有延迟）。本地玩家的选择还会随动画状态包广播给其他客户端。

### 修复

- **摔落伤害改成最大生命值的百分比**：原来走原版「(高度 − 安全高度) × 1 点血」那一套，
  和角色几千点的血量不是一个量纲。现在按一张高度表线性插值（12 格以内不掉血、
  22 格摔死），**下落攻击把免伤区间抬到 38 格**。对存档没有影响；
  这一摔的伤害直接扣当前出战角色。
- **重进存档 / 切第三人称时补播一次起飞动画**：存档里飞行状态是恢复的，而客户端那份
  「正在飞」的镜像从零开始，于是被误判成「刚打开飞行」又走了一遍前摇。现在每次启动
  跟实际的飞行状态对齐一次。

### 兼容性

- **存档键不变**：外观仍是 `leg_appearance` 一个 int。老存档里申鹤低 7 位的含义一个字未变；
  公共位 bit 7 恒为 0 = 常态不显示武器 —— 那本来就是本模组的出厂表现，所以老存档观感不变。
- **林薇云的武器种类位从低 3 位移到 bit 8–10**（低 3 位与申鹤的腿部袜子 / 鞋位冲突，
  选武器会连带换腿）。老存档里她低 3 位的取值会被读成「拳头」，也就是落到原来那个武器槽 ——
  原来那把武器照旧生效、照旧显示。
- **武器槽判定改成「槽号 ≥ 5 都是武器」**：背包接口（`slotCount()`）、装备 / 卸下、
  装备页的武器栏都按这条规则走。其它角色的槽位数仍是 6，行为与以前一致。

### 文档

- 新增 `docs/systems/flight.md`（**飞行与下落攻击**）：二连跳 → 前摇 → 自由飞行的时序、
  飞行期间的三条规矩、滑翔装备耐久倍率、按形态分的飞行片段与升降判定、武器脱手的放飞骨骼、
  下落攻击与摔落伤害表，以及这一套的常见坑。已登记进站点文档清单（`/doc/sys-flight`）。

- 「资源、渲染与界面」补四节：按武器种类分的飞行动画、兜底角色与共用资源目录、
  **武器角色与武器类型是两件事**（含 `CharacterWeaponClass` 与那套判断方法）、
  外观掩码的位布局与「角色专属段从 bit 8 起」（含为什么不能踩 bit 0–6）。
- 「角色系统」的角色清单补上林薇云与全武器类基类，并写明武器角色基类的六 ＋ 一种，
  以及「加一个新角色」一步里的外观数据与兜底说明。

### 还没接的

- 按武器种类选飞行动画（素材已就绪，接线待做）。
- 林薇云自己的招式内容：伤害与动画时序目前走通用兜底动作表 —— 能按、能被特性控制，
  但还打不出她自己的伤害。
- 部分武器还没有具体物品（弓 / 长柄 / 大剑 / 拳头，缺 definition / model / icon）——
  按用户口径**不着急、不是必须**，等有物品时六种一视同仁地加。

---

## 1.0.5 — 2026-09-27（内部版本）

本版把默认模型（`character/default`）的常态动画补齐，接上落地缓冲；顺带清掉一批"照着持武器素材搬过来"留下的不对称。

> 内部版本号只进日志：本版尚未公开发布，站点首页与 `version.json` 的公开标记、
> `gradle.properties` 的 `mod_version` 都仍停在 1.0.1，发布时再一起抬上去。

### 新增

- **默认模型的完整常态动画**：站 `idle`、走 `walk`、跑 `run`、跳 `jump`、下落 `jump_down`、
  落地 `landing`、飞 `fly`、游泳 `swim`、攀爬 `climb`。`jump` / `jump_down` 由状态机按
  「上升 / 下落」自动切；`landing` 是新的落地缓冲：下落姿态 → 深蹲吸震 → 站直（0.45s）。
- **落地缓冲接线**：默认常态配置接上 `withTransitions("landing", 9, null, 0)` —— 落体速度
  超过门槛就播一次落地，播完交回 `idle`。跑步急停（`runStop`）暂未接，素材里没有收停短片。
- **贴图合并**：默认模型的角色贴图与武器贴图合成一张 256×128（角色占左半、武器占右半，
  武器骨骼的面 UV 统一 +128），模型 `texture_width/height` 与 Blockbench 工程的
  resolution 一并改到 256×128 —— 一个人物同时只能用一张贴图。

### 变更

- **常态动画不再显示武器**：`long` / `sword` / `claymore` / `bow` 在站立、走、跑、跳这些
  常态动画里写成 `scale = 0`，空手跑动的手臂不再是"拎着东西"的角度。
- **跳跃从定格姿势变成真动作**：原先三条跳跃素材（起跳 / 上升 / 下落）都是固定的单帧姿势，
  现在起跳是「蹲姿预备 → 蹬出腾空 → 保持腾空」，下落是「下落姿态 + 轻微浮动」，落地是
  「下落姿态 → 深蹲 → 站直」。

### 修复

- **跑动时身体是歪的**：躯干被拧了 −10° 的 Y、头又转了 +9° 的 Y、歪了 −4° 的 Z（侧身持剑
  的姿势残留），已归零；跳跃的 +5° / −5° 同理。
- **右臂 / 右手还是持剑姿态**：跑、跳的右臂原本是"举着剑"的角度，连同前臂一起改成左臂的
  镜像；`Right Arm` 上那条持剑位移通道（`position`）已删除。
- **前发露头皮**：前发骨骼挂在一个会被动画旋转的父级下，父级一动前发就离开头皮；现在父级
  不再写通道、把旋转摊到四根侧 / 后发骨骼，前发只跟头走。
- **走路右臂与左臂不对称**：原先右臂是持剑的小幅摆，现在按左臂的镜像 + 半周期重做，
  左右只有相位差。

### 文档

- 「资源、渲染与界面」新增 **角色动画的约定**：常态动画名与播放时机、落地缓冲怎么接、
  单贴图怎么合并、常态隐藏武器、四肢 / 发丝两种对称规则、前发与其父级、来源骨骼静止角的坑。

---

## 1.0.4 — 2026-09-27（内部版本）

本版一件事：**放开疾跑的方向限制** —— 原来只有「按着 W」能起步与保持，现在 A / D / S 同样可以。

> 内部版本号只进日志：本版尚未公开发布，站点首页与 `version.json` 的公开标记、
> `gradle.properties` 的 `mod_version` 都仍停在 1.0.1，发布时再一起抬上去。

### 新增

- **任意水平方向都能疾跑**：原版把「能不能疾跑」与**前进冲动**绑死
  （`ClientInput#hasForwardImpulse()`，也就是 `moveVector.y > 0` = 按着 W），
  于是按住疾跑键 + A / D / S 起不来，松开 W 只按侧向或后退还会当场掉疾跑。
  本版把这条规则统一放宽成**任意水平移动冲动**（`moveVector` 非零）：
  - 按住疾跑键 + 任一方向键都能起步疾跑，侧移与后退时也保持疾跑；水里（游泳疾跑）同一套规则；
  - 双击方向键起步（原来只有双击 W）在四个方向上一致：后退键不再单独清掉起步窗口；
  - 站住不动照旧不会疾跑 —— 起步判据仍是「上一帧没动、这一帧刚动」，只是方向不再限定 W。
  - 速度加成、FOV、疾跑粒子、疾跑起跳前冲全部沿用原版：那 30% 加成是 `setSprinting`
    挂上的属性修饰符，与方向无关，服务端也不做方向判定。
- 实现落在 `mixin/mixins/LocalPlayerSprintMixin.java`：`LocalPlayer` 里四处
  `ClientInput#hasForwardImpulse()`（`aiStep` 的起步窗口、`canStartSprinting`、
  `shouldStopRunSprinting`、`shouldStopSwimSprinting`）与 `aiStep` 里
  `Input#backward()` 的重定向。**四处缺一不可**：只放开 `canStartSprinting` 的话，
  「按住 A 不放」会在第二刻自动起步。

### 兼容性

- 纯客户端表现层改动：服务端、存档、数据包与网络协议都没有变化。

---

## 1.0.3 — 2026-09-25（内部版本）

本版一件事：**把战斗里的高频开销收进一套独立的性能优化系统**，
并给「改动到底有没有效果」装上游戏内读数。

> 内部版本号只进日志：本版尚未公开发布，站点首页与 `version.json` 的公开标记、
> `gradle.properties` 的 `mod_version` 都仍停在 1.0.1，发布时再一起抬上去。

### 新增

- **性能优化系统**，分两个模块，都只做表现层优化、不参与任何伤害结算：
  - 计算侧 `core/system/performance/`：飘字生成合并（`DamageNumberThrottle`）、
    颜色解析缓存（`DamageTextColorCache`）、有上限 LRU 表（`BoundedLruMap`）、
    热路径日志限频（`HotPathLog`）、每刻查询快照（`TickSnapshot`）；
  - 渲染侧 `client/performance/`：每帧可见性规划与限量（`IndicatorFramePlanner`）、
    字形排版缓存（`IndicatorGlyphCache`）、开销读数（`IndicatorPerfStats`）、
    以及必须在资源重载时清缓存的 `IndicatorPerfReloadListener`。
- **飘字每帧开销读数（F3）**：走 NeoForge 的调试条目注册点，不再需要 mixin 改调试屏。
  按 F3 可直接读到每帧**规划耗时 / 顶点生产耗时 / 提交条数 / 排版缓存条数 / 合并命中率**。
- **新增配置 `minegenshin/performance.toml`**（键位与默认值见《性能优化系统》），默认开启。
- 新增文档《性能优化系统》（`docs/systems/performance.md`）。
- **角色与 GeckoLib 模型的几何优化**（`client/render/optimize/`，默认开启）：
  - 几何预编译（cube 绕自身轴心的旋转折进顶点表）、零分配骨骼遍历、顶点直写，
    把一个 83 骨骼 / 4896 顶点模型的每帧约 1.13 万次堆分配压到十级以内；
  - **GPU 蒙皮**：顶点缓冲常驻显存，每帧只上传骨骼矩阵（14368 字节 / 模型），
    4896 个顶点不再经过 CPU；
  - 骨骼遍历与挂点层复用同一份代码，动画、骨骼显隐、`isHidingChildren`、
    第一人称藏头、压扁 cube 的法线修正全部保持原语义。
- **F3 渲染读数 `mg-render` 扩成两行**，回答两个不同的问题：
  - 第一行 `N model (M gpu) | walk xx.xxms (y.y% of frame) | gpu x.xxms | Nv | xx.xus/model`：
    几何优化自己的开销 —— `walk` 是 CPU 交活的时间，`% of frame` 是它占一整帧的比例，
    新增的 `gpu` 是本模组这批绘制**在 GPU 上**的时间轴跨度（`TimerQuery` 同构的
    时间戳查询，与 vanilla `GPU: xx%` 同源）。CPU 省了不代表 GPU 没变贵，两栏要一起看；
    设备不支持时间戳查询时这一栏消失；被光影包挡下时那一栏写成 `gpu off (shader pack)`，
    绘制不受影响。
  - 第二行 `MineGenshin frame: xx.x fps (x.xxms) | 1%low xx.x fps | window n/600`：
    整帧帧时间与 **1% low**（最近 600 帧里最慢的 1% 帧的平均帧率）。vanilla 的 F3 只有
    瞬时帧率，看平均帧率看不出「偶发长帧」，`1%low` 才是手感有没有变差的判据。
  - 同一场景切换 `gpu-skinning`，两行的对照关系就是「这条优化到底划不划算」的答案。
- **与光影模组（Iris）的软依赖层**：新增一份独立的混入配置
  `minegenshin.iris.mixins.json`（`required: false` + `@Pseudo` + 字符串 `targets` +
  `IrisMixinPlugin` 门禁 + 注入器 `require = 0`），并在 `neoforge.mods.toml` 里把 `iris`
  声明为 **optional** 依赖。没装 Iris 时这一份整份跳过 —— 不报缺依赖、不留「目标类找不到」
  的警告、不影响启动；装了则接住 Iris 的重载入口 `reload()` / `loadShaderpack()`，
  把 GPU 蒙皮「光影包状态」自检的切换延迟从最长 1 秒压到下一帧。启动日志会留一行
  「检测到 / 未检测到光影模组」便于整合包作者核对这份软依赖有没有被加载。
- **韧性与控制系统：敌人未破韧时不吃控制**。新增 `core/system/poise/`
  （`PoiseService`：累积削韧 / 逐刻自恢复 / 破韧驻留 / 联机系数）与 `core/system/control/`
  （`ControlType` / `ControlService` 门控），全项目伤害收口 `LivingEntityHurtMixin`
  在**护盾结算之后**统一累加削韧。未破韧：敌人照常掉血变红，但不吃僵直 / 击退 / 悬浮 /
  聚怪牵引，也不被打断动作；破韧后这些控制才生效。玩家走同一套规则，另有
  `minegenshin:super_armor`（霸体强度）可堆。**护盾 = 霸体**：有盾期间攻击不进韧性条、
  聚怪也无效，对敌我一致。怪物血条下方新增一条削韧条（金色按比例，破韧闪白）。
- **两个通用技能节点（牵引与悬浮）**，`content/skill_node/`，不依赖任何角色即可使用：
  - `GatherPull`（聚怪）：两个 AABB（牵引核心 / 牵引范围）+ 4 档牵引等级 +
    「每秒几格」的速度换算 + 可选持续模式。**聚怪自己会削韧**：目标进入范围先砸一笔
    **高额初始削韧**（第 N 档 = 第 N 档韧性条的长度，每个目标每次进入范围只砸一次），
    **没打破就只掉韧性条、不拉**；打破了才按住对方的移动 AI（`MovementHold`）
    并清掉水平冲量，位移全额生效；持续牵引同时按住韧性恢复。可附带持续削韧、
    目标过滤可用来排除玩家。免疫的目标（三个 BOSS、其它模组实体，查
    `PoiseTiers.gatherResist`）任何等级都拉不动。
  - `Levitate`（悬浮）：把目标抬离地面并按住（风场 / 泡影一类），是强控 ——
    **未破韧与有盾时完全不生效**，这道门每刻重判，韧性恢复或被套盾时当场脱落；
    生效期间按住韧性恢复，落地不吃摔伤。
  - 配套新增 `MovementHold`（AI 持握**租约**）：把 `Mob#setNoAi` 借来按住一段时间，
    不再刷新就自动还原成原值，所以「技能中途消失」不会留下永久定身的怪。
- **星辉风旋（星璇）持续牵引**：星扩散生成的星璇在存在期间**每刻**把范围内的敌人
  往自身位置拉 —— 牵引等级 1（初始削韧 = 1 档韧性条长）、速度 1 格 / 1.5 秒，不拉玩家。
  原版普通生物被它砸一下就破韧、随即被拖过去；砸不动的只会掉一点韧性条、拉不走。
- **冲击子系统（硬直等级 + 重量判定）**：`core/system/poise/impact/`。
  `ImpactLevel` 是文献「常用冲击类型」那张 0–9 表的代码化（无影响 / 微颤 / 轻击 /
  击退 / 击飞五档名，外加 `(击退, 240, 300)` 这类自定力值的写法）；
  `ImpactSolver` 按文献的重量门槛判定「这一下推不推得动」：地面要水平力 ≥ 2×重量才位移、
  要竖直力 ≥ 5.5×重量才击飞，够不上一律降级（击飞 → 击退 → 微颤）；
  空中/攀爬时超过微颤一律击飞。**破韧那一瞬间**才施加冲量 ——
  「这一下是什么冲击」跟着伤害点走（`Hit.impact` → `HitImpact` → `ModDamageSpec`），
  由 `PoiseService.onBreak` 交给解算器。新增 `minegenshin:weight` 属性（默认 100）。
- **武器类型的基准削韧表**（`core/system/poise/WeaponPoiseTable`）：文献那五张逐武器
  削韧表按「武器类型 × 招式大类」取中位数落到代码里 —— 普攻单手剑 50 / 双手剑 107.4 /
  长柄 45.8 / 法器 10.2 / 弓 15.7，重击、下坠期间、低空坠地、高空坠地各有一档；
  同表还带着「硬直等级」列（普攻 3、下坠期间 2、低空坠地 4、高空坠地 7 等）。
  招式没写冲击时按攻击者的武器类型查这张表。战技与元素爆发的削韧取决于技能本身、
  与武器类型没有统计关系，所以不在这张表里，仍走按攻击类型的兜底值 + 角色自己覆盖。
- **反应自带的削韧与冲击**（`core/system/poise/ReactionPoiseTable`）：文献「反应」那张表
  逐行落地 —— 超载 90 / 冲击 5、扩散 130 / 1、感电 130 / 2、超导 30 /（击退，240，300）、
  碎冰 30 / 3、燃烧 30 / 0、月感电 130 / 2、月结晶 30 / 2、星扩散 20 / 2；
  **对敌与对角色是两行**（绽放 25 / 3 对敌、5 / 0 对角色，烈绽放与超绽放同理），
  按目标是不是玩家挑。反应伤害结算时同时攒削韧，所以「超载正好打破韧」会把敌人炸飞。
- **冻结直接破韧**：`PoiseFreezeBreak` 接上一直空着的 `PoiseService.forceBreak` ——
  冻住的那一 tick 把目标判为破韧，冻着期间按住破韧驻留（解冻后才接着走完），
  一次冻结只破一次。冻结本身照常生效，破韧只是它的副作用。
  新增配置 `minegenshin/poise.toml`（`freeze.force-break`、`reaction.poise`，默认都开）。
- **所有控制类效果收进一个入口（`core/system/control/Controllable#applyControl`）**：
  包括冻结的那个 NoAI —— 命中、悬浮、牵引、冻结都只把一次 `ControlRequest` 交给目标，
  由实体自己在 `verdict` 里判断「这是削韧、是控制、还是免疫拦住」：
  ① 冻结无视韧性 → ② 有盾 = 霸体拒绝 → ③ 破绽窗口 → ④ 没破韧只削韧 →
  ⑤ 首领 `blocksControl` → ⑥ 放行。
  调用方不再各写一份判据：技能节点问 `ControlService.canApply`，命中走 `ControlService.onHit`，
  要强行施加走直通口 `ControlService.force`（不问韧性、不问系数、不问窗口）。
- **打断动作与击退变成实体内置方法**（`Controllable#interruptAction` / `#cancelOngoingAction` / `#knockback`）：
  怪侧取消进行中的动作（含女巫喝药那种写在 `aiStep` 里、AI 开关拦不住的状态）+ 停掉正在跑的 Goal +
  `MoveControl.setWait` + 停导航 + 清移动意图与水平动量 + 开一个静止窗口；玩家侧走 `ActionManager.interrupt`；
  击退对玩家直接交回原版（这套冲量会替换水平分量，会把疾跑和自己的移动一起抹掉）。
- **通用打断：静止窗口从 AI 的总闸往下切三层**（换掉了上一版的「通用静止 Goal」：
  Goal 只够管 Goal 驱动的动作，它上面还有一整层 AI，下面又漏着不走 AI 的状态机）：
  ① **AI 闸门**（`mixin/mixins/MobServerAiStepMixin`）—— 窗口内把目标的 `serverAiStep()`
  整段掐掉。那是原版 AI 的总闸：探测、目标与 Goal 的取舍、导航、
  `customServerAiStep`（Brain 类怪的 `getBrain().tick()` 就在里面）、移动/视线/跳跃三个控制器
  全在它下面，所以「走 AI 的动作」这一整类不用点名就都停了，原版与其它模组的怪一视同仁；
  ② **每刻取消钩子**（`ControlService.onServerTick`）—— 窗口里每刻再调一次 `cancelOngoingAction`，
  拦住不写在 AI 管线里的动作，同时是本模组怪纯 Java 的覆盖点（不用碰 mixin）；
  ③ **`core/system/control/TickActionSuppressor`** —— 给原版那些连闸门都拦不住的状态机开口子
  （它们的私有字段只能在 mixin 里摸），核心代码通过接口转发、不点名任何一只怪。
  闸门**不动 `NoAI`、不进 NBT**：不跟冻结抢开关，也不会留下永久定身的怪；物理照跑，
  所以击退、下落、牵引的手感不受影响。
  **它是打断、不是定身**：窗口很短（`HIT_TICKS = 10` 刻），长时间按住仍然是 `MovementHold` 的事。
- **韧性上限变成逐实例可变属性**（`minegenshin:poise_max`，默认 `0` = 用档位长度）：
  同一个注册表条目（比如僵尸）在不同环境下可以有不同韧性 —— 副本里调高、普通地区保持默认。
  与档位长度一样乘联机系数，上限缩小会把当前值一起钳下来。
  **上限管条有多长，档位管每秒衰减与重置**，两者互不覆盖。
- **首领覆盖一个方法就够**（`ITeyvatBoss#blocksControl`）：破韧也照样拦，连冻结一并免疫 ——
  想给它一次控制只有两条路：`ControlService.openGap(boss, ticks)` 开一个**一次性**破绽窗口
  （窗口期内下一次命中的打断一定成立，无视韧性与抗打断系数，用完就没了），
  或 `ControlService.force(...)` 直通口（剧情、处决演出、调试）。首领同时有默认霸体强度
  `PoiseTiers.BOSS_SUPER_ARMOR = 10`，比文献那 0–9 表里最强的一档（击飞 9）还高一档，
  所以削韧也被 ÷10。部分精英怪不用改代码 —— 给它配个 3~5 的 `super_armor`
  就是「轻击打不断、击退/击飞才打断」。原版三个 BOSS（末影龙、凋灵、监守者）
  现在按同一档处理（`PoiseTiers.isBossTier`）。

### 变更

- **普通生物的韧性条拉长到原来的 3.3 倍**（`PoiseTiers.TIER_PROFILES` 1 档：条长 30 → **100**，
  每秒衰减 2 → 5）：原来单手剑 / 长柄 / 双手剑的普攻**一下就破韧**，普通怪几乎一直待在
  破韧状态里，「没破韧不吃控制」这条主玩法反而看不见。现在破一只普通怪要
  **单手剑 2 下、长柄 3 下、双手剑 1 下、弓 7 下、法器 10 下**（抗打断系数 1）。
  2 / 3 档跟着抬到 140 / 200 只是维持「档位越高越耐」的递增（这两档暂时没有生物落在上面）；
  **4 档（大体型与 BOSS）一个数都没动**。满条恢复时间四档都在 14–20 秒，
  「哪怕一直被攻击也会恢复」的手感不变。
  武器的削韧模板（`WeaponPoiseTable`）保持文献中位数未动 —— 它是另一个旋钮
  （要「降低角色削韧模板」时改那边），两边互不覆盖。
- **飘字改为世界空间提交**：几何活在世界投影里，近大远小由透视自然给出，
  不再需要旧 HUD 方案那套手动投影 + 距离缩放；渐变改为**逐顶点**在顶点色里插值，
  去掉了遮罩贴图这条链路。
- **密集伤害数字默认合并**：同一目标、同一配色的连续伤害会并成一条**累加**的数字，
  客户端活跃条数从「随攻击次数线性增长」变成「每目标常驻 1 条」。
- **单帧飘字上限与背面剔除**：默认单帧最多 128 条（就近保留），相机背后的不提交。
- **热路径日志限频**：感电触发、月感电刷云、元素战技扫方块这类逐次结算的日志，
  每处每秒最多 8 条，被压住的条数在下一秒用一条汇总行补出来。
  日志量不再随攻击速度线性上涨，也就不会让主线程卡在磁盘 I/O 上。
- **多个按实体记录战斗状态的静态表改为有上限 LRU**：这些表原来只写不删，
  长时间游戏后会持续占用内存。
- **GPU 蒙皮从「只认角色 + `entity_cutout`」扩到所有 GeckoLib 渲染器**：
  - 接管点从 `CharacterRenderer` 上移到 GeckoLib 的接口 default 方法
    `GeoRenderer#submitRenderTasks`（一个 mixin + 共享入口 `GeoRenderIntercept`），
    角色、本模组实体、其它模组的 GeckoLib 实体走的是同一份代码；
  - skinned 管线改为由原版 entity 管线**派生**（`RenderPipeline#toBuilder`），
    solid / cutout / translucent / 位移等变体各得一条深度、混合、shader define
    与之完全对应的管线，不再需要逐条手搓；
  - 提交按原版规则分流到 solid / translucentCustomGeometry / outline 三个阶段，
    半透明几何不会跑到不透明阶段去画；
  - 拦截用的 mixin 必须是 `interface` 形态：Mixin 按混入类自身的形态选混入子类型，
    类形态会落到 `SubType.Standard`，它对「接口目标」直接抛目标类型异常，
    `require = 0` 挡不住这类校验（写错会让客户端在混入准备阶段起不来）。
- **破韧期间改成「几乎无控制抗性」**：早先的实现只有「破韧那一瞬间」有击退与打断，
  判据还是写死的「硬直等级 ≥ 2」。现在**只要目标是破着韧的，每一次命中都推一次、
  也判一次打断**，判据从常数改成「这一下的打断强度 vs 目标的抗打断系数」——
  普通敌人（系数 1）被微颤（1）就能打断，也就是「随便攻击都能打断其目前的动作」；
  霸体堆到 3 的目标得靠击退、堆到 10 的只有破绽窗口能打断。
  文献那条「后一次攻击的冲击覆盖前一次」也因此天然成立（每次都用最新那一下，不做叠加）。
  **打断与击退彻底拆开**（用户口径：击退是次要的，不一定有，最主要的是打断动作）：
  请求没带冲击时按微颤 1 算（只打断、不推），只有 `ImpactLevel.NONE`（0）才既不打断也不推；
  击退与打断门槛无关，`impact == null` 时一点都不推 ——「打断而不推」是常用组合。
  判定与施加都在 `core/system/control/Controllable`（`applyHitControl` / `interruptAction` /
  `knockback`）：怪侧把**正在跑的 Goal 逐个 `stop()`**（真的取消当前动作，Goal 自己的 `stop()`
  会跑，冷却与复位都在那里）、`MovementHold` 按住 10 刻、再停掉导航；玩家侧走 `ActionManager`。
  别的模组的实体整块跳过（不敢对别人的 Goal 乱调 `stop()`）。
  同批删掉了 `core/system/control/StaggerControl` 与 `combat/action/ActionInterruptHandler` ——
  玩家与怪物现在共用同一个入口，不会再各写一份判据。
- **削韧条接入点补全**：`MobPoiseBar` / `MobHealthBar` 都拿到与 `HPProgressBar`
  同款的 0 宽裁剪保护（`MIN_CLIP_PIXELS` + 实时读 `layer.isDisplayed()`），
  并给 `mob-poise-bar` 补上 LSS 样式（未破韧金色、破韧纯白）。

### 修复

- **打断以前是逐怪点名，换个怪就不灵**（实测：女巫修好了，苦力怕照样把引信走满炸掉）：
  原因是「停 Goal」只够管 Goal 驱动的动作，而苦力怕的引信写在 `Creeper.tick()` 里、
  劫掠兽的攻击/击晕/咆哮计时器写在 `Ravager.aiStep()` 里 —— 都不在 AI 管线，
  连原版自己的 `setNoAi(true)` 都拦不住（NoAI 苦力怕照样炸）。
  现在改成上面那套**通用打断**（AI 闸门 + 每刻取消 + `TickActionSuppressor`）：
  走 AI 的动作整类由闸门拦下，剩下那两只按各自的状态归位
  （`CreeperFuseMixin` 把引信清零、方向拨回消退；`RavagerActionMixin` 清掉三个计时器），
  再遇到同类原版怪只需照抄一个 mixin，核心代码不动。
- **女巫被打断后「还在喝药、手上一直拿着药瓶」**：打断只翻了她的 `DATA_USING_ITEM`，
  可原版 `Witch#aiStep` 的喝药是另一套状态 —— 主手那瓶药**只在喝完时**才清、
  私有 `usingTime` 照旧、喝药减速 `minecraft:drinking` 也只在喝完时移除；
  于是下一 tick 她就重新起手，看起来完全没被打断（顺带还会永久慢 25%）。
  新增 `Controllable#cancelWitchDrink`：翻标记 + 拿走主手的药 + 拆掉减速，三件事一起做。
  同时 `interruptAction` 打了一行 debug 日志（「打断 X 的动作」），
  排查「到底有没有断」时直接看它。
- **飘字字形缓存的失效判据修正**：`Minecraft#font` 的实例在资源重载后并不会换
  （`FontManager#apply` 是就地重建 `FontSet`），因此「比字体实例」这条判据永远不成立 ——
  少了重载钩子，按 F3+T / 切换资源包后飘字会花屏。现在由资源重载监听器显式清缓存。
- **伤害管线的开发日志**：开关只挡得住「收」挡不住「拼」，公式日志的字符串
  每次伤害都在白拼一遍。改为「先问再拼」。
- **GPU 蒙皮角色模型一闪一闪，两个互相独立的根因**：
  1. 骨骼矩阵常量缓冲的 std140 偏移写错 —— 法线块原来紧接「已经写了几根骨骼」之后，
     而着色器里 `mat4 Bones[128]` 是定长 8192 字节，于是骨骼数少于 128 时每根骨骼
     读到的是别人或上一帧的残留法线，光照逐帧乱跳。现在固定从
     `MAX_BONES * 64 = 8192` 起写，`mat3` 按 std140 每列补 1 个 float；
  2. 隐藏子树时顶点游标漂移 —— `isHidingChildren`（第一人称藏头、挂点层 skipChildrenRender）
     命中时原来既不递归也不推进游标，被藏子树之后的所有骨骼区间整体前移、画到别人的顶点上。
     现在编译期在 `CompiledBone#subtreeVertexCount` 累加好子树顶点数，运行时一步跳过。
- **F3 读数在 GPU 路径下显示 `0 model`、没有 `us/model`**：`RenderOptimizeStats#recordGpu`
  漏了 `models++`（分母为零），顶点口径用的也是整网格容量。现在补上计数，
  并把顶点口径改成「可见 run 合计」，与 CPU 路径可直接对照。
- **GPU 蒙皮在光影下把模型画成散落的黑块**：光影模组（Iris 一类）是按<b>程序身份</b>选着色器的
  —— 它那张「顶点格式身份 → 着色器程序」的映射表只认原版格式常量，认不出的格式一律落进
  通用兜底程序；而 GPU 路径的顶点缓冲是本模组按 32 字节/顶点<b>自己打包上传</b>的，
  于是实际执行的那条程序按它自己的约定去读，位置就从 UV / 骨骼编号 / 法线修正掩码
  这些字段上取值，画出来就是「第三人称模型整个不见 + 角色周围散落黑块、有的坐标看着很远」。
  新增 `SkinnedPipelineGuard`：光影包生效
  （Iris API，反射调用，本模组不依赖 Iris）或管线顶点格式 / 顶点着色器被外部替换
  （不认模组名字的三方自检）时，GPU 蒙皮自动让位给 CPU 蒙皮 —— CPU 路径继续优化，
  只是顶点不再进显存。F3 上照旧显示 `N model`，GPU 那一栏换成 `gpu off (shader pack)` /
  `gpu off (vertex format)`；新增 `render-optimize.gpu-skinning-under-shaders`（默认关）
  用于强行放回 GPU 路径，确认问题是不是出在这条路径上。
- **飘字阴影离本体偏远**：阴影的右下偏移原来写死 1 字体像素（与香草文字阴影同距），
  但飘字是世界空间里的放大文字（约 1.36 倍命名牌），同一个 1 像素落到屏幕上比原版
  文字阴影更显眼，看起来像阴影飘出去了。改为可配 `indicator-render.shadow_offset`
  （字体像素，0–2），默认 `0.5` —— 跟着字号等比缩放，想要和原版一字不差就调回 `1`。
- **从薇斯娜切换到别的角色会把渲染线程打崩**（`IllegalArgumentException: Scissor size must be >0, was 0x8`）：
  队伍血条 `HPProgressBar` 的填充层与拖尾层都带 `Clip.SCISSOR`，宽度按血量比例算；
  LDLib2 会把裁剪框四舍五入到物理像素，比例小到不足半个物理像素时取整成 **0 宽**，
  而 `RenderPass#enableScissor` 对宽或高 ≤ 0 无条件抛异常 —— `0x8` 正是这条血条
  （高 4 gui 像素 × 界面缩放 2 = 8 物理像素）。切角色会让队伍 HUD 重建、被切走的槽位
  血量比例正好归零，所以只有切人时踩到。修法是让裁剪层永远不带着 0 宽的框进入绘制：
  宽度不足 `MIN_CLIP_PIXELS`（1 个物理像素）时整层 `setDisplay(false)`，
  且判据**实时读 `layer.isDisplayed()`**、不缓存成布尔字段 —— 缓存分不清「还没判过」
  与「判成不可见」，首帧起就是 0 宽的血条会永远等不到那一次写入。
- **削韧条在「比例正好是 0」的那一帧会把渲染线程打崩**：和先前修好的血条同款 ——
  LDLib2 把裁剪框四舍五入到物理像素，0 宽/0 高的框交给 `RenderPass#enableScissor`
  会无条件抛 `IllegalArgumentException: Scissor size must be >0`。破韧瞬间削韧条比例
  正好归零，所以这条路径比血条更容易踩到。修法与血条一致：让裁剪层永远不带着
  0 宽的框进入绘制，判据实时读显隐、不缓存成布尔字段。

### 文档

- 新增 `docs/systems/performance.md`，登记进文档站（`DocCatalog` + 导航兜底菜单）。
- 新增 `docs/systems/poise-control.md`（《韧性与控制》），登记进文档站；内含判定顺序、
  削韧来源、护盾=霸体、牵引为什么特殊、两个技能节点的用法与常见坑。
- `docs/systems/performance.md` 的配置表补上 `indicator-render.shadow_offset`。
- 文档站的链接重写改为**长键优先替换**，修掉 `docs/x.md` 与 `x.md` 同时命中时
  可能互相打断、留下 `docs//doc/...` 死链的问题。

### 兼容性

- **伤害数值、反应结算、掉落与注册 ID 完全不受影响**：本版所有改动都在表现层。
- **与光影模组共存**：GPU 蒙皮在光影包生效时自动让位给 CPU 蒙皮（光影按「程序身份」选着色器，
  GPU 路径自带的顶点格式在那种环境下会被交给不认识这份布局的兜底程序去读）。CPU 路径没有这个限制 ——
  它把顶点写进原版自己的缓冲，与 GeckoLib 原路径逐字节相同，所以光影下照常享受优化。
  想手动控制：`render-optimize.gpu-skinning` 关掉即完全回到 CPU 蒙皮；
  `render-optimize.gpu-skinning-under-shaders` 打开则在光影下也强行用 GPU 路径（仅排查用）。
- **存档与数据包不受影响**；飘字广播的 RPC 参数增加了「合并键 / 是否合并」两项，
  服务端与客户端需同版本（本就要求一致）。
- **默认观感会变**：连击数字会累加、单帧最多 128 条、相机背后的不再绘制。
  想要原样逐条飘字，在 `minegenshin/performance.toml` 里关掉
  `damage-number.merge`、把 `indicator-render.max_rendered_indicators` 调大、
  并关掉 `indicator-render.cull_behind_camera` 即可。飘字阴影的右下偏移默认由
  1 字体像素收紧到 0.5（`indicator-render.shadow_offset`），觉得阴影太淡就调回 `1`。
- 未做正式的游戏内对照测试：F3 上的读数就是为这件事准备的，真实收益随场景变化。
- **几何优化是纯表现层开关**：`render-optimize.character-geometry` 关掉即完全回到优化前的
  路径；`render-optimize.gpu-skinning` 关掉即回到 CPU 蒙皮（两者都可以在游戏内实时切）。
- **GPU 蒙皮的适用范围**：原版 entity 管线的 solid / cutout / translucent / 自发光 / 位移变体。
  带 `DISSOLVE`、`APPLY_TEXTURE_MATRIX` 的管线以及护甲渲染器（`GeoArmorRenderer` 自己覆写了
  提交方法）不接管，自动走原路径 —— 不会出现「该溶解的没溶解、该套纹理矩阵的没套」这类偏差。

---

## 1.0.2 — 2026-09-24（内部版本）

本版一件事：**自带模型与动画改为整包分发**，同时给「想自己改模型」的人留了一条明文通道。

> 内部版本号只进日志：本版尚未公开发布，站点首页与 `version.json` 的公开标记、
> `gradle.properties` 的 `mod_version` 都仍停在 1.0.1，发布时再一起抬上去。

### 变更

- **自带角色的模型与动画改为整包分发**：不再以一份份可读的 `.geo.json` / `.animation.json`
  出现在仓库与发行包里，而是收进一个资源包（整包一个文件，具体位置属实现细节）。
  代码里的逻辑路径（`character/vesna/vesna.geo.json` 这一类）、后缀判断、缓存键与目录索引**全部不变**
  —— 读取侧只换了「字节从哪来」，`CharacterRenderData` 与所有调用方都不用改。
- **新增 `local/` 明文目录**：对象目录里多了一个 `local/` 子目录
  （`character/<角色id>/local/vesna.geo.json`、`entity/<实体id>/local/<id>.animation.json`），
  放在这里的模型与动画**明文直读**、重启即生效，与资源包里的同名资源**同名时以它为准**；
  它不参与资源身份计算 —— 逻辑路径与缓存键都按「去掉 `local/` 这一层」来算。
- **构建前自动重新生成资源包**：改了模型直接构建就行，不需要记得手动跑任何命令；
  内容没变化时不会重写文件，仓库不会因为「跑了一次构建」而变脏。
- **明文模型 / 动画不进仓库**：对象目录下的 `.geo.json` / `.animation.json` 在 `.gitignore` 里排除，
  仓库里唯一的明文口子是 `local/` —— 所以克隆本仓库拿到的是一份**没有逐文件可读模型**的源码树，
  自带角色的模型与动画只有资源包那一个文件。想改某一个模型，走 `local/` 就是正式通道。
- **发行物只有一个文件**：本 MOD 只发 `build/libs/minegenshin-<版本>.jar` 这一个 jar，
  连同依赖 Mod（LowDragLib2、GeckoLib）放进 `mods/` 即可 —— 没有附加文件、附加目录，
  也没有需要单独下载的资源。

### 文档

- `docs/systems/render-asset.md` 补「模型与动画：资源包 + `local/` 明文目录」一节；
  `CHARACTER_SYSTEM.md` 的 5.2 路径解析与 9.1 统一布局同步这两条约定；
  `docs/entity-ai-goal-guide.md` 的资源契约与「新增一个测试实体」步骤改为把模型 / 动画放进 `local/`。
- `docs/systems/render-asset.md` 另补「明文怎么放、谁进仓库、发行形态」三条落地约定；
  `README.md` 的构建与运行一节写明发行物只有一个 jar。
- `README.md` 的文档表里几条指向 `web/*.html` 的旧路径改回真实页面 `/doc/<slug>`（原来会 404）。
- 更新日志新增本小节（内部版本，不改变公开发布状态）。

### 兼容性

- **资源路径语义未变**：引用 `character/**`、`entity/**`、`item/**`、`block/**` 的代码、配置与资源包都不用改。
- **存档、注册 ID、数据组件与网络协议不受影响**。
- **升级不用改任何东西**：从 1.0.1 换到本版，`mods/` 里放的还是那一个 jar 加依赖 Mod。
- 直接放在对象目录根下的明文模型 / 动画仍然会被读到（磁盘优先于资源包），
  但正式提交请走 `local/`，免得仓库里又出现逐文件的可读模型。

---

## 1.0.1 — 2026-09-24

本版两条主线：**资源布局统一**（项目自己的「入口按对象、对象内部按类型」布局成为唯一真相，
原版四个写死路径的入口改由本 MOD 的重定向层供料）与**元素起源**
（元素附着与元素反应收敛成单一入口，生物、方块、出战角色共用同一套「可附着宿主」，
元素不再只是挂在某个方块上的旁路）。

### 新增

- **资源重定向层**（`core/asset/AssetRedirects` + `mixin/mixins/FileToIdConverterRedirectMixin`）：
  在 `FileToIdConverter` 上补进虚拟入口，让原版四条通道都读我们的布局 ——
  `blockstates/<id>.json` ← `block/<id>/blockstate.json`、
  `items/<id>.json` ← `item/<id>/definition.json`（方块物品回落 `block/<方块id>/blockitem/definition.json`）、
  `models/<路径>.json` ← `<路径>.json`、`textures/<路径>.png` ← `<路径>.png`。
  **只对本 MOD 命名空间生效**，整合包里其它 MOD 的目录完全不受影响。
- **对象目录内的 `textures/` 与 `sounds/` 子目录**：一个角色以后会有很多张图与很多条语音，
  所以对象内部按类型再分一层（模型与动画留在对象根，文件名自带类型后缀）。
- **元素载体「可附着宿主」抽象**（`core/system/about/host/`）：生物、方块、出战角色共用同一个附着入口
  `ElementalAttachmentHelper.attach(宿主, ...)` —— 「附着 → 附着内反应 → 反应引发效果」这条链只有一份实现。
  新增一种"与元素有关的方块"只需要注册两条（能不能被附着、挂上之后变成什么），**不用改核心代码**。
- **环境自附着**：完整水源自带水元素、冰族自带冰元素，不衰减。"冰打水面能冻结"靠的是水这边这份先手元素，
  而不是"方块状态对不上就改状态"。
- **攻击范围附着**：原神模式每次攻击的伤害点按这一招的攻击距离取一次范围，把范围内的可附着方块
  送进同一个附着入口 —— 方块不再是一条只能靠左键点击去补的支线。
- **寒元素（`COLD`）与伴随机制**：冰的减速、冻的禁 AI 不再由冰/冻元素各自硬编码，而是集中到"效果载体"
  子元素寒身上 —— 冰/冻只负责附着，寒由 `ColdAura` 每 tick 伴随同步（有冰/冻就补、冰和冻都没了就撤）。

### 变更

- **资源布局全面收敛**：`assets/minegenshin/` 下只剩 `character/ item/ block/ entity/ gui/ icon/ lss/ lang/`
  七个顶层目录。原版的 `items/`、`models/`、`blockstates/`、`textures/` 在本命名空间下**不再存在**，
  `lang/` 是唯一保留的原版硬性入口（一种语言一个文件，不做重定向）。
- **物品资源全部进 `item/<物品id>/`**：`definition.json`（物品定义）、`model.json`（平面模型）、
  `textures/{texture,icon}.png`（平面贴图 / GUI 图标），geo 物品另有 `<id>.geo.json` 与 `textures/<id>.png`。
- **方块通道就位**：`block/<方块id>/` 下 `blockstate.json`、`model.json`、`textures/`、
  `blockitem/{definition,model}.json` + `blockitem/textures/`。
- **数据生成改产出我们的布局**：`ModModeProvider` 不再继承原版 `ModelProvider`，
  直接写 `item/<物品id>/{definition,model}.json`（模型 `layer0` 指向 `item/<物品id>/textures/texture.png`），
  并自带「本命名空间每个物品都要有定义」的等价校验。
- **角色美术归位**：头像 / HUD 头像 / 两种立绘 / 技能图标进 `character/<角色id>/textures/`，
  共用界面贴图进 `gui/`，元素图标进 `icon/elemental/`，实体三件套进 `entity/<实体id>/`。
- `ItemIcons` 解析顺序调整为「对象目录图标 → 通用图标目录 → 平面贴图 → geo 贴图兜底」。
- **免疫只拦伤害，不再吞附着**：元素生物（冰史莱姆、冰方块）照样会被挂上火/水并正常反应，同元素伤害仍为 0。
- **拒收附着 = 不反应**：修复"大型冰史莱姆明确拒绝水、水打上去却照样冻结"这类规则与行为不一致。
- **冻结对原版怪也生效**：冻元素存在期间关掉 AI 并清掉位移 —— 以前只对本 MOD 生物生效，
  而且水流推动/浮力仍会累积位移，解冻瞬间会"弹"回原位。
- **方块上的冻结/融化不显示反应文字**：水结冰、冰化水是形态变化，不再飘"冻结/融化"；
  同样的反应打在生物身上照常出字。
- **流动水完全不参与元素体系**：只有完整水源会被冻成浮冰（流动水既不自带水、也不收冰）——
  从根上避免"化开之后水位对不上 / 被原版水流灌回满水方块"。
- 附着档位映射修正：2.0U 现在走 `STRONG`（1.6U / 12s），此前被错误映射成 `MEDIUM`（1.2U / 10.75s）。
- 雷 + 冰的**超导反应**已注册并接入伤害（实现一直存在，却从未注册，配置项是死的）。
- **元素生物"免疫冰"的落地方式改了**：从"跳过减速"改成**不收寒**
  （`ElementalCreature.acceptsElementAttachment`）。冰史莱姆照样给自己挂冰、也能被挂冻，
  但收不到寒 → 既不被拖慢也不被冻住；豁免只需表达一次，不必在每个效果里各判一遍。
- `CryoElement` / `FrozenElement` 已删除（效果搬进寒之后只剩空壳，留着就是两份真相）。
- 冻结期间的位移锁判据改为"有寒且有冻"（`StatusTickHandler` → `ColdAura`）——
  冻元素没被宿主接受寒的单位本就不该被冻住，位移自然也不该锁。

### 修复

- `AssetGeoCache` 原先在客户端初始化阶段预热（那时资源还没就绪），索引恒为空、
  每次都回退到 `GenshinGeoCache`；现在注册为客户端资源重载监听器，异常全兜住，空扫描不落锚、最多重扫 5 次。
- `ModBlocks.register(modEventBus)` 从来没被主类调用（空注册器死脚手架），已接线。
- `GenshinAssets` 的 javadoc 声称「通过 `GeckoLibResourcesMixin` 把 `character/**` 加进 GeckoLib 扫描范围」——
  该 mixin 并不存在，已改写成真实机制（`GenshinGeoCache` 自扫自烘 + `GenshinGeoModel` 覆写两个 public 方法）。
- 删除 22 个死文件与误粘文件（`blockstates/flower_block.json`、`textures/block/*`、
  `textures/icon/**` 的逐字节副本、两张 `slime_cyro`、`textures/item/img.png`、`textures/gui/shenhe.png`、
  `character/vesna/{vesna.json, vesna_texture.png, a.json}`、`assets/color.txt`、`code.txt`、`render.txt`、
  `latest.log`、`data/.../untitled-1.java`）；其中不可从 git 恢复的 `vesna/a.json` 已另行归档。
- 方块元素的推进从"原版方块 tick 链"改为集中式推进（`BlockElementTicker`）：修掉"一片冰里边缘逐个化、
  中间一直冻着"（排期链断掉）与"一起结的冰不同时化"（同一 tick 被重复推进一次衰减）。
- 浮冰的融化判据只看**冻元素**是否耗尽，不再看冰元素 —— 此前任何一份残留冰元素都会把浮冰永久钉住（永不化）。
- 自然融化收尾时的掉帧：以前每 tick 都 `setData` 把整张 chunk 元素表同步给客户端（掉帧元凶）；
  现在去掉该同步、去重改为纯内存、提交幂等化。
- 原版旁路切断：已在元素体系里的冰被**破坏**时不再直接变成水（原版 `IceBlock.playerDestroy` 的行为），
  浮冰也不再被"周围同类少于 2 个"的原版规则旁路融化；没有元素容器的浮冰仍交还原版。
- `AttachmentProfile.permanent()` 的常驻标志恒为 `false`（时长参数与判据不一致），已修正。
- `LargeCryoSlime` 直接覆盖 `onAttachElement`（只拒水）导致**整体绕过**元素生物的默认筛查，
  寒照样被收下 → 冰史莱姆会被减速甚至冻住；已改为"先拒水、其余委托默认筛查"。

### 兼容性

- **引用过旧资源路径的资源包 / 材质包需要迁移**：`textures/character_avatar/**`、`textures/character_party_pose/**`、
  `textures/skill/**`、`textures/elemental/**`、`geckolib/models|animations/entity/**`、
  `items/*.json`、`models/item/*.json`、`textures/item/*.png` 这些旧位置**全部失效**，请按新布局放置。
- **存档与配置不受影响**：没有改动注册 ID、数据组件、序列化格式与网络协议字段。
- 数据生成产物位置变了：跑 `runData` 现在产出到 `src/generated/resources/assets/minegenshin/item/<id>/`。
- **方块元素容器换了格式**（坐标 → 完整状态容器）：旧存档里的方块元素态**不做迁移** ——
  它本来就是秒级瞬态（浮冰几秒化回水），重进世界重新附着即可。
- 浮冰的寿命与之前的手感可能不同：冻元素现在与实体走同一套衰减（0.4U/s 起、冻结中逐秒加快），
  不再是固定 0.2U/s。

### 文档

- `docs/systems/render-asset.md` 按新布局重写：四条重定向通道的对应关系、实测注入日志、
  以及「还剩下的原版硬性项（只剩 `lang/`）」。
- `CHARACTER_SYSTEM.md` 第九部分同步布局与图标解析顺序，并补第三轮迁移对照表；
  `docs/entity-ai-goal-guide.md` 的资源契约一节同步，并标注早期测试实体已被删除。
- 更新日志新增 1.0.1 小节；`gradle.properties`、`version.json`、README 与站点首页的版本号同步抬到 1.0.1。
- 新增 `docs/systems/element-host.md`（可附着宿主：宿主契约、三段判断、环境自附着、攻击范围附着、性能纪律）；
  `docs/systems/element-reaction.md` 与 `docs/systems/combat-attack.md` 同步反应入口、飘字开关与方块附着路径；
  `CHARACTER_SYSTEM.md` 4.7 补环境自附着与攻击范围附着。

---

## 1.0.0 — 2026-09-24

首个正式版本：把原神的核心玩法机制完整接入 Minecraft 26.2 / NeoForge。

### 玩法内容

| 系统 | 内容 |
|---|---|
| 角色 | 薇斯娜、申鹤、阿蕾奇诺、雷电将军、哥伦比娅、沃雅妮莎；等级、突破、天赋、命座、编队 |
| 属性 | HP/ATK/DEF 等属性与修饰器（固定/百分比、临时/永久），支持上限放宽 |
| 元素 | 元素附着与量级、附着冷却（衰减序列） |
| 元素反应 | 蒸发、融化、冻结、超导等，倍率可配置 |
| 战斗 | 普攻/战技/闪避/爆发四键动作系统；攻击判定与伤害管线；索敌锁；动作打断 |
| 伤害表现 | 屏幕空间伤害飘字：普通/暴击/反应/治疗/文本五类，元素配色与渐变 |
| 圣遗物 | 配置驱动的套装、主副词条、升级经验、背包与装备栏 |
| 武器 | 按类别（单手剑/法器/长柄/弓/双手剑）的等级与词条、精炼 |
| 祈愿 | 原石抽卡与重复角色返还 |
| 怪物 | 等级与防御注入、可按入侵状态生成 |
| 护盾 | 护盾吸收、元素与形状配置、每 tick 结算 |
| 状态 | 附着状态容器，作用于生物、方块与物品 |
| 掉落 | 入侵状态下生物额外掉落（原石、随机圣遗物） |
| 界面 | 角色信息、背包、升格、编队、祈愿界面与 HUD |

### 架构与规范（1.0.0 起确立）

- **分层与依赖方向**：`config`/`enums` → `core` → `content` → 客户端（`client`/`render`）；公共侧不得依赖客户端。
- **钩子归属规范**：跨模块入口放 `event/`，单模块监听点下沉到模块包并命名 `XxxHandler`；同一事件允许多个监听点（前提是归属清晰、不是同一职责被写两遍）。
- **攻击统一**：攻击相关处理一律并入 `core/system/combat/attack`，技能与其它模块不得自行注册攻击监听。
- **分类维度**：武器/角色按武器类型分包；实体按"生物 / 领域 / 杂项"分包；配置按域分包。
- **测试内容清理**：移除早期的测试实体、测试物品与相关资源，并把其中混用的真功能（冰块投射物、实体渲染器、资源缓存预热）迁到正式位置。
- **命名与路径整理**：修正包名拼写、包与目录不一致、双 `render` 目录、监听类命名等问题。
- **结构整理（全量，2026-09-24 完成）**：事件监听按"跨模块入口留 `event/`、单模块下沉到模块包"归位；顶层 `enums` 包按功能拆分归位（`ElementalsGIM`→`core.element`、`AttackType`→`combat.attack`、`AttachmentType`→`core.system.about`、`ElementalReactionType`→`core.system.reaction`、`CharacterAscendAttribute`→`core.character`）；`AttackType` 与 `DamageTypeEnum` 的重叠合并（删除零引用重复定义）；公共侧引用客户端类的问题清零——飘字改走 `api/damage` 契约、`getActiveCharacterId` 下沉到 `CharacterHelper`、动作与按键相关类整体迁到 `client.combat.{action,state}`；四个大类完成拆分或判定：`CharacterActionData`（684→146 行 + 10 个同包顶层类型）、`ResourceDrivenActionHandler`（707→554 行 + 2 个新类）、`ActionStateMachine`（716→680 行 + 2 个新类）、`ModDamageSpec` 判定不拆（内聚的不可变规格）。

### 文档

- 新增文档站（`docs/index.html`，带侧边栏导航）：技术文档、实体开发文档。
- README 改为项目介绍与开源协议；原 README 的开发者内容重写进技术文档。
- 更新日志从本版本重新建立。
- 文档站改为 `web/` 模块（Spring Boot + Java），Markdown 请求时实时渲染，菜单由后端 `/api/docs` 提供（`DocCatalog` 为唯一来源）；系统详解按模块拆成 11 篇。
- **全量同步（2026-09-24，两轮）**：按 20+ 个"结构整理中改过名的符号"扫描全部文档面（根目录 Markdown、`docs/**`、`web/**` 的源码与手写页），修正此前累积的滞后——`CHARACTER_SYSTEM.md`/`CHARACTER_IMPLEMENTATIONS.md` 的旧监听类名与旧路径、`entity-ai-goal-guide.md` 提到的已删除测试实体与旧资源路径、`README.md` 指向已不存在静态页的链接、`web/README.md` 路由表、站点首页指向已删除 `/technical.html` 的死链卡片，以及 `CHANGELOG` 的待办清单（重写为四条真实未决项）。

### 已知待办

- 客户端手感需实机确认：本轮把动作与按键相关类整体迁到 `client.combat.{action,state}`，已通过编译与冒烟加载（14 个 Mixin 全部注入），但按键与动作手感需在游戏中实测。
- `textures/skill/raiden_shogun_skill.png` 资源缺失（客户端日志会报 Missing resource，与本次结构整理无关）。
- `ClaymoreCharacter` 暂无子类（第四个武器类别的结构占位）；`MaskGenerator` 是生成飘字遮罩贴图的开发工具（带 `main()`），都非死代码，保留。
- 命令行执行 `runClient` 需注意 `build.gradle` 里的 `-XX:+AllowEnhancedClassRedefinition` 是 JBR 专有参数：用 IDEA（自带 JBR）可直接运行，用命令行 JDK 会拒绝启动。


---

## 新版本模板

```markdown
## x.y.z — YYYY-MM-DD

### 新增
- 

### 变更
- 

### 修复
- 

### 兼容性
- 需要版本迁移的存档 / 数据包 / 网络协议变化
```

# Photon2 使用指南（Mod 开发向 / Java 为主）

> 面向：把 Photon2 当作**模组依赖**来用，而不是只在游戏里点点编辑器的人。
> 官方文档站：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/>
> 本文写作对象版本：**本仓库实际依赖的 Photon `26.2.2.3` + LDLib2 `26.2.2.41.a`（MC `26.2` / NeoForge `26.2.0.88` / Java 25）**。
> 官方文档站描述的是 MC `1.21.1` / Photon `2.2.x`。类名、概念、编辑器 UI 基本一致，但**资源定位类型已经改名**（详见 [§12 版本差异](#12-版本差异与迁移注意)）。本文所有类名/方法名均取自本机 Gradle 缓存中的 `photon-neoforge-26.2-26.2.2.3-sources.jar`，与文档站不一致处以 jar 为准。

---

## 目录

1. [这份指南的定位](#1-这份指南的定位)
2. [心智模型：Authored 与 Runtime](#2-心智模型authored-与-runtime)
3. [依赖接入](#3-依赖接入)
4. [共用基础：编辑器与项目](#4-共用基础编辑器与项目)
5. [共用基础：FX 层级、Emitter 与数值函数](#5-共用基础fx-层级emitter-与数值函数)
6. [共用基础：Timeline / Shader / 后处理](#6-共用基础timeline--shader--后处理)
7. [Java API 全景](#7-java-api-全景)
8. [最小可运行示例](#8-最小可运行示例)
9. [三种 Executor 与生命周期](#9-三种-executor-与生命周期)
10. [运行时数据注入（RuntimeValue）](#10-运行时数据注入runtimevalue)
11. [Timeline Signal 与后处理](#11-timeline-signal-与后处理)
12. [版本差异与迁移注意](#12-版本差异与迁移注意)
13. [从服务端触发特效](#13-从服务端触发特效)
14. [分发：把 FX 打进模组 jar](#14-分发把-fx-打进模组-jar)
15. [扩展 Photon](#15-扩展-photon)
16. [调试与排错清单](#16-调试与排错清单)
17. [性能清单](#17-性能清单)
18. [参考](#18-参考)

---

## 1. 这份指南的定位

Photon2 是 KilaBash（LowDragMC）的实时 VFX 工具链：粒子/Trail/Beam 发射器、非线性 Timeline、Shader Graph 材质、图驱动的后处理。它本质上是一个**游戏内编辑器**——你先把效果"画"出来，导出成数据文件，再用 Java 在运行时播放它。

所以 Mod 开发里 Photon 的用法分两半：

| 你做的事 | 在哪做 |
| --- | --- |
| 效果本身（长什么样、怎么动） | Photon 编辑器（游戏内），产出 `.fx` / `.fxpack` |
| 什么时候播、播在哪、参数怎么变 | **Java（本文重点）** |

**Java 侧不做特效**。不要试图用代码拼粒子——那是编辑器的工作。Java 侧的职责就三件事：

1. **加载**：`FXHelper.getFX(id)` 拿到 `FX` 定义。
2. **播放**：`fx.createRuntime()` → 用一个 Executor 把它 `emit()` 到世界里。
3. **干预**：往运行时的 `RuntimeValue` 槽里写值、查对象、改 Transform、收 Timeline 信号。

一个贯穿全文的硬约束：**Photon 的播放与渲染 API 全部是客户端的**。`FX`、`FXRuntime`、Executor、粒子对象、Shader、后处理都活在客户端。任何被 Dedicated Server 强制加载的类里，都不要直接引用这些类型——用 `@EventBusSubscriber(value = Dist.CLIENT)`、客户端专属类 + `DistExecutor`/`FMLEnvironment` 判断来隔离。

---

## 2. 心智模型：Authored 与 Runtime

这是理解 Photon 最重要的一张图：

```
.fxproj / .fx  ──加载──>  FX（定义：对象树 + Timeline，近似不可变、可共享）
                            │
                            │  fx.createRuntime()      ← 每次播放一份独立副本
                            ▼
                        FXRuntime（可播放实例：对象树 + TimelinePlayer + Runtime 槽）
                            │
                            │  runtime.emit(executor)  ← 把每个对象交给粒子引擎
                            ▼
                        客户端 ParticleEngine（负责 tick / render / 回收）
                            ▲
                            │  executor: IEffectExecutor（提供 Level，收 tick/frame 回调）
```

两条铁律：

- **Authored 数据（编辑器里设的）是共享的**。一个 `FX` 可以被并发播放很多次，互不影响，因为每次 `createRuntime()` 都拿到一份独立的对象数据。
- **运行时改值只写 Runtime 槽，不碰 Authored**。Timeline 和 Java 写的是同一批槽，没有 override 时自动回退到编辑器里设的 Authored 值。

> 推论 A：同一关卡里 100 个火把用同一个 `mymod:torch_flame`，是 100 个 Runtime，1 份定义。
> 推论 B：**Timeline 与 Java 抢同一个槽时，后写的赢**。Timeline 每 tick/每帧都在写，所以 Java 一次性 `set()` 常常"看起来没生效"——这是最常见的坑，见 [§10](#10-运行时数据注入runtimevalue)。

---

## 3. 依赖接入

本仓库（`MineGenshin-26.2`）**已经接好了**，`gradle.properties`：

```properties
minecraft_version=26.2
neo_version=26.2.0.88
photon_version=26.2.2.3
ldlib2_version=26.2.2.41.a
```

`build.gradle`：

```groovy
repositories {
    maven { url = "https://maven.firstdark.dev/snapshots" }   // LDLib2 / Photon
}

dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:${ldlib2_version}")
    implementation("com.lowdragmc.photon:photon-neoforge-${minecraft_version}:${photon_version}") {
        transitive = false      // Photon 的传递依赖（LDLib2）由上一行显式提供
    }
}
```

新项目照抄时注意三点：

- **Photon 与 LDLib2 必须版本线匹配**。文档站的 `2.2.x` 数字对 1.21.1，本仓库的 `26.2.x` 数字对 MC 26.2。不要混搭，也不要把文档站的版本号抄进 26.2 的项目。
- 依赖声明用 `implementation` 就够（Photon 的 API 是运行时提供的）。
- Photon 的 `transitive = false` 意味着**你必须自己显式声明 LDLib2**。

另外提醒：Photon 2.2.0 起把编辑器分成了 `LDLib2` 编辑器框架 + Photon 插件，所以**LDLib2 不是可选依赖**。

---

## 4. 共用基础：编辑器与项目

这一节是"必须懂才能写 Java"的最小集，尤其**命名约定**——Java 靠名字找对象。

### 4.1 打开编辑器

```
/photon_editor
```

只能**单人世界**里开（要读写本地项目文件）。

### 4.2 六块面板

| 面板 | 作用 |
| --- | --- |
| FX Hierarchy | 创建/命名/父子/排序 FX 对象 |
| Scene | 实时预览、Gizmo、播放控制 |
| Inspector | 当前对象的一切配置 |
| Resources | Material / Graph / Curve / Gradient / Color / Mesh 等可复用资源 |
| Timeline | 编排与属性动画 |
| History | 撤销 |

### 4.3 三种文件，别混用

| 文件 | 是什么 | 什么时候用 |
| --- | --- | --- |
| `.fxproj` | **可编辑项目**（含项目元数据 + FX 定义） | 编辑器里编辑保存。**不要发布** |
| `.fx` | **运行时定义**（压缩 NBT），资源只存引用 | 依赖（材质/图片/Graph）已经在你的 mod 或资源包里 |
| `.fxpack` | **自包含 Resource Pack（zip）** | 需要连依赖一起分发 |

导出：**File → Export → FX** 或 **File → Export → FX Pack**。

### 4.4 资源路径约定

导出的 `.fx` 运行时位置：

```
assets/<namespace>/fx/<path>.fx
```

所以 `mymod:combat/hit` 对应：

```
assets/mymod/fx/combat/hit.fx
```

**传给 Java 的 `Identifier` 不带 `fx/` 前缀，也不带 `.fx` 后缀。**

一个 `.fx` 引用的其它资源（材质、Graph、Mesh、贴图）大致落在：

```
assets/<namespace>/textures/...
assets/<namespace>/models/...
assets/<namespace>/shaders/...
assets/ldlib2/resources/<provider>/<resource>.<type>.nbt
```

`<type>` 形如 `material` / `shader_graph` / `shader_function` / `fullscreen_graph` / `render_graph` / `mesh`。

> 路径**必须全小写、不含空格**。这是 Minecraft 资源系统的硬要求，也是"效果不显示"最高频的原因。

### 4.5 `.fxpack` 的两条规则

- **Pack 文件名 = namespace**，导出时输入的效果名 = path。
- 放进 `<gameDir>/photon/fxpacks/`，下次资源重载后作为客户端资源包挂载，里面的效果会出现在 `FXHelper.listAllFX()` 里。

Windows 上注意文件锁：已挂载的 pack 可能被文件句柄占住，覆盖前要先卸载/重载，否则会报 "FX Pack locked"。

### 4.6 常用命令

| 命令 | 说明 |
| --- | --- |
| `/photon_editor` | 开编辑器（单人） |
| `/photon fx <id> block <x y z> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [checkState]` | 绑到方块 |
| `/photon fx <id> entity <selector> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [autoRotate]` | 绑到实体，`autoRotate` ∈ `none/forward/look/xrot` |
| `/photon fx remove block <pos> <force> [id]` | 移除方块绑定 |
| `/photon fx remove entity <selector> <force> [id]` | 移除实体绑定 |
| `/photon_client clear_particles` | 清粒子 + Executor 缓存，并使已缓存 Runtime 失效 |
| `/photon_client clear_client_fx_cache` | 清 FX 定义缓存与列表缓存 |
| `/photon_client convert` | 转换 Photon 1 的旧文件 |
| `/photonfx list` / `test <effect> [weight]` / `clear` | 后处理调试（2.2.0+） |
| `/photon_iris status|dump|overlay on|mode auto` | Iris 兼容诊断（2.2.2+） |

**换过导出文件但游戏里还是旧效果？** `/photon_client clear_client_fx_cache`。

---

## 5. 共用基础：FX 层级、Emitter 与数值函数

### 5.1 对象类型

| 类型 | 用途 |
| --- | --- |
| Empty | 纯 Transform 容器，用来整体移动/旋转/缩放一组子对象 |
| Particle Emitter | 生成大量独立 Tile / Model 粒子 |
| Trail Emitter | 沿移动路径生成连续带状/管状几何 |
| Beam Emitter | 两点之间或 Raycast 方向的射线 |
| AraTrail Emitter | 带物理模拟的分段 Trail |
| Force Field | 对开启 External Forces 的粒子施加 directional / gravity / drag / vortex |

**每个 Runtime 都有一个恒存在的 root**（UUID 为 `FXRuntime.ROOT_UUID`，即 `new UUID(0,0)`）。没有显式父级的对象会自动挂到 root 下。

### 5.2 命名：Java 集成的地基

- 对象以 **UUID** 持久化，Timeline 也用 UUID 绑定。
- **Name 允许重复**，`findObject` 只返回第一个匹配。

所以给 Java 看的对象命名就一条纪律：**唯一、稳定、带语义**，例如 `muzzle`、`core_sparks`、`ring_02`。然后在效果资源旁边记一份名字清单。

推荐用 **Empty 作为命名控制点**——Java 只动 Empty 的 Transform，整套子对象跟着走，比逐个改 Emitter 干净得多。

### 5.3 Transform 与三种"空间"

这三个概念经常被混：

| 概念 | 决定什么 |
| --- | --- |
| Transform 父子继承 | 对象**在哪**、朝向、缩放 |
| Particle Simulation Space | 已生成粒子的位置/速度**存在哪个坐标系**（`LOCAL` / `WORLD` / `CUSTOM`） |
| ValueSpace（Velocity 等模块） | 配置里的向量按哪个基解释。**不会**改变 Simulation Space |

实践建议：附着在实体/武器上的光环用 `LOCAL`；移动源留下的烟用 `WORLD`。

### 5.4 Particle Emitter 关键参数

| 参数 | 含义 |
| --- | --- |
| `duration` | 单次循环长度（tick） |
| `looping` | 循环。**循环 Emitter 永远不会自然结束**，必须由 Executor 销毁 |
| `prewarm` | 首帧前预模拟的 tick 数（会一次性付出模拟成本） |
| `startLifetime` / `startSpeed` / `startSize` / `startRotation` | 出生值，都是函数而非固定数 |
| `startColor` | 出生色，之后还会被 Color 模块与材质相乘 |
| `maxParticles` | 同时存活上限 |
| `parallelUpdate` | 允许并行更新粒子（碰撞开启时会自动退回串行） |
| `emission` | Emission Rate / Distance Rate / Burst |
| `shape` | Dot / Box / Circle / Cone / Cylinder / Sphere / Mesh / Function |
| `physics` | gravity、碰撞、bounce、friction——**碰撞要访问世界，代价高** |

### 5.5 数值函数（NumberFunction）

Photon 里绝大多数数值字段不是 `float`，而是函数：

| 类型 | 行为 |
| --- | --- |
| `Constant` | 定值 |
| `RandomConstant` | 范围内随机 |
| `Curve` / `RandomCurve` | 按输入 time 采样 |
| `NumberFunction3` | 三个分量（size/rotation/velocity 这类 XYZ） |
| Color 系列 | `Color` / `RandomColor` / `Gradient` / `RandomGradient` / HDR 变体 |

**"time 输入"由消费者决定**：Over Lifetime 模块喂归一化 age，By Speed 模块喂重映射后的速度，Emission 喂 Emitter 时间……同一条 Curve 换个字段含义就变了。

### 5.6 模块清单（速查）

Color/Size/Rotation over Lifetime、Velocity/Force over Lifetime、Color/Size/Rotation by Speed、Lifetime by Emitter Speed、Inherit Velocity、Noise、Light、UV Animation、Trails、Sub Emitters、External Forces。

> 2.2.0 起，**Toggle 模块的 `enable` 也是 Runtime 值**——Timeline 与 Java 都能开关模块而不改配置。

### 5.7 渲染三层

```
Simulation（粒子状态） → Renderer（几何与层级） → Material（Shader/贴图/混合）
```

- **Renderer**：materials、layer（Opaque/Translucent）、orderInLayer、cull box、GPU Instancing、custom mask。
- **Material**：Texture / Sprite / Shader Graph / Custom Shader / UI Resource / Block Atlas。
- **Geometry 兼容性**：Trail/Beam 的 UV 不是 Billboard Sprite UV。读不到的数据通道**返回 0**——表现是"完全没效果"而不是编译报错。

---

## 6. 共用基础：Timeline / Shader / 后处理

### 6.1 Timeline 的 Track 类型

| Track | 控制什么 |
| --- | --- |
| Activator | 目标对象在 Clip 范围内是否 Active（不 active 就不 tick 不渲染） |
| Control | 进入 Clip 时 reset 目标子树、应用 seed、重启播放 |
| Animation | Transform 或 Emitter 的已注册 Runtime Property |
| Speed | 层级 `timeScale`（0 = 冻结，>1 = 每 tick 多个子步） |
| **Signal** | **带 `CompoundTag` 的命名事件 → Java 能收到** |
| Audio | 声音 + 音量/音高曲线 |
| Post Process | 每帧提交带权重的后处理请求 |
| Group | 只做组织与 Mute，**不产生 Transform** |

Java 开发真正要关心的只有两个：**Signal**（钩子）和 **Post Process**（随 Runtime 提交）。

### 6.2 Animation Track 写的是哪一层

Animation Property 把采样值写进目标对象上的 **`RuntimeValue` 槽**：

- 应用时 `slot.set(sampledValue)`；
- 属性被删除/Mute 时 `slot.clear()`，回退到 Authored。

**它不会修改原始 Curve 资源**，也不需要复制 FX 定义。

### 6.3 Signal 的语义

- Signal = `time` + `name` + `CompoundTag data`。
- **Track 的 Display Name 就是 channel**。
- 只在**实时正向播放**时派发，窗口是 `(上次信号 tick, 当前时间]`——所以不会重复触发。
- **编辑器 Scrub / Replay Preview 不派发**（拖进度条不会炸出一堆事件）。

### 6.4 后处理的"逐帧请求"模型

这是最容易误解的一点：后处理**不是一个全局开关**。

- Timeline 的 Post Process Clip 在范围内**每一帧**都向 `IEffectExecutor#postEffectSink()` 提交请求。
- Java 的 `PhotonPostFX.submit(...)` 同样**只对当前帧排队**；停止提交，下一帧效果就没了。
- 同一效果的多个请求会按 weight 合并。

### 6.5 Shader / GPU 数据的要点

- 新效果优先用 **Shader Graph**（Compiler 会自动启用它读到的数据通道，并适配不同渲染路径）。
- **Additional GPU Data**：内置 Channel（归一化年龄、速度、Trail 点生命等）+ 最多 **4 条 Custom Data Stream**（每条 `vec4`）。
- **Custom Stream 的 Index 就是列表顺序**，改显示名不影响 GPU 布局，但增删/移动 Stream 会改变后续 Index。
- 读不到的 Channel 一律返回 `0`（含非 Instanced CPU 路径与节点预览）。

---

## 7. Java API 全景

以下包路径与类名取自本机 `26.2.2.3` 源码 jar，可直接照抄 import。

### 7.1 核心（`com.lowdragmc.photon.client.fx`）

| 类 | 职责 |
| --- | --- |
| `FX` | 加载好的定义。`createRuntime()` / `createRuntime(true)` / `createInternalRuntime()` |
| `FXHelper` | `getFX(Identifier)` / `getFX(id, useCache)` / `listAllFX()` / `clearCache()` |
| `FXRuntime` | 一次播放实例。`emit` / `destroy` / `isFinished` / `isValid` / `setRate` / `findObject(s)` |
| `IEffectExecutor` | 播放上下文接口：提供 `Level`，收 tick/frame/信号回调 |
| `IFXEffectExecutor` | 在 `IEffectExecutor` 之上增加 offset/rotation/scale/delay/forcedDeath/allowMulti + `start()` |
| `FXEffectExecutor` | `IFXEffectExecutor` 的抽象基类，已实现配置与去重 |
| `BlockEffectExecutor` | 锚到方块 |
| `EntityEffectExecutor` | 锚到实体（含 `AutoRotate` 枚举） |

### 7.2 对象与运行时槽

| 类 | 包 | 说明 |
| --- | --- | --- |
| `IFXObject` | `...client.gameobject` | 场景对象接口：`getName` / `updatePos` / `updateRotation` / `updateScale` / `setSelfActive` / `setSelfTimeScale` / `remove` |
| `FXObject` / `EmptyFXObject` | `...client.gameobject` | 具体实现（root 是 `EmptyFXObject`） |
| `RuntimeValue<T>` | `...client.gameobject` | `get()` / `authored()` / `set()` / `clear()` / `isOverridden()` |
| `ParticleEmitter` | `...emitter.particle` | `runtime()` 返回 `ParticleRuntime` |
| `ParticleRuntime` | `...emitter.particle` | 顶层槽 + 各模块 Runtime + `customData` + `renderer` |
| `Constant` | `...data.number` | `new Constant(0.2f)` |
| `NumberFunction3` | `...data.number` | `new NumberFunction3(x, y, z)` |
| `RendererSetting.Runtime` | `...emitter.data` | `layer` / `orderInLayer` / `maskGroup` / `writeCustomMask` … |
| `CustomDataRuntime` | `...emitter.data` | `slot(stream, channel)` |

### 7.3 信号与后处理

| 类 | 包 |
| --- | --- |
| `PhotonSignals`（`Listener#onSignal`） | `com.lowdragmc.photon.client.fx.timeline` |
| `PhotonPostFX` | `com.lowdragmc.photon.client.postfx` |
| `PostEffectStack` | `com.lowdragmc.photon.client.postfx.runtime` |

### 7.4 网络包（服务端也能安全引用）

| 类 | 包 |
| --- | --- |
| `BlockEffectCommand` / `EntityEffectCommand` | `com.lowdragmc.photon.command` |
| `RemoveBlockEffectCommand` / `RemoveEntityEffectCommand` | `com.lowdragmc.photon.command` |

### 7.5 注册表

`PhotonRegistries`：`FX_OBJECTS`、`MATERIALS`、`NUMBER_FUNCTIONS`、`SHAPES`、`MODEL_SOURCES`、`TIMELINE_TRACKS`、`ANIMATED_PROPERTIES`。

---

## 8. 最小可运行示例

```java
package com.example.client;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class FirstFx {
    private FirstFx() {}

    /** 在指定方块中心播放 mymod:fire。必须在客户端主线程调用。 */
    public static void playAt(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        Identifier id = Identifier.fromNamespaceAndPath("mymod", "fire");
        FX fx = FXHelper.getFX(id);           // 使用缓存；加载/解析失败返回 null
        if (fx == null) return;               // 缺失资源是正常结果，不要当异常

        BlockEffectExecutor executor = new BlockEffectExecutor(fx, level, pos);
        executor.setOffset(0, 0.5, 0);
        executor.setScale(1, 1, 1);
        executor.start();
    }
}
```

就这么多。`start()` 内部做了：`fx.createRuntime()` → 设 root Transform → `runtime.emit(this, delay)`。

**几个必须养成的习惯：**

- `getFX` 失败返回 `null`，照常处理（资源包缺失、命名空间写错都走这条路）。
- 一定要判 `Minecraft.getInstance().level != null`。
- 只在客户端主线程调用。

---

## 9. 三种 Executor 与生命周期

### 9.1 选择

| 场景 | 用什么 |
| --- | --- |
| 效果绑在方块上（机器、祭坛、地面标记） | `BlockEffectExecutor` |
| 效果绑在实体上（玩家、怪物、抛射物） | `EntityEffectExecutor` |
| 自己完全拥有生命周期 / 不绑定任何锚点 | 自定义 `IEffectExecutor` |

### 9.2 BlockEffectExecutor

```java
FX fx = FXHelper.getFX(Identifier.fromNamespaceAndPath("mymod", "block_aura"));
if (fx != null) {
    BlockEffectExecutor executor = new BlockEffectExecutor(fx, level, pos);
    executor.setOffset(0, 0.5, 0);      // 相对方块中心
    executor.setRotation(0, 90, 0);     // 角度制（方便重载）
    executor.setScale(1.25, 1.25, 1.25);
    executor.setDelay(4);               // tick
    executor.setCheckState(true);       // BlockState 精确变化时结束
    executor.setForcedDeath(false);     // 锚点没了：残留自然消散
    executor.setAllowMulti(false);      // 同一锚点去重同一效果
    executor.setOnFinished(runtime -> onAuraEnded());
    executor.start();
}
```

结束条件：区块卸载、方块类型改变，或开启 `checkState` 后完整 `BlockState` 改变。

### 9.3 EntityEffectExecutor

```java
var executor = new EntityEffectExecutor(
        fx, level, entity, EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, -0.25, 0);
executor.setForcedDeath(true);          // 实体死了立刻清残留
executor.start();
```

Root 每帧跟随**插值后的 Eye Position**。

| AutoRotate | 行为 |
| --- | --- |
| `NONE` | 只用你设的 rotation |
| `FORWARD` | 按实体 forward 向量 |
| `LOOK` | 按视线向量 |
| `XROT` | 按实体视觉 yaw（Photon 的 X 朝向约定） |

### 9.4 自定义 Executor

```java
package com.example.client;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class OwnedExecutor implements IEffectExecutor {
    private final Level level;
    private final RandomSource random = RandomSource.create(1234L); // 固定种子 → 可复现
    private FXRuntime runtime;

    public OwnedExecutor(Level level) {
        this.level = level;
    }

    @Override public Level getLevel() { return level; }
    @Override public RandomSource getRandomSource() { return random; }

    /** 每 tick，每对象一次。低频逻辑放这里。 */
    @Override
    public void updateFXObjectTick(IFXObject object) {
        if (runtime == null || object != runtime.root) return;   // 只想每 Runtime 跑一次
        if (shouldStop()) {
            runtime.destroy(false);      // false = 残留自然消散
        }
    }

    /** 每帧，每对象一次。高频平滑逻辑放这里（拿得到 partialTicks）。 */
    @Override
    public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        if (runtime == null || object != runtime.root) return;
        runtime.root.updatePos(interpolatedPosition(partialTicks));
    }

    /** Timeline Signal 钩子。 */
    @Override
    public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
        // 只在实时正向播放时触发
    }

    public void start(FX fx) {
        runtime = fx.createRuntime();
        runtime.emit(this);          // 或 emit(this, delayTicks)
    }

    public void stop(boolean force) {
        if (runtime != null) runtime.destroy(force);
    }

    // 占位实现，替换成你的逻辑
    private boolean shouldStop() { return false; }
    private Vector3f interpolatedPosition(float partialTicks) { return new Vector3f(); }
}
```

要点：

- **`updateFXObjectTick` / `updateFXObjectFrame` 会对每个已 emit 的对象各调一次**。只想要"每 Runtime 一次"就判 `object == runtime.root`。
- 返回**稳定的 `RandomSource`**，编辑器里用 Random 函数的 Authored 效果才能在重播时复现。
- 自己创建的 Runtime 自己负责 `destroy`。

### 9.5 FXRuntime 生命周期

| 方法 | 含义 |
| --- | --- |
| `fx.createRuntime()` | 常规。播放用这个 |
| `fx.createRuntime(true)` | 构建前深拷贝 Authored 数据 |
| `fx.createInternalRuntime()` | 直接用 Raw `FXData`。**只给编辑器/受控内部用，游戏逻辑别碰** |
| `runtime.emit(executor)` / `emit(executor, delayTicks)` | 开始播放，重置 Timeline |
| `runtime.destroy(false)` | 停止未来工作，残留自然消散 |
| `runtime.destroy(true)` | 立即移除可见残留 |
| `runtime.isFinished()` | Timeline 无未来内容 **且** 没有对象在 Playing |
| `runtime.isAlive()` | `!isFinished()` |
| `runtime.isValid()` | 已 emit、未 destroy、**且仍被粒子引擎跟踪** |
| `runtime.setRate(float)` | 整个播放的速度（对象模拟 + Timeline 主时钟一起缩放） |

**`isValid()` 是缓存 Runtime 时唯一正确的存活检查。**

切换世界、`/photon_client clear_particles`、或别的 mod 清空粒子时，粒子引擎会直接把粒子丢掉，Runtime 根本没机会走到 `isFinished()`。`isValid()` 用 host generation + root heartbeat 检测这种情况，O(1)，可以每 tick 调。

```java
if (cachedRuntime == null || !cachedRuntime.isValid()) {
    cachedRuntime = fx.createRuntime();
    cachedRuntime.emit(executor);
}
```

几个易错点：

- **首次 `emit()` 前、`destroy()` 之后，`isValid()` 都是 false。**
- 非强制 `destroy` 后，要等可见残留消散，`isFinished()` 才变 true。
- **循环 Emitter 永远不会自然结束**——必须显式 destroy。
- `emmit(...)` 是老的拼写错误版，已 `@Deprecated`，统一用 `emit(...)`。
- `createRuntime()` 的 Runtime 之间互不影响，但**不要永久缓存一个 `FXRuntime`**——缓存 `Identifier` 或 `FX` 没问题。

---

## 10. 运行时数据注入（RuntimeValue）

### 10.1 语义

`RuntimeValue<T>` 是"带回退的覆盖槽"：

| 方法 | 结果 |
| --- | --- |
| `get()` | 有 override 返回 override，否则返回 Authored 值 |
| `authored()` | 永远返回 Authored 值（即使被覆盖） |
| `set(T)` | 写 override |
| `clear()` | 删除 override，恢复 Authored |
| `isOverridden()` | 是否有 override |
| `setRaw(Object)` | 给 Timeline 通用路径用的，**集成代码别用** |

`null` 表示"没有 override"，`set(null)` 不是清空语义——要清空请用 `clear()`。

### 10.2 完整示例

```java
package com.example.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;

public final class PhotonInjection {
    private PhotonInjection() {}

    /** 建议在 createRuntime() 之后、emit() 之前调用。 */
    public static void configure(FXRuntime runtime) {
        IFXObject object = runtime.findObject("sparks");
        if (!(object instanceof ParticleEmitter emitter)) return;   // 名字不保证唯一，必须判类型

        var values = emitter.runtime();

        // 顶层槽
        values.startSpeed.set(new Constant(0.2f));
        values.startLifetime.set(new Constant(40));
        values.startSize.set(new NumberFunction3(
                new Constant(0.3f), new Constant(0.3f), new Constant(0.3f)));
        values.maxParticles.set(256);
        values.looping.set(false);

        // 模块槽（值 + 开关是两个独立 override）
        values.emission.emissionRate.set(new Constant(24f));
        values.physics.enable.set(true);
        values.physics.gravity.set(new Constant(0.03f));
        values.colorOverLifetime.enable.set(true);

        // Custom Data：Stream 0 / Channel 0（X/R）
        values.customData.slot(0, 0).set(new Constant(0.85f));

        // Renderer override（值相同仍能合批）
        values.renderer.layer.set(RendererSetting.Layer.Translucent);
        values.renderer.orderInLayer.set(20);
        values.renderer.writeCustomMask.set(true);
        values.renderer.maskGroup.set("wiki_target");
    }

    /** 全部恢复编辑器里设的值。 */
    public static void restoreAuthored(FXRuntime runtime) {
        if (runtime.findObject("sparks") instanceof ParticleEmitter emitter) {
            emitter.runtime().clear();      // 清掉所有槽
        }
    }
}
```

### 10.3 可写的槽（节选，均来自 `ParticleRuntime`）

**顶层**：`startColor`、`startDelay`、`startLifetime`、`startSpeed`、`startSize`、`startRotation`、`duration`、`prewarm`、`maxParticles`、`looping`、`parallelUpdate`。

**模块 Runtime 字段名**（`values.` 后面接这个）：

`emission`、`shape`、`physics`、`sizeOverLifetime`、`rotationOverLifetime`、`forceOverLifetime`、`externalForces`、`lights`、`colorOverLifetime`、`velocityOverLifetime`、`inheritVelocity`、`lifetimeByEmitterSpeed`、`colorBySpeed`、`sizeBySpeed`、`rotationBySpeed`、`noise`、`uvAnimation`、`trails`、`subEmitters`、`customData`、`renderer`。

模块内的具体槽名举例：

| 路径 | 类型 |
| --- | --- |
| `values.emission.emissionRate` | `NumberFunction` |
| `values.emission.distanceRate` | `NumberFunction` |
| `values.physics.enable` / `.hasCollision` / `.removedWhenCollided` | `Boolean` |
| `values.physics.gravity` / `.friction` / `.bounceChance` | `NumberFunction` |
| `values.colorOverLifetime.color` | 颜色函数 |
| `values.sizeOverLifetime.size` | `NumberFunction3` |
| `values.velocityOverLifetime.linear` / `.orbital` / `.offset` | `NumberFunction3` |
| `values.velocityOverLifetime.radial` / `.speedModifier` | `NumberFunction` |
| `values.noise.frequency` | `Float` |
| `values.uvAnimation.cycle` | `Float` |
| `values.trails.ratio` | `Float` |
| `values.renderer.orderInLayer` | `Integer` |
| `values.renderer.useGPUInstance` | `Boolean` |
| `values.renderer.writeCustomMask` | `Boolean` |
| `values.renderer.maskAlphaCutoff` | `Float` |
| `values.customData.slot(stream, channel)` | `NumberFunction` |

任何槽都可以直接 `.clear()` 回到 Authored。

### 10.4 三个必须知道的坑

**坑 1：Timeline 和 Java 抢同一个槽。**
Timeline 的属性动画每 tick/每帧都在写同一个 `RuntimeValue`，一个采样周期内**最后写入的生效**。所以 Java 只 `set()` 一次、Timeline 又在驱动同一个槽时，你的值会"看起来被忽略"。三种正解：

- 换一个 Timeline 没在驱动的槽 / Custom Data Stream；
- 在每次 Timeline 求值之后再写（持续更新）；
- 让一个系统独占该值（删掉/Mute 那条 Timeline 绑定）。

**坑 2：角度 vs 弧度。**
`IFXEffectExecutor.setRotation(x, y, z)` 的重载接受**角度**；`IFXObject.updateRotation(Vector3f)` 与 JOML 的 `rotationXYZ` 是**弧度**。混用就是经典的 57.3 倍旋转错误。

**坑 3：不要改共享配置。**
`runtime.findObject(...)` 拿到的对象属于这个 Runtime 的副本，写它的 `runtime()` 槽是安全的。但**直接改已加载的 `FX` 定义或 Internal Runtime 的对象**会影响后续所有播放、破坏渲染合批假设、并与 Timeline 的 restore 打架。

### 10.5 注意时机与线程

- 能提前定的值，在 `createRuntime()` 之后、`emit()` 之前设。
- 跟随游戏状态的值在 **Client Tick** 更新；只有插值 Transform 或渲染期数据才每帧更新。
- **所有 Runtime/Renderer 修改都必须在客户端主线程**。网络回调、异步线程里想改就调度回主线程。

---

## 11. Timeline Signal 与后处理

### 11.1 查对象与改 Transform

```java
IFXObject obj   = runtime.getSceneObject(uuid);   // UUID 是稳定标识
IFXObject named = runtime.findObject("muzzle");   // 第一个同名
List<IFXObject> all = runtime.findObjects("spark"); // 全部同名（找不到返回空列表）
IFXObject root  = runtime.getRoot();              // 恒存在的 Empty root

obj.updatePos(new Vector3f(x, y, z));
obj.updateRotation(new Quaternionf().rotationXYZ(rx, ry, rz));  // 弧度
obj.updateScale(new Vector3f(sx, sy, sz));
```

未知 UUID / Name 返回 `null`（`findObjects` 返回空列表）。

Transform 遵循 Authored 父子层级：动 Empty 父级会带动子孙，已出生粒子是否继续跟随取决于 Simulation Space。

### 11.2 接 Timeline Signal

**方式一：在自定义 Executor 里覆写**（每个效果自己的反应）：

```java
@Override
public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
    if ("combat".equals(channel) && "hit".equals(name)) {
        spawnHitSpark(data.getFloatOr("power", 1f));
    }
}
```

**方式二：全局监听**（跨效果的统一日志/音效/UI）：

```java
PhotonSignals.Listener listener =
        (effect, channel, name, data, time) -> {
            // 客户端主线程；要延后使用请 copy 一份 CompoundTag
        };

PhotonSignals.register(listener);
// Mod 客户端关闭或 Owner 销毁时：
PhotonSignals.unregister(listener);
```

要点：

- 回调参数是 `(effect, channel, name, data, time)`；`channel` 就是 Track 的 Display Name，`time` 是主时钟 tick。
- **全局 Listener 必须 unregister**，否则泄漏 Owner。
- Scrub / Replay Preview 期间不派发，只有实时正向播放才触发。
- 回调里别做重活；`CompoundTag` 需要留用就拷贝。

### 11.3 后处理 Java API

```java
import com.lowdragmc.photon.client.postfx.PhotonPostFX;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import org.joml.Vector4f;
import java.util.Map;

IResourcePath effect = PhotonPostFX.parsePath("built-in:wiki_tint_effect");
PhotonPostFX.submit(effect, Map.of(
        "Tint",     new Vector4f(1.0f, 0.35f, 0.2f, 1.0f),
        "Strength", 0.8f
), 0.65f);   // weight ∈ (0, 1]
```

关键点：

- **每帧都要提交**。这是请求式 API，不是持久开关。停止提交 → 下一帧效果消失。
- 参数 key 是 Graph 里暴露的 **Display Name**；值支持 `Float` / `Vector2f/3f/4f` / 整数颜色 / `Boolean` / 以及 Graph Schema 声明的其它类型。
- `parsePath` 接受完整 `type(path)` 形式，或内置资源名；`listEffectPaths()` 返回可请求的路径（与 `/photonfx list` 一致）。
- **要在客户端渲染帧路径里提交，不要在 Server Tick 里**。Tick 里算好的状态要存下来，渲染时用插值后的值提交。
- 同一效果的多个请求通常合并：weight 按 `1 - Π(1 - wᵢ)` 组合；可 Lerp 参数按 weight 升序混合，不可 Lerp 的取最高 weight。只有各请求**必须独立执行**时才加保留参数 `Independent=true`。
- 独立场景（离屏预览之类）可以让 Executor 的 `postEffectSink()` 返回自己的 `PostEffectStack`，并往那个 stack 提交。`PhotonPostFX.submit(...)` 永远进全局 World Stack。
- 想先手工验证：`/photonfx test <effect> [weight]` 会每帧提交直到 `/photonfx clear`。

---

## 12. 版本差异与迁移注意

**这一节是本仓库最容易踩雷的地方**，因为官方文档站写的是 1.21.1。

### 12.1 资源定位类型改名

MC 26.2 里 **`ResourceLocation` 已改名 `Identifier`**（`net.minecraft.resources.Identifier`）。

| 官方文档（1.21.1 / 2.2.x） | 本仓库（26.2.2.3） |
| --- | --- |
| `ResourceLocation.parse("mymod:fire")` | `Identifier.parse("mymod:fire")` |
| `ResourceLocation.fromNamespaceAndPath("mymod", "fire")` | `Identifier.fromNamespaceAndPath("mymod", "fire")` |
| — | `Identifier.tryParse(str)`（失败返回 null） |

`parse` / `tryParse` / `fromNamespaceAndPath` 在 Photon 26.2.2.3 源码中都在实际使用（例如 `FXHelper.loadFX` 用的是 `Identifier.fromNamespaceAndPath`），可以放心用。

### 12.2 `emit` 拼写

`emmit(...)` 只是保留源码兼容，已 `@Deprecated`，与 `emit(...)` 行为一致。新代码统一写 `emit`。

### 12.3 前向兼容建议

- **以 jar 为准，不以文档站为准。** IDE 里 `Ctrl+B` 跳进 Photon 的类最快。
- 本机源码 jar 位置（可直接解压查看）：

  ```
  ~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/photon-neoforge-<mc>/<ver>/**/photon-neoforge-<mc>-<ver>-sources.jar
  ```

- `.fx` 文件格式带版本号（文档站写 `FXProject.VERSION = 5`），旧文件由 Data Fixer 迁移。**不要手工改 NBT 字段**——用当前编辑器打开旧项目、另存、重新导出。

---

## 13. 从服务端触发特效

这是 Mod 开发里最常见、也最容易做错的需求：**特效是客户端的，但"什么时候放"通常是服务端的逻辑。**

### 13.1 别自己造轮子：Photon 已经有两个现成的 payload

Photon 把 `/photon fx block ...` 和 `/photon fx entity ...` 做成了网络包，服务端命令就是往客户端发的：

```java
public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(Photon.MOD_ID);
    registrar.playToClient(BlockEffectCommand.TYPE, BlockEffectCommand.CODEC, BlockEffectCommand::execute);
    registrar.playToClient(EntityEffectCommand.TYPE, EntityEffectCommand.CODEC, EntityEffectCommand::execute);
    // ... remove 命令同理
}
```

客户端收到后会：`FXHelper.getFX(location)` → 建 Executor → 应用 offset/rotation/scale/delay → `start()`。

**所以你的 mod 可以直接 send 这两个包**，不需要自己写一套。

### 13.2 让实体身上放一个效果

```java
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.command.EntityEffectCommand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

/** 服务端调用：让 target 客户端播放 mymod:slash_trail。 */
public static void playSlash(Entity target) {
    if (target.level().isClientSide()) return;

    var command = new EntityEffectCommand();
    command.setLocation(Identifier.fromNamespaceAndPath("mymod", "slash_trail"));
    command.setEntities(List.of(target));
    command.setOffset(new Vec3(0, 0.6, 0));
    command.setScale(new Vec3(1, 1, 1));
    command.setAutoRotate(EntityEffectExecutor.AutoRotate.LOOK);
    command.setForcedDeath(true);
    command.setAllowMulti(true);

    // 只发给能看到它的玩家（服务端 → 客户端）
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, command);
}
```

> `PacketDistributor` 还有 `sendToAllPlayers` / `sendToPlayer(ServerPlayer, ...)` / `sendToPlayersTrackingChunk(level, chunkPos, ...)` 等重载，按需要选。`EntityEffectCommand` 与 `BlockEffectCommand` 的 `@Setter` 都作用在 `protected` 字段上，所以 `setLocation` / `setOffset(Vec3)` / `setRotation(Vec3)` / `setScale(Vec3)` / `setDelay(int)` / `setForcedDeath(boolean)` / `setAllowMulti(boolean)` / `setEntities(List<Entity>)` / `setAutoRotate(...)` 都是公开方法。

### 13.3 让方块位置放一个效果

```java
import com.lowdragmc.photon.command.BlockEffectCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public static void playAtBlock(ServerLevel level, BlockPos pos) {
    var command = new BlockEffectCommand();
    command.setLocation(Identifier.fromNamespaceAndPath("mymod", "altar_burst"));
    command.setPos(pos);
    command.setOffset(new Vec3(0, 0, 0));       // 相对方块中心
    command.setDelay(0);
    command.setForcedDeath(false);
    command.setAllowMulti(false);
    command.setCheckState(true);                // BlockState 变了就结束
    PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), command);
}
```

客户端会先检查 `level.isLoaded(pos)`。

### 13.4 什么时候才需要自己写 payload

上面两个包覆盖不了的情况：

- 需要在播放**之前**注入运行时参数（对应某个玩家的颜色、强度……）——payload 里没有参数位；
- 需要在客户端做**条件判断**后再决定播不播；
- 要播放的效果需要在特定 **PostEffectStack** 或自定义 Executor 里跑。

那就自己写一个 `CustomPacketPayload`，`playToClient` 注册，客户端 handler 里：

```java
public static void handle(MyFxPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {                       // 回到主线程
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var fx = FXHelper.getFX(payload.fxId());
        if (fx == null) return;
        var runtime = fx.createRuntime();
        // …在这里注入运行时参数…
        runtime.emit(new BlockEffectExecutor(fx, level, payload.pos()));
    });
}
```

`enqueueWork` 很关键——payload handler 默认不在主线程。

### 13.5 移除效果

`RemoveBlockEffectCommand` / `RemoveEntityEffectCommand` 是 public 的，字段同样有 setter（资源 id 可选，`force=true` 立即清残留）。用法和上面一样，`PacketDistributor` 发过去即可。

---

## 14. 分发：把 FX 打进模组 jar

### 14.1 两条路线

| 路线 | 做法 | 适用 |
| --- | --- | --- |
| **A. 资源直接进 jar**（推荐） | 把 `.fx` 及其依赖资源放进 `src/main/resources/` | 效果是你 mod 的一部分，必装 |
| **B. 发布 `.fxpack`** | 作为可选资源包让玩家丢进 `photon/fxpacks/` | 玩家可选内容、地图作者扩展 |

**永远不要分发 `.fxproj`**——那是编辑文件。

### 14.2 路线 A 的目录

最简单的做法：在编辑器里导出 **FX Pack**，然后把 pack 里的 `assets/` 目录整份拷进：

```
src/main/resources/assets/<namespace>/fx/<path>.fx
src/main/resources/assets/<namespace>/textures/...
src/main/resources/assets/<namespace>/models/...
src/main/resources/assets/<namespace>/shaders/...
src/main/resources/assets/ldlib2/resources/<provider>/<resource>.<type>.nbt
```

FX Pack 会**跟随资源引用自动收集依赖**（材质、Graph、Mesh、贴图、Shader），比手工挑文件可靠得多。共享资源按内容寻址，只存一份。

然后再从 Java 里用 `Identifier.fromNamespaceAndPath("<namespace>", "<path>")` 加载，和从资源包加载完全等价（`FXHelper` 走的是客户端 ResourceManager，对 jar / 资源包 / 已挂载 fxpack 一视同仁）。

### 14.3 打包前的自检

1. 导出 `.fx` 的 id 与 namespace 对不对；
2. 日志里有没有 `Failed to load fx ...` 或缺失的材质/Graph/Mesh/贴图路径（Photon 会打 warn）；
3. **路径全小写、无空格**；
4. 资源重载 + `/photon_client clear_client_fx_cache` 后再看；
5. 颜色发紫发黑 = 典型的依赖没收集全 → 改用 `.fxpack` 导出再拷。

### 14.4 一个真实的分工建议

- 特效作者在编辑器里做完 → **导出 FX Pack** → 把 `assets/` 提给开发。
- 开发把资源放进 `src/main/resources/`，在 Java 里只引用 `Identifier`。
- **约定一份名字清单**（`muzzle`、`sparks`、`root_hit`……）作为资源与代码之间的接口。

---

## 15. 扩展 Photon

Photon 的 Authored 模型是注册表驱动的，**这些是客户端注册**（不是服务端 gameplay registry）：

| 扩展点 | 注册表 | 类型 |
| --- | --- | --- |
| FX Object | `PhotonRegistries.FX_OBJECTS` | `FXObjectType`（静态单例） |
| Material | `PhotonRegistries.MATERIALS` | `Supplier<IMaterial>` |
| Number Function | `PhotonRegistries.NUMBER_FUNCTIONS` | `Supplier<NumberFunction>` |
| Shape | `PhotonRegistries.SHAPES` | `Supplier<IShape>` |
| Model Source | `PhotonRegistries.MODEL_SOURCES` | `Supplier<IModelSource>` |
| Timeline Track | `PhotonRegistries.TIMELINE_TRACKS` | `TrackType`（静态单例） |
| Animated Property | `PhotonRegistries.ANIMATED_PROPERTIES` | `AnimatedPropertyType` |

内置实现都用 `@LDLRegisterClient` 注解注册（`registry = "photon:fx_object"` 这样的名字），自定义时照抄这个模式。

几个现实提醒：

- 自定义 FX Object 要提供 `FXObjectType`（creator + icon + animatable properties）、Codec/Copy 行为、运行时行为、序列化兼容性。
- 自定义 Timeline Track **还需要 `TrackEditor`**，只注册数据不会有 UI。
- 加自定义渲染/Shader 之前，先确认真的做不到——Shader Graph + Custom Data 能覆盖很多需求，而扩展渲染路径的维护成本很高。

---

## 16. 调试与排错清单

### 16.1 按症状查

| 症状 | 先查什么 |
| --- | --- |
| **FX 加载不出来（`getFX` 返回 null）** | 路径 `assets/<ns>/fx/<path>.fx`、namespace 大小写、pack 是否挂载、日志里的 `FXHelper` warning |
| **Shader 全黑 / 崩** | Core Shader / Graph 编译日志 → 重载资源 → 清客户端 FX 缓存 |
| **切世界后 Runtime 不动了** | `isValid()` 变 false 是**预期行为**，为新 `ClientLevel` 建新 Runtime |
| **Graph 数据恒为 0** | 检查是否启用了 GPU Instancing，以及 Emitter 类型是否支持该 Channel |
| **Iris 下表现不同** | 用受支持的 Iris 版本，分别测"无包/有包"，检查 Photon 兼容配置；`/photon_iris dump` |
| **HDR/Bloom 不对** | 材质值是否 > 1、是否走 HDR target、Post Effect 的 Priority 是否在 Bloom 前后正确 |
| **改了导出文件没生效** | `/photon_client clear_client_fx_cache` |
| **Java 设的值没生效** | 是否被 Timeline 覆盖了同一个槽（[§10.4 坑 1](#104-三个必须知道的坑)） |
| **旋转差了 57 倍** | 角度/弧度混用（[§10.4 坑 2](#104-三个必须知道的坑)） |

### 16.2 三条硬规则

1. **不要每帧清缓存。** 缓存只在资源生命周期事件里清（资源重载会自动清 Definition Cache 与 Listing Cache）。
2. **不要手工改 NBT。** 用编辑器打开旧文件 → 另存 → 重新导出。
3. **`getFX` 返回 null 是正常结果**，不是异常。资源包缺失、可选内容没装都会走到这里。

### 16.3 快速验证流程

```
/photon_editor                      → 编辑器里确认效果本身没问题
File → Export → FX                  → 导出到 ldlib2/assets/photon/fx/
退出编辑器
/photon fx mymod:test block ~ ~-1 ~ → 命令播放，验证导出文件本身
/photon_client clear_particles      → 清理
（此时再切到 Java 调用）
```

命令能播而 Java 不能播，问题一定在 id / namespace / 客户端线程 / 客户端侧隔离上。

---

## 17. 性能清单

- **大量相同几何优先 GPU Instancing**，并保持材质/布局一致以便合批。
- 每个 Emitter 的 **Material/Renderer 运行时 override 会创建独立的渲染 pass**，破坏合批。用完就 `values.renderer.clear()` / `runtime.clearRenderOverride()` 回到共享快路径。
- **Parallel Particle Update** 适合计算密集、无碰撞的发射器；但工作线程里不能碰线程不安全的外部状态。
- **RuntimeValue 只在客户端线程更新**，并且尽量只在值真正变化时写。
- **碰撞（Physics + Collision）与高 Quality Noise 都是逐粒子 CPU 成本**，只在看得见接触的地方开。
- **后处理请求要合并**；宽 Filter 降采样；不需要独立参数就别加 `Independent=true`。
- **Sub Emitter 链必须无环**，否则粒子数指数增长。
- 用真实的 **Max Particle Count** 压测，别只看编辑器前两秒。

---

## 18. 参考

**官方**

- Photon2 手册（中文）：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/>
- 快速开始：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/getting-started.html>
- 编辑器与项目：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/editor-and-projects.html>
- 命令：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/commands.html>
- 分发与 FX Pack：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/distribution.html>
- Java API 章节：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/java-api/>
  - 加载、列出与缓存 FX
  - 内置 Effect Executor
  - FXRuntime 生命周期
  - Runtime 数据注入
  - Runtime Object 与 Transform
  - 自定义 Executor、Timeline 与 Signal
  - 后处理 Java API
  - 扩展、序列化与调试
- 粒子系统章节：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/particle-system/>
- Shader 与 GPU：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/shaders-and-gpu/>
- 后处理：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/post-processing/>
- Photon 源码仓库：<https://github.com/Low-Drag-MC/Photon>

**本仓库内**

- 依赖版本：`gradle.properties`（`photon_version` / `ldlib2_version`）
- 依赖声明：`build.gradle`
- 客户端入口参考：`src/main/java/com/linweiyun/genshin/MinegenshinClient.java`

---

### 附录：从 0 到 1 的最短路径

1. `build.gradle` 加 Photon + LDLib2 依赖，`gradle.properties` 锁版本。
2. 游戏里 `/photon_editor`，做个效果，**给要控制的对象起唯一名字**。
3. **File → Export → FX Pack**，把 `assets/` 拷进 `src/main/resources/`。
4. 写一个 `@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)` 的类，里面 `FXHelper.getFX(...)` → `BlockEffectExecutor`/`EntityEffectExecutor` → `start()`。
5. 需要在服务端决定时机 → 直接 `PacketDistributor` 发 `BlockEffectCommand` / `EntityEffectCommand`。
6. 需要动态参数 → 自定义 payload，客户端 `enqueueWork` 里 `createRuntime()` → 写 `RuntimeValue` → `emit()`。
7. 需要和动画时间点对齐 → Timeline 加 Signal Track，Executor 里覆写 `onTimelineSignal`。

# 9. 排错手册


## 9.1 按症状查

| 症状 | 最可能的原因 | 检查点 |
|---|---|---|
| 特效/模型**跟着镜头跑** | 坐标系搞错 | 有没有把相机相对坐标当世界坐标 |
| 特效在天边/地下 | 反过来，多加了相机位置 | 同上，看是否重复相加 |
| 旋转差 ~57 倍 | 角度/弧度混用 | `setRotation`（度）vs `updateRotation`（弧度） |
| 骨骼特效位置对不上 | 用错骨骼 / 偏移轴向不对 | 骨骼名；`WEAPON_TIP_OFFSET` 与轴向 |
| 骨骼特效不动 | 骨骼没渲染 / 缓存过期 / `worldPos` 为 null | 第一人称？离屏？是不是用了 `addBonePositionListener` 却没人写 `POSITION` |
| 特效**完全不出现** | FX id 写错或资源没导出 | `assets/<ns>/fx/<path>.fx`；日志里的加载失败 |
| 改了导出文件没生效 | 定义缓存 | `/photon_client clear_client_fx_cache` |
| 切世界后特效不回来 | 没有重建逻辑 | 是否用 `isValid()` 判断并重建 |
| Java 设的值没生效 | 被 Timeline 覆盖同一个槽 | 换槽 / 持续更新 / Mute 掉那条属性 |
| 模型凭空少一块 | 隐藏了骨骼但内容画不出来 | 「隐藏」和「绘制」必须成对，解析失败就别隐藏 |
| shader 全黑 | 编译失败或输入全 0 | 常量颜色 → 输出；再看编译日志 |
| 数据恒为 0 | 通道没启用 / 渲染路径不支持 | 不支持的通道返回 0，不报错 |
| 两个渲染层互相打架 | 骨骼显隐是共享布尔 | 动手前先读一次当前状态 |
| `BoneUpdater` 不生效 | 加晚了 | 必须在 `captureModelRenderPose` 之前加 |
| `getModelRenderMatrixState` 抛异常 | 提交前调用了 | 只能在 `submitRenderTasks` 之后调 |
| 换了渲染器偏半格 | `GeoObjectRenderer` 默认半格平移 | 覆写 `adjustRenderPose` |

## 9.2 Photon 的调试命令

| 命令 | 用途 |
|---|---|
| `/photon_editor` | 打开编辑器（仅单人世界） |
| `/photon fx <id> block …` / `entity …` | 用命令验证导出文件本身 |
| `/photon fx remove …` | 移除绑定 |
| `/photon_client clear_particles` | 清粒子 + Executor 缓存，并使已缓存 Runtime 失效 |
| `/photon_client clear_client_fx_cache` | 清 FX 定义缓存与列表缓存 |
| `/photonfx list` / `test <effect> [weight]` / `clear` | 后处理调试 |
| `/photon_iris status` / `dump` | Iris 兼容诊断 |

**分诊法**：命令能播而 Java 不能播 → 问题一定在 id / 命名空间 / 客户端线程 / 客户端侧隔离上。

## 9.3 客户端 / 服务端隔离

这是**唯一会让专用服务器崩掉**的一类错误：

> 公共包（`core/`、`content/`）**不可以**直接引用客户端类。

正确做法是加一层纯公共的通知点，客户端在自己初始化时注册监听：

```java
// 公共侧
public final class SkillCastHooks {
    @FunctionalInterface public interface Listener { void onSkillCast(Player player, int skillType); }
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    public static void register(Listener l) { if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l); }
    public static void unregister(Listener l) { LISTENERS.remove(l); }
    public static void fire(Player player, int skillType) { LISTENERS.forEach(l -> l.onSkillCast(player, skillType)); }
}

// 客户端初始化里
SkillCastHooks.register(TestCharacterFx::onSkillCast);
```

专用服务器上没有任何监听器，`fire` 就是一次空循环。

**另一个坑**：`@EventBusSubscriber` 注册的类如果方法是游戏总线事件，NeoForge 会自动判定；
但本项目里有一条既有约定 —— 游戏总线事件**显式注册**更稳妥：

```java
NeoForge.EVENT_BUS.addListener(TestCharacterFx::onClientTick);
```

---

## 9.4 深入：三分钟定位法

出事时按这个顺序跑一遍，基本能在三分钟内判断「是谁的问题」：

1. **看清是「帧率低」还是「卡顿」**：帧率低 = 每帧都慢；卡顿 = 间歇性长帧，
   两者的排查方向完全不同（前者看稳态开销，后者看尖峰与 GC/资源加载）。
2. **取三个数字**：帧时间、走优化路径的模型数、骨骼遍历耗时（见
   [8. 性能](/doc/rendering-26.2-effects-performance)）。
3. **一次只改一件事**：关优化路径 → 关本模组特效 → 关光影 → 切原版资源包，每次只动一个。
4. **按收益/风险排序再改**：先改模型侧（减骨骼/减面）、再改玩法侧（限制实例数）、最后才动管线。
5. **同场景、同路径、同时长复测**：换场景测等于没测。

## 9.5 命令与开关速查

```text
F3                          帧时间
/reload                     资源重载（管线、模型、几何缓存重建）
关 render-optimize.character-geometry   回到 GeckoLib 原路径（对照用）
Photon 客户端命令            清 .fx 缓存、预览特效（见 Photon 章）
```

## 9.6 三个常见误区

| 误区 | 事实 |
| --- | --- |
| 「按 F3 看到帧率掉了就是模组的问题」 | 先关优化路径/特效做对照，再下结论 |
| 「改完立刻看一次就算验证」 | 至少同场景跑两遍（第一次可能还在加载） |
| 「性能问题一定是渲染」 | 长帧也常来自资源加载、GC、区块构建，先看是不是间歇性的 |

## 9.7 客户端 / 服务端隔离的三条硬规矩

1. 任何 `client` 包里的类都只在客户端事件/入口里 import；
2. 服务端只发「播什么、播在哪」的意图包；
3. 交付前用**专用服务器**跑一遍 —— 单人存档不会暴露这类问题。

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

# 11. 排错手册

出问题时先按症状定位，再按「谁的数据不对」往回查。这一章给的是本项目实际踩过的顺序。

## 11.1 按症状查

### 什么都没画出来

| 现象 | 先查 |
| --- | --- |
| 完全不显示 | 渲染事件没挂上 / 阶段选错（比如挂在 `AFTER_SKY` 却指望被地形遮挡） |
| 一处都不显示、日志干净 | 忘 `endBatch()`；或顶点写在 `popPose()` 之后 |
| 只在某个视角显示 | 面剔除（`CULL`）或视锥剔除过头 |
| 只在某个维度显示 | 事件里读了客户端未同步的数据 |

### 颜色 / 贴图不对

| 现象 | 先查 |
| --- | --- |
| 全黑 / 全白 | 采样器没绑（`setSampler` 名字与 JSON 不一致）、`fragColor` 没赋值 |
| 洋红 | 贴图路径不存在（原版「缺失纹理」色） |
| UV 像颜色、颜色像 UV | `VertexConsumer` 调用顺序与 `VertexFormat` 不一致 |
| 半透明发灰 | 混合方程不是 `TRANSLUCENT_TRANSPARENCY`，或没开深度写入掩码 |

### 位置 / 朝向不对

| 现象 | 先查 |
| --- | --- |
| 整体偏半格 | 摆件原点补偿（见 [5. GeckoLib](/doc/rendering-1.21.1-reference-geckolib)） |
| 抖动 / 一跳一跳 | tick 与 frame 都写位置；或没做 `partialTick` 插值 |
| 上下颠倒 / 反向 | 模型空间与世界空间 X 轴反向、度/弧度混用（见 [6. 坐标空间](/doc/rendering-1.21.1-reference-transform)） |
| 远处抖动 | 先转 `float` 再减相机坐标 |

### 模型 / 动画

| 现象 | 先查 |
| --- | --- |
| 模型不显示、日志无报错 | 资源解析链（`AssetGeoCache → GenshinGeoCache → GeckoLib`）找不到文件 |
| 模型显示但不动 | 键名与 `.animation.json` 不一致；自定义 `getBakedModel` 绕过骨骼登记 |
| 换资源包后不更新 | 自己缓存了烘焙模型，改成只缓存键 |
| 动画冻结不了 | GeckoLib 4 没有 `PlayState.PAUSE`，要用 `setAnimationSpeed(0)` |

### 特效

| 现象 | 先查 |
| --- | --- |
| `.fx` 不生效 | 路径 / 命名空间；`/photon clear client cache fx` |
| 只播一次 | 执行器过早 `retire` / `FXRuntime` 被判失效（判据不能用时间轴时钟） |
| 退出世界还在播 | 没处理 `LevelEvent.Unload` |
| 服务端崩在 `NoClassDefFoundError` | 服务端碰了 `com.lowdragmc.photon.client.*` |

### 性能

| 现象 | 先查 |
| --- | --- |
| 帧时间随模型数线性涨 | CPU 蒙皮瓶颈（见 [7. GPU 蒙皮](/doc/rendering-1.21.1-reference-gpu-skinning)） |
| 帧时间随粒子/面积涨 | GPU 瓶颈（Overdraw、Bloom） |
| 开光影掉帧 | Photon 的 `enable_custom_effects_with_shader_pack`、动态光盏数 |
| 进入某场景后内存涨 | 渲染器里的 `Map` 没在实体卸载时清 |

## 11.2 常用开关与命令

```text
F3                        帧时间、坐标、区块
F3 + A                    重载区块（配合 /reload 排除资源问题）
/photon clear client cache fx   清 .fx 解析缓存
/reload                   资源重载（着色器、模型、几何缓存都会重建）
```

## 11.3 日志怎么看

| 日志形态 | 含义 |
| --- | --- |
| `Failed to compile shader ...` + GLSL 行号 | core shader 编译失败，看它指的那一行的上一行 |
| `Unable to find animation` | GeckoLib 找不到动画文件/键名 |
| `NoClassDefFoundError: ...client...` | 服务端加载了客户端类 |
| Mixin 注入失败 | 目标方法签名变了（多半是版本对不上） |

## 11.4 二分定位

按这个顺序排除，每一步都能砍掉一半可能：

1. **关掉自己的模组特效**：帧时间恢复 → 是本模组的锅；
2. **切成原版资源包**：现象变化 → 是资源 / 模型 / 着色器；
3. **关光影**：现象变化 → 是 Iris 兼容层；
4. **最小重现**：单独开一个 `.fx`、单独一个实体、单独一份着色器；
5. **换回上一个提交**：定位是哪次改动引入的。

## 11.5 客户端 / 服务端隔离（最常见的一类崩）

- `com.lowdragmc.photon.client.*`、`net.minecraft.client.*` 全部是客户端专用；
- 用 `@OnlyIn(Dist.CLIENT)` 或 `dist = Dist.CLIENT` 的事件订阅者包住；
- 服务端只发「播什么、播在哪」的意图包；
- 专用服务器（dedicated server）跑一遍再交付 —— 单人存档不会暴露这类问题。

下一章：[12. 附录](/doc/rendering-1.21.1-reference-appendix)。
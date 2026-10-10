# 1. 先建立正确的心智模型

这一册是**动手向**的速查：每一章讲一类事怎么做，细节在对应的「完全参考」章里，页末都有链接。

## 1.1 1.21.1 是「立即模式」

```text
拿 VertexConsumer → 写顶点 → endBatch → 画
```

没有「先记下来、稍后再画」的阶段（那是 26.2 的三段式）。所以：

- 渲染要用的数据，必须在你写顶点的那一刻全部就位；
- 写完之后改数值无效，要补就换个 `RenderType` 或换个阶段再画一遍；
- 每个 `RenderType` 自带全部绘制状态（着色器 / 混合 / 深度 / 剔除 / 贴图）。

## 1.2 一帧的顺序（只记有用的部分）

| 顺序 | 阶段 | 事件 `Stage` |
| --- | --- | --- |
| 1 | 天空 | `AFTER_SKY` |
| 2 | 不透明方块（solid / cutoutMipped / cutout） | `AFTER_SOLID_BLOCKS` 等 |
| 3 | 实体 | `AFTER_ENTITIES` |
| 4 | 方块实体 | `AFTER_BLOCK_ENTITIES` |
| 5 | 半透明方块 | `AFTER_TRANSLUCENT_BLOCKS` / `AFTER_TRIPWIRE_BLOCKS` |
| 6 | 粒子 | `AFTER_PARTICLES` |
| 7 | 天气 | `AFTER_WEATHER` |
| 8 | 收尾 | `AFTER_LEVEL` |

经验：**要被地形遮挡 → 放固体/实体之后、粒子之前；屏幕空间的东西 → 放粒子之后。**

## 1.3 线程与生命周期

- 「渲染线程」就是客户端主线程，tick 与 render 在同一个线程里交替；
- tick 只准备数据（位置、状态、要不要播），渲染回调只画；
- 帧内连续变化一律用 `partialTick` 插值；
- 服务端不能碰任何渲染类，只能发包说意图。

## 1.4 挂在哪

```java
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class MyRenderEvents {
    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        PoseStack pose = event.getPoseStack();          // 相机相对
        float partialTick = event.getPartialTick();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // …… 写顶点，最后 buffers.endBatch();
    }
}
```

## 1.5 与 26.2 的差别（写代码前先看）

| 关注点 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 渲染模型 | 立即模式 | 三段式（extract / submit / render） |
| 渲染数据 | 自己缓存 | `RenderState` + `DataTicket` |
| 着色器 | core shader JSON | Java 侧 `RenderPipeline` |
| GeckoLib | 4.9.3 | 5.5.6 |

详细内容：[完全参考 1. 心智模型](/doc/rendering-1.21.1-reference-frame) ·
[2. Blaze3D 是什么、怎么写](/doc/rendering-1.21.1-effects-blaze3d)。
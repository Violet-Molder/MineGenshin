# 7. GPU 蒙皮与渲染性能


## 7.1 本项目的骨骼调色板链路（可直接照抄的 GPU 蒙皮实现）

整体数据流（每一步都能在仓库里查到）：

```
编译期（资源加载后只做一次）
  GeoCompileCache / CompiledGeoModel    把 geo 模型编译成「骨骼数组 + 顶点区间」
  SkinnedMesh.compile(model)            把顶点打包成 GpuBuffer（顶点常驻显存）
        VertexFormat FORMAT             Position(vec3) / UV0(vec2) / Normal(RGBA8_SNORM) / BoneIds(RGBA16_UINT)

每帧（每个被接管的模型一次：GeoRenderIntercept.java:241）
  BoneMatrixPalette.compute(...)        深度优先前序走骨骼树 → 写 MATRICES / NORMAL_MATRICES
        → SkinDataStorage.write(...)    写进一块环形常量缓冲，返回 GpuBufferSlice
        → SkinnedSubmit                 记录 mesh / skinData / runs / 管线，交给 FeatureRenderer

绘制期
  SkinnedFeatureRenderer.executeGroup → draw(submit, encoder)
        renderPass.setPipeline(submit.pipeline())
        renderPass.setUniform("SkinData", submit.skinData())
        renderPass.setVertexBuffer(0, mesh.buffer().slice())
        renderPass.drawIndexed(quads * 6, 1, 0, firstVertex, 0)
  顶点着色器 entity_skinned.vsh 用 Bones[bone] 做蒙皮、NormalBones[bone] 转法线
```

关键实现点逐条拆开：

**① 常量缓冲的布局与容量**（`optimize/gpu/SkinDataStorage.java`）

```text
MAX_BONES = 128                                                     :31
SIZE = 128*64 (mat4 Bones[128]) + 128*48 (mat3 NormalBones[128]) + 16 (ivec4 LightOverlay) + 16 (vec4 Color)
     = 14368 字节                                                   :37
blockSize = ceilDiv(SIZE, minUniformOffsetAlignment) * 该对齐       :106
```

注释写明了为什么要自己写一份而不能直接用原版的 `DynamicUniformStorage`：原版对 blockSize 做的是**向下取整**（`Mth.roundToward`），14368 在 64 字节对齐的驱动上会被截成 14336，写入直接 `BufferOverflowException`（`SkinDataStorage.java:16-24`）。这是"照抄原版"时最容易踩的坑。

缓冲本体是 `MappableRingBuffer`（`net/minecraft/client/renderer/MappableRingBuffer.java:13,20,39,50`）：`USAGE_MAP_WRITE | USAGE_UNIFORM`，每写一条前进一个 block，帧末 `rotate()` 翻页（翻页时机挂在 `FlipFrameEvent` 上，`MinegenshinClient.java:136-139`）；容量不够时翻倍重建，**旧缓冲要留到帧末再关**，因为这一帧已经发出去的绘制还在读它（`SkinDataStorage.java:117-160`）。

**② std140 布局的对齐规则**（`optimize/gpu/BoneMatrixPalette.java:25-28,175-210`）

```java
/** std140 里 Bones[] 的定长字节数。法线块必须从这里开始，不能紧接「已写骨骼数」之后。 */
private static final int NORMAL_BLOCK_OFFSET = SkinDataStorage.MAX_BONES * 64;
/** std140 里 LightOverlay 的起始偏移 = Bones + NormalBones 的字节数。 */
private static final int LIGHT_OVERLAY_OFFSET = SkinDataStorage.MAX_BONES * 64 + SkinDataStorage.MAX_BONES * 48;

// std140 的 mat3 是「3 列，每列 4 个 float」：写 3 个分量后必须跳过第 4 个。
data.position(NORMAL_BLOCK_OFFSET);
for (int i = 0; i < boneCount; i++) {
    for (int column = 0; column < 3; column++) {
        data.putFloat(...); data.putFloat(...); data.putFloat(...);
        data.putFloat(0f);                       // 补齐到 vec4
    }
}
```

要点：

- **数组按定长偏移，不按写入长度**：着色器里是 `mat4 Bones[128]`，即使这一帧只用了 7 根骨骼，`NormalBones[]` 也必须从 `128 * 64` 开始写。写成 `position(写过的字节数)` 会让每根骨骼读到别人的法线，表现为光照逐帧乱跳。
- **mat3 在 std140 里占 48 字节**（3 列 × 16），不是 36。
- `ivec4 LightOverlay` 里 `.xy` 是方块光/天空光、`.zw` 是覆盖层 uv（和原版 `UV2`/`UV1` 的分工一致，`BoneMatrixPalette.java:198-203`）。
- 尺寸可以先用 `com/mojang/blaze3d/buffers/Std140SizeCalculator.java:8-44`（`putMat4f()` / `putVec4()` / `putIVec4()` / `align(int)`）算一遍，原版自己的投影矩阵 UBO 就是这么算的（`RenderSystem.java:38`）。

**③ 可见区间与 draw call 合并**（`BoneMatrixPalette.java:81-137`）

顶点在缓冲里的排布顺序 = 骨骼树的深度优先前序，所以"一根骨骼的几何"永远是连续区间；隐藏骨骼时**游标必须照样推过去**（顶点还在缓冲里占位），否则后面所有兄弟骨骼的区间都会前移，画到别人的顶点上（`collect` 的注释写的正是这个）。相邻可见区间会被合并成一条 run（`appendRun`），每条 run 最终对应一次 `drawIndexed`（`SkinnedFeatureRenderer.java:160-167`），所以 **runs 条数就是这次渲染的 draw call 数**。

**④ 管线派生**（`optimize/gpu/SkinnedPipelines.java`）

```java
public static final BindGroupLayout SKIN_DATA_LAYOUT = BindGroupLayout.builder()
        .withUniform("SkinData", UniformType.UNIFORM_BUFFER)            // :38-40
        .build();

private static RenderPipeline derive(RenderPipeline base) {
    return RenderPipeline.builder(base)                                 // 从原版管线派生
            .withLocation(Minegenshin.id("pipeline/skinned_" + ...))
            .withBindGroupLayout(SKIN_DATA_LAYOUT)
            .withVertexShader(...)                                      // :101-110
            .build();
}

public static void registerPipelines(RegisterRenderPipelinesEvent event) {
    for (RenderPipeline base : WHITELIST) { event.registerPipeline(derive(base)); }   // :111-116
}
```

白名单是 8 条原版实体管线（`entity_solid`、`entity_solid_z_offset_forward`、`entity_cutout`、`entity_cutout_cull`、`entity_cutout_z_offset`、`entity_translucent`、`entity_translucent_cull`、`entity_translucent_emissive`，`SkinnedPipelines.java:57-65`），派生时把顶点格式、图元拓扑、深度/混合/stencil 原样带过来，只换 shader 与绑定组。**这就是"不改视觉、只换顶点变换"的正确姿势**：任何一条不在白名单里的 RenderType（比如光影包替换过的实体管线）都会走回 CPU 蒙皮。

**⑤ 顶点格式必须与着色器输入逐字对齐**（`optimize/gpu/SkinnedMesh.java:41-47`）

```java
public static final VertexFormat FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)   // 0 偏移，vec3
        .addAttribute("UV0",      GpuFormat.RG32_FLOAT)    // 12 偏移，vec2
        .addAttribute("Normal",   GpuFormat.RGBA8_SNORM)   // 20 偏移，4 字节法线
        .addAttribute("BoneIds",  GpuFormat.RGBA16_UINT)   // 24 偏移，4 个 u16 = 骨骼号 + 法线修正位
        .build();                                          // 32 字节/顶点
```

着色器侧必须同名同序（`src/main/resources/assets/minegenshin/shaders/core/entity_skinned.vsh`）：

```glsl
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uvec4 BoneIds;

layout(std140) uniform SkinData {
    mat4 Bones[128];
    mat3 NormalBones[128];
    ivec4 LightOverlay;
    vec4 Color;
};
```

管线编译期是按**名字**把顶点格式接到着色器输入上的，改一处不改另一处 = 属性错位（`SkinnedMesh.java:26-31` 的注释）。

**⑥ 提交节点的形态**：`SkinnedSubmit implements SubmitNode`（`optimize/gpu/SkinnedSubmit.java:21`），并刻意**不实现** `BatchableSubmit` —— 每个模型一块顶点缓冲、每帧一块骨骼数据，合批只会让状态切换更糟；它只在 `prepare()` 里抓一次 `renderType.prepare()`（`:80-88`），把动态变换、裁剪、纹理定下来。

## 7.2 26.2 里可用的 GPU 数据手段（除本项目的 UBO 方案外）

| 手段 | 真实 API | 适用 |
|---|---|---|
| 常量缓冲（UBO） | `GpuDevice#createBuffer(label, USAGE_MAP_WRITE / USAGE_UNIFORM, size)`（`com/mojang/blaze3d/systems/GpuDevice.java:140,148`）、`GpuBuffer#slice(offset, length)`（`buffers/GpuBuffer.java:45`）、`GpuBufferSlice#map(read, write)`（本项目 `SkinDataStorage.write`）、`RenderPass#setUniform(String, GpuBufferSlice)`（`systems/RenderPass.java:106,110`） | 每帧少量、结构固定的数据（骨骼矩阵、光照、颜色） |
| 环形暂存 | `MappableRingBuffer`（`net/minecraft/client/renderer/MappableRingBuffer.java:13-60`）、`StagingBuffer`（`com/mojang/blaze3d/vertex/StagingBuffer.java:121-127`） | 一帧内多次写入、每帧翻页 |
| 顶点缓冲 | `GpuDevice#createBuffer(..., ByteBuffer data)` 上传 + `RenderPass#setVertexBuffer(slot, slice)`（`:150`） | 静态几何常驻显存（本项目做法） |
| 索引缓冲 | `RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)` + `IndexType`（`SkinnedFeatureRenderer.java:118-126`） | 顺序索引（`i,i+1,i+2,i+2,i+3,i`），配合 `drawIndexed(..., baseVertex)` |
| 绘制 | `RenderPass#drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)`（`:170`） | 每次 run 一次调用 |
| 实例化 | `drawIndexed` 的 `instanceCount` 参数（同签名） | 同一网格多份数据（本项目暂未使用，见下条备注） |
| GPU 计时 | `GpuDevice#createTimestampQueryPool` + `CommandEncoder#writeTimestamp` + `GpuQueryPool#getValues`（`optimize/gpu/SkinnedGpuTimer.java:19,42,60,105,121`） | 量自己那批绘制的 GPU 时间 |

> 未确认：本项目没有实例化渲染的实例，`drawIndexed` 的 `instanceCount` 语义（每实例读取哪些逐实例属性）需要按具体管线设计再核；本节只确认了签名存在。

## 7.3 性能预算与测量

**先分清你量的是哪一段**：

| 读数 | 量的是什么 | 在哪看 |
|---|---|---|
| CPU 提交（walk / us per model） | 骨骼遍历 + 写顶点缓冲的 CPU 时间 | 本项目 F3 的 `mg-render` 一行，由 `optimize/RenderOptimizeStats.java:60,94` 汇总 |
| GPU 蒙皮真实 GPU 时间 | 本模组那批 skinned 绘制的 GPU 时间轴跨度 | `optimize/gpu/SkinnedGpuTimer.java:69`（三格轮转、隔帧取回、乘 `timestampPeriod` 换成纳秒；驱动不支持时整栏隐藏） |
| 实体提取数量 | 这一帧提取了多少个实体状态 | F3 的实体渲染统计（`DebugEntryEntityRenderStats` → `LevelExtractor.entityStatistics()`，`extract/LevelExtractor.java:538`） |
| 区块编译 / GPU 利用率 | 地形侧开销 | 原版 metrics 采样器，`MetricSampler.createExtractSampler(name, MetricCategory.…, supplier)`（`client/profiling/ClientMetricsSamplersProvider.java:36-55`） |

**预算怎么定**：帧时间是可以直接换算的硬指标 —— 60 FPS 时一帧 16.6 ms，144 FPS 时 6.9 ms；任何一个"每帧都跑一遍"的自定义渲染代码，只要超过帧时间的几个百分点就值得优化。本项目的 `RenderOptimizeStats` 把 CPU 开销表示成帧时间的百分比而不是绝对微秒，原因就在注释里：绝对数字随场景规模变，占比才能横向比较（`RenderOptimizeStats.java:20-23`）。

**优化顺序（经验值，按收益从高到低）**：

1. **先减 draw call 与状态切换**：合批相同 RenderType、合并连续可见区间（本项目 `appendRun`）、避免每根骨骼一次 `drawIndexed`。
2. **再减少每帧 CPU 工作**：把"每帧重算"的东西编译期算好（本项目的 `CompiledGeoModel`）、把"每个模型一份"的数据合并成一块缓冲（本项目的骨骼矩阵 UBO 方案）。
3. **然后才是 GPU 侧**：顶点常驻显存（GPU 蒙皮）省的是 CPU 上传，不省 GPU 光栅化；如果瓶颈在填充率，要动的是分辨率 / 粒子数 / 是否做全屏后处理，而不是蒙皮。
4. **最后才是降质量**：降低模型面数、关闭某些层。顺序反了会"优化了半天没感觉"。

**两个必须遵守的工程约束**（都是本项目踩出来的）：

- **失败要能回退，不要崩帧**：`SkinDataStorage.write`、`SkinnedMesh.compile`、`SkinnedGpuTimer` 三处都是"失败 → 返回 null → 回退 CPU 蒙皮"，只有"设备不支持"这种不可恢复的错误才永久停用（`SkinDataStorage.java:44-47,117-140`）。
- **外部环境会打破你的假设**：光影包会替换实体管线、其他模组可能改顶点格式，所以 `SkinnedPipelineGuard` 会在这两种情况下主动让位给 CPU 蒙皮，并把原因显示在 F3 上（`RenderOptimizeStats.java:30-35`）。写自定义管线时都要留这样一道闸门。

> 未确认：本项目没有对"GPU 蒙皮 vs CPU 蒙皮"的实测帧率对比数据（只有逐帧的 CPU/GPU 读数）。要不要据此给出"提升 xx%"的结论，需要一次单独的性能对比测试。

## 7.4 案例：把 CPU 蒙皮换成 GPU 蒙皮，到底要动什么

**先看两种做法的差别**（这是决定"值不值得做"的唯一依据）：

| | CPU 蒙皮（GeckoLib 默认路径） | GPU 蒙皮（本项目 `optimize/gpu` 这条路） |
|---|---|---|
| 每帧做什么 | 遍历骨骼 → 在 CPU 上把该骨骼的顶点逐个变换 → 写进顶点缓冲 | 只算骨骼矩阵（≤128 根）写进一块 UBO，顶点变换交给顶点着色器 |
| 顶点量变大时的代价 | 线性上涨（顶点越多，CPU 越忙） | 基本不变（矩阵数量与顶点数无关） |
| 每帧上传量 | 整个模型的顶点数据 | 一块 `mat4[128] + mat3[128] + …` 的常量缓冲 |
| 实现复杂度 | 低（框架已经做好） | 高（要自己派生管线、自己管缓冲、自己做回退） |
| 什么时候值得 | 顶点少、模型少 | 同屏角色多、模型顶点多、CPU 侧"walk"时间占比高 |

**要动的六个文件**（按数据流顺序，每个职责都别越界）：

| 文件（`src/main/java/com/linweiyun/genshin/client/render/optimize/gpu/`） | 职责 |
|---|---|
| `BoneMatrixPalette.java` | 骨骼树的遍历顺序 + 每根骨骼的矩阵打包（深度优先前序，与顶点区间一一对应） |
| `SkinDataStorage.java` | 把打包好的矩阵写进常量缓冲（失败返回 `null`，**不抛异常**） |
| `SkinnedMesh.java` | 顶点格式与几何区间（`FORMAT` 的属性名必须与着色器逐个对上，见 §3.5） |
| `SkinnedFeatureRenderer.java` | 每帧收集可见区间、合并连续 run、每个 run 一次 `drawIndexed` |
| `SkinnedPipelines.java` | 从原版 `entity` 管线派生出 skinned 版本（换顶点着色器、换顶点格式、追加 `SkinData` 绑定组） |
| `SkinnedPipelineGuard.java` | 判断"现在还能不能走 GPU 这条路"，不能就整体让位给 CPU 蒙皮 |

**两条工程约束必须照抄**（都是本项目踩出来的）：

1. **失败要能回退，不要崩帧**：`SkinDataStorage.write`、`SkinnedMesh.compile`、`SkinnedGpuTimer`
   在出错时一律返回 `null` 并回退 CPU 蒙皮；只有"设备根本不支持"这种不可恢复的情况才永久停用
   （`SkinDataStorage.java:44-47`、`:117-140`）。
2. **外部环境会打破你的假设**：光影包会替换实体管线、别的模组可能改顶点格式，
   所以 `SkinnedPipelineGuard` 会在这两种情况下主动让位，并把原因写进 F3
   （`RenderOptimizeStats.java:30-35`）。任何"替换原版渲染路径"的优化都要有这道闸门。

**怎么确认它真的在工作**：

1. 打开 F3：`RenderOptimizeStats` 会把 `walk`（CPU 侧骨骼遍历 + 写顶点的耗时）、
   占帧百分比 `(x.x% of frame)`、以及 GPU 侧计时（`SkinnedGpuTimer` 的纳秒读数）显示出来；
2. `walk` 明显下降、帧率不变或上升，说明 CPU 侧省下来了；
3. 如果 F3 上出现"让位给 CPU 蒙皮"的原因，说明它其实没生效 —— 先解决那个原因，别急着调参。

**一句话结论**：GPU 蒙皮省的是 **CPU 时间**，不是"画面更好"；顶点少的时候做它反而更亏
（多了一条管线、多了一层回退逻辑）。判断标准只有一个：**`walk` 占帧时间的比例**。

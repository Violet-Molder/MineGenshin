# 6. Photon2 的渲染架构


Photon 不是「另一个粒子系统」，它是**一整套接进原版渲染管线的绘制引擎**：
自己做烘焙、上传、排序、合批、绘制、后处理，只借用原版的粒子生命周期来驱动 tick。

## 6.1 包结构与职责

| 包 | 职责 |
|---|---|
| `client.fx` | 定义、加载、运行时、Executor（**上层 API**） |
| `client.fx.timeline` | Timeline、Track、Clip、Signal |
| `client.fx.fxpack` | `.fxpack` 的挂载 / 列举 / GC |
| `client.gameobject` | FX 对象、`RuntimeValue`、`FXObjectType` |
| `client.gameobject.emitter.*` | 各类发射器与配置数据、渲染设置 |
| `client.gameobject.particle.*` | 粒子实现与实例记录填充器 |
| `client.render` | **绘制引擎**：烘焙、上传、合批、绘制、mask、bloom |
| `client.postfx` / `.graph` / `.runtime` / `.shadergraph` | 后处理：Render Graph、Fullscreen Graph、效果栈 |
| `client.shadergraph` | 粒子 Shader Graph 编译器 |
| `client.compat.iris` | 光影包兼容 |
| `gui.editor` | 游戏内编辑器（基于 LDLib2） |
| `command` | `/photon fx` 用的网络包 |

## 6.2 一帧里 Photon 干了什么

**它不用 `RenderLevelStageEvent` 画自己的粒子。** 它走原版粒子的 extract/submit/prepare/execute 管线，
在两个 `RenderPhaseKey` 上被 drain：

```
① emit        IFXObject.emit(...) → Minecraft.getInstance().particleEngine.add(particle)
② extract     PhotonParticleGroup.extractRenderState
                 └ 对所有 FX 对象抽帧（不可见的也要，否则时间轴会冻结）
                 └ 可见 Emitter 做视锥剔除 + extractBatches
                 └ state.defer(bakeTask)          ← 只登记，不烘焙
③ submit      PhotonFXRenderState.submit
                 └ submitSpecial(RenderPhaseKeys.SOLID         → AFTER_OPAQUE_FEATURES)
                 └ submitSpecial(RenderPhaseKeys.AFTER_TERRAIN → AFTER_TRANSLUCENT_PARTICLES)
④ prepare     PhotonFeatureRenderer.prepareGroup → Frame.prepare()
                 └ 逐个 task.bake()               ← CPU 网格 或 实例数据上传
                 └ resolve()                      ← 排序 + 合并上传 + 合并 draw
⑤ execute     PhotonFeatureRenderer.executeGroup → Frame.execute(stage)
⑥ drain       recordDraws() → createRenderPass("Photon fx") → pass.drawIndexed(...)
                 └ maskSubPass() / bloom / stack.consumeAndExecute() + SceneBlit.writeBack
⑦ finishLayer 光影包内合成 或 PhotonDeferredLayer.park
⑧ AfterLevel  PhotonPostFX.onLevelRenderComplete()
⑨ RenderFrameEvent.Post  PhotonPostFX.onFrameEnd() + PhotonWorldRenderState.endFrame()
```

## 6.3 `PhotonStage`：只有三个真实槽位

| 常量 | 含义 |
|---|---|
| `AFTER_OPAQUE_FEATURES` | 不透明 FX 走这里（会写深度） |
| `AFTER_TRANSLUCENT_PARTICLES` | 原版半透明粒子之后，**所有混合/HDR 效果**走这里 |
| `DEFERRED` | 同一缝提交，但画进独立的 `PhotonFXLayer`，composite 推迟到 `renderLevel` 返回之后 |
| `LAST` | **别名**，等于 `AFTER_TRANSLUCENT_PARTICLES`；后处理链在这一槽跑 |

⚠️ `PhotonStage` 的 javadoc 提到 `AFTER_LEVEL`，但**该常量在本版本不存在** —— 别按注释找枚举名。

发射器落在哪一槽由 `RendererSetting.Layer` 决定（**与 blend 无关**）：
`Opaque` → `AFTER_OPAQUE_FEATURES`，`Translucent` → `AFTER_TRANSLUCENT_PARTICLES`。
只有 `FXCompositeMode.LATE` **且全部材质 `isLayerSafe`** 才升级成 `DEFERRED`。
**读目标颜色的混合（multiply / min-max / 任何 `DST_*`）永远不会延迟。**

## 6.4 DrawJob 模型

| Job | 内容 |
|---|---|
| `Job` | CPU 烘焙网格：`RenderType` + `MeshData` + buffer + stage + orderInLayer + 距离 + mask |
| `InstancedJob` | GPU 实例化：programs + 顶点/索引/实例/点/数据/自定义数据/VAT buffer + 绑定 + 材质 slice |

排序：`orderInLayer` 升序 → 距离**由远到近**。

`resolve()` 做三件优化：**把 CPU 顶点块拼成一次瞬时上传**、
**相邻同 `RenderType` 且同 mask 且非 strip/fan 的 CPU job 跨发射器合并**、再分派到 `resolveRun` / `resolveInstanced`。

> 这条「跨发射器合并」很关键：**几何、材质 program、纹理状态、渲染层、顶点布局一致时，
> 多个发射器的粒子会并成一次 draw。**

## 6.5 材质与管线

```java
public interface IMaterial extends IConfigurable, IPersistedSerializable, ILDLRegisterClient<IMaterial, Supplier<IMaterial>> {
    @Nullable default RenderType getRenderType(MaterialSetting setting, PrimitiveTopology mode);  // 默认 null
    IGuiTexture preview();                                                                        // 必需
    default IGuiTexture previewLive();
    default IMaterial copy();
}
```

**`getRenderType(...)` 返回 null 的语义是「这段几何画不了」—— `Emitter.bakeGroup` 会静默跳过。**
这是「材质看着正常但粒子不显示」的隐蔽原因之一。

| 材质 | 要点 |
|---|---|
| `TextureMaterial` | 纹理 + `discardThreshold` + HDR 颜色/模式 + PixelArt 位数，可选软粒子 |
| `SpriteMaterial` | 走 Minecraft sprite atlas 窗口 |
| `ShaderGraphMaterial` | 引用一个 Shader Graph 资源，每个 `ParticlePipelineKey` 一个 RenderType |
| `CustomShaderMaterial` | 手写 core shader + 曲线/渐变 sampler + 自定义 uniform |
| `BlockTextureSheetMaterial` | 单例，绑方块图集 |
| `UIResourceMaterial` | 复用 LDLib2 的 UI 纹理资源 |

**合批的关键**：`MaterialRenderTypes` **按值缓存**（相同 纹理 + fragment + pipelineKey + uniforms
共用同一个 RenderType，可跨发射器合批）；而 `CustomShaderMaterial` / `ShaderGraphMaterial` 的
RenderType 是**每实例自有**并由 GC 释放。**想合批就共享同一个材质实例。**

## 6.6 GPU Instancing 的 9 个变体

`PhotonPipelines.InstancedVariant` 是**编译期属性**，每个变体对应一组 `#define` 与一套顶点/实例布局：

| 变体 | define | 用点缓冲 | 用自定义数据 |
|---|---|---|---|
| `TILE` | `PARTICLE_INSTANCE` | ✗ | ✓ |
| `MODEL` | `PARTICLE_MODEL_INSTANCE` | ✗ | ✓ |
| `MODEL_TANGENT` | `+ PHOTON_TANGENT` | ✗ | ✓ |
| `MODEL_VAT` | `+ PHOTON_VAT` | ✗ | ✓ |
| `MODEL_VAT_TANGENT` | `+ PHOTON_VAT + PHOTON_TANGENT` | ✗ | ✓ |
| `TRAIL` | `TRAIL_INSTANCE` | ✓ | ✗ |
| `ARA` | `ARA_TRAIL_INSTANCE` | ✓ | ✗ |
| `ARA_TUBE` | `ARA_TRAIL_TUBE_INSTANCE` | ✓ | ✗ |
| `BEAM` | `BEAM_INSTANCE` | ✗ | ✗ |

⚠️ **加一个变体要同步改四处**：`InstancedVariant`、`PhotonInstanceLayouts`、
`particle.glsl` 里 `getParticleData()` 的展开、以及对应的 `*ParticleRenderer` 填充器。

## 6.7 手写 Core Shader 的正确姿势

**路径**：`assets/<ns>/shaders/core/<path>.json`（`shaderLocation` 不含 `core/`、不含 `.json`）。

| JSON 字段 | 说明 |
|---|---|
| `vertex` / `fragment` | 程序 id，会被映射成 `ns:core/path`；缺省 vertex 是 `photon:core/particle` |
| `samplers` | `[{name}]`。`SamplerScene*` 前缀 = 场景采样器；`Sampler0/1/2` 是引擎内建 |
| `uniforms` | `[{name, type, count, values}]`。名字在 `BUILTIN_UNIFORMS` 里或带 `U_` 前缀的会被跳过 |
| `depthConvention` | 写 `"reverse_z"` 才是 26.2 原生；**缺省按 forward-Z legacy 处理** |

**⚠️ 26.2 手写 shader 不能有裸 uniform**：

- 材质侧的自定义 uniform 全部进**同一个 `uniform PhotonCustomMaterial`** std140 块，成员顺序 = JSON 声明顺序；
- 后处理 pass 侧进 `uniform PhotonPass`（只接受 `type=="float"` 且 `count∈[1,4]`）。

**允许的内建 uniform 名**：`ModelViewMat`、`ProjMat`、`IViewRotMat`、`ColorModulator`、
`FogStart`、`FogEnd`、`FogColor`、`FogShape`、`GameTime`、`ScreenSize`、`LineWidth`，
加上**所有 `U_` 前缀**的名字（相机相关的是 `U_ViewPort`，`vec4(x,y,w,h)`，来自 `PhotonEngine` 块）。

**内建 sampler**：`Sampler0` / `Sampler1` / `Sampler2`（`Sampler2` 是 lightmap）、
`SamplerSceneColor` / `SamplerSceneDepth`、`PhotonPoints` / `PhotonData` / `PhotonCustomData` / `PhotonVat`。

**内建 UBO**：`PhotonEngine`、`PhotonMaterial`、`PhotonCustomMaterial`、`PhotonMask`、`PhotonVatInfo`、`PhotonPass`、`PhotonBloom`。

## 6.8 后处理：逐帧请求 + 加权合并

```java
PhotonPostFX.submit(effectPath, Map.of("Tint", new Vector4f(...)), 0.65f);   // 每帧都要调
```

**合并规则**（`PostEffectStack`）：

1. 按 effect 路径分组，组内按 **weight 升序**；合并权重 `w += (1-w) × wᵢ`（等价 `1-Π(1-wᵢ)`）。
2. **可插值参数**（float / vec2 / vec3 / vec4）从 schema 默认值起按 weight 升序 lerp；
   **不可插值的取最高 weight 那份**；输出永远是完整 schema。
3. 参数 `Independent=true` → 该请求**退出合并**，单独完整执行一次。
4. 合并后 `weight < 1e-3` 丢弃。
5. 排序按 `priority` 再按来源路径；**内建 bloom 固定插在 `priority = 0`** ——
   `priority < 0` 跑在 bloom **之前**，`> 0` 跑在之后。
6. **消费时机**：`consumedFrame` 守卫让「效果」每帧只消费一次；
   **没被消费的请求在 `RenderFrameEvent.Post` 丢弃**。

几个容易踩的开关：

- `enableCustomEffects=false` 时 `wantsExecution()` 恒 false，请求被**静默丢弃**；
- 光影包下要跑自定义效果，必须过 `PhotonConfig.enableCustomEffectsWithShaderPack`；
- `PhotonViewSettings.effects` 是**每视图**开关（编辑器用独立 stack，与世界互不泄漏）。

**Mask / CustomDepth**：开启 `customMask` 的发射器会额外画一遍 mask 子 pass
（`R8_UNORM` 颜色 + **自带深度**，所以世界几何能遮挡 mask、它也能当 custom depth 读）。
**没有 pending mask 消费者时这个子 pass 完全不跑**，被标记的发射器零开销。

**HDR / Bloom**：所有绘制落进 `RGBA16_FLOAT`；回写必须用 `SceneBlit.writeBack`
（`WRITE_COLOR`，**不写 alpha**，且必须是 draw 不是 copy）。

## 6.9 生命周期：`isValid()` 为什么是必须的

| 方法 | 语义 |
|---|---|
| `isFinished()` | 时间轴没有未来内容 **且** 没有对象还在 playing |
| `isAlive()` | `!isFinished()` |
| `isValid()` | 已 emit、未 destroy，**且仍被粒子引擎跟踪** |
| `setRate(float)` | 同时缩放模拟与时间轴主时钟；>1 时按 `ceil(rate)` 子步；**不持久化** |

**为什么必须有 `isValid()`**：切维度、`/photon_client clear_particles`、别的模组伸手清粒子时，
粒子引擎会**直接把粒子丢掉**，Runtime 根本没机会走到 `isFinished()`。
`isValid()` 用 host generation + root heartbeat 检测这件事，O(1)，可以每 tick 调。

⚠️ **循环 Emitter 永远不会自然结束** —— 必须显式 `destroy`。

## 6.10 扩展注册表

| 注册表 | id | 注册对象 |
|---|---|---|
| `FX_OBJECTS` | `photon:fx_object` | 带注解的 **static 字段**（`FXObjectType`） |
| `MATERIALS` | `photon:material` | `Supplier<IMaterial>` |
| `NUMBER_FUNCTIONS` | `photon:number_function` | `Supplier<NumberFunction>` |
| `SHAPES` | `photon:shape` | `Supplier<IShape>` |
| `MODEL_SOURCES` | `photon:model_source` | `Supplier<IModelSource>` |
| `TIMELINE_TRACKS` | `photon:timeline_track` | 带注解的 static 字段（`TrackType`） |
| `ANIMATED_PROPERTIES` | `photon:animated_property` | 带注解的 static 字段（`AnimatedPropertyType`） |

`photon:fx_object` / `photon:timeline_track` / `photon:animated_property` 是 `LDLRegistry.String`，
**注册对象必须是带 `@LDLRegisterClient` 的静态字段**，不能是普通实例。

## 6.11 和其他 Mod 的边界

| 事件 | Photon 用它做什么 |
|---|---|
| `RegisterParticleGroupsEvent` | 注册 `photon:fx` 粒子组 |
| `RegisterFeatureRenderersEvent` | 注册 `photon:fx` feature renderer |
| `RenderFrameEvent.Pre` | 材质 / 后处理预览的待办 |
| **`RenderFrameEvent.Post`** | **帧边界**：清请求、回收目标池 |
| `RenderLevelStageEvent.AfterOpaqueFeatures` | 抓「仅不透明」深度快照（给 LATE 合成用） |
| `RenderLevelStageEvent.AfterTranslucentParticles` | 跑后处理链 |
| **`RenderLevelStageEvent.AfterLevel`** | 延迟层合成 + 晚期效果链（**只做全屏合成，不画几何**） |
| `FrameGraphSetupEvent` | 上传引擎 uniform |

**结论**：想「在 Photon 之后画东西」，用 `AfterTranslucentParticles` 之后的事件即可，
**不会**和 Photon 的 `PhotonStage` 抢缝。真正要小心的只有 `AfterLevel`（Photon 在这里合成）
和 `RenderFrameEvent.Post`（全局帧边界）。

## 6.12 关键事实速查

1. `PhotonPostFX.submit` 是**逐帧请求**；不调，下一帧就没了。
2. `FXHelper.getFX` 加载失败返回 **null**，这是正常结果不是异常。
3. FX id **不带** `fx/` 前缀和 `.fx` 后缀。
4. 缓存 Runtime 前**每次都查 `isValid()`**。
5. `allowMulti=false` 时重复 `start()` 会被**静默跳过** —— 自己管去重就设 `true`。
6. `emit` 的 delay 是在 `emit()` **之后**设的（`emit()` 会 reset 清零它）。
7. 材质**共享才是合批的前提**；每实例自有材质无法合批。
8. `IMaterial.getRenderType(...)` 返回 null = 这段几何**静默不画**。
9. `MaskGroups` 的 id 只在**会话内**稳定，持久化永远是组名字符串。
10. **不要直接写「主渲染目标」**，用 `PhotonRenderOutput.color()/depth()`。
11. `PhotonParticleRenderTypes.FX` **必须共用同一单例**，否则按组操作会失效。
12. `PhotonConfig` 是**客户端**配置；关键键：`enable_bloom`、`fx_composite_mode`（默认 LATE）、
    `enable_custom_effects`、`enable_custom_effects_with_shader_pack`、`postfx_pool_budget_mb`。

---

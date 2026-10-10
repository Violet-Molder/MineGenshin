# 12. 附录


## 12.1 术语中英对照

读源码、翻 Photon 编辑器、搜英文资料时，同一个概念经常在三种叫法之间跳。下表按本文用到的顺序收口，
中间一列是源码与编辑器里真正出现的英文写法。

| 中文 | 英文 / 源码写法 | 在本文里指什么 |
|---|---|---|
| 提取 | extract | 把「这一帧要画什么」从游戏对象里摘成纯数据（RenderState）的阶段 |
| 提交 | submit | 把几何登记进提交收集器、交给渲染管线排序的阶段 |
| 绘制 | draw | 管线按状态排序后真正发绘制命令的阶段 |
| 渲染状态 | `*RenderState` | 每帧重建的纯数据对象（实体、相机、关卡各有一套） |
| 渲染管线 | `RenderPipeline` | 着色器 + 混合 + 深度 + 顶点格式打包成的不可变描述 |
| 渲染类型 | `RenderType` | 游戏侧「用哪条管线、绑什么纹理、怎么排序」的命名组合 |
| 顶点消费者 | `VertexConsumer` | 往当前几何缓冲写顶点的入口 |
| 顶点格式 | `VertexFormat` / `VertexFormatElement` | 顶点里有哪些属性、各占几个分量 |
| 姿态栈 | `PoseStack` | 矩阵栈，负责从局部空间一路乘到相机空间 |
| 相机空间 | camera space / view space | 提交几何时顶点所处的坐标系，原点是相机 |
| 裁剪空间 | clip space | 顶点着色器输出、投影之后的坐标系 |
| 骨骼 | bone / `GeoBone` | 模型里可动画的节点 |
| 定位点 | locator / `GeoLocator` | 骨骼上标注出来的空挂点（挂武器、挂特效用） |
| 骨骼快照 | bone snapshot | 某一帧骨骼位姿的只读拷贝，可以带出渲染回调使用 |
| 渲染层 | render layer | 挂在主渲染器之后、负责追加几何的一层 |
| 渲染通道 | render pass / pass | 同一批状态下的连续绘制 |
| 特效 | FX（`.fx`） | Photon 编辑器里编辑、运行时按名字实例化的资产 |
| 特效包 | `.fxpack` | 把多个 FX 与依赖资源打进一个文件的容器 |
| 发射器 | emitter | FX 里负责产生粒子 / 光束 / 拖尾的节点 |
| 模块 | module | 挂在发射器上、逐帧改粒子数据的可组合单元 |
| 运行时 | runtime / `FXRuntime` | FX 在游戏里的一份实例及其每帧状态 |
| 运行时数据注入 | RuntimeValue | 每帧往运行时对象里写游戏侧数据（位置、颜色、进度）的通道 |
| 执行器 | executor / `IEffectExecutor` | 决定「特效跟谁、活多久、什么时候销毁」的对象 |
| 时间线 | timeline | 一段带轨道与信号的动画时间轴 |
| 轨道 | track | 时间线上按时间驱动某一类属性的行 |
| 信号 | signal | 时间线上发出的事件点，Java 侧可以接 |
| 后处理 | post-processing | 在画面画完之后对整屏做的效果（辉光、扭曲、暗角） |
| 实例化 | instancing | 一次绘制画一批同类对象，用实例数据区分 |
| 绘制调用 | draw call | 一次实际的绘制命令，数量直接决定 CPU 侧开销 |
| 蒙皮 | skinning | 用骨骼矩阵把静止顶点变形到当前姿态 |
| 矩阵调色板 | matrix palette | 把所有骨骼矩阵打包进 GPU 缓冲的那块数据 |
| 统一缓冲 | UBO / uniform block / std140 | 一次上传、着色器里按块读取的常量数据 |

## 12.2 类名速查

只列正文提到过的承重类；包名照抄即可，改版本后值得逐条复核。

**Minecraft / Blaze3D**

| 用途 | 类 |
|---|---|
| 姿态栈（矩阵栈） | `com.mojang.blaze3d.vertex.PoseStack` |
| 提交几何 | `net.minecraft.client.renderer.SubmitNodeCollector`（父接口 `OrderedSubmitNodeCollector`） |
| 渲染管线 | `com.mojang.blaze3d.pipeline.RenderPipeline` / `RenderPipeline.Snippet` / `CompiledRenderPipeline` |
| 原版管线常量 | `net.minecraft.client.renderer.RenderPipelines` |
| 渲染类型 | `net.minecraft.client.renderer.rendertype.RenderType` / `RenderTypes` / `RenderSetup` |
| 顶点与几何 | `com.mojang.blaze3d.vertex.VertexFormat` / `VertexFormatElement` / `DefaultVertexFormat` / `VertexConsumer` / `MeshData` |
| 设备与通道 | `com.mojang.blaze3d.systems.GpuDevice` / `CommandEncoder` / `RenderPass` / `RenderSystem` |
| 缓冲与 uniform | `com.mojang.blaze3d.buffers.GpuBuffer` / `GpuBufferSlice` / `Std140Builder` / `Std140SizeCalculator` |
| 着色器加载 | `net.minecraft.client.renderer.ShaderManager` + `com.mojang.blaze3d.shaders.ShaderType` |
| 着色器变体定义 | `net.minecraft.client.renderer.ShaderDefines` |
| 渲染状态 | `net.minecraft.client.renderer.state.level.LevelRenderState` / `CameraRenderState` |
| 帧与关卡 | `net.minecraft.client.renderer.GameRenderer` / `LevelRenderer` / `LevelExtractor` |

**NeoForge 事件**（都在 `net.neoforged.neoforge.client.event`）

| 事件 | 用途 |
|---|---|
| `RenderLevelStageEvent.After*` | 帧内八个阶段钩子（顺序见 §1.6） |
| `ExtractLevelRenderStateEvent` | 自定义渲染状态必须在这一步摘出来 |
| `SubmitCustomGeometryEvent` | 不写渲染器也能提交自定义几何 |
| `RenderFrameEvent.Pre` / `.Post` | 渲染帧边界（本项目用它推进渲染帧号） |
| `RegisterRenderPipelinesEvent` | 注册自定义 `RenderPipeline`（mod 总线、仅客户端） |

**GeckoLib 5**

| 用途 | 类 |
|---|---|
| 渲染器骨架（基类） | `com.geckolib.renderer.base.GeoRenderer` / `GeoRendererInternals` |
| 通用对象渲染器 | `com.geckolib.renderer.GeoObjectRenderer` |
| 单趟渲染的上下文对象 | `com.geckolib.renderer.base.RenderPassInfo` |
| 挂在渲染器上的层基类 | `com.geckolib.renderer.layer.GeoRenderLayer` |
| 每骨骼回调 | `com.geckolib.renderer.base.PerBoneRender` / `RenderPassInfo.BoneUpdater` / `BonePositionListener` |
| 骨骼与模型 | `com.geckolib.cache.model.GeoBone` / `BakedGeoModel` / `GeoLocator` |
| 骨骼快照 | `com.geckolib.animation.state.BoneSnapshot` / `com.geckolib.renderer.base.BoneSnapshots` |
| 层间传数据 | `com.geckolib.constant.dataticket.DataTicket` / `com.geckolib.constant.DataTickets` |

**Photon2**（`com.lowdragmc.photon.*`）

| 用途 | 类 |
|---|---|
| 定义与加载 | `client.fx.FX` / `FXData` / `FXHelper` |
| 运行时实例 | `client.fx.FXRuntime` |
| 执行器 | `client.fx.IEffectExecutor` / `IFXEffectExecutor` / `FXEffectExecutor` / `BlockEffectExecutor` / `EntityEffectExecutor` |
| 运行时数据注入 | `client.gameobject.RuntimeValue` / `RuntimeBinding` |
| 对象模型 | `client.gameobject.FXObject` / `IFXObject` / `FXObjectType` |
| 发射器 | `client.gameobject.emitter.Emitter` / `IParticleEmitter`、`...emitter.particle.ParticleEmitter` / `ParticleConfig` / `ParticleRuntime` |
| 拖尾 / 光束 / 模拟拖尾 | `...emitter.aratrail` 与 `TrailRuntime` / `BeamRuntime` / `AraTrailRuntime` |
| 力场对象 | `client.gameobject.ForceFieldObject` + `ForceFieldConfig` |
| 时间线 | `client.fx.timeline.TimelinePlayer` / `Signal` / `PhotonSignals` |
| 后处理 | `client.postfx.PhotonPostFX` / `client.postfx.runtime.PostEffectStack` |
| 附加 GPU 数据 | `client.gameobject.emitter.data.AdditionalGPUDataSetting` / `PhotonGpuChannels` |
| 特效包 | `client.fx.fxpack.FXPacks` |
| 编辑器 | `gui.editor.FXEditor` / `FXProject` |
| 网络 payload | `command.EffectCommand` / `BlockEffectCommand` / `EntityEffectCommand` / `RemoveBlockEffectCommand` / `RemoveEntityEffectCommand` |
| 注册与配置 | `PhotonRegistries` / `PhotonConfig` / `PhotonNetworking` |
| 原版侧渲染衔接 | `client.render.PhotonWorldRenderState` / `PhotonStage` / `PhotonPipelines` / `PhotonParticleGroup` / `PhotonFXLayer` |
| 调试命令 | `client.ClientCommands` / `client.PhotonParticleManager` |

**本项目**（`com.linweiyun.genshin.*`）

| 用途 | 类 |
|---|---|
| 角色渲染调度 | `client.render.character.CharacterRenderDispatcher` |
| 角色渲染器 | `client.render.character.CharacterRenderer` |
| 武器/挂点渲染层 | `client.render.character.WeaponAnchorGeoLayer` / `BoneMountGeoLayer` |
| 挂点缓存 | `client.render.character.WeaponAnchorCache` |
| 特效锚点 | `client.fx.FxAnchor` / `AnchorPose` / `FixedPointExecutor` |
| 示例特效接线 | `client.fx.TestCharacterFx` |
| GPU 蒙皮 | `client.render.optimize.gpu.BoneMatrixPalette` / `SkinDataStorage` / `SkinnedFeatureRenderer` / `SkinnedPipelines` / `SkinnedMesh` / `SkinnedGpuTimer` |
| 公共侧技能钩子 | `core.character.talent.SkillCastHooks` |

## 12.3 一页纸检查清单

### 新增一个自定义渲染层

- [ ] 渲染层是否只挂在**客户端**（在专用服务器上不能被触碰）？
- [ ] 需要的数据是否在 `ExtractLevelRenderStateEvent` / 实体提取阶段就摘好？拿游戏对象引用会在渲染期崩。
- [ ] 写世界坐标时，有没有把相机位置加回去（提交几何的 `PoseStack` 原点是相机）？
- [ ] 要延迟执行的几何，是否把 `PoseStack.Pose` 拷了出来，而不是在 lambda 里用外层的栈？
- [ ] 是否确认了几何不会被更早/更晚的批次盖掉（提交顺序不等于绘制顺序）？

### 新增一个特效（FX）

- [ ] `.fx` 与它依赖的贴图 / 着色器放在 `assets/<modid>/` 下的哪条路径，是否与代码里引用的名字一致？
- [ ] 这个特效是**跟随**（实体、骨骼、方块）还是**定点**？锚点从哪来、由谁推进？
- [ ] 生命周期谁管：常驻跟随靠每帧条件判断，定点靠自己的计时或状态机？
- [ ] 世界切换、维度跳转、`/photon` 清理命令之后，特效会不会变成「代码以为还在、引擎已经没有」的状态？重建逻辑写了吗？
- [ ] 需要从游戏侧喂进去的数据（颜色、进度、目标位置）走哪条注入通道，什么时候写、写在第几帧？

### 从服务端触发

- [ ] 服务端**不加载** Photon 的客户端类；触发只能通过现成的 payload 或自定义网络包？
- [ ] 广播范围：只给附近玩家，还是全服？（按距离裁剪，别让全服一起炸粒子）
- [ ] 专用服务器上跑一遍会不会因为引用客户端类而崩？

### 发布前

- [ ] 资源是否都打进 jar（`processResources` 之外临时加的目录会被漏掉）？
- [ ] 资源包 / 数据包侧的引用路径是否与开发环境一致（大小写、命名空间）？
- [ ] 性能：粒子数、材质切换、后处理层数是否在预算内？先合并批次、再谈降数量。
- [ ] 文档与[更新日志](/doc/changelog)是否同步（文档站实时渲染仓库里的 Markdown，改完刷新即可）？

## 12.4 本文的边界与维护约定

**事实优先级**：本机源码 jar（Minecraft / NeoForge / Photon / GeckoLib）> 本项目源码 > 官方文档站。
任何一条与源码冲突的描述，以源码为准；本文里凡是没能在源码里找到直接证据的结论，都写了「未确认」。

**核对方法**：正文里出现的类名、方法名、事件名都能在本机搜索到，搜索路径见
[§0.3](/doc/rendering-26.2-reference-intro#03-怎么核对本文的每条说法)；项目侧结论都给了 `文件:行号`。

**维护约定**：

- 本文是仓库里的 Markdown（`docs/rendering-photon2-reference.md`），文档站请求时实时渲染，改完刷新即生效，不需要构建。
- 新增 / 改名文档时，同步 `web/src/main/java/com/linweiyun/minegenshin/web/docs/DocCatalog.java` 的文档清单
  与 `web/src/main/resources/static/assets/docs.js` 的兜底菜单（两处 slug 必须一致，否则侧边栏会少条目）。
- 影响使用者或存档的渲染 / 特效行为变更，按仓库约定记进根目录更新日志（站点页：[更新日志](/doc/changelog)）的对应版本小节。
- 升级 Minecraft、NeoForge、Photon2、GeckoLib 之后，本文里所有类名与事件名都值得重新核一遍；
  Blaze3D 与 `RenderState` 体系在版本之间改动频繁，本文已按 26.2 的口径写死。

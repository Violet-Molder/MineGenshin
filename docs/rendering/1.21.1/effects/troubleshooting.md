# 9. 排错手册

## 9.1 按症状查

| 症状 | 先查 |
| --- | --- |
| 完全没有画面 | 事件没挂 / 阶段选错；忘 `endBatch`；顶点写在 `popPose` 之后 |
| 只在某些角度可见 | 面剔除（`NO_CULL`）、视锥剔除 |
| 全黑 / 全白 | 采样器没绑、`fragColor` 没赋值 |
| 洋红 | 贴图路径不存在 |
| UV / 颜色错位 | `VertexConsumer` 调用顺序与 `VertexFormat` 不一致 |
| 位置抖动 | tick 与 frame 都写位置；或没插值 |
| 偏半格 | 摆件原点补偿 |
| 远处抖动 | 先转 `float` 再减相机坐标 |
| 模型不播动画 | `.animation.json` 键名；模型解析链 |
| 特效只播一次 | 执行器提前结束；存活判据用了时间轴时钟 |
| 退出世界还在响 | 没处理 `LevelEvent.Unload` |
| 专用服务器崩 | 服务端加载了 `client` 包 |
| 开光影黑掉 | `enable_custom_effects_with_shader_pack`；Iris 接管 |

## 9.2 常用命令

```text
F3                      帧时间 / 坐标
/reload                 资源重载（着色器、模型、几何缓存重建）
/photon clear client cache fx   清 .fx 解析缓存
```

## 9.3 日志形态

| 日志 | 含义 |
| --- | --- |
| `Failed to compile shader` + 行号 | core shader 编译失败 |
| `Unable to find animation` | GeckoLib 找不到动画文件 / 键名 |
| `NoClassDefFoundError ...client...` | 服务端加载了客户端类 |
| Mixin 注入失败 | 目标方法签名变了（版本对不上） |

## 9.4 二分定位

1. 关掉本模组特效 → 帧时间恢复 = 本模组的锅；
2. 切原版资源包 → 现象变化 = 资源 / 模型 / 着色器；
3. 关光影 → 现象变化 = Iris 兼容层；
4. 最小重现（一个 `.fx` / 一个实体 / 一份 shader）；
5. 回退到上一个提交，定位引入点。

深入：[完全参考 11. 排错手册](/doc/rendering-1.21.1-reference-troubleshooting)。
# 9. 排错手册


## 9.1 按症状查

| 症状 | 优先怀疑 |
| --- | --- |
| 特效不出现 | `.fx` 路径 / 命名空间写错；`FXHelper` 缓存没清；资源重载后没重建 |
| 特效出现一次就没了 | `FXRuntime` 被 `remove(force=true)`；执行器提前 `retire` |
| 位置抖动 | tick 与 frame 都写位置（§6.3） |
| 位置偏 | 摆件 +0.5 平移（§4.6）；模型以方块角为原点 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` 关着；Iris 接管了管线 |
| 模型不播动画 | GeckoLib 4 的键名 / 模型供给链（§4.6） |
| 动态光不亮 | `dynamic_light.enabled=false`；`shadowed_lights` 用满；光的位置在相机背后 |

## 9.2 Photon 的调试命令

```text
/photon clear client cache fx        清 .fx 解析缓存
/photon ... convert                  Photon 1 的 fx 转成 2.x（需要权限 2）
```

(完整子命令见 `com/lowdragmc/photon/client/ClientCommands.java`。)

## 9.3 客户端 / 服务端隔离

- `com.lowdragmc.photon.client.*` 全部是 `@OnlyIn(Dist.CLIENT)`；
- 服务端只能发「播什么特效、播在哪」的意图；
- 本项目 `FixedPointExecutor` 这类类只在客户端 tick 里创建，任何服务端代码都不要 import。

---

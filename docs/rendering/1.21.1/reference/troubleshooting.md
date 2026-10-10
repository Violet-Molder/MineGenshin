# 11. 排错手册与性能


## 11.1 按症状查

| 症状 | 优先怀疑 |
| --- | --- |
| 特效不出现 | `.fx` 路径 / 命名空间写错；`FXHelper` 缓存没清；资源重载后没重建 |
| 只出现一次 | `FXRuntime` 被提前 `remove(force=true)` / `retire` |
| 位置抖动 | tick 与 frame 都写位置（§9.1） |
| 位置偏 | 摆件 +0.5 平移（§5.6）；模型以方块角为原点 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` 关着；Iris 接管了管线 |
| 模型不播动画 | GeckoLib 4 的键名 / 模型供给链（§5.6） |
| 动态光不亮 | `dynamic_light.enabled=false`；`shadowed_lights` 用满；光源在相机背后 |
| 一进世界就掉帧 | 动态光 / 体积雾开着且盏数多；`voxel_budget_ms` 太松 |

## 11.2 常用命令与开关

```text
F3                                  帧时间
/photon clear client cache fx       清 .fx 解析缓存
/photon … convert                   Photon 1 → 2 的 fx 转换（权限 2）
config/photon-client.toml           客户端配置
```

## 11.3 性能预算参考

| 项 | 建议上限 | 依据 |
| --- | --- | --- |
| 同屏角色模型 | 依模型面数而定，先测 10 个同模型 | §7.2 CPU 路径 |
| 动态光（带阴影） | ≤ 8 盏 | `shadowed_lights` 默认 8 |
| 体积光采样 | 4（默认） | `volumetric_samples` |
| 动态光体素预算 | 2ms | `voxel_budget_ms` |
| 后处理池 | 256 MB | `postfx_pool_budget_mb` |

---

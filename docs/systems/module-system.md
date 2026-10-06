# 模块系统（26.2.0.2 起）

## 这个系统负责什么

给宿主挂「一份可拓展的数据」：elementlib 内置元素模块，本模组注册韧性模块。
宿主种类、模块类型、容器、存取都走同一套入口，行为层不再区分"是实体还是方块"。

## 一张图

```
攻击（ElibAttackPipeline）
  └─ 收集宿主（ElibModuleQuery：实体 + 方块）
       └─ 每个宿主一个 ElibModuleContainer
            ├─ elementlib:element   （元素，数据对象是 StatusContainer）
            └─ minegenshin:toughness（韧性） / minegenshin:block_toughness
```

## 关键类型（elementlib）

| 类型 | 作用 |
| --- | --- |
| `ElibModuleType<T>` | 注册项类型：支持哪些宿主、持久化与同步策略、工厂与编解码 |
| `ElibModuleData` | 注册项实例数据 |
| `ElibModuleContainer` | 一个宿主身上的全部模块数据 |
| `ElibModuleHost` / `ElibModuleHosts` | 宿主适配器与工厂 |
| `ElibModuleTargetKind(s)` | 宿主种类（`elementlib:entity/block/item`，本模组加 `minegenshin:character`） |
| `ElibModuleQuery` | 一次收集范围内所有模块（实体 + 方块） |
| `ElibModuleAttachments` | `elementlib:modules`（实体）、`elementlib:chunk_modules`（区块）、`elementlib:item_modules`（物品组件） |

## 存储与生命周期

| 宿主 | 存储 | 备注 |
| --- | --- | --- |
| 实体 | 附件 `elementlib:modules` | 存档 + 同步 |
| 方块 | 区块 `elementlib:chunk_modules` 持久区 | 元素等长期数据 |
| 方块（瞬态） | 同附件的瞬态区 | 方块韧性：区块卸载即丢，重载回满 |
| 物品 | DataComponent `elementlib:item_modules` | 阶段 1 只读 |
| 角色 | `PGCharacterData.moduleContainer` | 旧 `status_container` 首次访问时迁移 |

## 攻击入口

`ElibAttackPipeline.dispatch(action)` 的三种触发：

| 触发 | 来源 |
| --- | --- |
| `ACTION_DAMAGE_POINT` | 动作系统每个伤害点（`ActionState.fireDamagePoint`），带这一下的实际削韧值 |
| `BLOCK_LEFT_CLICK` | `PlayerInteractEvent.LeftClickBlock`（START），每次左键一次；创造模式同样触发 |
| `ENTITY` | lib 默认桥（本模组关闭） |
| 伤害落点 | `LivingEntityHurtMixin` 见到带元素的 `ModDamageSource` → `ElibAttackPipeline.dispatchAround(..., checkGate=false)` |

本模组在 `ElementLibBridge` 里注入：

- 门禁：只有原神模式才进管线；
- 元素解析：取当前出战角色的元素；
- 方块关注点与监听器：`ToughnessAttackListener`。

## 两类目标都走同一入口

| 目标 | 入口 | 说明 |
| --- | --- | --- |
| 方块 | `dispatch(action)` 几何扫掠 | 命中 `BlockElementRules` 的方块走 `BlockElementHelper.applyElementAndGet`（含落盘与方块状态迁移） |
| 实体 | `dispatchOn(action, target, reactionUnit)` | 伤害管线的精确实体目标；左键、技能命中、元素剑命中都走它 |

实体不走几何近似：范围盒子里的实体不一定真的被打中。`DirectDamagePipeline` 只在门禁没过
（例如非玩家来源）时才回退到直连附着，保证非原神模式的旧行为不变。

伤害落点这条覆盖所有不经过动作伤害点的元素伤害：领域实体的持续伤害、下落攻击落地、
召唤物伤害等。它按伤害落点周围 2 格扫方块，并且同一格在同一 game tick 内只会吃到一个元素
（`BlockElementHelper` 内部去重），不会和动作扫掠重复附着。

## 加一种新模块

在 `ElibModuleRegistry.register` 里声明类型，然后按宿主取用：

```java
ElibModuleHost host = ElibModuleHosts.of(entity);
PoiseState state = host.ensure(ToughnessTypes.TOUGHNESS);
```

不改容器、宿主、查询三层的任何代码。

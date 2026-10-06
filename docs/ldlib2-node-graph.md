# LDLib2 节点图工具包（Node Graph Toolkit）

> **本文针对的版本**：LDLib2 `26.2.2.41.a`（本仓库 `gradle.properties` 里锁定的版本）
> **官方文档**：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/>
> **想先看真实用例**：Photon2 的 **Shader Graph / Fullscreen Graph / Render Graph 全部构建在这套框架上**，
> 所以本仓库依赖树里就有一份生产级用法可读（见 §12）。

## 1. 它解决什么问题

如果你要在 Minecraft 里做一个「拖节点、连线条、改参数、存成资源文件」的编辑器，
需要自己写的东西非常多：图的数据模型与撤销、端口类型系统与连线合法性、
画布缩放/框选/吸附、变量面板、子图、保存与加载……

**Node Graph Toolkit 就是 LDLib2 把这套东西做完之后的产物。**
你只需要三件事：

1. 定义自己的 `Graph` 子类（声明这个图支持哪些节点）；
2. 定义若干 `Node` 子类（声明端口、选项、预览）；
3. 用 `GraphEditorView` 把它挂到编辑器里。

其余（撤销栈、连线合法性、Blackboard、子图下钻、面包屑、资源面板）都是现成的。

### 1.1 先记住这个拼写

包名是：

```
com.lowdragmc.lowdraglib2.nodegraphtookit
                                 ^^^^^^
```

**源码里就是 `tookit`（少一个 `l`）**，不是 `toolkit`。
这是搜索/import 时最容易卡住的地方 —— IDE 里补全不出来时，先怀疑这个拼写。

## 2. 五个核心对象

| 对象 | 包 | 职责 |
|---|---|---|
| **`Graph`** | `api.graph` | **面向使用者的图定义**：这个图支持哪些节点类、哪些类型、哪些变量种类 |
| **`GraphModel`** | `model.graph` | **实际的图状态**：节点、端口、连线、变量、子图、Placemat、Sticky Note、变更追踪 |
| **`GraphView`** | `gui` | 画布 + 面板宿主（缩放、拖拽、连线、框选） |
| **`GraphEditorView`** | `editor` | **推荐的编辑器入口**：包装 `GraphView`，再加保存、dirty、面包屑、子图下钻 |
| **`GraphResource`** | `editor` | 把图接进 LDLib2 Editor 的资源系统（能出现在资源面板里、能被保存） |

它们的关系：

```
GraphResource<G>            ← 资源系统里的一份「图资源」
   └ createGraph() → G extends Graph     ← 你的定义（无状态）
                        └ graphModel : CustomGraphModelImpl   ← 真实状态（有状态、可序列化）
GraphEditorView
   └ loadGraph(Graph, onSaved)  → 把它变成可编辑的界面
        └ GraphView（画布）
```

**关键分界**：`Graph` 是**定义**（单例语义、无状态），`GraphModel` 是**状态**（一份图 = 一个 model）。
不要往 `Graph` 里存节点状态。

## 3. 包结构

| 包 | 内容 |
|---|---|
| `api.graph` | `Graph`、`IGraph`、`GraphNodeRegistry`、`GraphLogger` |
| `api.node` | `Node`、`INode`、`NodeAttribute`、`ContextNode`、`BlockNode`、`IConstantNode`、`IVariableNode`、`ISubgraphNode`、`INodeOption` |
| `api.port` | `IPort`、`PortDirection`、`PortCapacity`、`PortOrientation`、`PortType`、`PortConnectorUI`、`IInputPortBuilder`、`IOutputPortBuilder` |
| `api.type` | `TypeHandle`、`TypeHandles`、`TypeHandleHelpers`、`ITypeConfigurable` |
| `api.variable` | `IVariable`、`VariableKind` |
| `model.graph` | `GraphModel`、`CustomGraphModelImpl`、`ElementsByType`、`PortWireIndex` |
| `model.node` | `NodeModel`、`PortModel`、`definition/IPortDefinitionContext`、`definition/IOptionDefinitionContext` |
| `model.wire` / `model.variable` / `model.constant` / `model.group` | 连线、变量、常量节点、Placemat 等模型 |
| `gui` | `GraphView`、`GraphPanel`、`GraphInspector`、`GraphBreadcrumb`、`GraphPreview`、`WireElement`、`NodeElement`、`PlacematElement` |
| `gui.command` | `IGraphCommand` 与内置命令（删除、移动、粘贴、复制、建节点、连线……） |
| `gui.blackboard` | Blackboard 面板 |
| `gui.layout` | 自动布局（`LayeredLayout` 等） |
| `gui.itemlibrary` | 节点库（可拖出来的节点列表） |
| `editor` | `GraphEditorView`、`GraphResource` |

## 4. 最小可用示例

### 4.1 定义一个节点

节点的全部契约是：**注解声明身份 + 实现显示名 + 声明端口**。

```java
package com.example.graph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

@NodeAttribute(
        name = "example_add",                       // 注册名（唯一）
        group = "example_math",                     // 节点库里的分组
        graphTypes = {ExampleGraph.class}           // 这个节点属于哪些图
)
public class AddNode extends Node {

    @Override
    public Component getDisplayName() {
        return Component.literal("Add");            // 唯一抽象方法，必须实现
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", TypeHandles.FLOAT).withDefaultValue(0.0f);
        context.addInputPort("b", TypeHandles.FLOAT).withDefaultValue(0.0f);
        context.addOutputPort("sum", TypeHandles.FLOAT);
    }
}
```

三个要点：

- **`@NodeAttribute.graphTypes` 是硬约束**：节点只会出现在它声明的图里。一个节点想同时给多个图用就都列上
  （Photon 的 `DepthFadeNode` 就同时声明了 `ShaderGraph` 与 `PhotonShaderFunctionGraph`）。
- **端口用字符串 id 标识**，后续取值/连线都靠这个 id。
- **`withDefaultValue(...)` 让输入端口在没连线时可用**：端口自带一个内联常量编辑器，
  没连线就读它。这是「输入端口没接也能工作」的实现方式，不是魔法默认值。

### 4.2 定义一个图

```java
public class ExampleGraph extends Graph {

    /** 这个图的节点注册表。Graph 类加载时建立，声明支持哪些节点。 */
    public static final GraphNodeRegistry REGISTRY = GraphNodeRegistry.create(
            Identifier.fromNamespaceAndPath("minegenshin", "example_graph"),
            ExampleGraph.class);

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return REGISTRY.getNodeClasses();
    }
}
```

`Graph` 上还可以覆写的钩子（都是**可选**的）：

| 方法 | 默认行为 | 用途 |
|---|---|---|
| `getSupportNodes()` | **抽象** | 支持的节点类 |
| `getSupportTypes()` | `null` = 从节点端口自动探测 | 支持的 `TypeHandle`。**覆写是替换而不是追加**，要保留自动探测得自己 union |
| `getLibrarySupportNodes()` | 同 `getSupportNodes()` | 节点库里显示哪些节点 |
| `getLibrarySupportTypes()` | 可作者化的类型子集 | 常量节点库里显示哪些类型 |
| `getVariableSupportTypes()` | 同 `getSupportTypes()` | 建/改变量时类型选择器里有什么 |
| `getSupportedSubgraphVariableKinds()` | `{INPUT, OUTPUT}` | 当这个图被当作子图时，哪些变量能暴露成端口 |
| `acceptsSubgraphGraph(Graph other)` | `false` | 是否接受**其它类型**的图作为子图 |
| `canExecuteCommand(IGraphCommand)` | `true` | **命令拦截**：所有会改动图的操作都先过这里 |
| `onCommandExecuted(IGraphCommand)` | 空 | 命令执行后的副作用 |
| `onGraphChanged(GraphLogger)` | 空 | 图加载/刷新后做校验，往日志里发诊断 |

### 4.3 打开编辑器

```java
GraphEditorView view = ...;                       // 由 GraphResource.getGraphViewFactory() 提供
view.loadGraph(new ExampleGraph(), tag -> {
    // 保存回调：tag 是要写进资源的 NBT
});
```

`GraphEditorView` 的公开 API：

| 方法 | 用途 |
|---|---|
| `loadGraph(Graph, Consumer<CompoundTag> onSaved)` | 载入一份图，并绑定保存回调 |
| `serializeGraph()` | 把当前图序列化成 `CompoundTag` |
| `setReadOnly(boolean)` | 只读模式 |
| `clear()` | 清空 |
| `markAsDirty()` / `clearDirty()` / `notifySaved()` | 脏标记 |
| `getCurrentView()` | 拿到内部的 `GraphView` |
| `enterSubgraph(SubgraphNodeModel)` / `enterExternalGraph(...)` / `popToLevel(int)` | 子图下钻与面包屑 |
| `setRootPath(IResourcePath)` | 关联资源路径 |
| `screenTick()` | 每 tick 推进（由宿主调用） |

### 4.4 接进资源系统

```java
public abstract class GraphResource<G extends Graph> extends Resource<CompoundTag> {
    public abstract G createGraph();                       // 新建一份空图
    public CompoundTag serializeGraphResource(G graph);    // 存
    public G deserializeGraphResource(CompoundTag tag, @Nullable IGraphReferenceResolver resolver);  // 读
    public Supplier<? extends GraphView> getGraphViewFactory();   // UI 工厂
}
```

`IGraphReferenceResolver` 是给「图里引用了别的图」用的（子图 / 外部图引用），
反序列化时靠它把路径解析成真的图实例。

## 5. 端口：方向、容量与语义

### 5.1 方向

```java
public enum PortDirection { NONE, INPUT, OUTPUT }
```

`getOpposite()` 可以取反。`NONE` 表示这个端口不参与连线（纯展示用）。

### 5.2 容量

```java
public enum PortCapacity { NONE, SINGLE, MULTIPLE }
```

| 取值 | 含义 |
|---|---|
| `SINGLE` | 最多一条连线（典型的输入端口） |
| `MULTIPLE` | 可以接多条（典型的输出端口、汇总型输入） |
| `NONE` | 不可连线 |

**容量决定连线合法性**。超容量的连线在 UI 层就会被拒绝，不需要你自己挡。

### 5.3 端口构建器

```java
public interface IInputPortBuilder<T extends IInputPortBuilder<T>> extends IPortBuilder<T> {
    T withConfigurable(ITypeConfigurable configurable);  // 自定义这个端口的常量编辑器
    T withFieldContext(Field field, Object owner);       // 绑定到一个 Java 字段
    T withCodec(Codec<?> codec);                         // 自定义序列化
    T withoutSerialization();                            // 不参与序列化
    T withoutConfigurator();                             // 不显示常量编辑器
}
```

`withFieldContext(Field, Object)` 是「端口 ↔ Java 字段」的桥：
声明一次，端口的读写就自动落到那个字段上，不用手写 getter/setter 分支。

`withDefaultValue(...)`（上面示例里用的）来自 `IPortBuilder`，
作用是把默认值写进端口的常量编辑器。

### 5.4 端口命名

端口 id 是**你自己起的字符串**，但有两个地方会被用到，起名时要有意识：

- **代码里取值**：`ctx.input("distance")` / `ctx.output("fade", expr)`；
- **UI 显示**：显示名通常由 id 推导（或在构建器上另设）。

## 6. 类型系统：`TypeHandle`

### 6.1 为什么不用 `java.lang.reflect.Type` 就完事

`TypeHandle` 是图里的「类型」概念，它比 `Type` 多了三样必需品：

- **常量**能不能被「作者化」（写成一个字面量节点）；
- **默认值**；
- **图标 / 颜色 / configurator**（UI 怎么显示、怎么编辑）。

`TypeHandleHelpers.fromType(Type)` 能把普通 `Type` 转成 `TypeHandle`，
所以 `context.addInputPort("x", SomeType.class)` 这种写法也是通的。

### 6.2 内置类型（`TypeHandles`）

| 分类 | 常量 |
|---|---|
| 特殊 | `AUTOMATIC`、`MISSING`、`UNKNOWN`、`MISSING_PORT` |
| 控制流 | `EXECUTION_FLOW` |
| 子图 | `SUBGRAPH` |
| 标量 | `BOOL`、`VOID`、`CHAR`、`DOUBLE`、`FLOAT`、`INT`、`LONG`、`STRING`、`OBJECT` |
| 颜色 | `COLOR`、`HDR_COLOR` |
| 游戏对象 | `DIRECTION`、`BLOCK`、`ITEM`、`FLUID`、`ENTITY_TYPE`、`ITEM_STACK`、`FLUID_STACK` |

几个特殊类型值得单独说：

- **`EXECUTION_FLOW`** —— 执行流端口，用来表达「顺序」而不只是数据依赖。
- **`SUBGRAPH`** —— 子图端口。
- **`AUTOMATIC`** —— 「类型待定」，通常用于让连线时反推类型。
- **`MISSING` / `MISSING_PORT`** —— 反序列化时找不到对应类型/端口的占位，**不是错误而是容错**。
  如果你升级了节点却保留了旧图，看到这两个就说明图里有「已经不存在的端口」，要在 `onGraphChanged` 里报出来。

### 6.3 自定义类型

走 `TypeHandle` 的注册/描述路径（`TypeHandleDescriptor`），
并提供：显示名、颜色、图标、默认值、以及一个 `ITypeConfigurable`（决定这个类型的常量在端口上怎么编辑）。

**「可作者化」是个独立属性**：一个类型只要能进端口就应该出现在类型选择器里，
但只有「字面量能被作者化」的类型才应该出现在**常量节点库**里。
源码里 `Graph.getLibrarySupportTypes()` 的注释明确解释了这一点 ——
给一个没法真正产生值的类型提供拖拽常量，比不提供更糟（节点能建出来、但只能产生 null）。

## 7. 变量与 Blackboard

| 概念 | 说明 |
|---|---|
| `IVariable` | 图里声明的一个变量（有名字、类型、默认值、kind） |
| `VariableKind` | `LOCAL` / `INPUT` / `OUTPUT` |
| `IVariableNode` | 「引用某个变量」的节点（取值/赋值） |
| **Blackboard** | 编辑器面板：列出/增删改变量 |

**变量 ≠ 变量节点**：`Graph.getVariables()` 返回的是**声明**，
变量节点是图里引用声明的那些节点，二者不是一回事（`getNodes()` 会把变量节点算进去，变量声明不会）。

`INPUT` / `OUTPUT` 的变量有额外含义：**当这个图被当作子图使用时，它们会成为子图节点的端口**。
`getSupportedSubgraphVariableKinds()` 控制允许哪些方向暴露；
`LOCAL` 变量不受这个开关影响，永远可用。

## 8. 子图

两种形态：

| 形态 | 说明 |
|---|---|
| **本地子图**（inline） | 子图嵌在本图里，跟着本图一起序列化 |
| **外部子图**（external reference） | 引用另一份图资源，靠 `IGraphReferenceResolver` 解析 |

- 同类型子图**总是允许**；
- 跨类型子图要覆写 `acceptsSubgraphGraph(Graph other)` 显式放行；
- `GraphEditorView.enterSubgraph(...)` / `enterExternalGraph(...)` / `popToLevel(...)`
  负责下钻与返回，`GraphBreadcrumb` 显示当前位置。

## 9. Context 节点与 Block 节点

这一对是「一个节点内部包含一串有序子节点」的表达方式（类似逻辑图里的 Sequence、
或着色器里的「主函数体」）：

- **`ContextNode` / `IContextNode`**：容器节点，拥有一个**有序的 block 列表**；
- **`BlockNode` / `IBlockNode`**：只能存在于某个 context 内部。

⚠️ **`BlockNode` 不出现在 `Graph.getNodes()` 里** —— 它只能通过父 `ContextNode` 访问。
遍历全图时漏掉这一点会以为节点丢了。

Photon 的 `FullscreenOutputBlock` 就是这一类角色（全屏图的输出块）。

## 10. 命令、能力与自定义点

### 10.1 命令（所有编辑操作）

编辑器里**每一个会改动图的操作**都是一个 `IGraphCommand`，
统一走 `GraphView.dispatchCommand`。这带来三个自定义点：

```java
// 1) 拦截：返回 false 阻止执行
@Override
public boolean canExecuteCommand(IGraphCommand command) {
    if (command instanceof GraphCommands.DeleteElementsCommand del) {
        return del.elementsToDelete.stream().noneMatch(this::isProtected);
    }
    return true;
}

// 2) 事后反应
@Override
public void onCommandExecuted(IGraphCommand command) { ... }

// 3) 校验/诊断（图加载或刷新后）
@Override
public void onGraphChanged(GraphLogger logger) { ... }
```

**拦截 vs 能力**：如果某个元素是「永远不能被删」，正确做法是关掉它的
`Capabilities.DELETABLE`（在选中阶段就被过滤掉），
而不是在 `canExecuteCommand` 里判断 —— 后者适合「这次操作整体不允许」。

### 10.2 内置命令

`gui.command` 下有删除、移动、粘贴、复制、重命名、改色、
建节点 / 建连线 / 建 Placemat / 建子图等一整套。撤销栈由框架管理，你不需要自己实现。

### 10.3 其他自定义点

| 自定义点 | 位置 |
|---|---|
| 节点显示名 / 图标 / 宽度 | `Node#getDisplayName` / `getNodeIcon` / `getNodeWidth` |
| 节点选项（会显示在 Inspector 里的参数） | `Node#onDefineOptions(IOptionDefinitionContext)` + `INodeOption` |
| 节点预览 | `Node#hasNodePreview` / `onBuildNodePreview` / `onUpdateNodePreview` |
| 节点说明 UI | `Node#createDescriptionUI()` |
| 端口的常量编辑器 | `ITypeConfigurable` |
| 图的诊断信息 | `Graph#onGraphChanged(GraphLogger)` |

### 10.4 节点选项

端口负责「连线」，**选项**负责「这个节点自己的参数」：

```java
@Override
public void onDefineOptions(IOptionDefinitionContext context) {
    // 选项会出现在 Inspector 里，并且可以持久化
}
```

`OptionVisibility` 控制选项什么时候可见（跟着某个端口/选项的取值走），
`INodeOption` 描述选项本身。

## 11. 一个完整的最小落地清单

1. 写 `XxxGraph extends Graph`，建 `GraphNodeRegistry`，实现 `getSupportNodes()`；
2. 写若干 `XxxNode extends Node`，加 `@NodeAttribute(graphTypes = XxxGraph.class)`，
   实现 `getDisplayName()` 与 `onDefinePorts(...)`；
3. 写 `XxxGraphResource extends GraphResource<XxxGraph>`，实现 `createGraph()`
   与 `getGraphViewFactory()`；
4. 把资源类型注册到 LDLib2 Editor；
5. 在编辑器里打开，验证：能建节点、能连线、能改参数、能保存、重开后状态还在。

## 12. 真实用例：Photon2 的三个图

Photon 2 的图形资源全部是这套框架的实例，**是最值得抄的参考**：

| Photon 资源 | 对应的 Graph | 说明 |
|---|---|---|
| Shader Graph | `photon:shader_graph` | 生成粒子着色器 |
| Fullscreen Graph | `photon:fullscreen_graph` | 生成全屏 pass |
| Render Graph | `photon:render_graph` | 把多个 pass 连成后处理效果 |

它的节点普遍继承 KilaGraph 的 `ShaderNode`（KilaGraph 再建在 LDLib2 的 `Node` 之上），
所以你会看到三个钩子叠在一起：

```java
@NodeAttribute(name = "photon_depth_fade", group = "photon_scene",
        graphTypes = {ShaderGraph.class, PhotonShaderFunctionGraph.class})
public class DepthFadeNode extends ShaderNode {

    @Override public StageAffinity stageAffinity() { return StageAffinity.FRAGMENT_ONLY; }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {      // ← LDLib2 的钩子
        context.addInputPort("distance", TypeHandles.FLOAT).withDefaultValue(1.0f);
        context.addOutputPort("fade", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {                  // ← KilaGraph 的钩子
        String distance = ctx.input("distance").code();
        ctx.output("fade", new ShaderExpr("...", GlslType.FLOAT));
    }

    @Override protected String previewOutputPortId() { return "fade"; }
}
```

这段真实的节点代码说明了三件事：

1. **`onDefinePorts` 是 LDLib2 层的唯一必需钩子**，其余都是上层框架加的；
2. **未连线的输入端口读的是它的内联常量编辑器**（`withDefaultValue` 设的那个）；
3. **`graphTypes` 可以列多个图**，一个节点复用给多个图。

Photon 的图资源、编译器和节点都在 `com.lowdragmc.photon.client.shadergraph` /
`.postfx.graph` / `.postfx.shadergraph` 下，配合 [渲染与 Photon2 特效](/doc/rendering-photon2)
的 §6 一起看会更清楚。

## 13. 坑与排错

| 症状 | 原因 |
|---|---|
| IDE 补全不出任何类 | 包名是 `nodegraphtookit`（少一个 `l`），不是 `toolkit` |
| 节点在库里找不到 | `@NodeAttribute.graphTypes` 没声明当前图；或 `getLibrarySupportNodes()` 把它过滤掉了 |
| 端口连不上 | `PortCapacity` 是 `NONE`，或方向不对（输入接输入） |
| 输入端口没连线时是 0 | 没设 `withDefaultValue(...)`，端口常量编辑器里就是类型的默认值 |
| 图打开后节点变了样 | 节点的端口/选项改了但旧图还存着旧 id；看 `onGraphChanged` 报的 `MISSING_PORT` 诊断 |
| 遍历节点漏了几个 | `BlockNode` 不在 `getNodes()` 里，要通过父 `ContextNode` 拿 |
| 覆写 `getSupportTypes()` 后类型变少了 | 覆写是**替换**不是追加，要自己把自动探测的结果 union 进去 |
| 保存后重开状态丢失 | `withoutSerialization()` 的端口本来就不存；或者没走 `serializeGraph()` |
| 常量库里出现一堆没用的类型 | 别把「能进端口」和「能作者化字面量」混为一谈，见 §6.3 |

## 14. 参考

- 官方文档：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/>
  - [快速开始](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/getting-started.html)
  - [Graph 定义](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/graph-definition.html)
  - [节点和端口](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/nodes-and-ports.html)
  - [变量和 Blackboard](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/variables-and-blackboard.html)
  - [Type Handles](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/type-handles.html)
  - [子图](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/subgraphs.html)
  - [Context 和 Block 节点](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/context-and-block-nodes.html)
  - [命令与自定义](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/node-graph-toolkit/commands-and-customization.html)
- 源码：`~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.ldlib2/ldlib2-neoforge-26.2/<版本>/*-sources.jar`
- 本仓库内的相关文档：[渲染与 Photon2 特效](/doc/rendering-photon2)

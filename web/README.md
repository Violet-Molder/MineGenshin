# web/ —— 文档站（Spring Boot + Java）

一个独立的 Spring Boot 应用，用来跑 MineGenshin 的文档站。**与 Mod 本体完全隔离**：独立的 Gradle 构建、独立的依赖、独立的进程；根目录的 `./gradlew build` 与 Mod 的 jar 都不会与它产生关系。

## 为什么是 Spring Boot

- **Markdown 不再需要预生成**：文档在请求时由 Java 渲染（commonmark + GFM 表格扩展），改完源文件刷新即可，没有 Node 构建步骤。
- 文档只有一份源：`site.docs-root` 指向仓库根，直接读 `CHARACTER_SYSTEM.md`、`docs/*.md` 等，不存在副本漂移。
- 后续要加搜索、版本切换、鉴权或 API，都是普通的 Java 后端工作。

## 运行

```bash
cd web
./gradlew bootRun                 # 开发运行，默认 http://localhost:8081
./gradlew bootJar                 # 打包 → build/libs/minegenshin-web.jar
java -jar build/libs/minegenshin-web.jar
```

在仓库根目录外运行时，用参数或环境变量指定文档源目录：

```bash
java -jar minegenshin-web.jar --site.docs-root=E:/MCMOD/MineGenshin
SITE_DOCS_ROOT=/path/to/repo java -jar minegenshin-web.jar
```

端口用 `--server.port=8081` 覆盖；配置项都在 `src/main/resources/application.yml`。

## 路由

| 路径 | 内容 |
|---|---|
| `/` | 首页（卡片导航、协议说明） |
| `/doc/{slug}` | 由 Markdown 实时渲染的文档页，slug 见 `DocCatalog` |
| `/entity-development.html` | 手写的实体开发文档（`src/main/resources/static/`） |
| `/assets/docs.css`、`/assets/docs.js` | 站点样式与导航脚本 |

侧边栏、页内目录、过滤框与滚动高亮由 `docs.js` 提供：跨页菜单来自 `/api/docs`（后端 `DocCatalog` 是唯一来源），页内章节直接读当前页的 `h2`/`h3` 并挂到该页节点下（新增章节不用改导航）。标题锚点 id 由 `MarkdownRenderer` 按 GitHub 规则注入，保证文档内部的 `#锚点` 链接可用。

## 加一篇文档

1. 把 Markdown 放进仓库（推荐 `docs/`）。
2. 在 `DocCatalog.DOCS` 里加一行：`new Doc("slug", "标题", "相对仓库根的路径.md")`。

菜单会自动出现；顺带在 `docs.js` 的 `FALLBACK_MENU` 里补一个同名条目（那是直接用 `file://` 打开时的兜底）。

## 目录结构与版本切换

文档按 NeoForge 官方文档的做法组织：**一页只讲一个主题**，目录由 `DocCatalog` 给出三级结构 ——
分类（`group`）→ 文档（`section`）→ 章节页。渲染教程按章拆页，两条技术线各一套：

```
渲染与特效 / 渲染与 Photon2 完全参考 / 2. Blaze3D API 地图   ← 一页
docs/rendering/26.2/reference/blaze3d.md                     ← 它的源文件
docs/rendering/1.21.1/reference/blaze3d.md                   ← 1.21.1 线上同名的那一页
```

- 左侧目录是**纯导航**：分类与文档两级都可展开收缩，**默认全部收起，只有点击才展开**；
  没有自动展开、没有滚动跟随，当前页只做高亮。
- 页内 h2/h3 不放左栏，而是页面右侧的「本页」目录（`#outline`，宽度 < 1280px 时隐藏）。
- 只有真正分版本的页面才填 `version` / `counterpart`；两条线的同名章节互相配对。
  右上角版本按钮在成对的页面上跳同名章，在共享页面上就地切换（左栏与首页卡片跟着换，正文不动）。
  选过的版本记在 `localStorage["minegenshin.docs.version"]`，默认取 `DocCatalog.DEFAULT_VERSION`。

加一页的步骤：Markdown 放进 `docs/`（分章的书放 `docs/rendering/<线>/<reference|effects>/<id>.md`）→
在 `DocCatalog.DOCS`（散篇）或 `DocCatalog.REFERENCE` / `EFFECTS`（分章的书）里加一行 →
`docs.js` 的 `FALLBACK_LOOSE` / `FALLBACK_BOOKS` 兜底表里补一条。标题与 catalog 保持一致
（正文第一行 `# 标题` 会被渲染器丢掉）。
## 依赖隔离说明

| 关注点 | 结论 |
|---|---|
| Mod 编译/打包 | 不受影响：`web/` 是独立构建，根项目不 include 它 |
| 依赖来源 | 只有 web 侧用 Maven Central（Spring Boot、commonmark） |
| 版本工具链 | web 用 Java 21 + Gradle 8.14；Mod 用 Java 25 toolchain + Gradle 9.2.1 |
| 运行时 | 文档站是独立进程，Mod 不需要它也能跑 |

## 预览

浏览器打开 <http://localhost:8081/>。若要临时快速看静态内容，也可以直接访问 `src/main/resources/static/` 下的页面，但 `/doc/*` 路由需要应用在运行。

## 在 IntelliJ IDEA 里运行

仓库已提供两个共享运行配置（`.run/` 目录，IDEA 打开仓库根目录即可看到）：

| 配置 | 说明 |
|---|---|
| `MineGenshin Web (8081)` | Application 型：直接跑 `SiteApplication`，工作目录 `web/`，并传 `-Dsite.docs-root=$PROJECT_DIR$`。需要先在 IDEA 里把 `web` 链接为 Gradle 项目（Gradle 面板 → Link Gradle Project → 选 `web/settings.gradle`），否则模块 `minegenshin-web.main` 不存在 |
| `MineGenshin Web (gradle bootRun)` | Gradle 型：对 `web` 执行 `bootRun`，不依赖模块导入，只要 IDEA 认识这个 Gradle 构建即可 |

两种方式都默认 8081 端口；若 IDEA 把仓库根识别成项目、而 `web` 是独立构建，优先用 Gradle 型配置。

### 两个 Gradle JVM 怎么共存（重要）

主项目要 **JBR 25**（根目录 `gradle.properties` 的 `org.gradle.java.home`），web 要 **JDK 21**：
web 的 wrapper 是 **Gradle 8.14**，而 Gradle 8.14 官方支持的运行 JVM 最高到 **Java 24**，
拿 JBR 25 去跑它，IDEA 会直接报
`Incompatible Gradle JVM：您的构建当前配置为使用不兼容的 Java 25.0.4 和 Gradle 8.14` 并拒绝同步。

两者本来就是**两个独立构建**（各有自己的 `settings.gradle` 与 wrapper，Gradle 只读"本次构建根目录"
那一层的 `gradle.properties`），所以完全可以各用各的 JVM：

| 谁 | Gradle 版本 | 用哪个 JVM | 在哪里设 |
|---|---|---|---|
| 仓库根（Mod） | 9.2.1 | JBR 25 | 根 `gradle.properties` 的 `org.gradle.java.home`，IDEA 里对应 `jbr-25` |
| `web/`（文档站） | 8.14 | JDK 21 | `web/gradle.properties` 已钉死（命令行/CI 直接生效） |

**IDEA 里必须再手动对齐一次**，因为 IDEA 的 "Gradle JVM" 设置优先级高于 `gradle.properties`：

1. Gradle 工具窗口 → `+`（Link Gradle Project）→ 选 `web/settings.gradle`；
2. 该项目的 **Gradle JVM 选 JDK 21**（本机 SDK 名 `liberica-21`，路径 `E:\Java\JDK21`）；
3. `Settings → Build, Execution, Deployment → Build Tools → Gradle` 里能同时看到两个项目：
   根项目 = `jbr-25`，`web` = `liberica-21`；改完点 Gradle 面板的 **Reload**。

> 旧版 IDEA 若没有"按项目设 Gradle JVM"的入口，就把 `web` 用**独立窗口**打开
> （`File → Open → web/settings.gradle → Open as Project`），在那个窗口里设 JDK 21；
> 主项目窗口继续用 JBR 25，两边互不影响。
>
> 不要把 web 的 wrapper 升到 Gradle 9 —— Spring Boot 3.4.5 的 Gradle 插件不支持 Gradle 9，
> 要升就得先把 Spring Boot 升到 3.5+。

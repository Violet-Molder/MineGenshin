package com.linweiyun.minegenshin.web.docs;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点收录的文档：slug → 标题 → 仓库里的 Markdown 源文件（相对 docs-root）。
 *
 * <p>目录是三级：分类（{@code group}）→ 文档（{@code section}，同一本书的章节页归在这里）→ 章节页。
 * 一页只讲一个主题；渲染教程按章拆页，两条技术线各一套。
 * 只有「同一个主题在两条线上各一份」的页面才带 {@code version} 与 {@code counterpart}，
 * 侧边栏按当前版本只显示其中一篇。
 */
public final class DocCatalog {

    public static final String V26_2 = "26.2";
    public static final String V1_21_1 = "1.21.1";
    /** 不参与版本切换的文档：没有版本，也没有对应篇。 */
    private static final String V_NONE = "";
    /** 站点默认版本：没记住过选择、当前页也不带版本时用这个。 */
    public static final String DEFAULT_VERSION = V1_21_1;
    /** 版本切换的显示顺序（与切换按钮一致）。 */
    public static final List<String> VERSIONS = List.of(V26_2, V1_21_1);

    /** 章节页：id 用来拼 slug 与文件名，两条线同名章节用同一个 id。 */
    private record Chapter(String id, String title) {}

    private static final List<Chapter> REFERENCE = List.of(
            new Chapter("intro", "0. 导读"),
            new Chapter("frame", "1. 心智模型：一帧是怎么画出来的"),
            new Chapter("blaze3d", "2. Blaze3D API 地图"),
            new Chapter("shaders", "3. 着色器、渲染管线与 GPU 数据"),
            new Chapter("entity-render", "4. 实体渲染与渲染状态"),
            new Chapter("geckolib", "5. GeckoLib：骨骼动画与渲染层"),
            new Chapter("transform", "6. 坐标空间、矩阵与四元数"),
            new Chapter("gpu-skinning", "7. GPU 蒙皮与渲染性能"),
            new Chapter("photon-runtime", "8. Photon2：从编辑器到运行时"),
            new Chapter("photon-api", "9. Photon2 Java API 与运行时注入"),
            new Chapter("practice", "10. 项目实战"),
            new Chapter("troubleshooting", "11. 排错手册"),
            new Chapter("appendix", "12. 附录"));

    private static final List<Chapter> EFFECTS = List.of(
            new Chapter("mental-model", "1. 先建立正确的心智模型"),
            new Chapter("blaze3d", "2. Blaze3D 是什么、怎么写"),
            new Chapter("coordinates", "3. 坐标空间完全指南"),
            new Chapter("geckolib", "4. GeckoLib 的渲染管线"),
            new Chapter("shaders", "5. 着色器与渲染类型"),
            new Chapter("photon", "6. Photon2 的渲染架构"),
            new Chapter("practice", "7. 实战：把渲染接到 Photon"),
            new Chapter("performance", "8. 性能"),
            new Chapter("troubleshooting", "9. 排错手册"),
            new Chapter("appendix", "10. 附录"));

    public record Doc(String slug, String title, String source, String group, String section,
                      String version, String counterpart) {
        /** 不分版本的独立文档：直接挂在分类下。 */
        public Doc(String slug, String title, String source, String group) {
            this(slug, title, source, group, "", V_NONE, null);
        }

        public boolean versioned() {
            return !V_NONE.equals(version);
        }
    }

    /** 版本切换按钮的一项：{@code href} 为空表示就地切换（两个版本是同一页）。 */
    public record VersionOption(String version, String href, boolean active) {}

    public static final List<Doc> DOCS = build();

    private static List<Doc> build() {
        List<Doc> docs = new ArrayList<>();

        // 系统详解：每个模块一份，含关键类、数据流、扩展步骤与坑
        docs.add(new Doc("sys-registry", "注册中心与内容注册", "docs/systems/registry.md", "系统详解"));
        docs.add(new Doc("sys-character", "角色系统", "docs/systems/character.md", "系统详解"));
        docs.add(new Doc("sys-attachment-sync", "附件与数据同步", "docs/systems/attachment-sync.md", "系统详解"));
        docs.add(new Doc("sys-combat-attack", "战斗 · 攻击与伤害管线", "docs/systems/combat-attack.md", "系统详解"));
        docs.add(new Doc("sys-combat-action", "战斗 · 动作与动画", "docs/systems/combat-action.md", "系统详解"));
        docs.add(new Doc("sys-flight", "飞行与下落攻击", "docs/systems/flight.md", "系统详解"));
        docs.add(new Doc("sys-element-reaction", "元素附着与元素反应", "docs/systems/element-reaction.md", "系统详解"));
        docs.add(new Doc("sys-element-host", "元素载体：可附着宿主", "docs/systems/element-host.md", "系统详解"));
        docs.add(new Doc("sys-attribute-effect", "属性与角色效果", "docs/systems/attribute-effect.md", "系统详解"));
        docs.add(new Doc("sys-loot-monster", "掉落与怪物等级", "docs/systems/loot-monster.md", "系统详解"));
        docs.add(new Doc("sys-shield-status", "护盾与状态", "docs/systems/shield-status.md", "系统详解"));
        docs.add(new Doc("sys-poise-control", "韧性与控制", "docs/systems/poise-control.md", "系统详解"));
        docs.add(new Doc("sys-render-asset", "资源、渲染与界面", "docs/systems/render-asset.md", "系统详解"));
        docs.add(new Doc("sys-network-event", "网络、事件与数据生成", "docs/systems/network-event-datagen.md", "系统详解"));
        docs.add(new Doc("sys-performance", "性能优化系统", "docs/systems/performance.md", "系统详解"));

        // 渲染与特效：两条线各一本「完全参考」+ 一本「特效」，按章拆页
        docs.addAll(versionedPages("渲染与特效", "渲染与 Photon2 完全参考", "reference", REFERENCE));
        docs.addAll(versionedPages("渲染与特效", "渲染与 Photon2 特效", "effects", EFFECTS));

        // 扩展框架与现有深入文档
        docs.add(new Doc("ldlib2-node-graph", "LDLib2 节点图工具包", "docs/ldlib2-node-graph.md", "扩展框架"));
        docs.add(new Doc("graphics-matrix-notes", "图形学学习笔记：4×4 变换矩阵",
                "web/src/main/resources/static/graphics-matrix-notes.html", "图形学学习笔记"));
        docs.add(new Doc("entity-development", "实体开发文档",
                "web/src/main/resources/static/entity-development.html", "深入文档"));
        docs.add(new Doc("entity-ai", "实体 AI 指南", "docs/entity-ai-goal-guide.md", "深入文档"));
        docs.add(new Doc("character-system", "角色系统详解（薇斯娜）", "CHARACTER_SYSTEM.md", "深入文档"));
        docs.add(new Doc("character-implementations", "角色实现清单", "CHARACTER_IMPLEMENTATIONS.md", "深入文档"));
        docs.add(new Doc("port-targeting", "索敌系统移植参考", "docs/port-targeting-changelog.md", "深入文档"));
        docs.add(new Doc("port-targeting-patch", "索敌移植补丁记录", "docs/port-targeting-to-reference2.patch.md", "深入文档"));
        docs.add(new Doc("readme", "项目介绍", "README.md", "项目"));
        docs.add(new Doc("changelog", "更新日志", "CHANGELOG.md", "项目"));

        return List.copyOf(docs);
    }

    /** 同一本书在两条线上各一套：逐章生成两边的页面，并互相指向对方同名的那一章。 */
    private static List<Doc> versionedPages(String group, String book, String sub, List<Chapter> chapters) {
        String slug26 = "rendering-26.2-" + sub;
        String slug121 = "rendering-1.21.1-" + sub;
        List<Doc> docs = new ArrayList<>();
        for (Chapter chapter : chapters) {
            String file = sub + "/" + chapter.id() + ".md";
            docs.add(new Doc(slug26 + "-" + chapter.id(), chapter.title(),
                    "docs/rendering/26.2/" + file, group, book, V26_2, slug121 + "-" + chapter.id()));
            docs.add(new Doc(slug121 + "-" + chapter.id(), chapter.title(),
                    "docs/rendering/1.21.1/" + file, group, book, V1_21_1, slug26 + "-" + chapter.id()));
        }
        return docs;
    }

    public static Doc bySlug(String slug) {
        return DOCS.stream().filter(d -> d.slug().equals(slug)).findFirst().orElse(null);
    }

    /** 版本切换按钮的选项：带配对的页面跳到对应章，其余就地切换。 */
    public static List<VersionOption> versionOptions(Doc doc, String activeVersion) {
        List<VersionOption> options = new ArrayList<>();
        for (String version : VERSIONS) {
            options.add(new VersionOption(version, hrefFor(doc, version), version.equals(activeVersion)));
        }
        return options;
    }

    /** 切到目标版本时该去哪一页；{@code null} = 留在本页就地切换。 */
    private static String hrefFor(Doc doc, String target) {
        if (doc == null || !doc.versioned()) {
            return null;
        }
        if (doc.version().equals(target)) {
            return "/doc/" + doc.slug();
        }
        Doc counterpart = counterpartOf(doc);
        return counterpart == null ? null : "/doc/" + counterpart.slug();
    }

    private static Doc counterpartOf(Doc doc) {
        if (doc.counterpart() != null) {
            return bySlug(doc.counterpart());
        }
        return DOCS.stream().filter(d -> doc.slug().equals(d.counterpart())).findFirst().orElse(null);
    }

    private DocCatalog() {}
}
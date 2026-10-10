package com.linweiyun.minegenshin.web.docs;

import java.util.List;

/** 站点收录的文档：slug → 标题 → 仓库里的 Markdown 源文件（相对 docs-root）。 */
public final class DocCatalog {

    /** 版本标签：两条技术线各一份；两边都成立的不打标签。 */
    public static final String V26_2 = "26.2";
    public static final String V1_21_1 = "1.21.1";
    public static final String V_BOTH = "通用";

    /** 每个版本各自的入口篇：切到「本篇暂无该版本」时落到这里。 */
    private static final String HUB_26_2 = "rendering-photon2-reference";
    private static final String HUB_1_21_1 = "rendering-photon2-reference-1.21.1";

    public record Doc(String slug, String title, String source, String group, String version, String counterpart) {
        /** 不分版本的文档：没有版本标签，也没有对应篇。 */
        public Doc(String slug, String title, String source, String group) {
            this(slug, title, source, group, V_BOTH, null);
        }
    }

    /** 版本切换器的一个选项：当前篇的版本高亮，另一版本指向对应篇或该版本的入口篇。 */
    public record VersionOption(String label, String href, boolean active, String hint) {}

    public static final List<Doc> DOCS = List.of(
            // 系统详解：每个模块一份，含关键类、数据流、扩展步骤与坑
            new Doc("sys-registry", "注册中心与内容注册", "docs/systems/registry.md", "系统详解"),
            new Doc("sys-character", "角色系统", "docs/systems/character.md", "系统详解"),
            new Doc("sys-attachment-sync", "附件与数据同步", "docs/systems/attachment-sync.md", "系统详解"),
            new Doc("sys-combat-attack", "战斗 · 攻击与伤害管线", "docs/systems/combat-attack.md", "系统详解"),
            new Doc("sys-combat-action", "战斗 · 动作与动画", "docs/systems/combat-action.md", "系统详解"),
            new Doc("sys-flight", "飞行与下落攻击", "docs/systems/flight.md", "系统详解"),
            new Doc("sys-element-reaction", "元素附着与元素反应", "docs/systems/element-reaction.md", "系统详解"),
            new Doc("sys-element-host", "元素载体：可附着宿主", "docs/systems/element-host.md", "系统详解"),
            new Doc("sys-attribute-effect", "属性与角色效果", "docs/systems/attribute-effect.md", "系统详解"),
            new Doc("sys-loot-monster", "掉落与怪物等级", "docs/systems/loot-monster.md", "系统详解"),
            new Doc("sys-shield-status", "护盾与状态", "docs/systems/shield-status.md", "系统详解"),
            new Doc("sys-poise-control", "韧性与控制", "docs/systems/poise-control.md", "系统详解"),
            new Doc("sys-render-asset", "资源、渲染与界面", "docs/systems/render-asset.md", "系统详解"),
            new Doc("sys-network-event", "网络、事件与数据生成", "docs/systems/network-event-datagen.md", "系统详解"),
            new Doc("sys-performance", "性能优化系统", "docs/systems/performance.md", "系统详解"),
            // 渲染与特效：Blaze3D / GeckoLib / Photon2 的完整链路
            new Doc("rendering-photon2-reference", "Minecraft 26.2 渲染与 Photon2 完全参考",
                    "docs/rendering-photon2-reference.md", "渲染与特效", V26_2, "rendering-photon2-reference-1.21.1"),
            new Doc("rendering-photon2-reference-1.21.1", "Minecraft 1.21.1 渲染与 Photon2 完全参考",
                    "docs/rendering-photon2-reference-1.21.1.md", "渲染与特效", V1_21_1, "rendering-photon2-reference"),
            new Doc("rendering-photon2", "渲染与 Photon2 特效", "docs/rendering-and-photon2.md", "渲染与特效", V26_2, "rendering-photon2-1.21.1"),
            new Doc("rendering-photon2-1.21.1", "渲染与 Photon2 特效（1.21.1）",
                    "docs/rendering-and-photon2-1.21.1.md", "渲染与特效", V1_21_1, "rendering-photon2"),
            // 扩展框架：项目依赖的第三方框架怎么用
            new Doc("ldlib2-node-graph", "LDLib2 节点图工具包", "docs/ldlib2-node-graph.md", "扩展框架", V26_2, null),
            // 现有深入文档
            new Doc("graphics-matrix-notes", "图形学学习笔记：4×4 变换矩阵", "web/src/main/resources/static/graphics-matrix-notes.html", "图形学学习笔记"),
            new Doc("entity-development", "实体开发文档", "web/src/main/resources/static/entity-development.html", "深入文档"),
            new Doc("entity-ai", "实体 AI 指南", "docs/entity-ai-goal-guide.md", "深入文档", V26_2, null),
            new Doc("character-system", "角色系统详解（薇斯娜）", "CHARACTER_SYSTEM.md", "深入文档"),
            new Doc("character-implementations", "角色实现清单", "CHARACTER_IMPLEMENTATIONS.md", "深入文档"),
            new Doc("port-targeting", "索敌系统移植参考", "docs/port-targeting-changelog.md", "深入文档"),
            new Doc("port-targeting-patch", "索敌移植补丁记录", "docs/port-targeting-to-reference2.patch.md", "深入文档"),
            new Doc("readme", "项目介绍", "README.md", "项目"),
            new Doc("changelog", "更新日志", "CHANGELOG.md", "项目")
    );

    public static Doc bySlug(String slug) {
        return DOCS.stream().filter(d -> d.slug().equals(slug)).findFirst().orElse(null);
    }

    /**
     * 版本切换器的选项：当前篇高亮，另一版本优先指向声明了对应关系的篇目，
     * 找不到就退到那个版本的入口篇（渲染参考）。
     */
    public static List<VersionOption> versionOptions(Doc doc) {
        if (doc == null || V_BOTH.equals(doc.version())) {
            return List.of();
        }
        String other = V1_21_1.equals(doc.version()) ? V26_2 : V1_21_1;
        Doc counterpart = counterpartOf(doc);
        String href = counterpart != null ? "/doc/" + counterpart.slug() : "/doc/" + hubOf(other);
        String hint = counterpart != null
                ? "切到 " + counterpart.title()
                : other + " 线的渲染参考（本篇暂无 " + other + " 版本）";
        return List.of(
                new VersionOption(doc.version(), "/doc/" + doc.slug(), true, "当前页"),
                new VersionOption(other, href, false, hint));
    }

    private static Doc counterpartOf(Doc doc) {
        if (doc.counterpart() != null) {
            return bySlug(doc.counterpart());
        }
        return DOCS.stream().filter(d -> doc.slug().equals(d.counterpart())).findFirst().orElse(null);
    }

    private static String hubOf(String version) {
        return V1_21_1.equals(version) ? HUB_1_21_1 : HUB_26_2;
    }

    private DocCatalog() {}
}
package com.linweiyun.minegenshin.web.docs;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点收录的文档：slug → 标题 → 仓库里的 Markdown 源文件（相对 docs-root）。
 *
 * <p>只有「同一个主题在两条技术线上各一篇」的文档才带 {@code version} 与 {@code counterpart}，
 * 侧边栏按当前版本只显示其中一篇；其它文档不参与版本切换。
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

    /** 每个版本各自的入口篇：切到「本篇暂无该版本」时落到这里。 */
    private static final String HUB_26_2 = "rendering-photon2-reference";
    private static final String HUB_1_21_1 = "rendering-photon2-reference-1.21.1";

    public record Doc(String slug, String title, String source, String group, String version, String counterpart) {
        public Doc(String slug, String title, String source, String group) {
            this(slug, title, source, group, V_NONE, null);
        }

        public boolean versioned() {
            return !V_NONE.equals(version);
        }
    }

    /** 版本切换按钮的一项：{@code href} 为空表示就地切换（两个版本是同一页）。 */
    public record VersionOption(String version, String href, boolean active) {}
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
            new Doc("rendering-photon2-reference", "渲染与 Photon2 完全参考",
                    "docs/rendering-photon2-reference.md", "渲染与特效", V26_2, "rendering-photon2-reference-1.21.1"),
            new Doc("rendering-photon2-reference-1.21.1", "渲染与 Photon2 完全参考",
                    "docs/rendering-photon2-reference-1.21.1.md", "渲染与特效", V1_21_1, "rendering-photon2-reference"),
            new Doc("rendering-photon2", "渲染与 Photon2 特效", "docs/rendering-and-photon2.md", "渲染与特效", V26_2, "rendering-photon2-1.21.1"),
            new Doc("rendering-photon2-1.21.1", "渲染与 Photon2 特效",
                    "docs/rendering-and-photon2-1.21.1.md", "渲染与特效", V1_21_1, "rendering-photon2"),
            // 扩展框架：项目依赖的第三方框架怎么用
            new Doc("ldlib2-node-graph", "LDLib2 节点图工具包", "docs/ldlib2-node-graph.md", "扩展框架"),
            // 现有深入文档
            new Doc("graphics-matrix-notes", "图形学学习笔记：4×4 变换矩阵", "web/src/main/resources/static/graphics-matrix-notes.html", "图形学学习笔记"),
            new Doc("entity-development", "实体开发文档", "web/src/main/resources/static/entity-development.html", "深入文档"),
            new Doc("entity-ai", "实体 AI 指南", "docs/entity-ai-goal-guide.md", "深入文档"),
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

    /** 版本切换按钮的选项：带配对的文档跳到对应篇，其余就地切换。 */
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
        if (counterpart != null) {
            return "/doc/" + counterpart.slug();
        }
        return "/doc/" + hubOf(target);
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
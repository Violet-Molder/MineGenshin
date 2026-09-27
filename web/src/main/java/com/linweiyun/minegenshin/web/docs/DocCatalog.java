package com.linweiyun.minegenshin.web.docs;

import java.util.List;

/** 站点收录的文档：slug → 标题 → 仓库里的 Markdown 源文件（相对 docs-root）。 */
public final class DocCatalog {

    public record Doc(String slug, String title, String source, String group) {}

    public static final List<Doc> DOCS = List.of(
            // 系统详解：每个模块一份，含关键类、数据流、扩展步骤与坑
            new Doc("sys-registry", "注册中心与内容注册", "docs/systems/registry.md", "系统详解"),
            new Doc("sys-character", "角色系统", "docs/systems/character.md", "系统详解"),
            new Doc("sys-attachment-sync", "附件与数据同步", "docs/systems/attachment-sync.md", "系统详解"),
            new Doc("sys-combat-attack", "战斗 · 攻击与伤害管线", "docs/systems/combat-attack.md", "系统详解"),
            new Doc("sys-combat-action", "战斗 · 动作与动画", "docs/systems/combat-action.md", "系统详解"),
            new Doc("sys-element-reaction", "元素附着与元素反应", "docs/systems/element-reaction.md", "系统详解"),
            new Doc("sys-element-host", "元素载体：可附着宿主", "docs/systems/element-host.md", "系统详解"),
            new Doc("sys-attribute-effect", "属性与角色效果", "docs/systems/attribute-effect.md", "系统详解"),
            new Doc("sys-loot-monster", "掉落与怪物等级", "docs/systems/loot-monster.md", "系统详解"),
            new Doc("sys-shield-status", "护盾与状态", "docs/systems/shield-status.md", "系统详解"),
            new Doc("sys-poise-control", "韧性与控制", "docs/systems/poise-control.md", "系统详解"),
            new Doc("sys-render-asset", "资源、渲染与界面", "docs/systems/render-asset.md", "系统详解"),
            new Doc("sys-network-event", "网络、事件与数据生成", "docs/systems/network-event-datagen.md", "系统详解"),
            new Doc("sys-performance", "性能优化系统", "docs/systems/performance.md", "系统详解"),
            // 现有深入文档
            new Doc("entity-development", "实体开发文档", "web/src/main/resources/static/entity-development.html", "深入文档"),
            new Doc("entity-ai", "实体 AI 指南", "docs/entity-ai-goal-guide.md", "深入文档"),
            new Doc("character-system", "角色系统详解（薇斯娜）", "CHARACTER_SYSTEM.md", "深入文档"),
            new Doc("character-implementations", "角色实现清单", "CHARACTER_IMPLEMENTATIONS.md", "深入文档"),
            new Doc("port-targeting", "索敌系统移植参考", "docs/port-targeting-changelog.md", "深入文档"),
            new Doc("port-targeting-patch", "索敌移植补丁记录", "docs/port-targeting-to-reference2.patch.md", "深入文档"),
            new Doc("readme", "项目介绍", "README.md", "项目"),
            new Doc("changelog", "更新日志", "CHANGELOG.md", "项目")
    );

    private DocCatalog() {}
}

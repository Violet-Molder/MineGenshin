package com.linweiyun.minegenshin.web.docs;

import java.util.List;

/** 页面外壳：侧边栏、样式与脚本。与前端 docs.js 约定的元素 id 保持一致。 */
public final class SiteTemplate {

    private SiteTemplate() {}

    public static String page(String pageId, String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <meta name="color-scheme" content="light dark">
                <meta name="theme-color" content="#3b6ea5" media="(prefers-color-scheme: light)">
                <meta name="theme-color" content="#14171c" media="(prefers-color-scheme: dark)">
                <meta name="description" content="MineGenshin 开发文档：%s">
                <title>%s · MineGenshin</title>
                <link rel="stylesheet" href="/assets/docs.css">
                </head>
                <body data-page="%s">
                <header id="mobilebar">
                  <button id="menu-toggle" type="button" aria-label="打开目录" aria-controls="sidebar" aria-expanded="false">☰ 目录</button>
                  <span class="bartitle">%s</span>
                </header>
                <div id="nav-overlay" hidden></div>
                <div class="layout">
                  <aside id="sidebar"></aside>
                  <main id="content">
                %s
                  </main>
                </div>
                <script src="/assets/docs.js"></script>
                </body>
                </html>
                """.formatted(title, title, pageId, title, body);
    }

    public static String index(List<DocCatalog.Doc> docs) {
        StringBuilder cards = new StringBuilder("<div class=\"cards\">");
        cards.append(card("/entity-development.html", "实体开发文档",
                "从注册实体到渲染：实体类骨架、属性、AI、同步、投射物范例与检查清单"));
        for (DocCatalog.Doc doc : docs) {
            cards.append(card("/doc/" + doc.slug(), versioned(doc), "由 " + doc.source() + " 实时渲染"));
        }
        cards.append("</div>");

        String body = """
                <h1>MineGenshin 文档</h1>
                <p class="lede">把《原神》的核心玩法机制移植到 Minecraft 的 NeoForge Mod —— 角色、元素附着与反应、圣遗物与武器、祈愿、怪物等级、战斗与动作系统。</p>
                <p><span class="tag">1.21.1 · NeoForge 21.1.250 · Java 21</span><span class="tag">26.2 · NeoForge 26.2.0.88 · Java 25</span></p>
                <p class="lede">两条技术线的写法差别很大（立即模式 vs 三段式、GeckoLib 4 vs 5、Photon 2.2.x vs 26.2.x）：
                   渲染相关的文档各写一份，右上角的版本切换在两条线之间跳。</p>
                <nav id="versionbar" aria-label="版本入口">
                  <span class="vlabel">版本</span>
                  <a class="vchip" href="/doc/rendering-photon2-reference">26.2</a>
                  <a class="vchip" href="/doc/rendering-photon2-reference-1.21.1">1.21.1</a>
                </nav>
                <h2>从这里开始</h2>
                %s
                <div class="note">
                  <b>文档源与站点</b>：每份文档只有一份源文件（仓库里的 Markdown），本页由 Spring Boot 在请求时渲染，
                  因此改完 Markdown 刷新即可看到，不需要额外的构建步骤。
                </div>
                <h2>开源协议</h2>
                <p>本项目采用 <b>CC BY-NC-SA 4.0</b>：允许非商业使用、修改与分发，必须署名并以相同协议发布，禁止任何商业用途。完整条款见仓库根目录的 <code>LICENSE.txt</code>。</p>
                <p>MineGenshin 的原创美术、音频与文本资源保留所有权利，未经书面许可不得提取或用于其他项目。</p>
                """.formatted(cards);
        return page("index", "MineGenshin 文档", body);
    }

    public static String docPage(String slug, String title, String source,
                                 List<DocCatalog.VersionOption> versions, String renderedHtml) {
        String body = """
                %s
                <h1>%s</h1>
                <p class="srcbar">本页由 <code>%s</code> 实时渲染。</p>
                %s
                """.formatted(versionBar(versions), title, source, renderedHtml);
        return page(slug, title, body);
    }

    /** 页面右上角的版本切换：当前篇高亮，另一版本指向对应篇或该版本的入口篇。 */
    private static String versionBar(List<DocCatalog.VersionOption> versions) {
        if (versions.isEmpty()) {
            return "";
        }
        StringBuilder bar = new StringBuilder("<nav id=\"versionbar\" aria-label=\"版本切换\">");
        bar.append("<span class=\"vlabel\">版本</span>");
        for (DocCatalog.VersionOption option : versions) {
            if (option.active()) {
                bar.append("<span class=\"vchip active\" aria-current=\"true\">")
                        .append(option.label())
                        .append("</span>");
            } else {
                bar.append("<a class=\"vchip\" href=\"").append(option.href())
                        .append("\" title=\"").append(option.hint())
                        .append("\">").append(option.label()).append("</a>");
            }
        }
        return bar.append("</nav>").toString();
    }

    /** 卡片标题：有版本标签的挂一个小徽章。 */
    private static String versioned(DocCatalog.Doc doc) {
        if (DocCatalog.V_BOTH.equals(doc.version())) {
            return doc.title();
        }
        return doc.title() + " · " + doc.version();
    }

    private static String card(String href, String title, String desc) {
        return "<a class=\"card\" href=\"%s\"><b>%s</b><span>%s</span></a>".formatted(href, title, desc);
    }
}

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
            cards.append(card("/doc/" + doc.slug(), doc.title(), "由 " + doc.source() + " 实时渲染"));
        }
        cards.append("</div>");

        String body = """
                <h1>MineGenshin 文档</h1>
                <p class="lede">把《原神》的核心玩法机制移植到 Minecraft 的 NeoForge Mod —— 角色、元素附着与反应、圣遗物与武器、祈愿、怪物等级、战斗与动作系统。</p>
                <p><span class="tag">MC 26.2</span><span class="tag">NeoForge 26.2.0.88</span><span class="tag">Java 25</span><span class="tag">Mod 1.0.1</span></p>
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

    public static String docPage(String slug, String title, String source, String renderedHtml) {
        String body = """
                <h1>%s</h1>
                <p class="srcbar">本页由 <code>%s</code> 实时渲染。</p>
                %s
                """.formatted(title, source, renderedHtml);
        return page(slug, title, body);
    }

    private static String card(String href, String title, String desc) {
        return "<a class=\"card\" href=\"%s\"><b>%s</b><span>%s</span></a>".formatted(href, title, desc);
    }
}

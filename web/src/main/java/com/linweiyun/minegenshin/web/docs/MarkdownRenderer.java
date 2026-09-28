package com.linweiyun.minegenshin.web.docs;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Markdown → 站点 HTML：去掉首个 H1、改写文档互链、按 GitHub 规则注入标题锚点。 */
@Service
public class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

    /** 文档里指向其它 Markdown 的链接 → 站点路由 */
    private static final Map<String, String> LINK_REWRITE = Map.ofEntries(
            Map.entry("docs/entity-ai-goal-guide.md", "/doc/entity-ai"),
            Map.entry("entity-ai-goal-guide.md", "/doc/entity-ai"),
            Map.entry("docs/port-targeting-to-reference2.patch.md", "/doc/port-targeting-patch"),
            Map.entry("port-targeting-to-reference2.patch.md", "/doc/port-targeting-patch"),
            Map.entry("docs/port-targeting-changelog.md", "/doc/port-targeting"),
            Map.entry("port-targeting-changelog.md", "/doc/port-targeting"),
            Map.entry("docs/systems/performance.md", "/doc/sys-performance"),
            Map.entry("performance.md", "/doc/sys-performance"),
            Map.entry("docs/systems/combat-attack.md", "/doc/sys-combat-attack"),
            Map.entry("combat-attack.md", "/doc/sys-combat-attack"),
            Map.entry("docs/systems/character.md", "/doc/sys-character"),
            Map.entry("docs/systems/render-asset.md", "/doc/sys-render-asset"),
            Map.entry("render-asset.md", "/doc/sys-render-asset"),
            Map.entry("docs/rendering-photon2-reference.md", "/doc/rendering-photon2-reference"),
            Map.entry("rendering-photon2-reference.md", "/doc/rendering-photon2-reference"),
            Map.entry("docs/rendering-and-photon2.md", "/doc/rendering-photon2"),
            Map.entry("rendering-and-photon2.md", "/doc/rendering-photon2"),
            Map.entry("CHARACTER_IMPLEMENTATIONS.md", "/doc/character-implementations"),
            Map.entry("CHARACTER_SYSTEM.md", "/doc/character-system"),
            Map.entry("RENDER_SYSTEM.md", "/doc/character-system"),
            Map.entry("README.md", "/doc/readme"),
            Map.entry("CHANGELOG.md", "/doc/changelog")
    );

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    public String render(String markdown) {
        StringBuilder sb = new StringBuilder();
        boolean titleDropped = false;
        for (String line : markdown.split("\n", -1)) {
            if (!titleDropped && line.startsWith("# ")) {
                titleDropped = true;
                continue;
            }
            sb.append(line).append('\n');
        }
        String md = sb.toString();
        // 长键先替换：短键是长键的后缀（`docs/x.md` 与 `x.md`），
        // 先替换短键就会把长键打断，留下 `docs//doc/...` 这种死链。
        // Map.ofEntries 的迭代顺序不保证，所以这里显式按长度降序排一次。
        for (Map.Entry<String, String> entry : LINK_REWRITE.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList()) {
            md = md.replace(entry.getKey(), entry.getValue());
        }
        return withHeadingIds(renderer.render(parser.parse(md)));
    }

    /** 与前端 docs.js 及 GitHub 一致的锚点算法，保证文档内 #锚点 跳转可用。 */
    static String slug(String text) {
        return text.trim().toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s_-]", "")
                .replaceAll("\\s", "-");
    }

    private static String withHeadingIds(String html) {
        var matcher = java.util.regex.Pattern.compile("<(h[1-4])>([\\s\\S]*?)</\\1>").matcher(html);
        StringBuilder out = new StringBuilder();
        // 同名标题（如多节的「常见坑」）会算出同一个 id，页内跳转与滚动高亮都会串行；
        // 第二次出现起补 -1 / -2 后缀，与 GitHub 的处理一致。
        Map<String, Integer> used = new java.util.HashMap<>();
        while (matcher.find()) {
            String inner = matcher.group(2);
            // 先去掉标签、再把 HTML 实体还原成字符，否则标题里的引号会变成 `&quot;`，
            // slug 之后就会留下 `quot` 这种噪音（`116-...从quot卡quot到...`）。
            String plain = decodeEntities(inner.replaceAll("<[^>]+>", ""));
            String base = slug(plain);
            if (base.isEmpty()) {
                base = "section";
            }
            int seen = used.merge(base, 1, Integer::sum);
            String id = seen == 1 ? base : base + "-" + (seen - 1);
            matcher.appendReplacement(out,
                    "<" + matcher.group(1) + " id=\"" + id + "\">" + java.util.regex.Matcher.quoteReplacement(inner) + "</" + matcher.group(1) + ">");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String decodeEntities(String text) {
        return text.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}

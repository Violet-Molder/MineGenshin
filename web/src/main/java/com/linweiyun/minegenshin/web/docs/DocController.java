package com.linweiyun.minegenshin.web.docs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 站点路由：首页 + 文档页。Markdown 每次请求时读取，改完源文件刷新即可。 */
@RestController
public class DocController {

    /** 当前页属于哪条线；不带版本的文档用站点默认版本。 */
    private static String activeVersion(DocCatalog.Doc doc) {
        return doc != null && doc.versioned() ? doc.version() : DocCatalog.DEFAULT_VERSION;
    }

    private final MarkdownRenderer renderer;
    private final Path docsRoot;

    public DocController(MarkdownRenderer renderer, @Value("${site.docs-root:..}") String docsRoot) {
        this.renderer = renderer;
        this.docsRoot = Path.of(docsRoot).toAbsolutePath().normalize();
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return SiteTemplate.index(DocCatalog.DOCS);
    }

    /** 站点菜单：前端侧边栏从这里取，避免后端目录与前端清单两份不一致。 */
    @GetMapping("/api/docs")
    public java.util.List<java.util.Map<String, String>> menu() {
        return DocCatalog.DOCS.stream()
                .map(d -> java.util.Map.of("slug", d.slug(), "title", d.title(), "group", d.group(), "section", d.section(), "version", d.version(), "counterpart", d.counterpart() == null ? "" : d.counterpart()))
                .toList();
    }

    @GetMapping(value = "/doc/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> doc(@PathVariable String slug) {
        var doc = DocCatalog.DOCS.stream().filter(d -> d.slug().equals(slug)).findFirst();
        if (doc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(SiteTemplate.page("404", "找不到页面", "",
                            "<h1>找不到页面</h1><p class=\"lede\">没有这个文档：<code>" + slug + "</code>。回 <a href=\"/\">首页</a> 看看。</p>"));
        }
        Path file = docsRoot.resolve(doc.get().source()).normalize();
        if (doc.get().source().endsWith(".html")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/" + file.getFileName())
                    .build();
        }
        if (!file.startsWith(docsRoot) || !Files.isReadable(file)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(SiteTemplate.page("error", "文档源缺失", "",
                            "<h1>文档源缺失</h1><p class=\"lede\">读不到 <code>" + file
                                    + "</code>。请确认启动目录，或用 <code>-Dsite.docs-root=仓库根目录</code> 指定。</p>"));
        }
        try {
            String markdown = Files.readString(file, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(SiteTemplate.docPage(doc.get().slug(), doc.get().title(), doc.get().source(), activeVersion(doc.get()), DocCatalog.versionOptions(doc.get(), activeVersion(doc.get())),
                            renderer.render(markdown)));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(SiteTemplate.page("error", "读取失败", "",
                            "<h1>读取失败</h1><pre>" + e.getMessage() + "</pre>"));
        }
    }
}

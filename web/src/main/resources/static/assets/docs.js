/* 文档站导航：跨页菜单 + 页内目录自动生成 + 滚动高亮 + 过滤 + 移动端抽屉。
   不依赖任何构建工具或网络资源。 */

/* 菜单由后端 /api/docs 提供（DocCatalog 是唯一来源）；取不到时用这份兜底。 */
const FALLBACK_MENU = [
  { slug: "index", title: "文档首页", group: "导览", href: "/" },
  { slug: "sys-registry", title: "注册中心与内容注册", group: "系统详解", href: "/doc/sys-registry" },
  { slug: "sys-character", title: "角色系统", group: "系统详解", href: "/doc/sys-character" },
  { slug: "sys-attachment-sync", title: "附件与数据同步", group: "系统详解", href: "/doc/sys-attachment-sync" },
  { slug: "sys-combat-attack", title: "战斗 · 攻击与伤害管线", group: "系统详解", href: "/doc/sys-combat-attack" },
  { slug: "sys-combat-action", title: "战斗 · 动作与动画", group: "系统详解", href: "/doc/sys-combat-action" },
  { slug: "sys-flight", title: "飞行与下落攻击", group: "系统详解", href: "/doc/sys-flight" },
  { slug: "sys-element-reaction", title: "元素附着与元素反应", group: "系统详解", href: "/doc/sys-element-reaction" },
  { slug: "sys-element-host", title: "元素载体：可附着宿主", group: "系统详解", href: "/doc/sys-element-host" },
  { slug: "sys-attribute-effect", title: "属性与角色效果", group: "系统详解", href: "/doc/sys-attribute-effect" },
  { slug: "sys-loot-monster", title: "掉落与怪物等级", group: "系统详解", href: "/doc/sys-loot-monster" },
  { slug: "sys-shield-status", title: "护盾与状态", group: "系统详解", href: "/doc/sys-shield-status" },
  { slug: "sys-poise-control", title: "韧性与控制", group: "系统详解", href: "/doc/sys-poise-control" },
  { slug: "sys-render-asset", title: "资源、渲染与界面", group: "系统详解", href: "/doc/sys-render-asset" },
  { slug: "sys-network-event", title: "网络、事件与数据生成", group: "系统详解", href: "/doc/sys-network-event" },
  { slug: "sys-performance", title: "性能优化系统", group: "系统详解", href: "/doc/sys-performance" },
  { slug: "graphics-matrix-notes", title: "图形学学习笔记：4×4 变换矩阵", group: "图形学学习笔记", href: "/graphics-matrix-notes.html" },
  { slug: "entity-development", title: "实体开发文档", group: "深入文档", href: "/entity-development.html" },
  { slug: "entity-ai", title: "实体 AI 指南", group: "深入文档", href: "/doc/entity-ai" },
  { slug: "character-system", title: "角色系统详解（薇斯娜）", group: "深入文档", href: "/doc/character-system" },
  { slug: "character-implementations", title: "角色实现清单", group: "深入文档", href: "/doc/character-implementations" },
  { slug: "port-targeting", title: "索敌系统移植参考", group: "深入文档", href: "/doc/port-targeting" },
  { slug: "port-targeting-patch", title: "索敌移植补丁记录", group: "深入文档", href: "/doc/port-targeting-patch" },
  { slug: "rendering-photon2", title: "渲染与 Photon2 特效", group: "渲染与特效", href: "/doc/rendering-photon2" },
  { slug: "rendering-photon2-reference", title: "Minecraft 26.2 渲染与 Photon2 完全参考", group: "渲染与特效", href: "/doc/rendering-photon2-reference" },
  { slug: "readme", title: "项目介绍", group: "项目", href: "/doc/readme" },
  { slug: "changelog", title: "更新日志", group: "项目", href: "/doc/changelog" },
];

async function loadMenu() {
  try {
    const res = await fetch("/api/docs", { headers: { accept: "application/json" } });
    if (res.ok) {
      const docs = await res.json();
      const items = [{ slug: "index", title: "文档首页", group: "导览", href: "/" }];
      for (const d of docs) {
        items.push({ slug: d.slug, title: d.title, group: d.group, href: "/doc/" + d.slug });
      }
      return items;
    }
  } catch (e) {
    /* 直接用 file:// 打开时没有后端，走兜底 */
  }
  return FALLBACK_MENU;
}

/* 与 GitHub 一致的锚点算法：小写 → 去掉非「字母/数字/空白/连字符/下划线」→ 空白转连字符。
   文档正文里的 #锚点 是按这套规则写的，因此必须保持一致，否则页内跳转会失效。 */
function slug(text) {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, "")
    .replace(/\s/g, "-");
}

/* ---------- 移动端抽屉 ---------- */

function isMobile() {
  return window.matchMedia("(max-width: 900px)").matches;
}

function setNav(open) {
  document.body.classList.toggle("nav-open", open);
  const toggle = document.getElementById("menu-toggle");
  if (toggle) toggle.setAttribute("aria-expanded", open ? "true" : "false");
  const overlay = document.getElementById("nav-overlay");
  if (overlay) overlay.hidden = !open;
}

function closeNav() {
  if (document.body.classList.contains("nav-open")) setNav(false);
}

function wireMobileNav() {
  const toggle = document.getElementById("menu-toggle");
  if (toggle) {
    toggle.addEventListener("click", () => setNav(!document.body.classList.contains("nav-open")));
  }
  const overlay = document.getElementById("nav-overlay");
  if (overlay) overlay.addEventListener("click", closeNav);

  // 点侧边栏里的任何链接（含页内目录）→ 关抽屉
  document.addEventListener("click", (e) => {
    if (isMobile() && e.target.closest("#sidebar a")) closeNav();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeNav();
  });

  // 转成桌面宽度后把抽屉状态清掉，避免 body 一直 overflow:hidden
  window.addEventListener("resize", () => {
    if (!isMobile()) setNav(false);
  });
}

/* ---------- 表格：窄屏横向滚动 ---------- */

function wrapTables() {
  document.querySelectorAll("#content > table").forEach((table) => {
    const wrap = document.createElement("div");
    wrap.className = "table-wrap";
    table.parentNode.insertBefore(wrap, table);
    wrap.appendChild(table);
  });
}

/* ---------- 代码块复制（手机上长代码很难手动选中） ---------- */

/* ---------- 语法高亮：配色对齐 IntelliJ IDEA 的 Dark 方案 ----------
   取值来自 IDEA 2025.3 自带的 themes/expUI/expUI_darkScheme.xml
   （关键字 #CF8E6D、字符串 #6AAB73、数字 #2AACB8、方法 #56A8F5、字段/常量 #C77DBB、
   注解 #B3AE60、类名 #BCBEC4），其中注释沿用本机 IDEA 的自定义值。 */

const HL_KEYWORDS = {
  java:
    "abstract assert boolean break byte case catch char class const continue default do double else enum exports extends final finally float for goto if implements import instanceof int interface long module native new non-sealed open opens package permits private protected provides public record requires return sealed short static strictfp super switch synchronized this throw throws to transient transitive try uses var void volatile while with yield",
  glsl:
    "attribute const uniform varying buffer shared coherent volatile restrict readonly writeonly layout centroid flat smooth noperspective patch sample break continue do for while switch case default if else subroutine in out inout float double int uint void bool true false invariant precise discard return struct mat2 mat3 mat4 vec2 vec3 vec4 ivec2 ivec3 ivec4 uvec2 uvec3 uvec4 bvec2 bvec3 bvec4 sampler1D sampler2D sampler3D samplerCube highp mediump lowp std140 std430 location binding",
  powershell:
    "param function filter if elseif else switch foreach for while do until return try catch finally throw begin process end class enum break continue in not and or true false null",
  json: "true false null",
  mcfunction: "true false run execute as at if unless say",
  text: "",
};

function hlEscape(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/* 把一段代码切成带 class 的片段；无法识别的部分按原文输出（只做 HTML 转义）。 */
function hlTokenize(code, lang) {
  const keywords = new Set((HL_KEYWORDS[lang] || "").split(/\s+/).filter(Boolean));
  const hashComments = lang === "powershell" || lang === "mcfunction" || lang === "bash";
  const cLike = !hashComments;
  const out = [];
  const push = (cls, text) => {
    out.push(cls ? '<span class="tok-' + cls + '">' + hlEscape(text) + "</span>" : hlEscape(text));
  };

  const reLineComment = hashComments ? /#[^\n]*/y : /\/\/[^\n]*/y;
  const reDocComment = /\/\*\*[\s\S]*?\*\//y;
  const reBlockComment = /\/\*[\s\S]*?\*\//y;
  const reString = /"(?:\\.|[^"\\])*"?/y;
  const reChar = /'(?:\\.|[^'\\])*'?/y;
  const reNumber =
    /\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.[\d_]+)?(?:[eE][+-]?\d+)?)[fFdDlLuU]?\b/y;
  const reAnnotation = /@[A-Za-z_][\w.]*/y;
  const reWord = lang === "powershell" ? /[A-Za-z_$][\w$-]*/y : /[A-Za-z_$][\w$]*/y;
  const rePreproc = /#[ \t]*[A-Za-z_]\w*/y;
  const reAnglePath = /<[A-Za-z0-9_./:$-]+>/y;
  const reQuotedPath = /"[^"\n]*"/y;
  const reCommand = /\/[A-Za-z_]\w*/y;

  let i = 0;
  let prevSig = "";
  let atLineStart = true;
  const n = code.length;
  while (i < n) {
    let m;
    // GLSL：预处理指令（IDEA 里这一类是单独的颜色）
    if (lang === "glsl" && atLineStart && code[i] === "#") {
      rePreproc.lastIndex = i;
      if ((m = rePreproc.exec(code))) {
        push("preproc", m[0]);
        i = rePreproc.lastIndex;
        const directive = m[0].replace(/^#[ \t]*/, "").toLowerCase();
        if (directive === "moj_import" || directive === "include" || directive === "define") {
          while (i < n && code[i] === " ") { push("", " "); i++; }
          reAnglePath.lastIndex = i;
          reQuotedPath.lastIndex = i;
          if ((m = reAnglePath.exec(code)) || (m = reQuotedPath.exec(code))) {
            push("string", m[0]);
            i = reAnglePath.lastIndex > i ? reAnglePath.lastIndex : reQuotedPath.lastIndex;
          }
        }
        prevSig = " "; atLineStart = false; continue;
      }
    }
    // mcfunction：行首的命令名
    if (lang === "mcfunction" && atLineStart && code[i] === "/") {
      reCommand.lastIndex = i;
      if ((m = reCommand.exec(code))) {
        push("punct", "/");
        push("method", m[0].slice(1));
        i = reCommand.lastIndex; prevSig = "/"; atLineStart = false; continue;
      }
    }
    if (cLike) {
      reDocComment.lastIndex = i;
      if ((m = reDocComment.exec(code))) { push("doc", m[0]); i = reDocComment.lastIndex; prevSig = " "; atLineStart = false; continue; }
      reBlockComment.lastIndex = i;
      if ((m = reBlockComment.exec(code))) { push("comment", m[0]); i = reBlockComment.lastIndex; prevSig = " "; atLineStart = false; continue; }
    }
    reLineComment.lastIndex = i;
    if ((m = reLineComment.exec(code))) { push("comment", m[0]); i = reLineComment.lastIndex; prevSig = " "; atLineStart = false; continue; }

    reString.lastIndex = i;
    if ((m = reString.exec(code))) {
      // JSON 的键（后面跟冒号）单独着色，与 IDEA 的 JSON 观感一致
      let j = reString.lastIndex;
      while (j < n && (code[j] === " " || code[j] === "\t")) j++;
      push(lang === "json" && code[j] === ":" ? "field" : "string", m[0]);
      i = reString.lastIndex; prevSig = '"'; atLineStart = false; continue;
    }
    reChar.lastIndex = i;
    if ((m = reChar.exec(code))) { push("string", m[0]); i = reChar.lastIndex; prevSig = "'"; atLineStart = false; continue; }

    reNumber.lastIndex = i;
    if ((m = reNumber.exec(code))) { push("number", m[0]); i = reNumber.lastIndex; prevSig = "0"; atLineStart = false; continue; }

    reAnnotation.lastIndex = i;
    if ((m = reAnnotation.exec(code))) { push("annot", m[0]); i = reAnnotation.lastIndex; prevSig = m[0]; atLineStart = false; continue; }

    reWord.lastIndex = i;
    if ((m = reWord.exec(code))) {
      const word = m[0];
      let j = reWord.lastIndex;
      while (j < n && /[ \t]/.test(code[j])) j++;
      const next = code[j] || "";
      let cls = "";
      if (keywords.has(word)) cls = "keyword";
      else if (next === "(") cls = "method";
      else if (prevSig === ".") cls = "field";
      else if (/^[A-Z][A-Z0-9_]{1,}$/.test(word) && word.indexOf("_") >= 0) cls = "field";
      else if (/^[A-Z]/.test(word)) cls = "type";
      push(cls, word);
      i = reWord.lastIndex;
      prevSig = word.slice(-1);
      atLineStart = false;
      continue;
    }

    const ch = code[i];
    if (/\s/.test(ch)) { push("", ch); if (ch === "\n") atLineStart = true; }
    else { push("punct", ch); prevSig = ch; atLineStart = false; }
    i++;
  }
  return out.join("");
}

function wireSyntaxHighlight() {
  document.querySelectorAll("#content pre > code").forEach((code) => {
    const m = /language-([\w-]+)/.exec(code.className);
    if (!m) return;
    const lang = m[1].toLowerCase();
    if (!Object.prototype.hasOwnProperty.call(HL_KEYWORDS, lang)) return;
    if (!HL_KEYWORDS[lang]) return;                       // text：不处理
    if (code.dataset.highlighted === "1") return;
    code.innerHTML = hlTokenize(code.textContent, lang);
    code.dataset.highlighted = "1";
  });
}

function wireCopyButtons() {
  document.querySelectorAll("#content pre").forEach((pre) => {
    if (!pre.querySelector("code")) return;
    const btn = document.createElement("button");
    btn.className = "copy-btn";
    btn.type = "button";
    btn.textContent = "复制";
    btn.setAttribute("aria-label", "复制这段代码");
    btn.addEventListener("click", async () => {
      const text = pre.querySelector("code").innerText;
      let ok = true;
      try {
        await navigator.clipboard.writeText(text);
      } catch (e) {
        // 非安全上下文 / 老浏览器回退：临时 textarea + execCommand
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.opacity = "0";
        document.body.appendChild(ta);
        ta.select();
        try { ok = document.execCommand("copy"); } catch (e2) { ok = false; }
        ta.remove();
      }
      btn.textContent = ok ? "已复制" : "复制失败";
      setTimeout(() => { btn.textContent = "复制"; }, 1500);
    });
    pre.appendChild(btn);
  });
}

/* ---------- 回到顶部 ---------- */

function wireBackToTop() {
  const btn = document.createElement("button");
  btn.id = "to-top";
  btn.type = "button";
  btn.textContent = "↑";
  btn.setAttribute("aria-label", "回到顶部");
  btn.hidden = true;
  btn.addEventListener("click", () => window.scrollTo({ top: 0, behavior: "smooth" }));
  document.body.appendChild(btn);
  const onScroll = () => { btn.hidden = window.scrollY < 600; };
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
}

/* ---------- 侧边栏 ---------- */

async function buildSidebar() {
  const sidebar = document.getElementById("sidebar");
  const content = document.getElementById("content");
  if (!sidebar || !content) return;

  const current = document.body.dataset.page || "";
  const menu = await loadMenu();
  const parts = [];

  parts.push('<a class="brand" href="/">MineGenshin 文档<small>Minecraft 26.2 · NeoForge · Java 25</small></a>');
  parts.push('<input id="filter" class="search" type="search" placeholder="过滤目录…" autocomplete="off">');

  const groups = [...new Set(menu.map((m) => m.group))];
  for (const group of groups) {
    parts.push(`<div class="side-group">${group}</div><ul class="side-list">`);
    for (const page of menu.filter((m) => m.group === group)) {
      const id = page.slug === "index" ? "index" : page.slug;
      const active = id === current ? ' class="active"' : "";
      parts.push(`<li><a href="${page.href}"${active}>${page.title}</a></li>`);
    }
    parts.push("</ul>");
  }

  // 源文档用 h1 作大节标题，因此目录覆盖 h1/h2/h3；页面自身的标题（第一个 h1）排除在外
  const pageTitle = content.querySelector("h1");
  const headings = [...content.querySelectorAll("h1, h2, h3")].filter((h) => h !== pageTitle);
  if (headings.length) {
    parts.push('<div class="side-group">本页目录</div><ul class="side-list" id="toc">');
    headings.forEach((h) => {
      if (!h.id) h.id = slug(h.textContent);
      const level = h.tagName === "H3" ? " lv3" : "";
      parts.push(`<li><a href="#${h.id}" class="toc-link${level}" data-text="${h.textContent}">${h.textContent}</a></li>`);
      const anchor = document.createElement("a");
      anchor.className = "anchor";
      anchor.href = `#${h.id}`;
      anchor.textContent = "#";
      h.appendChild(anchor);
    });
    parts.push("</ul>");
  }

  sidebar.innerHTML = parts.join("");
  wireFilter();
  wireScrollSpy();
}

function wireFilter() {
  const input = document.getElementById("filter");
  if (!input) return;
  input.addEventListener("input", () => {
    const q = input.value.trim().toLowerCase();
    document.querySelectorAll(".side-list li").forEach((li) => {
      const text = li.textContent.toLowerCase();
      li.hidden = q.length > 0 && !text.includes(q);
    });
  });
}

function wireScrollSpy() {
  const links = [...document.querySelectorAll(".toc-link")];
  if (!links.length) return;
  const byId = new Map(links.map((a) => [a.getAttribute("href").slice(1), a]));
  const observer = new IntersectionObserver(
    (entries) => {
      const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
      if (!visible.length) return;
      links.forEach((a) => a.classList.remove("active"));
      const link = byId.get(visible[0].target.id);
      if (link) {
        link.classList.add("active");
        // 手机上侧边栏是抽屉：自动滚动高亮会让抽屉自己乱跳，所以只在桌面端跟随
        if (!isMobile()) link.scrollIntoView({ block: "nearest" });
      }
    },
    { rootMargin: "-10% 0px -75% 0px", threshold: [0, 1] }
  );
  document.querySelectorAll("#content h1, #content h2, #content h3").forEach((h) => observer.observe(h));
}

document.addEventListener("DOMContentLoaded", () => {
  wireMobileNav();
  wrapTables();
  wireSyntaxHighlight();
  wireCopyButtons();
  wireBackToTop();
  buildSidebar();
});
/* 文档站导航：跨页菜单 + 页内目录自动生成 + 滚动高亮 + 过滤 + 移动端抽屉。
   不依赖任何构建工具或网络资源。 */

/* 菜单来自后端 /api/docs（DocCatalog 是唯一来源）。下面这份是 file:// 直接打开时的兜底，
   结构与 DocCatalog 一致：分类（group）→ 文档（section）→ 章节页。 */

const FALLBACK_LOOSE = [
  ["sys-registry", "注册中心与内容注册", "系统详解"],
  ["sys-character", "角色系统", "系统详解"],
  ["sys-attachment-sync", "附件与数据同步", "系统详解"],
  ["sys-combat-attack", "战斗 · 攻击与伤害管线", "系统详解"],
  ["sys-combat-action", "战斗 · 动作与动画", "系统详解"],
  ["sys-flight", "飞行与下落攻击", "系统详解"],
  ["sys-element-reaction", "元素附着与元素反应", "系统详解"],
  ["sys-element-host", "元素载体：可附着宿主", "系统详解"],
  ["sys-attribute-effect", "属性与角色效果", "系统详解"],
  ["sys-loot-monster", "掉落与怪物等级", "系统详解"],
  ["sys-shield-status", "护盾与状态", "系统详解"],
  ["sys-poise-control", "韧性与控制", "系统详解"],
  ["sys-render-asset", "资源、渲染与界面", "系统详解"],
  ["sys-network-event", "网络、事件与数据生成", "系统详解"],
  ["sys-performance", "性能优化系统", "系统详解"],
  ["ldlib2-node-graph", "LDLib2 节点图工具包", "扩展框架"],
  ["graphics-matrix-notes", "图形学学习笔记：4×4 变换矩阵", "图形学学习笔记"],
  ["entity-development", "实体开发文档", "深入文档"],
  ["entity-ai", "实体 AI 指南", "深入文档"],
  ["character-system", "角色系统详解（薇斯娜）", "深入文档"],
  ["character-implementations", "角色实现清单", "深入文档"],
  ["port-targeting", "索敌系统移植参考", "深入文档"],
  ["port-targeting-patch", "索敌移植补丁记录", "深入文档"],
  ["readme", "项目介绍", "项目"],
  ["changelog", "更新日志", "项目"],
];

const FALLBACK_BOOKS = [
  { book: "渲染与 Photon2 完全参考", sub: "reference", chapters: [
    ["intro", "0. 导读"], ["frame", "1. 心智模型：一帧是怎么画出来的"], ["blaze3d", "2. Blaze3D API 地图"],
    ["shaders", "3. 着色器、渲染管线与 GPU 数据"], ["entity-render", "4. 实体渲染与渲染状态"],
    ["geckolib", "5. GeckoLib：骨骼动画与渲染层"], ["transform", "6. 坐标空间、矩阵与四元数"],
    ["gpu-skinning", "7. GPU 蒙皮与渲染性能"], ["photon-runtime", "8. Photon2：从编辑器到运行时"],
    ["photon-api", "9. Photon2 Java API 与运行时注入"], ["practice", "10. 项目实战"],
    ["troubleshooting", "11. 排错手册"], ["appendix", "12. 附录"],
  ] },
  { book: "渲染与 Photon2 特效", sub: "effects", chapters: [
    ["mental-model", "1. 先建立正确的心智模型"], ["blaze3d", "2. Blaze3D 是什么、怎么写"],
    ["coordinates", "3. 坐标空间完全指南"], ["geckolib", "4. GeckoLib 的渲染管线"],
    ["shaders", "5. 着色器与渲染类型"], ["photon", "6. Photon2 的渲染架构"],
    ["practice", "7. 实战：把渲染接到 Photon"], ["performance", "8. 性能"],
    ["troubleshooting", "9. 排错手册"], ["appendix", "10. 附录"],
  ] },
];

function fallbackMenu() {
  const items = [{ slug: "index", title: "文档首页", group: "导览", section: "", href: "/", version: "" }];
  for (const [slug, title, group] of FALLBACK_LOOSE) {
    items.push({ slug: slug, title: title, group: group, section: "", href: "/doc/" + slug, version: "" });
  }
  for (const book of FALLBACK_BOOKS) {
    for (const version of ["26.2", "1.21.1"]) {
      for (const [id, title] of book.chapters) {
        const slug = "rendering-" + version + "-" + book.sub + "-" + id;
        items.push({ slug: slug, title: title, group: "渲染与特效", section: book.book, href: "/doc/" + slug, version: version });
      }
    }
  }
  return items;
}

async function loadMenu() {
  try {
    const res = await fetch("/api/docs", { headers: { accept: "application/json" } });
    if (res.ok) {
      const docs = await res.json();
      const items = [{ slug: "index", title: "文档首页", group: "导览", section: "", href: "/", version: "" }];
      for (const d of docs) {
        items.push({ slug: d.slug, title: d.title, group: d.group, section: d.section || "",
                     href: "/doc/" + d.slug, version: d.version || "" });
      }
      return items;
    }
  } catch (e) {
    /* file:// 直接打开时没有后端，走兜底 */
  }
  return fallbackMenu();
}
/* ---------- 版本状态 ----------
   站点一次只显示一套目录：带版本的文档按当前版本出一篇，其余文档两条线共用。
   当前版本 = 页面自带的版本 → 上次选择 → 站点默认。 */

const VERSION_KEY = "minegenshin.docs.version";
const DEFAULT_VERSION = "1.21.1";

function storedVersion() {
  try {
    return localStorage.getItem(VERSION_KEY) || "";
  } catch (e) {
    /* 隐私模式 / file:// 下取不到，按没有算 */
    return "";
  }
}

function setStoredVersion(version) {
  try {
    if (version) localStorage.setItem(VERSION_KEY, version);
  } catch (e) {
    /* 同上 */
  }
}

function activeVersion() {
  return document.body.dataset.version || storedVersion() || DEFAULT_VERSION;
}

/* 左栏的展开状态与滚动位置也记在 localStorage —— 换页时保持原样，不重置。
   这是「记住用户点过什么」，不是自动展开：默认仍然全部收起。 */

const NAV_STATE_KEY = "minegenshin.docs.nav";

function readNavState() {
  try {
    return JSON.parse(localStorage.getItem(NAV_STATE_KEY) || "{}");
  } catch (e) {
    return {};
  }
}

function writeNavState(state) {
  try {
    localStorage.setItem(NAV_STATE_KEY, JSON.stringify(state));
  } catch (e) {
    /* 隐私模式 / 配额满：忽略 */
  }
}
/** 切换版本：只换版本相关的显示（目录里配对的那几篇、首页卡片、按钮状态）。 */
function applyVersion(version) {
  document.body.dataset.version = version;
  document.querySelectorAll("#versionbar .vchip").forEach((chip) => {
    const own = chip.dataset.version === version;
    chip.classList.toggle("active", own);
    if (own) chip.setAttribute("aria-current", "true");
    else chip.removeAttribute("aria-current");
  });
  document.querySelectorAll("#sidebar li.nav-item[data-version]").forEach((li) => {
    li.hidden = li.dataset.version !== version;
  });
  document.querySelectorAll("#sidebar details.nav-group").forEach((group) => {
    const visible = [...group.querySelectorAll("li.nav-item")].some((li) => !li.hidden);
    group.hidden = !visible;
  });
  document.querySelectorAll(".card[data-version]").forEach((card) => {
    card.hidden = card.dataset.version !== version;
  });
}

/** 右上角版本按钮：带配对的文档跳对应篇，其余就地切换。 */
function wireVersionSwitch() {
  const bar = document.getElementById("versionbar");
  if (!bar) return;
  bar.querySelectorAll(".vchip").forEach((chip) => {
    chip.addEventListener("click", () => {
      const version = chip.dataset.version;
      setStoredVersion(version);
      if (chip.dataset.href) {
        location.href = chip.dataset.href;
        return;
      }
      applyVersion(version);
    });
  });
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

/* ---------- 侧边栏：纯导航（分类 → 文档 → 章节页） ----------
   默认全部收起，只有点击才展开；没有任何自动展开、自动跟随高亮。 */

function navPageItem(page) {
  const version = page.version ? ` data-version="${page.version}"` : "";
  return `<li class="nav-item"${version} data-title="${page.title}"><a href="${page.href}">${page.title}</a></li>`;
}

async function buildSidebar() {
  const sidebar = document.getElementById("sidebar");
  if (!sidebar) return;
  const current = document.body.dataset.page || "";
  const menu = await loadMenu();

  const parts = [];
  parts.push('<a class="brand" href="/">MineGenshin 文档<small>NeoForge Mod 开发文档</small></a>');
  parts.push('<input id="filter" class="search" type="search" placeholder="过滤目录…" autocomplete="off">');

  for (const group of [...new Set(menu.map((m) => m.group))]) {
    const inGroup = menu.filter((m) => m.group === group);
    parts.push('<details class="nav-group" data-nav-key="g:' + group + '"><summary><span class="nav-caret" aria-hidden="true"></span>' +
      `<span class="nav-group-title">${group}</span></summary><ul class="nav-list">`);
    for (const page of inGroup.filter((m) => !m.section)) {
      parts.push(navPageItem(page));
    }
    for (const book of [...new Set(inGroup.filter((m) => m.section).map((m) => m.section))]) {
      parts.push('<li class="nav-book"><details class="nav-book-details" data-nav-key="b:' + book + '">' +
        '<summary><span class="nav-caret" aria-hidden="true"></span>' +
        `<span class="nav-book-title">${book}</span></summary><ul class="nav-list">`);
      for (const page of inGroup.filter((m) => m.section === book)) {
        parts.push(navPageItem(page));
      }
      parts.push("</ul></details></li>");
    }
    parts.push("</ul></details>");
  }

  sidebar.innerHTML = parts.join("");

  const link = sidebar.querySelector(`li.nav-item a[href="/doc/${current}"]`);
  if (link) link.classList.add("active");

  wireNavState(sidebar);
  wireFilter();
}

/* 恢复上次的展开状态与滚动位置，并把之后的变化记下来 */
function wireNavState(sidebar) {
  const state = readNavState();
  sidebar.querySelectorAll("details[data-nav-key]").forEach((details) => {
    if (state[details.dataset.navKey] === true) details.open = true;
    details.addEventListener("toggle", () => {
      const now = readNavState();
      now[details.dataset.navKey] = details.open;
      writeNavState(now);
    });
  });
  if (typeof state.__scroll === "number") sidebar.scrollTop = state.__scroll;
  let timer = 0;
  sidebar.addEventListener("scroll", () => {
    if (timer) return;
    timer = window.setTimeout(() => {
      timer = 0;
      const now = readNavState();
      now.__scroll = sidebar.scrollTop;
      writeNavState(now);
    }, 200);
  });
}

/* ---------- 右侧「本页」：只列当前页的 h2/h3，纯链接，不动左栏 ---------- */

function buildOutline() {
  const outline = document.getElementById("outline");
  const content = document.getElementById("content");
  if (!outline || !content) return;
  const headings = [...content.querySelectorAll("h2, h3")];
  if (!headings.length) {
    outline.hidden = true;
    return;
  }
  const parts = ['<div class="outline-title">本页</div><ul class="outline-list">'];
  for (const h of headings) {
    if (!h.id) h.id = slug(h.textContent);
    const clean = h.cloneNode(true);
    clean.querySelectorAll("a.anchor").forEach((a) => a.remove());
    const lv = h.tagName === "H3" ? " lv3" : "";
    parts.push(`<li><a href="#${h.id}" class="outline-link${lv}">${clean.textContent.trim()}</a></li>`);
    if (!h.querySelector("a.anchor")) {
      const anchor = document.createElement("a");
      anchor.className = "anchor";
      anchor.href = `#${h.id}`;
      anchor.textContent = "#";
      h.appendChild(anchor);
    }
  }
  parts.push("</ul>");
  outline.innerHTML = parts.join("");
  outline.hidden = false;
}

/** 过滤目录只筛页面级条目；输入时把命中的分类展开（这是用户输入触发的）。 */
function wireFilter() {
  const input = document.getElementById("filter");
  if (!input) return;
  input.addEventListener("input", () => {
    const q = input.value.trim().toLowerCase();
    document.querySelectorAll("#sidebar details.nav-group").forEach((group) => {
      let shown = 0;
      group.querySelectorAll("li.nav-item").forEach((li) => {
        const hit = q.length === 0 || (li.dataset.title || "").toLowerCase().includes(q);
        li.hidden = !hit;
        if (hit) shown++;
      });
      group.hidden = shown === 0;
      if (q.length > 0 && shown > 0) group.open = true;
    });
  });
}

document.addEventListener("DOMContentLoaded", () => {
  if (document.body.dataset.version) setStoredVersion(document.body.dataset.version);
  wireMobileNav();
  wrapTables();
  wireSyntaxHighlight();
  wireCopyButtons();
  wireBackToTop();
  wireVersionSwitch();
  buildSidebar().then(() => applyVersion(activeVersion()));
  buildOutline();
});
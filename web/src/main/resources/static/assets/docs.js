/* 文档站导航：跨页菜单 + 页内目录自动生成 + 滚动高亮 + 过滤。
   不依赖任何构建工具或网络资源，直接双击 index.html 也能用。 */

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
  { slug: "entity-development", title: "实体开发文档", group: "深入文档", href: "/entity-development.html" },
  { slug: "entity-ai", title: "实体 AI 指南", group: "深入文档", href: "/doc/entity-ai" },
  { slug: "character-system", title: "角色系统详解（薇斯娜）", group: "深入文档", href: "/doc/character-system" },
  { slug: "character-implementations", title: "角色实现清单", group: "深入文档", href: "/doc/character-implementations" },
  { slug: "port-targeting", title: "索敌系统移植参考", group: "深入文档", href: "/doc/port-targeting" },
  { slug: "port-targeting-patch", title: "索敌移植补丁记录", group: "深入文档", href: "/doc/port-targeting-patch" },
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
        link.scrollIntoView({ block: "nearest" });
      }
    },
    { rootMargin: "-10% 0px -75% 0px", threshold: [0, 1] }
  );
  document.querySelectorAll("#content h1, #content h2, #content h3").forEach((h) => observer.observe(h));
}

document.addEventListener("DOMContentLoaded", () => {
  const toggle = document.getElementById("menu-toggle");
  if (toggle) toggle.addEventListener("click", () => document.body.classList.toggle("nav-open"));
  document.addEventListener("click", (e) => {
    if (window.innerWidth <= 900 && e.target.closest("#sidebar a")) document.body.classList.remove("nav-open");
  });
  buildSidebar();
});

#!/usr/bin/env node
/**
 * 对比度量化自查 —— WCAG 2.1 relative luminance / contrast ratio
 *
 * 为什么需要这个脚本：
 *   2026-09 的视觉改造把 Bento 大卡与分类卡的底色从"中性 surface"换成了
 *   "满浓度类目色"，由此产生一批**从未被量化验证过**的前景/背景组合
 *   （例如 onSurfaceVariant 压在 LIFE 的 #FAEEDA 上、cat.on 压在 #20364F 上）。
 *   肉眼在浅色主题下"看着还行"完全不代表对比度达标，必须算。
 *
 * 判据（WCAG 2.1）：
 *   正文文本         >= 4.5:1
 *   大字 / 非文本图形 >= 3.0:1
 *   "色块 vs 页面底色"没有 WCAG 标准，仅作**设计可辨度参考**输出数值不判定。
 *
 * 色值来源：实时解析 Color.kt，改色后重跑即可，不需要同步维护第二份色表。
 *
 * 用法：  node scripts/contrast_check.mjs
 * 退出码：存在任何 FAIL 时为 1（可直接挂 CI）。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const COLOR_KT = path.resolve(
  HERE,
  '../app/src/main/java/com/flechazo/toolbox/core/designsystem/theme/Color.kt',
);

// ---------------------------------------------------------------------------
// 解析 Color.kt
// ---------------------------------------------------------------------------

const src = fs.readFileSync(COLOR_KT, 'utf8');

/** val md_light_xxx = Color(0xFFRRGGBB) → Map<"light_xxx", "RRGGBB"> */
const flat = new Map();
for (const m of src.matchAll(/^\s*val\s+(md_\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)/gm)) {
  flat.set(m[1], m[2].toUpperCase());
}

/** 解析 `val Name: Map<String, CategoryColor> = mapOf( ... )` */
function categoryMap(name) {
  const block = src.match(new RegExp(`val\\s+${name}\\s*:[^=\\n]*=\\s*mapOf\\(([\\s\\S]*?)\\n\\)`));
  if (!block) throw new Error(`Color.kt 里找不到 ${name}`);
  const out = new Map();
  for (const e of block[1].matchAll(
    /"(\w+)"\s*to\s*CategoryColor\(Color\(0xFF([0-9A-Fa-f]{6})\)\s*,\s*Color\(0xFF([0-9A-Fa-f]{6})\)\)/g,
  )) {
    out.set(e[1], { container: e[2].toUpperCase(), on: e[3].toUpperCase() });
  }
  if (out.size === 0) throw new Error(`${name} 解析出 0 条 —— 正则失配，脚本需要更新`);
  return out;
}

const CAT = {
  light: categoryMap('LightCategoryColors'),
  dark: categoryMap('DarkCategoryColors'),
};

const ref = (theme, key) => {
  const v = flat.get(`md_${theme}_${key}`);
  if (!v) throw new Error(`Color.kt 缺少 md_${theme}_${key}`);
  return v;
};
const L = (key) => ref('light', key);
const D = (key) => ref('dark', key);

// ---------------------------------------------------------------------------
// WCAG 2.1
// ---------------------------------------------------------------------------

const toLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);

function luminance(hex) {
  const [r, g, b] = [0, 2, 4]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(toLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
  const [x, y] = [luminance(a), luminance(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

const TEXT = 4.5;
const GRAPHIC = 3.0;

// ---------------------------------------------------------------------------
// 色对清单 —— 与 HomeBento.kt / Components.kt 的实际用法一一对应
// ---------------------------------------------------------------------------

const pairs = [];
const add = (group, label, fg, bg, min, isRef = false) =>
  pairs.push({ group, label, fg, bg, min, isRef });

for (const theme of ['light', 'dark']) {
  const T = theme === 'light' ? L : D;
  const zh = theme === 'light' ? '浅色' : '深色';

  for (const [key, c] of CAT[theme]) {
    // HeroToolCard：底色 = 满浓度类目色
    add(`Bento 大卡 · ${zh}`, `标题 onSurface on ${key}`, T('onSurface'), c.container, TEXT);
    add(`Bento 大卡 · ${zh}`, `描述 onSurfaceVariant on ${key}`, T('onSurfaceVariant'), c.container, TEXT);
    add(`Bento 大卡 · ${zh}`, `类目标签 cat.on on ${key}`, c.on, c.container, TEXT);
    add(`Bento 大卡 · ${zh}`, `图标 cat.on on 图标底片`, c.on, T('surfaceContainerLowest'), GRAPHIC);
    // CategoryEntryCard：底色同样是满浓度类目色
    add(`分类入口卡 · ${zh}`, `标题 onSurface on ${key}`, T('onSurface'), c.container, TEXT);
    add(`分类入口卡 · ${zh}`, `副文本 onSurfaceVariant on ${key}`, T('onSurfaceVariant'), c.container, TEXT);
    add(`分类入口卡 · ${zh}`, `圆点 cat.on on ${key}`, c.on, c.container, GRAPHIC);
    // 小卡的图标底片 = 类目色叠在中性卡底上
    add(`小卡图标底片 · ${zh}`, `图标 cat.on on ${key}`, c.on, c.container, GRAPHIC);
    // 非文本可辨度：色块本身要能从页面底色里看出来
    add(`色块可辨度 · ${zh}`, `${key}.container vs surface`, c.container, T('surface'), 0, true);
  }

  // CompactToolCard：中性底
  add(`小卡 · ${zh}`, '标题 onSurface on surfaceContainerLow', T('onSurface'), T('surfaceContainerLow'), TEXT);
  add(`小卡 · ${zh}`, '描述 onSurfaceVariant on surfaceContainerLow', T('onSurfaceVariant'), T('surfaceContainerLow'), TEXT);

  // 语义色
  add(`语义色 · ${zh}`, 'onSuccess on success', T('onSuccess'), T('success'), TEXT);
  add(`语义色 · ${zh}`, 'onSuccessContainer on successContainer', T('onSuccessContainer'), T('successContainer'), TEXT);
  add(`语义色 · ${zh}`, 'onWarning on warning', T('onWarning'), T('warning'), TEXT);
  add(`语义色 · ${zh}`, 'onWarningContainer on warningContainer', T('onWarningContainer'), T('warningContainer'), TEXT);

  // M3 主色组（应当由模板保证，但既然能算就一起验）
  for (const role of ['Primary', 'Secondary', 'Tertiary', 'Error']) {
    const low = role.toLowerCase();
    add(`主色 · ${zh}`, `on${role} on ${low}`, T(`on${role}`), T(low), TEXT);
    add(`主色 · ${zh}`, `on${role}Container on ${low}Container`, T(`on${role}Container`), T(`${low}Container`), TEXT);
  }
  add(`主色 · ${zh}`, 'onSurface on surface', T('onSurface'), T('surface'), TEXT);
  add(`主色 · ${zh}`, 'onSurfaceVariant on surface', T('onSurfaceVariant'), T('surface'), TEXT);
  add(`主色 · ${zh}`, 'onBackground on background', T('onBackground'), T('background'), TEXT);
}

// ---------------------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------------------

const rows = pairs.map((p) => {
  const cr = contrast(p.fg, p.bg);
  return { ...p, cr, ok: p.isRef || cr >= p.min };
});

const fails = rows.filter((r) => !r.ok);
const groups = [...new Set(rows.map((r) => r.group))];

console.log('# 对比度量化自查报告');
console.log('');
console.log(`色值来源：\`Color.kt\`　|　判据：WCAG 2.1（正文 ≥4.5:1，大字/图形 ≥3.0:1）　|　色对总数：${rows.length}`);
console.log('');

for (const g of groups) {
  console.log(`## ${g}`);
  console.log('');
  console.log('| 色对 | 前景 | 背景 | 对比度 | 要求 | 判定 |');
  console.log('| --- | --- | --- | ---: | ---: | :---: |');
  for (const r of rows.filter((x) => x.group === g)) {
    const need = r.isRef ? '参考' : `${r.min.toFixed(1)}:1`;
    const verdict = r.isRef ? '—' : r.ok ? '✅' : '❌';
    console.log(`| ${r.label} | \`#${r.fg}\` | \`#${r.bg}\` | **${r.cr.toFixed(2)}** | ${need} | ${verdict} |`);
  }
  console.log('');
}

console.log('---');
console.log('');
if (fails.length === 0) {
  console.log(`**结论：${rows.length} 组色对全部达标。**`);
} else {
  console.log(`**结论：${fails.length} 组不达标 ——**`);
  console.log('');
  for (const r of fails) {
    console.log(`- ${r.group} ｜ ${r.label}：**${r.cr.toFixed(2)}:1** < ${r.min}:1（\`#${r.fg}\` on \`#${r.bg}\`）`);
  }
}

// 可辨度参考值的额外提示（不参与判定）
const blockRows = rows.filter((r) => r.isRef);
const faint = blockRows.filter((r) => r.cr < 1.15);
if (faint.length) {
  console.log('');
  console.log(`另：${faint.length} 组色块与页面底色的对比 < 1.15，肉眼可辨度偏弱 ——`);
  for (const r of faint) {
    console.log(`- ${r.label}：${r.cr.toFixed(3)}`);
  }
}

process.exit(fails.length === 0 ? 0 : 1);

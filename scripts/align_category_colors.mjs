// 把浅色类目 container 对齐到同一"色块可辨度"（vs 页面底色）。
//
// 为什么需要：6 张 Bento 色块如果浓度不齐，"有颜色 = 优先级高"这个信号强度就不一致。
// 做法：保持色相 H 与饱和度 S 不变，只二分调整 HSL 的 L，使相对亮度命中目标。
// 只在 H/S 不变的前提下调 L，才能保证"改的是浓度，不是颜色"。
//
// 用法：node scripts/align_category_colors.mjs [目标对比度，默认 1.18]

const TARGET = Number(process.argv[2] ?? 1.18);

const BG = [0xfd, 0xfb, 0xf7]; // md_light_surface

const PALETTE = {
  CALCULATE: [0xdd, 0xe9, 0xfa],
  IMAGE: [0xee, 0xed, 0xfe],
  TEXT: [0xe1, 0xf5, 0xee],
  LIFE: [0xfa, 0xee, 0xda],
  MEASURE: [0xea, 0xf3, 0xde],
  SECURITY: [0xfb, 0xea, 0xf0],
};

const srgbToLin = (c) => {
  const x = c / 255;
  return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
};
const lum = ([r, g, b]) =>
  0.2126 * srgbToLin(r) + 0.7152 * srgbToLin(g) + 0.0722 * srgbToLin(b);
const contrast = (a, b) => {
  const la = lum(a);
  const lb = lum(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

function rgbToHsl([r, g, b]) {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0;
  let s = 0;
  const l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h /= 6;
  }
  return [h, s, l];
}

function hslToRgb([h, s, l]) {
  const hue2rgb = (p, q, t) => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  if (s === 0) {
    const v = Math.round(l * 255);
    return [v, v, v];
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  return [
    Math.round(hue2rgb(p, q, h + 1 / 3) * 255),
    Math.round(hue2rgb(p, q, h) * 255),
    Math.round(hue2rgb(p, q, h - 1 / 3) * 255),
  ];
}

const hex = (rgb) =>
  '#' + rgb.map((x) => x.toString(16).padStart(2, '0').toUpperCase()).join('');

// 命中目标亮度所需的 HSL 明度（保持 H/S 不变）
function solveL(h, s, targetLum) {
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 60; i++) {
    const mid = (lo + hi) / 2;
    if (lum(hslToRgb([h, s, mid])) > targetLum) hi = mid;
    else lo = mid;
  }
  return (lo + hi) / 2;
}

const bgLum = lum(BG);
const targetLum = (bgLum + 0.05) / TARGET - 0.05;

console.log(`页面底色 ${hex(BG)} 亮度 ${bgLum.toFixed(6)}`);
console.log(`目标：container vs surface = ${TARGET}:1 → 目标亮度 ${targetLum.toFixed(6)}\n`);
console.log('| 类目 | 原色值 | 原可辨度 | 新色值 | 新可辨度 | 色相偏移 |');
console.log('| --- | --- | ---: | --- | ---: | ---: |');

const result = {};
for (const [key, rgb] of Object.entries(PALETTE)) {
  const [h, s] = rgbToHsl(rgb);
  const l = solveL(h, s, targetLum);
  const next = hslToRgb([h, s, l]);
  const [h2] = rgbToHsl(next);
  result[key] = hex(next);
  console.log(
    `| ${key} | \`${hex(rgb)}\` | ${contrast(rgb, BG).toFixed(4)} | \`${hex(next)}\` | ` +
      `${contrast(next, BG).toFixed(4)} | ${(Math.abs(h2 - h) * 360).toFixed(4)}° |`,
  );
}

console.log('\nColor.kt 片段：');
for (const [key, v] of Object.entries(result)) {
  console.log(`    "${key}" to CategoryColor(Color(0x${v.slice(1)}), Color(0xFF...)),`);
}

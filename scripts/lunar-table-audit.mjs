/**
 * 农历表审计：TableLunarCalendar.LUNAR_INFO 的逐位校验。
 *
 * 为什么需要它：那张 201 项的表是**手抄的数据块**，JVM 单测只能拿表自校验 ——
 * 而真正出现过的三类错误（1933 / 1996 / 2060）全都是"全年月长总和正确、月内分布错位"，
 * 往返一致、年长区间、闰月数量这些断言一条都抓不到，只有独立的天文算法实现能抓。
 *
 * 做法：用 6tail/lunar-javascript（按真朔+中气、UTC+8 推算，与这张十六进制表无渊源）
 * 反推每一农历年的 12+1 个月段长度，重建出 info 位模式，与 Kotlin 里的表逐项比对。
 *
 * 用法（表被改动后、或每季度跑一次）：
 *   node scripts/lunar-table-audit.mjs
 *
 * 依赖会被下载到 build/lunar-oracle/（已随 build 目录一起被 gitignore），不参与运行时。
 * 有差异时以**出版的黄历/万年历日期事实**裁决，不要盲从本脚本 —— 两个实现都可能错。
 */
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync, mkdirSync, existsSync, statSync } from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dirname, '..')
const CACHE_DIR = path.join(ROOT, 'build', 'lunar-oracle')
const LIB = path.join(CACHE_DIR, 'lunar.js')
const KOTLIN = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'flechazo', 'toolbox', 'feature', 'countdown', 'TableLunarCalendar.kt')
const SOURCE = 'https://cdn.jsdelivr.net/gh/6tail/lunar-javascript@master/lunar.js'
const BASE_YEAR = 1900
const YEARS = 201

if (!existsSync(LIB)) {
  mkdirSync(CACHE_DIR, { recursive: true })
  // 用 node 自带 fetch：Windows 下 curl 走 schannel 会因证书吊销检查离线失败
  // (CRYPT_E_REVOCATION_OFFLINE)，而子进程 + 管道在受限环境里也不稳。
  const res = await fetch(SOURCE, { redirect: 'follow' })
  if (!res.ok) {
    console.error(`下载 oracle 失败 HTTP ${res.status}: ${SOURCE}`)
    console.error(`也可以手工把该文件放到 ${LIB} 再重跑。`)
    process.exit(2)
  }
  writeFileSync(LIB, await res.text())
}

const { Lunar } = createRequire(import.meta.url)(LIB)

/** 逐月段走一遍该农历年，重建 info：bit16=闰月大小，bit15..4=正月起各月大小，bit3..0=闰几月。 */
function infoOf(year) {
  let cur = Lunar.fromYmd(year, 1, 1).getSolar()
  const segments = []
  for (let i = 0; i < 14; i++) {
    const l = cur.getLunar()
    if (l.getYear() !== year || l.getDay() !== 1) break
    const month = Math.abs(l.getMonth())
    const leap = l.getMonth() < 0
    let length = 1
    while (length < 32) {
      const n = cur.next(length).getLunar()
      if (Math.abs(n.getMonth()) !== month || (n.getMonth() < 0) !== leap) break
      length++
    }
    segments.push({ month, leap, length })
    cur = cur.next(length)
  }
  let info = 0
  let leapLength = 0
  for (const s of segments) {
    if (s.leap) leapLength = s.length
    else if (s.length === 30) info |= 0x10000 >> s.month
  }
  const leapMonth = segments.find((s) => s.leap)?.month ?? 0
  if (leapMonth) info |= leapMonth
  if (leapLength === 30) info |= 0x10000
  const total = segments.reduce((a, s) => a + s.length, 0)
  return { info, total, segments: segments.length }
}

// 只取数据行：注释里也会写 0x05ac0 这样的字面量，混进来会整体错位
const tableText = readFileSync(KOTLIN, 'utf8')
const body = tableText.slice(tableText.indexOf('LUNAR_INFO = intArrayOf('))
const table = body
  .split('\n')
  .filter((line) => !line.trim().startsWith('//'))
  .join('\n')
const entries = [...table.matchAll(/0x([0-9a-fA-F]{5})/g)].map((m) => parseInt(m[1], 16))

if (entries.length !== YEARS) {
  console.error(`表长度 ${entries.length}，期望 ${YEARS}（1900..2100）—— 解析方式该修了，别信下面的结论`)
  process.exit(2)
}

let bad = 0
for (let i = 0; i < YEARS; i++) {
  const year = BASE_YEAR + i
  const truth = infoOf(year)
  if (truth.info !== entries[i]) {
    bad++
    console.log(
      `year ${year}: table=0x${entries[i].toString(16).padStart(5, '0')} ` +
        `oracle=0x${truth.info.toString(16).padStart(5, '0')} ` +
        `年长=${truth.total} 月段=${truth.segments}`,
    )
  }
  // 月段数只能是 12（平年）或 13（闰年）；年长必须落在 353..385。越界说明 oracle 本身跑飞了。
  if (truth.segments < 12 || truth.segments > 13) console.log(`  !! ${year} 月段数异常 ${truth.segments}`)
  if (truth.total < 353 || truth.total > 385) console.log(`  !! ${year} 年长异常 ${truth.total}`)
}
console.log(bad === 0 ? `OK: ${YEARS} 年逐月长度与天文实现完全一致` : `MISMATCHES: ${bad}/${YEARS}`)

/**
 * 第二道校验：香港天文台 (HKO) 官方 Gregorian-Lunar 转换表。
 * 这是**一手出版物**，不是又一份抄本，权威性高于任何实现 —— 1933 年那处错误就是它定的。
 * 数据需自行下载到 HKO_DIR（每卷 `T{年份}e.txt`），缺失时跳过而不是假通过。
 *
 * 格式（每月首日一行）：`1933-06-23     5th Lunar month     Friday`
 * 闰月的写法是**同一个序数出现两次**（05-24 与 06-23 都是 5th），第二次即闰五月。
 */
const HKO_DIR = path.join(ROOT, 'lunar-research', 'hko')
const START_RE = /^(\d{4})[-/](\d{2})[-/](\d{2})\s+(\d+)(?:st|nd|rd|th) Lunar month/

function monthStarts(fileYear) {
  const p = path.join(HKO_DIR, `T${fileYear}e.txt`)
  if (!existsSync(p) || statSync(p).size < 1000) return null
  const out = []
  for (const line of readFileSync(p, 'utf8').split('\n')) {
    const m = START_RE.exec(line.trim())
    if (!m) continue
    out.push({ key: `${m[1]}-${m[2]}-${m[3]}`, ordinal: +m[4], day: Date.UTC(+m[1], +m[2] - 1, +m[3]) / 86400000 })
  }
  return out
}

function infoFromHko(year) {
  const pool = new Map()
  for (const fy of [year - 1, year, year + 1]) {
    const rows = monthStarts(fy)
    if (!rows) return null // 缺卷：宁可跳过，也不拿不完整的区间下结论
    for (const r of rows) pool.set(r.key, r)
  }
  const all = [...pool.values()].sort((a, b) => a.day - b.day)
  const from = all.findIndex((r) => r.ordinal === 1 && new Date(r.day * 86400000).getUTCFullYear() === year)
  if (from < 0) return null
  const segs = []
  for (let i = from; i < all.length - 1; i++) {
    if (i > from && all[i].ordinal === 1) break // 下一个农历年开始
    const cur = all[i]
    const nxt = all[i + 1]
    const isLeap = cur.ordinal === all[i - 1]?.ordinal
    segs.push({ month: cur.ordinal, isLeap, length: nxt.day - cur.day })
  }
  if (segs.length < 12 || segs.length > 13) return null
  let info = 0
  for (const s of segs) {
    if (s.length === 30) {
      if (s.isLeap) info |= 0x10000
      else info |= 0x10000 >> s.month
    }
    if (s.isLeap) info |= s.month
  }
  return { info, total: segs.reduce((a, s) => a + s.length, 0) }
}

if (!existsSync(HKO_DIR)) {
  console.log(`跳过 HKO 主源校验：${path.relative(ROOT, HKO_DIR)} 不存在（天文台年历需手工下载）`)
} else {
  let checked = 0
  let hkoBad = 0
  for (let i = 0; i < YEARS; i++) {
    const year = BASE_YEAR + i
    const h = infoFromHko(year)
    if (!h) continue
    checked++
    if (h.info !== entries[i]) {
      hkoBad++
      console.log(
        `HKO DIFF year ${year}: table=0x${entries[i].toString(16).padStart(5, '0')} ` +
          `hko=0x${h.info.toString(16).padStart(5, '0')} hko年长=${h.total}`,
      )
    }
  }
  console.log(
    hkoBad === 0
      ? `OK: 与 HKO 官方年历比对了 ${checked} 年，全部一致（另 ${YEARS - checked} 年缺卷未覆盖，不代表通过）`
      : `HKO MISMATCHES: ${hkoBad}/${checked} 年 —— 以官方年历为准`,
  )
  if (hkoBad > 0) process.exit(1)
}

process.exit(bad === 0 ? 0 : 1)

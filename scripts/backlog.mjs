#!/usr/bin/env node
/**
 * 待办台账管家：把 backlog/*.md 里的总览表收上来，统一核对，生成 backlog/OVERVIEW.md。
 *
 * 存在的理由：台账是手写的 Markdown，一旦"发版了但没核销""明细改了总览没改""工具改名了
 * 台账还挂着旧 id"这些问题出现，它就变成一份看着安心、实际骗人的清单。管家把这些变成
 * 机器可判定的 error —— 并且接进了 scripts/release.ps1，让发版动作本身给台账把关。
 *
 * 用法：
 *   node scripts/backlog.mjs                         生成总览 + 控制台摘要 + 体检
 *   node scripts/backlog.mjs --check                 只体检；有 error 则退出码 1（发版闸门用这个）
 *   node scripts/backlog.mjs --check --release v1.3.0  额外拦"本次要发的版本还有 P1 没做完"
 *   node scripts/backlog.mjs --list P1               只列某个重要程度
 *   node scripts/backlog.mjs --json                  输出机器可读结果
 *
 * 台账写法见 backlog/README.md（列名与取值域在那边定义，改动需同步这里）。
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dirname, '..')
const BACKLOG_DIR = path.join(ROOT, 'backlog')
const OVERVIEW = path.join(BACKLOG_DIR, 'OVERVIEW.md')
const CATALOG = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'flechazo', 'toolbox', 'core', 'registry', 'ToolCatalog.kt')
const RELEASE_LOG = path.join(ROOT, 'releases', 'RELEASE_LOG.md')
const GRADLE = path.join(ROOT, 'app', 'build.gradle.kts')

/** 总览表的列契约：列名与顺序都不能改（见 backlog/README.md）。 */
const COLS = ['编号', '类型', '标题', '重要程度', '计划版本', '状态', '登记时间']
const PRIORITIES = ['P1', 'P2', 'P3']
const STATUSES = ['⬜', '🔄', '✅']
const TYPE_LABEL = { '🐞': '缺陷', '✨': '功能扩展', '🎨': '体验优化', '🔒': '合规/隐私', '🛠': '工程/质量' }
const UNSCHEDULED = new Set(['', '—', '-', '未排', '未排期', '待定', 'TBD'])
/** 登记超过这么多天仍未开始的 P2/P3，提醒一次（不拦发版）。 */
const STALE_AFTER_DAYS = 180

const argv = process.argv.slice(2)
const has = (f) => argv.includes(f)
const opt = (name, dflt = null) => {
  const i = argv.indexOf(name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : dflt
}

// ---------- 仓库事实 ----------

/** ToolCatalog 是"所属"的唯一权威源：id 拼错、工具已删，都能当场发现。 */
function loadTools() {
  if (!existsSync(CATALOG)) return []
  const src = readFileSync(CATALOG, 'utf8')
  const out = []
  for (const m of src.matchAll(/id\s*=\s*"([A-Za-z0-9_]+)"\s*,\s*title\s*=\s*"([^"]+)"/g)) {
    out.push({ id: m[1], title: m[2] })
  }
  return out
}

function loadReleasedVersions() {
  if (!existsSync(RELEASE_LOG)) return []
  const out = []
  for (const m of readFileSync(RELEASE_LOG, 'utf8').matchAll(/^## (v\d+\.\d+\.\d+)/gm)) out.push(m[1])
  return out
}

function loadCurrentVersion() {
  if (!existsSync(GRADLE)) return null
  const g = readFileSync(GRADLE, 'utf8')
  const name = /versionName\s*=\s*"([^"]+)"/.exec(g)?.[1]
  const code = /versionCode\s*=\s*(\d+)/.exec(g)?.[1]
  return name ? `v${name}${code ? ` (code ${code})` : ''}` : null
}

// ---------- 解析 ----------

const clean = (s) => (s ?? '').replace(/\*\*/g, '').replace(/`/g, '').trim()

function cellsOf(line) {
  const t = line.trim()
  if (!t.startsWith('|')) return null
  const cells = t.split('|').slice(1, -1).map((c) => c.trim())
  return cells.length ? cells : null
}

function parseFrontMatter(raw) {
  if (!raw.startsWith('---')) return {}
  const end = raw.indexOf('\n---', 3)
  if (end < 0) return {}
  const fm = {}
  for (const line of raw.slice(3, end).split('\n')) {
    const m = /^([A-Za-z_][\w-]*)\s*:\s*(.*)$/.exec(line.trim())
    if (m) fm[m[1]] = m[2].trim()
  }
  return fm
}

/** 找到第一张符合列契约的总览表，返回条目与解析过程中发现的问题。 */
function parseOverviewTable(lines, file) {
  const issues = []
  let header = -1
  for (let i = 0; i < lines.length; i++) {
    const c = cellsOf(lines[i])
    if (!c) continue
    const norm = c.map(clean)
    if (norm[0] === COLS[0] && norm.includes(COLS[5])) {
      const missing = COLS.filter((k) => !norm.includes(k))
      if (missing.length) {
        issues.push({ level: 'error', msg: `${file}: 总览表缺列 ${missing.join(' / ')}` })
        return { items: [], issues }
      }
      header = i
      const idx = Object.fromEntries(COLS.map((k) => [k, norm.indexOf(k)]))
      const items = []
      for (let j = i + 1; j < lines.length; j++) {
        const row = cellsOf(lines[j])
        if (!row) break
        if (row.every((c2) => /^:?-{2,}:?$/.test(c2.trim()))) continue
        if (row.length < COLS.length) {
          issues.push({ level: 'error', msg: `${file}: 第 ${j + 1} 行只有 ${row.length} 列，应为 ${COLS.length} 列` })
          continue
        }
        items.push({
          id: clean(row[idx['编号']]),
          type: clean(row[idx['类型']]),
          title: clean(row[idx['标题']]),
          priority: clean(row[idx['重要程度']]),
          plan: clean(row[idx['计划版本']]),
          status: clean(row[idx['状态']]),
          date: clean(row[idx['登记时间']]),
          line: j + 1,
        })
      }
      return { items, issues }
    }
  }
  issues.push({ level: 'error', msg: `${file}: 找不到符合契约的总览表（表头须为「${COLS.join(' | ')}」）` })
  return { items: [], issues }
}

/** 明细小节里若重写了计划版本/重要程度，必须与总览一致 —— 不一致说明台账已经在骗人。 */
function parseDetails(lines) {
  const out = new Map()
  let current = null
  for (const line of lines) {
    const h = /^###\s+([A-Za-z0-9-]+)(\s|$|·|—)/.exec(line.trim())
    if (h) {
      current = h[1]
      if (!out.has(current)) out.set(current, {})
      continue
    }
    if (!current) continue
    const c = cellsOf(line)
    if (!c || c.length < 2) continue
    const key = clean(c[0])
    if (key === '**计划版本**' || key === '计划版本') out.get(current).plan = clean(c[1])
    if (key === '**重要程度**' || key === '重要程度') out.get(current).priority = clean(c[1])
    if (key === '**状态**' || key === '状态') out.get(current).status = clean(c[1])
  }
  return out
}

function planOf(text) {
  const t = clean(text)
  const m = /^v?(\d+)\.(\d+)(?:\.(x|\d+))?\b/.exec(t)
  if (!m) return { kind: UNSCHEDULED.has(t) ? 'none' : 'text', exact: null, series: null, raw: t || '—' }
  const series = `${m[1]}.${m[2]}.x`
  if (m[3] === 'x' || m[3] === undefined) return { kind: 'series', exact: null, series, raw: t }
  return { kind: 'exact', exact: `v${m[1]}.${m[2]}.${m[3]}`, series, raw: t }
}

const ageDays = (dateStr, now) => {
  const d = new Date(`${dateStr}T00:00:00Z`)
  return Number.isNaN(d.getTime()) ? null : Math.floor((now - d) / 86400000)
}

const isDone = (s) => s === '✅'
const isOpen = (s) => !isDone(s)

// ---------- 采集 ----------

function collect(tools) {
  const toolById = new Map(tools.map((t) => [t.id, t]))
  if (!existsSync(BACKLOG_DIR)) return { ledgers: [], issues: [{ level: 'error', msg: 'backlog/ 目录不存在' }] }
  const files = readdirSync(BACKLOG_DIR).filter((f) => f.endsWith('.md') && f !== 'README.md' && f !== 'OVERVIEW.md')
  const issues = []
  const ledgers = []
  const seenIds = new Map()

  if (!files.length) issues.push({ level: 'error', msg: 'backlog/ 里没有任何台账文件' })

  for (const file of files) {
    const stem = file.replace(/\.md$/, '')
    const raw = readFileSync(path.join(BACKLOG_DIR, file), 'utf8').replace(/^\uFEFF/, '')
    const fm = parseFrontMatter(raw)
    const lines = raw.split(/\r?\n/)
    const toolId = clean(fm.tool)
    const title = toolById.get(toolId)?.title ?? null

    if (!toolId) {
      issues.push({ level: 'error', msg: `${file}: front matter 缺 tool:` })
    } else if (!toolById.has(toolId)) {
      issues.push({ level: 'error', msg: `${file}: tool: ${toolId} 在 ToolCatalog 里不存在（拼错或工具已下线）` })
    } else if (stem !== toolId) {
      issues.push({ level: 'error', msg: `${file}: 文件名应与 tool: ${toolId} 一致（改成 ${toolId}.md）` })
    }
    if (fm.plan && !existsSync(path.join(ROOT, fm.plan))) {
      issues.push({ level: 'warn', msg: `${file}: plan 指向的文件不存在：${fm.plan}` })
    }

    const { items, issues: tableIssues } = parseOverviewTable(lines, file)
    issues.push(...tableIssues)
    const details = parseDetails(lines)

    const parsed = []
    for (const it of items) {
      if (!it.id) { issues.push({ level: 'error', msg: `${file}:${it.line} 编号为空` }); continue }
      if (seenIds.has(it.id)) {
        issues.push({ level: 'error', msg: `编号 ${it.id} 重复：${file}:${it.line} 与 ${seenIds.get(it.id)}` })
      } else seenIds.set(it.id, `${file}:${it.line}`)

      if (!PRIORITIES.includes(it.priority)) issues.push({ level: 'error', msg: `${file}:${it.line} ${it.id} 重要程度非法："${it.priority}"（须 ${PRIORITIES.join('/')}）` })
      if (!STATUSES.includes(it.status)) issues.push({ level: 'error', msg: `${file}:${it.line} ${it.id} 状态非法："${it.status}"（须 ${STATUSES.join('/')}）` })
      if (!/^\d{4}-\d{2}-\d{2}$/.test(it.date)) issues.push({ level: 'error', msg: `${file}:${it.line} ${it.id} 登记时间非法："${it.date}"（须 YYYY-MM-DD）` })

      const det = details.get(it.id)
      // 按语义比较而非字面：明细里写「P1（因为…）」是正当的理由说明，不该逼人被删掉；
      // 计划版本同理按结构比，"v1.2.x 补测" 与 "v1.2.x" 视为同义。
      const prioOf = (s) => /\bP[123]\b/.exec(clean(s))?.[0] ?? null
      const planKey = (s) => { const p = planOf(s); return `${p.kind}:${p.exact ?? p.series ?? p.raw}` }
      if (det?.plan && planKey(det.plan) !== planKey(it.plan)) {
        issues.push({ level: 'error', msg: `${file}: ${it.id} 明细计划版本「${det.plan}」与总览「${it.plan}」分叉` })
      }
      const detPrio = det?.priority ? prioOf(det.priority) : null
      if (detPrio && prioOf(it.priority) && detPrio !== prioOf(it.priority)) {
        issues.push({ level: 'error', msg: `${file}: ${it.id} 明细重要程度「${detPrio}」与总览「${prioOf(it.priority)}」分叉` })
      }

      parsed.push({ ...it, ledger: file, tool: toolId, toolTitle: title ?? toolId, planInfo: planOf(it.plan), age: ageDays(it.date, NOW) })
    }

    for (const id of details.keys()) {
      if (!parsed.some((p) => p.id === id)) issues.push({ level: 'error', msg: `${file}: 明细小节 ${id} 在总览表中不存在` })
    }

    ledgers.push({
      file, tool: toolId, toolTitle: title ?? toolId,
      plan: fm.plan ?? '', baseline: fm.baseline ?? '', released: fm.released ?? '', updated: fm.updated ?? '',
      items: parsed,
    })
  }
  return { ledgers, issues }
}

// ---------- 体检（台账与仓库事实交叉核对） ----------

function audit(ledgers, issues, { released, releasing }) {
  const releasedSet = new Set(released)
  for (const l of ledgers) {
    if (!clean(l.updated)) issues.push({ level: 'warn', msg: `${l.file}: front matter 缺 updated:，无法判断台账是否已过期` })
    const newest = l.items.reduce((a, b) => (b.date > a ? b.date : a), '')
    if (l.updated && newest && newest > l.updated) {
      issues.push({ level: 'warn', msg: `${l.file}: updated=${l.updated} 早于最新登记 ${newest}，改条目时记得同步` })
    }
    for (const it of l.items) {
      if (isOpen(it.status)) {
        // 版本号写死成已发布的版本却还没做完：发版时没核销，这是台账说谎，拦。
        if (it.planInfo.kind === 'exact' && releasedSet.has(it.planInfo.exact)) {
          issues.push({ level: 'error', msg: `${l.file}: ${it.id}「${it.title}」计划版本 ${it.planInfo.exact} 已发布，但状态仍是 ${it.status} —— 标 ✅ 或改到未来版本` })
        }
        if (releasing && it.planInfo.kind === 'exact' && it.planInfo.exact === releasing && it.priority === 'P1') {
          issues.push({ level: 'error', msg: `${l.file}: ${it.id}「${it.title}」是本次 ${releasing} 的 P1 且尚未完成 —— 要么先做，要么把计划版本改走` })
        }
        if (it.priority === 'P1' && it.planInfo.kind === 'none') {
          issues.push({ level: 'warn', msg: `${l.file}: ${it.id} 是 P1 却没排期（计划版本「${it.plan}」）` })
        }
        if ((it.priority === 'P2' || it.priority === 'P3') && it.age !== null && it.age > STALE_AFTER_DAYS) {
          issues.push({ level: 'warn', msg: `${l.file}: ${it.id} 登记已 ${it.age} 天仍未开始（僵尸项，考虑删掉或降级）` })
        }
      }
    }
  }
}

// ---------- 呈现 ----------

const prioRank = { P1: 0, P2: 1, P3: 2 }
const openSorted = (a, b) =>
  (prioRank[a.priority] ?? 9) - (prioRank[b.priority] ?? 9) ||
  (a.date || '9999').localeCompare(b.date || '9999') || a.id.localeCompare(b.id)

function renderOverview({ ledgers, issues, tools, released, current }) {
  const open = ledgers.flatMap((l) => l.items).filter((i) => isOpen(i.status))
  const done = ledgers.flatMap((l) => l.items).filter((i) => isDone(i.status))
  const covered = new Set(ledgers.map((l) => l.tool))
  const uncovered = tools.filter((t) => !covered.has(t.id))
  const errs = issues.filter((i) => i.level === 'error')
  const warns = issues.filter((i) => i.level === 'warn')
  const by = (p) => open.filter((i) => i.priority === p).length
  const lines = []

  lines.push('# 全站待办总览（由管家生成）', '')
  lines.push(`> 生成：${TODAY} ｜ \`node scripts/backlog.mjs\` 重跑。`)
  lines.push('> **本文件是生成物，不要手改**；要改内容请改各工具台账（写法见 `backlog/README.md`）。')
  lines.push('')
  lines.push(`- 当前版本：**${current ?? '未知'}** ｜ 最新已发布：${released[0] ?? '无'} ｜ 已发布版本 ${released.length} 个`)
  lines.push(`- 台账 ${ledgers.length} 份（覆盖 ${covered.size}/${tools.length} 个工具） ｜ 未完成 **${open.length}** 项：P1 ${by('P1')} · P2 ${by('P2')} · P3 ${by('P3')} ｜ 已完成 ${done.length} 项`)
  lines.push(`- 体检：**error ${errs.length}** ｜ warn ${warns.length}`)
  lines.push('')

  lines.push('## 一、台账一览', '', '| 所属 | 台账 | 方案 | 未完成 | P1 | 最早登记 | 台账修订 | 已发布 |', '| --- | --- | --- | --- | --- | --- | --- | --- |')
  for (const l of ledgers) {
    const o = l.items.filter((i) => isOpen(i.status))
    const earliest = o.map((i) => i.date).sort()[0] ?? '—'
    lines.push(`| ${l.toolTitle} \`${l.tool}\` | ${l.file} | ${l.plan ? `\`${l.plan}\`` : '—'} | ${o.length} | ${o.filter((i) => i.priority === 'P1').length} | ${earliest} | ${l.updated || '—'} | ${l.released || '—'} |`)
  }

  const p1 = open.filter((i) => i.priority === 'P1').sort(openSorted)
  lines.push('', '## 二、全站 P1（最老的排前面）', '')
  if (!p1.length) lines.push('（无）')
  else {
    lines.push('| 编号 | 所属 | 类型 | 标题 | 计划版本 | 状态 | 登记 | 账龄 |', '| --- | --- | --- | --- | --- | --- | --- | --- |')
    for (const i of p1) lines.push(`| ${i.id} | ${i.toolTitle} | ${i.type} | ${i.title} | ${i.plan} | ${i.status} | ${i.date} | ${i.age ?? '?'} 天 |`)
  }

  lines.push('', '## 三、P2 一览', '', '| 编号 | 所属 | 标题 | 计划版本 | 状态 | 登记 |', '| --- | --- | --- | --- | --- | --- |')
  for (const i of open.filter((x) => x.priority === 'P2').sort(openSorted)) {
    lines.push(`| ${i.id} | ${i.toolTitle} | ${i.title} | ${i.plan} | ${i.status} | ${i.date} |`)
  }

  const groups = new Map()
  for (const i of open) {
    const key = i.planInfo.kind === 'exact' ? (new Set(released).has(i.planInfo.exact) ? `${i.planInfo.exact}（已发布，未核销）` : i.planInfo.exact)
      : i.planInfo.kind === 'series' ? `v${i.planInfo.series}`
      : i.planInfo.kind === 'none' ? '未排期' : `其他：${i.planInfo.raw}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(i)
  }
  lines.push('', '## 四、按排期分组', '')
  for (const key of [...groups.keys()].sort((a, b) => verSortKey(a).localeCompare(verSortKey(b)))) {
    const list = groups.get(key)
    lines.push(`- **${key}** · ${list.length} 项（P1 ${list.filter((i) => i.priority === 'P1').length}）：${list.map((i) => `${i.id}`).join('、')}`)
  }

  lines.push('', '## 五、覆盖缺口', '')
  if (!uncovered.length) lines.push('（所有工具都已有台账）')
  else {
    lines.push(`以下 ${uncovered.length} 个工具尚无台账（不是问题，只是没登记过；有意不做就留个 ⬜ 说明，比空白强）：`, '')
    for (let i = 0; i < uncovered.length; i += 4) {
      lines.push(`- ${uncovered.slice(i, i + 4).map((t) => `${t.title} \`${t.id}\``).join(' ｜ ')}`)
    }
  }

  lines.push('', '## 六、体检', '')
  lines.push(errs.length ? `### error（${errs.length}，会拦发版）` : '### error：无', '')
  for (const e of errs) lines.push(`- ❌ ${e.msg}`)
  lines.push('', `### warn（${warns.length}，只提醒）`, '')
  for (const w of warns) lines.push(`- ⚠️ ${w.msg}`)
  if (!warns.length) lines.push('（无）')
  lines.push('')
  return lines.join('\n')
}

const verSortKey = (s) => {
  const m = /v?(\d+)\.(\d+)\.([\d x]+)/.exec(s)
  return m ? `${String(m[1]).padStart(3, '0')}.${String(m[2]).padStart(3, '0')}.${String(m[3]).trim().padStart(3, '0')}` : `000.000.999 ${s}`
}

function printConsole({ ledgers, issues, tools, current, released }) {
  const open = ledgers.flatMap((l) => l.items).filter((i) => isOpen(i.status))
  const errs = issues.filter((i) => i.level === 'error')
  const warns = issues.filter((i) => i.level === 'warn')
  const covered = new Set(ledgers.map((l) => l.tool))
  console.log(`台账 ${ledgers.length} 份（覆盖 ${covered.size}/${tools.length} 工具） ｜ 未完成 ${open.length}：P1 ${open.filter((i) => i.priority === 'P1').length} · P2 ${open.filter((i) => i.priority === 'P2').length} · P3 ${open.filter((i) => i.priority === 'P3').length}`)
  console.log(`当前 ${current ?? '?'} ｜ 最新已发布 ${released[0] ?? '无'}`)
  if (listFilter) {
    const rows = open.filter((i) => i.priority === listFilter).sort(openSorted)
    console.log(`\n${listFilter} 共 ${rows.length} 项：`)
    for (const i of rows) console.log(`  ${i.status} ${i.id.padEnd(18)} ${i.plan.padEnd(14)} ${i.toolTitle} · ${i.title}`)
  }
  if (errs.length) {
    console.log(`\n❌ error ${errs.length}（会拦发版）：`)
    for (const e of errs) console.log(`   - ${e.msg}`)
  }
  if (warns.length) {
    console.log(`\n⚠️ warn ${warns.length}：`)
    for (const w of warns) console.log(`   - ${w.msg}`)
  }
  if (!errs.length && !warns.length) console.log('\n体检通过：台账与仓库事实一致，无遗留未核销项。')
}

// ---------- 主流程 ----------

const NOW = Date.now()
const TODAY = new Date(NOW).toISOString().slice(0, 10)
const listFilter = opt('--list', null)

const tools = loadTools()
const released = loadReleasedVersions()
const releasing = opt('--release', null)
const { ledgers, issues } = collect(tools)
audit(ledgers, issues, { released, releasing })

const view = { ledgers, issues, tools, released, current: loadCurrentVersion() }

if (has('--json')) {
  console.log(JSON.stringify({
    generated: TODAY, current: view.current, latestReleased: released[0] ?? null, releasing,
    tools: tools.length, ledgers: ledgers.map((l) => ({ file: l.file, tool: l.tool, title: l.toolTitle, released: l.released, updated: l.updated, open: l.items.filter((i) => isOpen(i.status)).length, total: l.items.length })),
    items: ledgers.flatMap((l) => l.items),
    errors: issues.filter((i) => i.level === 'error').map((i) => i.msg),
    warnings: issues.filter((i) => i.level === 'warn').map((i) => i.msg),
  }, null, 2))
} else if (has('--check')) {
  printConsole(view)
} else {
  writeFileSync(OVERVIEW, renderOverview(view), 'utf8')
  printConsole(view)
  console.log(`\n已生成 ${path.relative(ROOT, OVERVIEW)}`)
}

const errorCount = issues.filter((i) => i.level === 'error').length
if ((has('--check') || has('--gate')) && errorCount > 0) process.exit(1)

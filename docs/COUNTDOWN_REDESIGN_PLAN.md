# 倒数日工具重构方案 v1.0

> 状态：**待实施** · 建议目标版本 **v1.3.0**
> 范围：`feature/countdown`（全量重写）+ `core/notify`（调度重构）+ `core/data`（Room 迁移）+ 导入导出 + 桌面小组件（P2）
> 前置：现有单测全绿（含 `CountdownTest` 3 例）、`assembleRelease` 可构建
> 文档范式对齐 [PERIOD_OPTIMIZATION_PLAN.md](PERIOD_OPTIMIZATION_PLAN.md)

---

## 一、结论摘要

现状是一个**"能记、能删、能响一声"的最小可用版**（功能代码约 290 行）。它与主流倒数日产品的差距不在视觉，而在**三个根基**：

| 根基 | 现状 | 目标 |
| --- | --- | --- |
| **事件模型** | 只有"标题 + 一个死日期 + 文案方向开关"，事件**建完就不能改** | 可编辑；支持重复规则、农历、备注、颜色、置顶、多档提醒 |
| **时间语义** | 目标日过了就永远"已过 N 天"。生日、结婚纪念日这类**倒数日第一使用场景**在本工具里过一次就报废 | 重复事件自动推进到下一次，"还有 12 天"永远成立 |
| **提醒可靠性** | 固定当天 09:00、单条闹钟、重启即丢、不打开 App 永不恢复 | 多档提前提醒 + 开机/改时重排 + 每日兜底心跳，**零精确闹钟权限也不丢** |

一句话：**用户装倒数日 App，90% 的需求是"每年都要倒数的那个日子"，而现在这个功能整个缺失。**

调研详见 §三（覆盖 [倒数日 · Days Matter](https://apps.apple.com/cn/app/%E5%80%92%E6%95%B0%E6%97%A5-days-matter/id406170251)（271 万评分 4.8）、[DaysTill](https://apps.apple.com/cn/app/daystill-countdown-countup/id6474996230)、[时间规划局](https://apps.apple.com/cn/app/%E6%97%B6%E9%97%B4%E8%A7%84%E5%88%92%E5%B1%80-%E5%80%92%E8%AE%A1%E6%97%B6%E4%B8%8E%E6%8F%90%E9%86%92%E4%BA%8B%E9%A1%B9/id1439723850)（38 万评分 4.9）、[Countdown Widget](https://apkpure.com/cn/countdown-widget%E3%83%BBcountdown-app/me.gira.widget.countdown) 等）。

本次共识别 **7 个 P0 缺陷、9 个 P1 缺口**，其中 5 个缺陷可直接在现有代码上复现。

---

## 二、现状诊断

### 2.1 代码结构

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `feature/countdown/CountdownData.kt` | 70 | Room 实体 + DAO + **`AppDatabase` 与 `DatabaseModule` 竟定义在 feature 包内** + Repository |
| `feature/countdown/CountdownScreen.kt` | 303 | UI + ViewModel + 文案算法，三者混在一个文件 |
| `core/notify/CountdownNotifications.kt` | 116 | 通知渠道 + `AlarmManager` 调度 + `CountdownAlarmReceiver` |
| `app/src/test/.../CountdownTest.kt` | 34 | 3 个 `countdownLabel` 用例 |
| `core/data/LegacyImporter.kt` | — | 旧版备份导入（`type: "countdown" \| "anniversary"`） |
| `feature/settings/SettingsScreen.kt` | — | 倒数日 JSON 导出/导入入口 |

数据模型只有 5 个字段：

```kotlin
data class CountdownEntity(
    val id: Long, val title: String, val date: String /* yyyy-MM-dd */,
    val type: Int /* 0=倒数,1=纪念 */, val createdAt: Long,
)
```

DAO 只有 `observeAll / insert / delete` —— **没有 `@Update`，全仓没有任何编辑路径**。

### 2.2 P0 缺陷

| # | 级别 | 位置 | 缺陷 | 复现 |
| --- | --- | --- | --- | --- |
| **P0-1** | 🔴 | `CountdownData.kt:34-44` | **事件不可编辑**。改一个错别字都要"删除 + 重建"，且重建后 `createdAt` 归零、id 变化 | 添加"婚礼纪念 2020-05-20" → 界面无任何编辑入口 |
| **P0-2** | 🔴 | 数据模型 | **不支持重复事件**。生日/纪念日过一次即永久报废，只能手动改明年的日期 | 记录"妈妈生日 2025-05-12"，5/13 起显示"已过 1 天"，明年今天仍是"已过 366 天" |
| **P0-3** | 🔴 | `CountdownNotifications.kt:57-68` | **闹钟在重启后丢失且不恢复**。注释声称"顺带覆盖重启后系统闹钟丢失"，但重排逻辑写在 `ViewModel.init` 里（`CountdownScreen.kt:75-85`）——只有用户**主动打开倒数日页面**才会重排；`Manifest` 未申请 `RECEIVE_BOOT_COMPLETED`，也没有 boot receiver | 昨晚设好今天的提醒 → 今早手机重启 → 提醒静默消失 |
| **P0-4** | 🟠 | `CountdownScreen.kt:153-169` | **`type` 语义混乱**：它既不是事件属性也不控制行为差异，只切换"已/还剩"措辞。两个方向的分支几乎完全重叠（倒数日过去→"已过 N 天"；纪念日未来→"还有 N 天"） | 用户选"纪念日"记录一个未来日期 → 表现与"倒数日"完全一致，无从理解差异 |
| **P0-5** | 🟠 | `CountdownData.kt:36` | **排序恒为 `date ASC`**，过去的纪念日永远占据列表最前，真正"即将到来"的事件被压到最底 | 记录 3 个生日（1990/1985/2019 年生）→ 它们永远排在未来考试之前 |
| **P0-6** | 🟠 | `CountdownScreen.kt:176` | **跨零点不刷新**：`LocalDate.now()` 在 Compose 中读取，但 `events` Flow 不变即不重组 | 页面挂着过夜，"还有 1 天"到第二天仍显示"还有 1 天" |
| **P0-7** | 🟡 | `CountdownScreen.kt:121-126` | 列表用 `Column` + `forEach` 而非 `LazyColumn`，与 ToolScaffold 的 `verticalScroll` 叠加；事件数上百时全量组合。同时删除**无二次确认、无撤销** | 长按误触删除 → 数据立即丢失（Room 无软删除） |

### 2.3 P1 能力缺口

| # | 缺口 | 主流实现 |
| --- | --- | --- |
| P1-1 | **无农历** | Days Matter / DaysTill / 时间规划局全部支持。中国用户的生日、春节、中秋、除夕全按农历，缺失等于不可用 |
| P1-2 | **提醒只有"当天 09:00"一档** | DaysTill："提前 1 天、7 天、30 天…自定义"；全部产品均支持自选时刻 |
| P1-3 | **无颜色/背景个性化** | 时间规划局"可为每个事件设置背景"、DaysTill"个性化颜色" |
| P1-4 | **无分类/标签** | Days Matter 默认"纪念日/工作/生活"+ 自定义；时间规划局支持分类管理 |
| P1-5 | **无置顶、无排序选择** | 时间规划局"支持事件置顶 + 自动排序"；DaysTill"手动拖拽或自动排序" |
| P1-6 | **无大数字/进度呈现** | 倒数日产品的标志性视觉就是"大号剩余天数"；时间规划局另有"百分比形式 + 可调精度" |
| P1-7 | **无备注** | DaysTill"事件备注" |
| P1-8 | **无桌面小组件** | 倒数日类产品的**第一使用场景**：Days Matter 通知中心挂件、时间规划局 100+ 桌面小组件、DaysTill"不用打开 App 就能看到" |
| P1-9 | **通知不 deep link** | `CountdownNotifications.kt:79-84` 只 `Intent(context, MainActivity)`，点通知落在首页，不进入对应事件 |

### 2.4 架构问题

| # | 问题 | 说明 |
| --- | --- | --- |
| AR-1 | **`AppDatabase` 放在 feature 包内** | `CountdownData.kt` 定义了整个 App 的 Room DB 和 `DatabaseModule`。倒数日重构会加多张表/字段，DB 归属应上移到 `core/data`（或 `core/database`） |
| AR-2 | **Room v1 且无迁移** | `version = 1`、`exportSchema = false`、无 `addMigrations`。任何加字段都必须写 `Migration(1, 2)`，否则老用户升级即 `IllegalStateException` 崩溃。**不可用 `fallbackToDestructiveMigration`——那会静默清空用户的事件** |
| AR-3 | **ViewModel 里双订阅** | `init` 块另开一路 `repository.observeAll().collect` 只为重排闹钟，每次发射都 O(n) 重排全部事件；调度职责应下沉到应用级 Scheduler |
| AR-4 | **导出 schema 未版本化** | `SettingsScreen.kt:139-147` 导出 `{title, date, type}` 无 `schemaVersion`；模型扩展后旧文件仍能导入（好事），但新字段无处安放，必须补版本号 |

> AR-2 与 P0-3 是本方案的两个"必须做对否则直接砸用户数据/信任"的点。

---

## 三、主流产品调研

### 3.1 先行结论

倒数日类产品能留住用户，靠的是四件事，按重要性排序：

1. **重复事件 + 农历**——生日、纪念日、节日是倒数日 App 的**主要负载**（而非"距离高考 30 天"这种一次性事件）。不能循环的倒数日，一年后就变成一堆"已过 N 天"的坟场。
2. **桌面小组件**——用户真正的使用动作是"划一下主屏就看到还剩几天"，打开 App 是低频事件。**没有小组件的倒数日 App 等于没有出口**。
3. **每事件视觉人格**——颜色/背景/大数字。用户会用"那张家有宝宝照片的卡片"来定位事件，而不是靠标题文字。
4. **提醒的分层**——当天、提前一周、提前一月，不同事件不同档位，且不能丢。

### 3.2 产品功能对照

| 能力 | 倒数日 Days Matter<br>(iOS，271 万评分 4.8) | DaysTill<br>(iOS，4.7) | 时间规划局<br>(iOS，38 万评分 4.9) | **本工具现状** |
| --- | --- | --- | --- | --- |
| 倒数 / 正数 | ✅ | ✅ 正数与倒数 | ✅ | ✅ `type` |
| 每年/每周重复 | ✅（纪念日按年） | ✅ "设置一次永久生效" | ✅ 每天/每周/每月/每年 | ❌ |
| 农历 | ✅ 1901–2049 | ✅ | ✅ | ❌ |
| 提醒档位 | ✅ | ✅ 提前 1/7/30 天自定义 | ✅ 推送 + 自定义铃声 | ⚠️ 仅当天 09:00 |
| 分类 / 标签 | ✅ 默认 3 类 + 自定义 | ✅ 标签分类 | ✅ 分类管理 | ❌ |
| 事件颜色 | ✅ | ✅ 个性化颜色 | ✅ 字体/颜色/透明度 | ❌ |
| 事件背景图 | ✅ 自定义事件背景 | ✅ | ✅ + 动态壁纸 | ❌ |
| 置顶 | ✅ | ✅ 手动拖拽 | ✅ 事件置顶 | ❌ |
| 自动排序 | ✅ | ✅ | ✅ | ⚠️ 仅 `date ASC` |
| 备注 | ✅ | ✅ 事件备注 | — | ❌ |
| 大数字 / 秒级 | ✅ | ✅ | ✅ 精确到秒 | ⚠️ 小字 |
| 百分比 / 进度 | — | — | ✅ 精度可调 | ❌ |
| 桌面小组件 | ✅ 通知中心 / Watch / iMessage | ✅ 多种样式 | ✅ 100+ 桌面 + 70+ 锁屏 | ❌ |
| 日期计算器 | ✅ | — | ✅ 年龄计算器等 | ❌ |
| 历史上的今天 | ✅ | — | ✅ 节假日 | ❌ |
| 密码保护 / Face ID | ✅ 高级功能 | — | — | ❌ |
| 云同步 | ✅ iCity | ✅ iCloud | ✅ iCloud | ✅ 走系统 Room 备份（更轻） |
| 商业模式 | 免费 + ¥3/月会员 | 免费 + 会员 | ¥3 买断 + 会员 | **全部免费、无广告、无账号** |

> 三者的付费墙位置高度一致：**重复规则、农历、小组件样式、背景、无限事件数量**是付费点。本工具既然定位"无广告、本地优先"，这些能力应当**直接免费全给**——这是国产免费工具最难被复制的差异化。

### 3.3 设计模式提炼（采纳清单）

| 模式 | 来源 | 落到本方案 |
| --- | --- | --- |
| 每年自动循环，"设置一次永久生效" | DaysTill | §5 `RepeatRule.YEARLY_*` + §6.1 `nextOccurrence` |
| 农历与公历双轨，事件标注"农历生日" | Days Matter / 时间规划局 | §5 `isLunar` + §6.3 农历换算 |
| 提醒按"提前 N 天"多选档位 + 自选时刻 | DaysTill | §5 `remindDaysBefore` + §8.2 |
| 事件置顶 + 临近优先自动排序 | 时间规划局 | §7.1 分组 + `pinToTop` |
| 每事件一个强调色，卡片即身份 | DaysTill / 时间规划局 | §7.3 `CountdownPalette` |
| 大数字剩余天数为主视觉 | 全部 | §7.2 Hero 卡与列表卡 |
| 百分比进度条（起点→目标） | 时间规划局 | §5 `anchorDate` + §7.2 进度条 |
| 多单位分解（年/月/日） | Days Matter "万年支持" | §6.4 `formatBreakdown` |
| 点击通知直达该事件 | 通用最佳实践 | §8.4 deep link |
| 深色/浅色自适应 | DaysTill | §7.3 调色板双档 token |
| 密码保护 | Days Matter | P2 备选（复用密码箱思路），不进 P0/P1 |
| 小组件 | 全部 | §9（P2） |

### 3.4 要避免的坑

| 坑 | 来源 | 对策 |
| --- | --- | --- |
| 把"倒数日/纪念日"做成措辞开关 | **本工具现状 P0-4** | 方向由"日期 vs 今天"自动决定（§6.2），`mode` 只控制**副行口径** |
| 事件数量硬上限 / 付费解锁 | DaysTill"会员可解锁无限事件" | 不设上限 |
| 背景图导致 OOM | 图片类工具通病 | 复用 `core/util` 下采样 + 尺寸上限；P1 只做纯色/渐变，图片背景推到 P2（见 §12 风险 RS-3） |
| 闹钟滥用精确权限 | API 31+ `SCHEDULE_EXACT_ALARM` 需用户手动授予 | 继续用非精确闹钟（保留现有正确决策），靠 boot + 每日兜底保证不丢（§8.3） |
| 动态壁纸 / 悬浮窗 / 灵动岛 / 自定义铃声 | 时间规划局的杂项堆料 | 不采用——本工具是工具箱里的一个工具，不是超级 App |
| 云同步 / 账号 | Days Matter iCity、DaysTill iCloud | 不采用；已有系统备份 + JSON 导出双出口 |
| 农历闰月处理错 | 自研农历库高发 bug | §6.3 明确回退策略 + 强制单测 |
| 静默迁移清空用户数据 | `fallbackToDestructiveMigration` | 禁用；写显式 `Migration(1,2)`（§5.3） |

---

## 四、目标与非目标

### 目标

1. **可用**：事件可增、可改、可删（可撤销）、可置顶——不再"建完就废"。
2. **循环**：生日/纪念日按年（公历或农历）自动滚动，永远显示正确的"还有 N 天"。
3. **不丢提醒**：重启、改时区、厂商强杀后提醒仍能送达；且支持多档提前提醒。
4. **像倒数日**：大数字 + 事件配色 + 进度，一眼可读，列表有主次。
5. **数据无损**：v1 用户升级到 v2 一条事件都不丢、提醒行为不回退。
6. **可测**：重复/农历/提醒计算全部为纯函数，新增单测 ≥ 35。

### 非目标（本方案不做）

- ❌ 不做云同步 / 账号 / 社区（与"本地优先、无追踪"直接冲突）。
- ❌ 不做动态壁纸、自定义通知铃声、悬浮窗、灵动岛。
- ❌ 不做"历史上的今天""万年历""日期计算器"等旁支功能——它们是 Days Matter 的**独立功能模块**，塞进一个工具页会摧毁信息层级。（P2 可考虑把"日期计算器"作为独立工具注册。）
- ❌ 不做事件封面照片的在线图库/网络搜图（时间规划局有）——违反无网络依赖承诺。
- ❌ 不做 Widget 的多套精美模板体系（时间规划局 100+ 模板）——P2 只做 2 种尺寸 × 2 种样式。

---

## 五、数据模型设计

### 5.1 Room v2

```kotlin
// feature/countdown/CountdownModels.kt
enum class EventMode {          // 替代旧 type(0/1)，语义收敛为"主显示口径"
    COUNTDOWN,   // 主显示"还有 N 天"（目标在未来）
    ELAPSED,     // 主显示"已 N 天"（目标在过去，如出生、恋爱开始）
    BOTH,        // 主数字按相对方向自动选，副行给另一侧
}

enum class RepeatRule { NONE, WEEKLY, MONTHLY, YEARLY_SOLAR, YEARLY_LUNAR }

@Entity(tableName = "countdown_events")
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 公历 yyyy-MM-dd。农历事件时此字段仅作"最近一次已解析到的公历日"缓存 */
    val date: String,
    val mode: Int = 0,               // EventMode ordinal
    val createdAt: Long = System.currentTimeMillis(),
    // ---- v2 新增（全部可空或带默认值，便于 ADD COLUMN）----
    val note: String = "",
    val repeat: Int = 0,             // RepeatRule ordinal
    val isLunar: Boolean = false,
    val lunarMonth: Int = 0,         // 1..12；isLunar=false 时为 0
    val lunarDay: Int = 0,           // 1..30
    val lunarLeapMonth: Boolean = false,
    val pinned: Boolean = false,
    val colorKey: String = "",       // 见 CountdownPalette，空 = 跟随主题 primary
    val remindEnabled: Boolean = true,   // 默认开，与 v1 行为对齐（见 §5.3 规则 2）
    val remindDaysBefore: String = "0",  // CSV "0,1,7,30"，0 = 当天
    val remindHour: Int = 9,             // 0..23 本地时刻
    val anchorDate: String? = null,      // 进度条起点；null = 用 createdAt
    val updatedAt: Long = 0L,
)
```

DAO 补齐 `@Update`、按 `pinned` 与"下一次发生日"排序的查询（见 §5.2 派生表方案）、`deleteById` → 软删除 + 回收。

```kotlin
@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown_events")
    fun observeAll(): Flow<List<CountdownEntity>>       // 排序交给内存层（见下）

    @Upsert suspend fun upsert(e: CountdownEntity)
    @Insert suspend fun insert(e: CountdownEntity): Long
    @Delete suspend fun delete(e: CountdownEntity)
    @Query("DELETE FROM countdown_events WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM countdown_events WHERE id = :id") suspend fun byId(id: Long): CountdownEntity?
}
```

> **排序为什么放内存而不放 SQL**：`nextOccurrence` 依赖农历换算与重复规则，SQL 算不出来。事件量级为个位数到几十，直接在 Repository 外层 `map { it.sortedWith(...) }` 即可；同时把算好的 `nextDate` 塞进 `CountdownUiState`，供小组件与提醒共用。**唯一保留的 SQL 排序是 `ORDER BY pinned DESC`** 这种纯字段场景。

### 5.2 UI 状态模型（派生，不落库）

```kotlin
data class CountdownItem(
    val event: CountdownEntity,
    val nextDate: LocalDate?,    // 下一次发生（重复事件）或目标日
    val lastDate: LocalDate?,    // 上一次发生（用于"已过/已 N 天"）
    val daysToNext: Long,
    val daysSinceLast: Long,
    val isToday: Boolean,
    val progress: Float?,        // anchor→next 的完成度，无起点为 null
    val lunarLabel: String?,     // "四月廿一"，非农历事件为 null
)

data class CountdownUiState(
    val upcoming: List<CountdownItem> = emptyList(),  // 未来
    val passed: List<CountdownItem> = emptyList(),    // 已过去（正数日）
    val today: LocalDate = LocalDate.now(),
    val filter: CategoryFilter = CategoryFilter.All,
    val sort: SortMode = SortMode.NEAREST,
)
```

### 5.3 迁移（必须无损）

`AppDatabase`：`version = 1 → 2`，新增 `Migration(1, 2)`，全部为 `ALTER TABLE countdown_events ADD COLUMN`（v2 新增字段均为 SQLite 定长默认值，满足"非 nullable 列必须带 DEFAULT"的要求）。

**三条硬规则：**

1. **禁止** `fallbackToDestructiveMigration()`——那会在升级时静默删光用户的事件。宁可崩，也要让问题可见；本方案两者都不要，写正确的 `Migration`。
2. **`remindEnabled` 与 `remindDaysBefore` 的默认值必须等于 v1 的行为**（`1` 与 `'0'`，即"每个事件当天 09:00 提醒"）。理由：v1 是无条件给每个事件排当天 09:00 提醒，若 v2 实体默认改成"关闭"，老用户升级后**提醒全部静默失效**——这是最坏的结果。把 Kotlin 实体默认值也定成 `true / "0"`，迁移与新建就共用同一组默认，少一条特例（表单里用户仍可显式关闭）。
3. `mode` 由旧 `type` 直接映射：`type 0 → COUNTDOWN`、`type 1 → ELAPSED`。旧 `date` 原样保留。

```sql
-- Migration(1,2) 主体（列默认值与 Kotlin 默认值逐一对齐）
ALTER TABLE countdown_events ADD COLUMN note TEXT NOT NULL DEFAULT '';
ALTER TABLE countdown_events ADD COLUMN repeat INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN isLunar INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN lunarMonth INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN lunarDay INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN lunarLeapMonth INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0;
ALTER TABLE countdown_events ADD COLUMN colorKey TEXT NOT NULL DEFAULT '';
ALTER TABLE countdown_events ADD COLUMN remindEnabled INTEGER NOT NULL DEFAULT 1;  -- 见规则 2
ALTER TABLE countdown_events ADD COLUMN remindDaysBefore TEXT NOT NULL DEFAULT '0';-- 见规则 2
ALTER TABLE countdown_events ADD COLUMN remindHour INTEGER NOT NULL DEFAULT 9;
ALTER TABLE countdown_events ADD COLUMN anchorDate TEXT;
ALTER TABLE countdown_events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
```

同时把 `AppDatabase` / `DatabaseModule` 从 `feature/countdown/CountdownData.kt` 移到 `core/data/AppDatabase.kt`（AR-1），并打开 `exportSchema = true` + `schemas/` 目录纳入版本控制，为后续 P2 迁移铺路。

> `exportSchema = true` 必须同时配 KSP 参数，否则 Room 只告警不落盘：
>
> ```kotlin
> // app/build.gradle.kts
> ksp { arg("room.schemaLocation", "$projectDir/schemas") }
> ```
>
> 并把 `app/schemas/` 加入版本控制（不要 `.gitignore` 掉）。这是 §5.3 迁移可被 `MigrationTestHelper` 校验的前提，也是 RS-1 的兜底证据。

### 5.4 导入导出兼容

`SettingsScreen` 的导出补 `schemaVersion: 2` 与全部新字段；`LegacyImporter` 读取时：有 `schemaVersion` 按 v2 解析，缺失则按 v1 解析并套用 §5.3 规则 2 的提醒默认值。旧文件必须**继续可导入**（这是回归测试项）。

### 5.5 备份

倒数日数据非敏感（标题/日期），**继续保留**在云备份与换机迁移内（当前 `backup_rules.xml` 未排除 `toolbox.db`，符合预期）。P2 若引入相册封面图，需把封面目录加入 `cloud-backup` 与 `device-transfer` 排除（照片可能含隐私内容）。

---

## 六、核心算法设计（纯函数 + 单测）

全部落在 `feature/countdown/CountdownEngine.kt` 与 `CountdownLunar.kt`，**不依赖 Android、不依赖 `LocalDate.now()`**（一律显式传入 `today`），沿用本仓"算法可测"原则。

### 6.1 下一次发生日

```kotlin
fun nextOccurrence(e: CountdownEntity, today: LocalDate): LocalDate?
fun prevOccurrence(e: CountdownEntity, today: LocalDate): LocalDate?
```

规则：

| `repeat` | 算法 | 边界 |
| --- | --- | --- |
| `NONE` | 即 `date`，不滚动 | `date < today` → `nextOccurrence` 返回 `null`（列表归入"已过去"） |
| `WEEKLY` | 从 `date` 起按 7 天推进到首个 ≥ `today` | 保持星期几不变 |
| `MONTHLY` | 同"日号"，逐月推进 | 目标月天数不足 → **取该月最后一天**（31 日 → 2/28、4/30） |
| `YEARLY_SOLAR` | 同"月-日"，逐年推进 | **2/29 → 平年取 2/28**（策略常量 `LeapDayPolicy.CLAIM_LAST_DAY`，可单测） |
| `YEARLY_LUNAR` | 按农历月/日在**农历年**内定位，转公历 | 见 §6.3 闰月回退 |

推进用 `while (candidate < today) candidate = step(candidate)`，**并加最大迭代 4000 次保护**（防脏数据死循环，与经期方案 P0-2 的"不静默跳到未来"教训同源）。

### 6.2 主显示口径（修 P0-4）

```kotlin
fun mainDisplay(item: CountdownItem): MainDisplay
```

方向不再由用户选择，而由 `nextDate` 与 `today` 的关系推导：

| 情形 | 主显示 | 副显示 |
| --- | --- | --- |
| `repeat != NONE` | `还有 N 天`（N = daysToNext） | 上一次：`已 N 天`（如"出生已 12 345 天"） |
| `repeat == NONE` 且 `date > today` | `还有 N 天` | 绝对日期 + 星期 |
| `repeat == NONE` 且 `date == today` | `就是今天` | 庆祝态 |
| `repeat == NONE` 且 `date < today`，`mode == ELAPSED` | `已 N 天` | 绝对日期 |
| `repeat == NONE` 且 `date < today`，`mode == COUNTDOWN` | `已过 N 天`（弱化灰色，降权排到"已过去"组） | 绝对日期 |

> 关键取舍：**用户不需要决定"这是倒数日还是纪念日"**，只需要给日期。表单里 `mode` 降级为"这个日子是**还没到的**还是**已经过去的**"这一句提问（配 `ELAPSED` 默认勾选"每年重复"）。

### 6.3 农历（用 ICU，零新依赖）

> ⚠️ **实现已改**：最终用纯 Kotlin 数据表 `TableLunarCalendar` 而非 ICU，原因与仍然保留的设计（闰月回退规则、往返校验、1901–2099 口径）见 §13.1。本节的数据结构描述仍然有效，仅"靠 ICU 提供换算"这一条作废。

`android.icu.util.ChineseCalendar`（API 24+，本仓 `minSdk 26` ✅），无需引第三方库。

```kotlin
fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, leap: Boolean): LocalDate
fun solarToLunar(date: LocalDate): LunarDate   // year/month/day/isLeap
fun formatLunar(l: LunarDate): String          // "甲子年四月廿一"
```

**闰月策略（必须写死并测）：**
事件声明为"闰五月初五"而目标农历年**无闰五月**时 → **回退到同年"五月初五"**（与民间习惯一致，也与 Days Matter 行为一致）。
反之，事件为"五月初五"且当年**有闰五月**时 → 取**正五月**（非闰月）初五。

回退的实现技巧：`lunarToSolar` → `solarToLunar` **往返校验**，若返回的 `month/isLeap` 与请求不符即判定"该年无此月"，走回退分支。此技巧比查闰月表可靠，且天然可单测。

**支持范围**：1901–2099（ICU 实际覆盖更宽，但产品口径保守；表单日期选择器限死此区间并给出提示）。注意：Days Matter 只做到 2049——我们做到 2099 是一处**免费超越**（ICU 无此限制，无需自己维护农历表）。

**性能**：`ChineseCalendar` 非线程安全且构造不便宜 → 每次换算 new 一个（不用共享单例）；结果缓存在 `CountdownItem` 里，一屏一次换算最多 O(n) 次，几十条事件可忽略。

### 6.4 天数分解（Days Matter "万年支持"的呈现）

```kotlin
data class DurationParts(val years: Int, val months: Int, val days: Int) {
    val totalDays: Long
}
fun decompose(from: LocalDate, to: LocalDate): DurationParts   // Period.between
fun countdownLabel(item: CountdownItem, today: LocalDate): String   // 取代旧 3 参版本
```

呈现优先级：`|N| < 100` → 纯天数；`≥ 100` → `已 3 年 2 个月 17 天`（大数字仍显示总天数 `12 345`，分解放副行）。

### 6.5 保留与替换旧 API

`countdownLabel(target, type, today)` 是 `internal` 且被 `CountdownTest` 引用 → **删除**，测试迁到新的两参签名；`@Deprecated(level = ERROR)` 反而制造死代码，不要保留兼容壳。

---

## 七、交互与视觉设计

### 7.1 首屏信息架构（自上而下）

```
[ToolScaffold] 倒数日 · "记录并倒数重要的日子"     [排序 ⋮] [＋ 添加]
1. Hero 卡（唯一强调块）：最近即将到来的事件
   ├─ 事件色为底 + 大数字（displaySmall，tabular）+ "天后"
   ├─ 标题 / 日期（公历 + 农历）/ 星期 / "每年" 角标
   └─ 进度条（有 anchorDate 时）
2. 「今天」组（仅当有事件命中今天时出现）：庆祝态，主色实心 + 彩带描边
3. 即将到达（≤ 30 天）：紧凑卡，色条 + 剩余天数右侧大字
4. 更远的未来：同上，弱化
5. 已过去 / 正数日：灰阶卡，可折叠
[空态] FeedbackBlock(EMPTY) + 3 个一键模板：「生日」「纪念日是过去的事」「考试/截止日」
```

- 顶部工具行放 `SegmentedTabs`：**全部 / 置顶 / 农历**（替代尚不存在的分类系统；分类见 §7.5 取舍）。
- 排序入口（`DropdownMenu`）：`临近优先`（默认，修 P0-5）/ `创建时间` / `名称` / `自定义（拖拽，P2）`。
- 列表用 `LazyColumn`（修 P0-7），但保留 ToolScaffold 的 `scrollable = false` 以免嵌套滚动冲突。

### 7.2 卡片规格

```
┌──────────────────────────────────────────┐
│▌  ← 4dp 事件色条                           │
│ 妈妈生日                         还有      │  titleMedium
│ 农历四月廿一 · 5月18日 周六      [ 12 ] 天  │  数字 displaySmall 36sp tabular
│ ▓▓▓▓▓▓▓▓░░░░░░░░░░░ 62%      · 每年        │  LinearProgress 3dp + 角标
└──────────────────────────────────────────┘
```

| 状态 | 视觉 |
| --- | --- |
| 今天 | 事件色**实心背景** + `onXxx` 反色文字 + "就是今天" 大字；**不做彩带动画**（克制，且避免每次进入都放） |
| ≤ 7 天 | 数字用事件色强调 + "· X 天后" 副行 |
| 已过去（COUNTDOWN） | `onSurfaceVariant` 灰阶，数字前缀"已过"，沉到末组 |
| 正数日（ELAPSED） | 数字前缀"已"，副行给"年+月+日"分解 |
| 重复事件 | 右上角 `Repeat` 微标 + 文案"每年 / 每月" |
| 置顶 | 左上角 `PushPin` 图标 |
| 有提醒 | 标题后 `Notifications` 12dp 微标 |

**动效**：仅两处——列表项增删用 `animateItemPlacement()`；删除后 Snackbar 撤销。不做入场逐条动画（信息类工具，动画是噪音）。

### 7.3 调色板（`feature/countdown/CountdownPalette.kt`）

8 色，每色提供 light / dark 两档容器色 + 反差文字色，与品牌暖青 `#2E6E5E` 共存而不冲突；`colorKey == ""` 时回落到 `MaterialTheme.colorScheme.primary`（尊重动态取色）。

| key | Light 容器 | Dark 容器 | 语义提示 |
| --- | --- | --- | --- |
| `teal` | `#B2ECD9` | `#0F5245` | 默认/品牌 |
| `coral` | `#FFD9D4` | `#68302A` | 恋爱/家人 |
| `amber` | `#FFE1A8` | `#4E342E` | 考试/截止 |
| `violet` | `#E6DBFF` | `#3F3B6B` | 工作 |
| `mint` | `#D8F2CE` | `#2F4A2A` | 健康 |
| `sky` | `#CDE5FF` | `#27445D` | 出行 |
| `rose` | `#FFD9E8` | `#5A2A3C` | 生日 |
| `slate` | `#E3E0DA` | `#3F443F` | 中性/杂项 |

> 取舍：**不用 Material 的 `tertiaryContainer` 等固定角色**，因为动态取色（`dynamicColor = true`，见 `AppNavHost.kt:135`）下角色色随机，八张卡片可能全是近似色。事件色必须是**独立常量表**。

### 7.4 添加 / 编辑表单（`ModalBottomSheet`，取代现在的 `AlertDialog`）

理由：字段从 3 个涨到 8 个，`AlertDialog` 在小屏放不下；且编辑与新建必须共用同一份表单。

```
[拖拽条]
名称            [ ToolTextField，单行 ]
这是            (◉ 还没到的日子)  (○ 已经过去的日子)     ← 修 P0-4 的提问式文案
日期            [ 2026-05-18 周一 ▸ ]   [ 农历 ○ ]      ← 切换后变 月/日 双下拉
重复            [ 不重复 ][ 每周 ][ 每月 ][ 每年 ]        ← SegmentedTabs
提醒            [关闭][当天][1天][3天][1周][1月]          ← ChipMultiSelect（可多选）
      提醒时刻  [ 09:00 ▸ ]                              ← TimePicker
颜色            ● ● ● ● ● ● ● ●
置顶            [ Switch ]
备注            [ ToolTextField，多行，200 字上限 ]
[ 保存 ]                                    [ 删除（仅编辑态）]
```

- 日期用 `DatePicker`（保留现有 UTC 毫秒↔`LocalDate` 的正确写法，`CountdownScreen.kt:282-301` 注释里那条坑要原样搬过去）。
- 农历开启时，`DatePicker` 换成 `LabeledDropdown`（月 1–12 + 闰月标记，日 1–30），并**实时回显对应公历日**（"对应 2026-06-02"），避免用户盲选。
- 校验：名称必填；农历日越界（如"腊月三十"该年不存在）→ 保存不阻断，按 §6.3 回退并在卡片上标注"（按廿九过）"。
- 保存后 `Snackbar`：新建 → "已添加「妈妈生日」"；编辑 → "已更新"；删除 → "已删除「X」" + **撤销**（`Undo` 动作把实体原样 `insert` 回去，保留原 id 与 `createdAt`）。

### 7.5 分类系统的取舍

主流产品都有"分类/标签"。**本方案 P1 不做分类**，理由：本工具是工具箱里的一个入口，事件量级预期在 5–30 条，加分类只会多出一层无收益的导航；而"置顶 + 临近排序 + 颜色"已覆盖"快速找到重要事件"的真实诉求。若 P2 数据显示用户事件数中位数 > 50，再用 `colorKey` 兼作隐式分类（点击色点筛同类），成本近乎为零。

### 7.6 无障碍

- 卡片整体 `Modifier.semantics { contentDescription = "妈妈生日，农历四月廿一，还有 12 天，每年重复" }`。
- 所有 `IconButton` 必须有 `contentDescription`（现在的 `Add` 图标传的是 `null`，要补）。
- 点击目标 ≥ 44dp（`ToolTopBar` 已按此实现，卡片沿用）。
- 数字用 `tabular` 字体（`ResultCard` 已有此风格）避免天数跳动时抖动。

---

## 八、提醒与后台调度设计

**这是本方案最重要的技术改造。**目标：**不申请任何新权限（除已声明的 `POST_NOTIFICATIONS`）、不要求精确闹钟授权，也不丢提醒。**

### 8.1 抽出应用级 Scheduler

```kotlin
// core/notify/CountdownScheduler.kt
@Singleton
class CountdownScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CountdownDao,
    private val alarmManager: AlarmManager,
) {
    fun ensureChannels()
    suspend fun rescheduleAll()          // 全量重排（幂等）
    suspend fun reschedule(event: CountdownEntity, today: LocalDate)
    fun cancel(id: Long)
}
```

`rescheduleAll()` 幂等且廉价（读全表 → 逐条算下一次触发点 → `setWindow`/`cancel`），**替代现在 ViewModel 里那路订阅（AR-3）**。ViewModel 只在增删改后调 `reschedule`，不再自己碰 `AlarmManager`。

### 8.2 触发点计算

```kotlin
fun reminderTriggers(e: CountdownEntity, today: LocalDate): List<LocalDateTime>
```

对 `nextOccurrence` 的每个 `d in remindDaysBefore` 生成 `occurrence.minusDays(d).atTime(remindHour, 0)`，**丢弃已过去的**、去重、按时间排序，最多保留 6 个待触发点（防重复事件在极端配置下排爆）。

`remindDaysBefore` 用 CSV 字符串（SQLite 无数组），Repository 层提供 `List<Int>` 编解码并单测。

### 8.3 三层不丢保障

| 层 | 机制 | 覆盖场景 |
| --- | --- | --- |
| **L1 事件级闹钟** | `AlarmManager.setWindow(RTC_WAKEUP, triggerAt, 10 * 60_000, pi)` ——**保留现有"非精确"的正确决策**（API 31+ 精确闹钟需用户手动授权，"当天 9 点"对 10 分钟偏差不敏感） | 正常路径 |
| **L2 系统广播重排** | `CountdownSystemReceiver` 监听 `BOOT_COMPLETED` / `ACTION_DATE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` → `goAsync()` + `rescheduleAll()` | 重启（修 P0-3）、改系统时间/时区 |
| **L3 每日兜底心跳** | `WorkManager`（依赖已在 `build.gradle.kts`：`work-runtime-ktx:2.9.1` ✅）唯一周期任务 `CountdownReminderWorker`，每日一次：重排闹钟 + 补发今天已漏的提醒 + 触发小组件刷新 | 厂商 ROM 强杀、Doze 漏醒、闹钟被系统丢弃 |

`Manifest` 新增：

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<receiver android:name=".core.notify.CountdownSystemReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

> `RECEIVE_BOOT_COMPLETED` 是**普通权限、安装时授予、无需弹窗**，与"无追踪、本地优先"不冲突，需在隐私说明里加一句"开机后恢复你的提醒闹钟"。`exported="true"` 是 `BOOT_COMPLETED` 的硬要求（系统广播来自 system uid）。

### 8.4 通知设计

| 元素 | 规格 |
| --- | --- |
| 标题 | `妈妈生日` |
| 正文 | 提前 N 天 → `还有 3 天（5月18日 周一）`；当天 → `就是今天 🎂`；正数日 → `第 3 个年头，已 1 095 天` |
| 分组 | `setGroup("countdown_reminders")`；同日多事件时发摘要 `今天有 3 个重要的日子`（避免通知栏被单条霸占） |
| 渠道 | 保留 `countdown_reminders`，`IMPORTANCE_DEFAULT`（现值合理，别降到 LOW——降到 LOW 不响，倒数日提醒不响等于没有） |
| 私密 | `setVisibility(VISIBILITY_PRIVATE)` + `publicVersion` 写"你有 1 条日期提醒"（标题可能含"离婚协议签署"这类不想锁屏显示的内容） |
| deep link | `Intent(context, MainActivity)` + `putExtra("nav_tool","countdown")` + `putExtra("cd_event_id", id)`；`ToolboxApp` 启动时消费该 extra → `navigate("tool/countdown?eventId=$id")` 并把对应卡片滚动 + 高亮 1 次（`LaunchedEffect` + `animateItem`）。注意 `launchSingleTop` 与复用 `MainActivity` 的 `onNewIntent` |
| 权限时机 | 从"首次添加事件后"改为**用户在表单里开启提醒时才请求**（现在在 `CountdownScreen.kt:135-140` 保存后立刻弹权限，是"未经同意就要权限"的坏姿势）；拒绝则静默降级并保留全部其他功能 |

### 8.5 跨零点刷新（修 P0-6）

```kotlin
// core/util/DayTicker.kt
fun dayFlow(): Flow<LocalDate>   // 每次系统日期变更发射一次，驱动 UiState.today
```

`CountdownViewModel` 用 `combine(eventsFlow, dayFlow())`，`dayFlow` 内部用 `delayUntilNextMidnight()` 自递归。**这是纯逻辑，单测注入假 `dayFlow` 即可**。小组件侧由 §8.3 L3 的每日 Worker 触发刷新，双端一致。

---

## 九、桌面小组件（P2，方案预研）

倒数日没有小组件等于没有出口。放在 P2 是因为它是**独立交付面**，不应与"修好数据模型"绑定发版。

| 项 | 结论 |
| --- | --- |
| 技术 | `androidx.glance:glance-appwidget:1.1.1`。**已实测 POM 依赖**：它只声明 Compose 版本**下界**（`runtime:1.1.1`、`ui-graphics:1.1.1`、`ui-unit:1.1.1`、`kotlin-stdlib:1.8.22`），Gradle 会解析到本仓 Compose BOM 2024.09.00 提供的 1.7.x 与 Kotlin 1.9.22，**无版本冲突**。DataStore 下界 1.0.0 < 本仓 1.1.1，同样安全。会新引入 `androidx.core:core-remoteviews:1.1.0`（体积小） |
| 尺寸 | 2×2（单事件大数字）、4×2（单事件带背景色）、4×4（多事件列表，Glance `LazyColumn`） |
| 配置 | 点组件 → 选事件（`GlanceAppWidgetReceiver` + `MainActivity` 带 `appWidgetId` 参数）；映射存 DataStore（复用 `CoreStores.kt` 模式，不新开 Room 表） |
| 刷新 | 每日 00:05 `AlarmManager` + §8.3 的 Worker 兜底 `updateAllAppWidgets`；不做秒级（电池） |
| 主题 | 跟随应用主题色或事件色；`RemoteViews` 无法读 Compose `ColorScheme` → 由 Repository 落一份 `resolvedColors.json` 给组件用 |
| 风险 | **APK 体积**：release 现为 ~2 MB，Glance 会带入 Compose RemoteViews 运行时，**R8 后仍可能 +0.4~0.8 MB**。P2 开工前先做一次 spike 量化，超阈值则改用手写 `RemoteViews`（体积可控、样式受限）。这是本方案唯一可能因体积而砍掉的功能 |

---

## 十、文件级改造清单

| 文件 | 动作 | 内容 |
| --- | --- | --- |
| `core/data/AppDatabase.kt` | 🆕 | 从 feature 上移 DB + `DatabaseModule`，`version = 2`，`exportSchema = true`，`addMigrations(MIGRATION_1_2)` |
| `core/data/Migrations.kt` | 🆕 | `Migration(1, 2)`（§5.3） |
| `feature/countdown/CountdownData.kt` | ✂️ | 删除（实体迁走），文件消失，避免 AR-1 遗留 |
| `feature/countdown/CountdownModels.kt` | 🆕 | 实体 + `EventMode` / `RepeatRule` / CSV 编解码 |
| `feature/countdown/CountdownDao.kt` | 🆕 | 补 `upsert` / `byId` / 软删除 |
| `feature/countdown/CountdownEngine.kt` | 🆕 | `nextOccurrence` / `prevOccurrence` / `mainDisplay` / `decompose` / 排序比较器 |
| `feature/countdown/CountdownLunar.kt` | 🆕 | ICU `ChineseCalendar` 换算 + 闰月回退（§6.3） |
| `feature/countdown/CountdownRepository.kt` | 🆕 | 从 `CountdownData.kt` 拆出，加 `update`，暴露派生 `Flow<List<CountdownItem>>` |
| `feature/countdown/CountdownViewModel.kt` | 🆕 | 从 Screen 抽出；含表单状态机、撤销、Scheduler 调用 |
| `feature/countdown/CountdownScreen.kt` | ♻️ | 重写：Hero 卡 + 分组 LazyList + 排序菜单（§7.1） |
| `feature/countdown/CountdownCards.kt` | 🆕 | `HeroCard` / `EventCard` / `EmptyTemplatesRow` |
| `feature/countdown/CountdownEditor.kt` | 🆕 | `ModalBottomSheet` 表单（§7.4） |
| `feature/countdown/CountdownPalette.kt` | 🆕 | 8 色双档 token（§7.3） |
| `core/notify/CountdownScheduler.kt` | 🆕 | 应用级调度（§8.1），取代 `CountdownNotifications.schedule` 的散落调用 |
| `core/notify/CountdownNotifications.kt` | ♻️ | 只留"发通知"；渠道、`setWindow`、摘要、`publicVersion`、deep link |
| `core/notify/CountdownSystemReceiver.kt` | 🆕 | boot / time / timezone 重排（§8.3 L2） |
| `core/notify/CountdownReminderWorker.kt` | 🆕 | 每日兜底 + 小组件刷新（§8.3 L3） |
| `core/util/DayTicker.kt` | 🆕 | `dayFlow()`（§8.5） |
| `AndroidManifest.xml` | ♻️ | 加 `RECEIVE_BOOT_COMPLETED`、新 receiver；`MainActivity` 加 `launchMode="singleTop"` |
| `ToolboxApplication.kt` | 🔧 | 注入并调用 `scheduler.ensureChannels()` + 排 L3 周期任务 |
| `core/registry/ToolCatalog.kt` | 🔧 | 描述改"倒数日 · 农历 · 纪念日"；关键词补 `生日/纪念日/农历/纪念日/倒计时/周年/days matter/birthday/anniversary/lunar` |
| `feature/settings/SettingsScreen.kt` | 🔧 | 导出加 `schemaVersion: 2` 与新字段 |
| `core/data/LegacyImporter.kt` | 🔧 | 双版本解析（§5.4） |
| `app/schemas/*.json` | 🆕 | `exportSchema = true` 产物，提交进仓库 |
| `test/.../CountdownEngineTest.kt` | 🆕 | ≥20 例（§6.1/§6.4 夹具） |
| `test/.../CountdownLunarTest.kt` | 🆕 | ≥10 例（闰月回退、往返一致、2049 边界） |
| `test/.../CountdownMigrationTest.kt` | 🆕 | ≥6 例（列齐备、提醒默认值=开、旧 type→mode 映射、旧导出文件仍可导入） |
| `test/.../CountdownTest.kt` | ♻️ | 迁到新 `countdownLabel(item, today)` 签名 |

---

## 十一、分期实施计划与验收

### P0 · 正确性与可用（v1.3.0，一次发版）

1. `AppDatabase` 上移 + 写 `Migration(1,2)` + 开 `exportSchema` → 先做 `CountdownMigrationTest`。
2. 抽 `CountdownEngine.kt` / `CountdownRepository` / `CountdownViewModel`，先写 `CountdownEngineTest`。
3. **重复事件**（`NONE/WEEKLY/MONTHLY/YEARLY_SOLAR`，含 §6.1 全部边界）。
4. **编辑器 + 删除撤销**（§7.4，P0-1/P0-7 修复）。
5. **排序改"临近优先" + 置顶**（P0-5）。
6. **提醒三层保障**（§8.3，P0-3）+ 多档提前提醒 + 表单内请求权限。
7. `DayTicker` 跨零点刷新（P0-6）。
8. `ToolCatalog` 文案与关键词。
9. 回归：`gradlew :app:testDebugUnitTest` 全绿 → `assembleRelease` → **真机装 v1.1.3 造数据、覆盖安装 v1.3.0**。

**验收标准**

- [ ] 旧版本升级后：事件数不变、`mode` 映射正确、**提醒仍按当天 09:00 触发**（规则 2 未回退）
- [ ] "妈妈生日 2025-05-12 + 每年"，在 5/13 打开 → 显示"还有 364 天"，明年同日显示"就是今天"
- [ ] 2/29 出生 + 每年 → 平年显示 2/28
- [ ] 31 号 + 每月 → 2 月显示 2/28（闰年 2/29）
- [ ] 事件可编辑：改名/改日期/改提醒后**闹钟时刻随之变化**（`adb shell dumpsys alarm` 核对）
- [ ] 删除后 Snackbar 撤销，事件 `id` 与 `createdAt` 均不变
- [ ] **重启手机后，今天的提醒仍送达**（L2 生效）；关闭 App 进程后仍送达
- [ ] 修改系统时区后重排成功
- [ ] 未授予通知权限时，事件与天数计算全部可用，无弹窗骚扰
- [ ] 3 个旧导出 JSON 文件均可导入，无字段丢失
- [ ] 首页搜索"生日""农历""周年""days matter"均命中本工具
- [ ] 全仓单测：现有 + 新增 ≥35 全绿；`assembleRelease` 通过，`toolbox.db` schema v2 已导出

### P1 · 体验对齐主流（v1.3.x）

农历（§6.3，含表单双轨与实时回显）→ 事件配色 + Hero 卡 → 进度条与年+月+日分解 → 备注 → 分组折叠与排序菜单 → 通知 deep link + 摘要分组 + `publicVersion` → 无障碍语义。

> 农历放 P1 而非 P0 的唯一理由是工作量（ICU 语义 + 闰月单测约需一整天），**不是不重要**。若可分两次发版，建议把农历并入 P0 一起上——中国用户没有农历的倒数日， birthday 场景仍是残缺的。

### P2 · 差异化（v1.4+）

桌面小组件（§9，先做体积 spike）→ 分享卡片图（`Canvas` + `core/util` MediaStore 保存）→ 相册封面背景（复用下采样）→ 网格视图 → 拖拽自定义排序 → 事件密码保护（复用密码箱 PBKDF2 思路）。

---

## 十二、风险与取舍

| # | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| RS-1 | **Room 迁移写错 → 用户数据丢失** | 高 | 显式 `Migration(1,2)` + 只做 `ADD COLUMN`（不改名不删列）+ `CountdownMigrationTest` 用 `Room.inMemoryDatabaseBuilder` 校验列集合 + 真机覆盖安装验收项。**禁止 `fallbackToDestructiveMigration`** |
| RS-2 | 迁移后老用户提醒静默失效 | 高 | §5.3 规则 2：`remindEnabled` 迁移默认 `true`；验收项明确"升级后仍按当天 09:00 响" |
| RS-3 | `ChineseCalendar` 的月份 0 基 / 闰月字段易搞反 | 中 | 一律用**往返校验**（lunar→solar→lunar）判定合法性，不靠字段直觉；`CountdownLunarTest` 覆盖 1901/2049/2099 边界 + 闰五月有/无两种年份 |
| RS-4 | 厂商 ROM（MIUI/ColorOS）限制自启与 `BOOT_COMPLETED` | 中 | L3 WorkManager 兜底；首次开启提醒时给一句"如收不到提醒，请在系统设置允许本应用自启"的可操作提示（对齐仓库"失败可解释"原则） |
| RS-5 | 重复事件 + 多档提醒 → 闹钟数量膨胀 | 低 | 每事件最多 6 个待触发点；`setWindow` 合并同刻；`dumpsys alarm` 列为验收项 |
| RS-6 | `RECEIVE_BOOT_COMPLETED` 引起隐私观感质疑 | 低 | 隐私说明加一句用途；它不触发运行时弹窗，不带来任何数据传输 |
| RS-7 | Glance 拉高 APK 体积（release 现 ~2 MB） | 中 | P2 开工前量化；超 +0.8 MB 则退手写 `RemoteViews`。此风险不阻塞 P0/P1 |
| RS-8 | 编辑器字段膨胀，小屏一屏装不下 | 中 | `ModalBottomSheet` + 可滚动；提醒/颜色/置顶折叠到"更多设置"，默认只露名称/日期/重复 |
| RS-9 | 大数字排版在极端天数（7 位）下溢出 | 低 | 数字用 `autoSize`（或分级字号：<1000 / <10000 / ≥10000），单测覆盖 9999999 天 |
| RS-10 | 重构范围大于经期 P0（触碰 DB 层 + 调度层） | 中 | P0 严格只做 §十一的 9 步；农历/配色/组件都不挤进 P0。DB 上移（AR-1）单独一个 commit，方便回滚 |

---

## 十三、实施结果与偏差（v1.3.0 落地记录）

P0 全部 9 步 + 农历（§6.3，按建议并入本次发版）已实现。`assembleRelease` 通过，全仓 **185 项单测全绿**（重构前 101 项，倒数日相关新增 84 项）。以下为**有意偏离方案**之处，后续维护者请以本节为准。

### 13.1 与方案的偏离

| 方案条款 | 实际实现 | 原因 |
| --- | --- | --- |
| §6.3 农历用 `android.icu.util.ChineseCalendar` | 纯 Kotlin 数据表 `TableLunarCalendar`（1900–2100 共 201 项 `LUNAR_INFO`） | ICU 在 JVM 单测里是 no-op stub，而本仓的立身约定是"算法可测"，农历恰恰是最需要被测的部分；且 ICU 数据随 ROM 版本漂移，同一日期在不同厂商可能差一天。**闰月回退规则与往返校验技巧照搬 §6.3**，未改设计 |
| §6.3 "ICU 无范围限制" | 对外口径仍限 1901–2099（表本身覆盖 1900–2100，两端各留一年缓冲） | 与原方案的用户可见口径一致 |
| §5.1 新增 `mode` 列 | **复用 v1 的 `type` 列**存 `EventMode` ordinal | v1 的 `type` 只有 `countdown`/`anniversary` 两个值，语义与"倒数/已过"一一对应；再加一列是冗余且要写映射代码 |
| §5.1 `repeat` 列 | 列名 `repeat_rule`（实体属性仍叫 `repeat`） | `repeat` 作为列名可读性差、易与 SQL 关键字混淆 |
| §5.2 `EventMode.AUTO` | 删除 | "自动判定倒数/正数"会让卡片语义不稳定（同一条记录换天可能翻转），而 §6.2 的显示口径已覆盖真实需求 |
| §5.3 `MigrationTestHelper` 校验迁移 | 无法使用 → 改为 `CountdownSchemaTest` 逐列比对 Room 生成的 `app/schemas/…/2.json` | **v1 从未开启 `exportSchema`，仓库里不存在 `1.json`**，测试助手没有基线可用。因此追加了两道替代防线：① 断言 `MIGRATION_1_2_SQL` 与 `2.json` 的 `createSql` 列名/类型/NOT NULL/DEFAULT 逐字节一致；② 源码看门狗断言 `fallbackToDestructiveMigration` 不在 `DatabaseModule` 里 |
| §8.1 精确闹钟 | `AlarmManager.setWindow`（非精确，600s 弹性窗口） | API 31+ 精确闹钟需用户手工授予 `SCHEDULE_EXACT_ALARM`，跳设置页的体验代价远大于"准点"的收益；用 §13.1 之外的三层恢复弥补 |
| §8.3 冷启动补发 | 冷启动 `backfillHours = 0`（只重排，不补发） | 每次启动都补发会把用户**刚划掉的提醒**重新弹出来，等于通知骚扰。补发只在 WorkManager 每日 00:20 与开机广播两条路径上以 12 小时窗口进行 |
| §8.3 开机恢复 | 只注册 `BOOT_COMPLETED`，**不加** `LOCKED_BOOT_COMPLETED` | 数据库在 credential-encrypted 存储区，设备未解锁时根本读不到，加了只会让接收器崩溃或空转 |
| §5.5 / 通知 deep link 带事件 id 到路由层 | 工具级深链（`tool/countdown`）+ `DeepLink` 单例传递 `eventId` | 27 个工具共用 `tool/{toolId}` 这一条路由，为其加导航参数等于改动所有工具的内容签名。`MainActivity` 的 `EXTRA_TOOL_ID`/`EXTRA_EVENT_ID` → `DeepLink` → 列表滚动+高亮，效果等价 |
| §6.1 重复推进 | 由"下一次发生日"算术后推进（`lastOccurrence`/`nextOccurrence` 各自独立求解），不做 `Period.plusYears` 累加 | 累加会把月末回退误差逐代传递下去；独立求解天然幂等 |

### 13.2 实施中额外发现并修掉的缺陷（方案未列，全部有回归测试）

1. **农历表三处抄本错误**：`1933`(闰五月/六月大小写反)、`1996`(五至八月月长整体反)、`2060`(三/四月月长反)。三处的共性是**全年月长总和正确、只有月内分布错位**，因此往返一致、年长区间、闰月数量之类的自校验断言一条都抓不到 —— 必须靠外部日期事实。裁决依据是**香港天文台官方《Gregorian-Lunar Calendar Conversion Table》**（一手出版物，非抄本）：如 1933 年表内 "5th Lunar month" 出现两次（05-24 与 06-23）即闰五月，跨 06-23→07-22 共 **30 天**，六月起于 07-23 且只有 29 天（"六月小"），三家出版黄历同此 —— 坊间抄本 `0x06e95` 与 npm 版 `solarlunar` 都把它记反了。
   两道校验已固化为 `scripts/lunar-table-audit.mjs`（`node scripts/lunar-table-audit.mjs`）：① 用 6tail/lunar-javascript（真朔+中气推算）重建 201 年逐月长度 → **201/201 一致**；② 解析本地 HKO 年历（需手工下载到 `lunar-research/hko/`，缺卷则明确跳过而非假通过）→ **可比对的 108 年全部一致**。**表被改动后必须再跑一次。**
   另一路独立佐证：并行调研产出的表与本表 201 行中 200 行一致，唯一分歧 2057 行系该路 HKO 卷下载为空、退回了错的抄本，天文实现与 `solarlunar` 均支持本表的 `0x06b20`。
2. **备份导入丢掉 1970 年之前的日期**：`CountdownBackup.resolveDate` 原以 `targetDate > 0` 判断"字段缺失"，而 v1 存的是 epoch 毫秒 —— 祖辈生日是负数，整条被静默丢弃。改为 `!= 0`。
3. **农历事件在极端年份后静默停摆**：`nextLunar` 的年份游标从 **base 的农历年**起算 + 固定 6 年窗口，2025 年设的农历生日在 2031 年之后再也够不到未来，卡片会永远停在"已过 N 天"。游标改为从"今天所在的农历年"起算。
4. **`requestCode` 的 Long 溢出绕过饱和保护**：`id * 512` 在脏数据下溢出成负数，绕过 `> Int.MAX_VALUE` 的夹取，两个事件抢同一个 `PendingIntent` 互相顶掉提醒。改为先给 id 封顶再乘。
5. **release 构建会混淆备份 DTO**：`CountdownBackup$Event`/`$Document` 走 Gson 反射，一旦改名，导出的 JSON 键变成 `a/b/c`，换机导入静默读空 —— **只在 release 复现，debug 单测抓不到**。已在 `proguard-rules.pro` 补 keep，同时删掉指向已不存在的 `LegacyCountdown` 的失效规则。
6. **通知跳转高亮不生效**：`CountdownViewModel` 在 `combine` 的 lambda 里读 `highlightFlow.value`，不是订阅，高亮永不触发。已把它接成第 5 个 combine 源。
7. **农历导入不再丢**：`LegacyImporter` 旧实现直接跳过农历事件；现在连同闰月字段一起导入。
8. `CountdownDraft.fromEntity` 的 `lunar` 参数原本是摆设（v1 老数据 `isLunar=true` 但缺农历字段时无处回填），现在真的用它反查回填。

### 13.3 验收标准对照（§十一）

已自动化覆盖并通过：

- [x] 旧版默认值不回退：SQL `DEFAULT 1/'0'/9` 与实体默认值**同时**断言（`CountdownSchemaTest.reminderDefaultsPreserveV1Behaviour`）—— 二者任一改动都会红
- [x] 迁移只追加 13 列、顺序=实体声明顺序、`countdown_events` 表名不变、v1 五列不动、`anchorDate` 是唯一可空新增、每个新增 NOT NULL 列都有 DEFAULT
- [x] "妈妈生日 2025-05-12 + 每年"，5/13 → 还有 364 天；发生日当天 → "就是今天"（`CountdownEngineTest`）
- [x] 2/29 + 每年 → 平年 2/28；31 号 + 每月 → 2/28（闰年 2/29）；月末回退可被检测（`fallbackUsed`）
- [x] 备份兼容：v1 millis / v2 ISO / 脏 `type` 字符串 / pre-1970 日期 / 农历字段回填，共 12 项
- [x] 全仓单测 185 全绿；`assembleRelease` 通过；`app/schemas/com.flechazo.toolbox.core.data.AppDatabase/2.json` 已生成并纳入版本控制（`.gitignore` 未忽略该路径）

**必须人工完成（本机无设备，无法自动化）**：

- [ ] **真机装 v1.1.3 造若干事件（含农历、置顶、旧提醒设置）→ 覆盖安装 v1.3.0**：事件数不变、旧提醒仍按当天 09:00 档触发。这是 §5.3 放弃 `MigrationTestHelper` 后唯一能证明"真机 SQLite 迁移不丢数据"的手段，**发版前不可跳过**
- [ ] `adb shell dumpsys alarm | Select-String countdown`：改事件时间/提醒档后闹钟时刻随之变化，且总闹钟数 ≤ 6/事件
- [ ] 重启手机后当天提醒仍送达；杀进程后仍送达；改系统时区后重排成功
- [ ] 未授予 `POST_NOTIFICATIONS` 时功能全部可用、只出现一次引导条不弹窗骚扰；授予后能收到通知；锁屏显示私有内容 + `publicVersion` 生效
- [ ] 部分厂商 ROM（小米/华为/OPPO）需在引导里提示用户加白名单，否则 L1/L2 全失效 —— 属已知外部限制（RS-3）
- [ ] 首页搜索"生日""农历""周年""days matter"均命中

### 13.4 交付清单

新增：`CountdownModels.kt`、`CountdownEngine.kt`、`CountdownLunar.kt`、`TableLunarCalendar.kt`、`CountdownReminders.kt`、`CountdownRepository.kt`、`CountdownDao.kt`、`CountdownDraft.kt`、`CountdownPalette.kt`、`CountdownBackup.kt`、`CountdownEditor.kt`、`CountdownCards.kt`、`CountdownViewModel.kt`、`CountdownModule.kt`、`core/notify/*`（Scheduler / Notifications / Rescheduler / AlarmReceiver / SystemReceiver / ReminderWorker）、`core/util/DayTicker.kt`、`core/navigation/DeepLink.kt`、`scripts/lunar-table-audit.mjs`、`app/schemas/…/2.json`；测试 5 个新文件 + 重写 1 个。
删除：`feature/countdown/CountdownData.kt`（实体/DAO/DB 曾位于 feature 包，AR-1 上移至 `core/data`）。
重写：`CountdownScreen.kt`、`AppDatabase.kt`、`MainActivity.kt`、`ToolboxApplication.kt`、`LegacyImporter.kt`、`AndroidManifest.xml`、`AppNavHost.kt`。

### 13.5 未做（留给 P1/P2）

桌面小组件（§9）、分享卡片图、相册封面背景、网格视图、拖拽自定义排序、事件密码保护、"重要日纪念"类扩展玩法。P0 中"进度条/年+月+日分解/配色/分组折叠/无障碍语义"已随本次一并落地。

---

## 附录 A：与现有仓库约定的一致性

- **组件复用**：`ToolScaffold` / `ToolSectionCard` / `ResultCard`（大数字，`tabular` 已有）/ `SegmentedTabs` / `FeedbackBlock` / `ToolTextField` / `LabeledDropdown` / `BottomActionBar`，全部已在 `core/designsystem/components/Components.kt`。
- **算法纯函数**：`CountdownEngine` / `CountdownLunar` 与 `PeriodPredictor` 同风格，不引 ViewModel 测试框架。
- **通知**：沿用倒数日已有的"非精确闹钟 + 拒绝权限即静默降级"范式（`CountdownNotifications.kt:21-27` 的注释判断是对的，本方案保留并补齐缺失的恢复层）。
- **本地优先**：不引任何网络、统计、账号能力；`toolbox.db` 继续走系统备份（非敏感）。
- **发版**：走 `scripts/release.ps1`，`RELEASE_NOTE.md` 面向用户撰写，重点写"生日终于可以每年自动倒数了""重启后提醒不会丢"。

## 附录 B：调研来源

- 倒数日 · Days Matter（iOS，Clover 出品）：<https://apps.apple.com/cn/app/%E5%80%92%E6%95%B0%E6%97%A5-days-matter/id406170251>
- DaysTill - 倒计时与纪念日（iOS）：<https://apps.apple.com/cn/app/daystill-countdown-countup/id6474996230>
- 时间规划局 - 倒计时与提醒事项（iOS）：<https://apps.apple.com/cn/app/%E6%97%B6%E9%97%B4%E8%A7%84%E5%88%92%E5%B1%80-%E5%80%92%E8%AE%A1%E6%97%B6%E4%B8%8E%E6%8F%90%E9%86%92%E4%BA%8B%E9%A1%B9/id1439723850>
- Countdown Widget · Countdown app（Android，Gira Mobile）：<https://apkpure.com/cn/countdown-widget%E3%83%BBcountdown-app/me.gira.widget.countdown>
- `android.icu.util.ChineseCalendar`（API 24+，农历实现依据）：<https://developer.android.google.cn/reference/android/icu/util/ChineseCalendar>
- Jetpack Glance 小组件版本可用性（Google Maven `androidx.glance:glance-appwidget`，最新稳定 1.1.1）：<https://dl.google.com/android/maven2/androidx/glance/glance-appwidget/maven-metadata.xml>
- API 31+ 精确闹钟权限约束：`SCHEDULE_EXACT_ALARM`（Android 12 行为变更）——本方案据此**刻意不用**精确闹钟

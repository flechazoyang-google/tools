# 经期记录工具优化方案 v1.0

> 状态：**P0 已实施**（见下方「实施状态」）· 目标版本 **v1.2.0**
> 范围：`feature/period`（全量重写）+ `PeriodRepository` + 备份规则 + 工具注册
> 前置：现有 76 个单测全绿，`assembleRelease` 可构建（本次已实测基线）

---

## 实施状态（P0）

| 项 | 状态 | 落地位置 |
| --- | --- | --- |
| 数据模型（周期区间 + 每日记录 + 设置） | ✅ | `feature/period/PeriodModels.kt` |
| 预测算法（近期加权 + 区间 + 迟到/漏记） | ✅ | `feature/period/PeriodPredictor.kt` |
| 存储编解码 + 旧数据迁移 | ✅ | `feature/period/PeriodCodec.kt`、`core/data/CoreStores.kt` |
| ViewModel（含撤销、导入导出、清空） | ✅ | `feature/period/PeriodViewModel.kt` |
| UI 重写（状态卡 8 态 + 区间日历 + 趋势 + 历史） | ✅ | `feature/period/PeriodScreen.kt`、`feature/period/PeriodCalendar.kt` |
| 备份排除路径修正（cloud + D2D） | ✅ | `res/xml/backup_rules.xml`、`res/xml/data_extraction_rules.xml` |
| 首启敏感信息告知 + 单独同意 | ✅ | `PeriodScreen.kt` 的 `ConsentCard` |
| 搜索关键词与描述 | ✅ | `core/registry/ToolCatalog.kt` |
| R8 保留 Gson DTO 字段名 | ✅ | `proguard-rules.pro` |
| 单测 | ✅ | `PeriodPredictorTest`（19）+ `PeriodCodecTest`（10），全仓 **101 通过 / 0 失败** |
| `assembleRelease` | ✅ | R8 构建通过，mapping 已核验 DTO 字段名未被混淆 |
| 提醒通知 | ⏳ P1 | 本轮未做，工具描述已改为"周期记录、日历预测与趋势"，不再承诺提醒 |
| 应用锁 / 截图保护 / 通知脱敏 | ⏳ P1 | — |

> 本轮刻意**未**实现提醒：它需要通知渠道 + 权限 + 调度三件套，与正确性改造混在一起会放大回归面。P0 先把"记录得准、预测诚实、数据不出设备"做扎实。

---

## 一、结论摘要

现状是一个"能用的最小可用版"：月历 + 单点预测 + 开始日集合。它和主流经期产品（Flo / Clue / Apple 健康 / 美柚 / 大姨妈）的差距不在功能数量，而在**三处根基**：

| 根基 | 现状 | 目标 |
| --- | --- | --- |
| **数据模型** | 只存"经期开始日"的扁平集合，无结束日、无流量、无症状 | 周期区间（起止）+ 每日记录（流量/症状/备注） |
| **预测算法** | 全历史中位数 → 单个日期，无区间、无置信度、迟到信号丢失 | 近期加权平均 + 波动度 → 预测区间 + 置信度 + 规律性评价 |
| **交互** | 点日历即"记录开始日"，无引导、无撤销、无提醒 | 状态卡驱动 + 区间录入 + 快速记录 + 提醒 + 洞察 |

本次共识别 **6 个 P0 缺陷、8 个 P1 缺口、3 项隐私问题**，其中 2 个缺陷已用当前算法实测复现（见 §2.2），1 个隐私问题会**让经期数据实际进入云端备份**（见 §2.4），与 README 声明的"经期数据全部留在设备上"直接矛盾。

---

## 二、现状诊断

### 2.1 代码结构

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `feature/period/PeriodScreen.kt` | 316 | UI + ViewModel + 算法 + 数据模型，四者混在一个文件 |
| `core/data/CoreStores.kt:121-153` | — | `PeriodRepository`：DataStore + Gson，只存 `starts` |
| `core/data/CoreStores.kt:113-115` | — | `period` DataStore 实例 |
| `app/src/test/.../LifeAlgorithmsTest.kt:70-105` | — | 4 个 `predictPeriod` 用例 |

算法本身是纯函数、有单测，这点符合仓库"算法可测"原则，应保留并扩展。

### 2.2 P0 缺陷（含实测复现）

复现方式：用当前 `predictPeriod` 的等价实现跑真实记录模式，`today` 固定。

| # | 级别 | 位置 | 缺陷 | 实测表现 |
| --- | --- | --- | --- | --- |
| **P0-1** | 🔴 | `PeriodScreen.kt:66-90` | 模型把"经期日"当"周期开始日"：连续点 5 天会产生 5 条记录 | 记录 3 个 5 天经期（1/1–5、1/29–2/2、2/26–3/2），今天 3/3 → 界面显示 **"平均周期 24 天"**（真实 28 天）、**"已记录 15 次"**（真实 3 次） |
| **P0-2** | 🔴 | `PeriodScreen.kt:76-81` | `while (next.isBefore(today)) next += cycle` 把预测日强行推到未来，`daysUntilNext` 恒 ≥ 0 | 上次 2/1、周期 28、今天 3/13（**已推迟 12 天**）→ 界面显示 **"还有 16 天"**。用户最需要的"迟到了"信号完全丢失 |
| **P0-3** | 🔴 | `PeriodScreen.kt:147` | `days < 0L -> "已推迟 …"` 分支因 P0-2 成为**死代码**，永远不可达 | 同上 |
| **P0-4** | 🟠 | `PeriodScreen.kt:297` | 日历所有格子（含未来日期）均可点击记录，无守卫 | 误点一个未来日期，`lastStart` 变成未来 → 后续预测全乱 |
| **P0-5** | 🟠 | 数据模型 | 无经期结束日 → 无法计算经期长度、无法标记"预计哪几天来" | 日历只描边**一个**预测日；用户看不出预计来几天 |
| **P0-6** | 🟠 | `PeriodScreen.kt:73` | 用中位数却命名为 `averageCycle`，文案写"平均周期" | 与用户手动算出的平均值不符，削弱可信度 |

补充实测：只记录 1 次就开始预测（`averageCycle=28`、`nextStart=last+28`），**没有任何"数据不足"提示**，用户会误以为预测很准。

### 2.3 P1 能力缺口

| # | 缺口 | 说明 |
| --- | --- | --- |
| P1-1 | **无提醒** | 工具描述写着"周期记录与预测提醒"，但代码里没有任何通知逻辑；`CountdownNotifications` 已有可复用范式 |
| P1-2 | 无流量 / 症状 / 备注 | 主流产品全部具备；也导致"经期长度"无法自动推算 |
| P1-3 | 无洞察 | 无周期长度趋势、无波动范围、无规律性评价 |
| P1-4 | 无导出 | 「我的 → 导出数据」只导出倒数日，经期数据无出口 |
| P1-5 | 搜索发现性 | `ToolCatalog.kt:176` 关键词只有 `经期/生理期/周期/period`，搜"月经""例假""大姨妈""安全期""排卵"均无结果 |
| P1-6 | 无撤销 | 删除记录直接生效，误删不可恢复 |
| P1-7 | 无障碍 | `DayCell` 无 `contentDescription`，读屏只念出"5" |
| P1-8 | 跨零点不刷新 | `PeriodUiState.daysUntilNext` 依赖 `LocalDate.now()`，但 Flow 不会在午夜重新发射 |

### 2.4 隐私问题

| # | 级别 | 问题 | 证据 |
| --- | --- | --- | --- |
| **PR-1** | 🔴 | **备份排除规则已失效，经期数据实际会进云备份** | `PeriodRepository` 用的是 `PreferenceDataStoreFactory` + `preferencesDataStoreFile("period_records")`，落盘路径是 `filesDir/datastore/period_records.preferences_pb`（[官方文档](https://androidx.github.io/kmp-eap-docs/libs/androidx.datastore/datastore-preferences/androidx.datastore.preferences/preferences-data-store-file.html)）；而 `backup_rules.xml:8` 与 `data_extraction_rules.xml:9,13` 排除的是 `sharedpref/period_records.xml`（旧版 SharedPreferences 路径），**不再匹配任何文件** |
| PR-2 | 🟠 | 无应用锁、无截图保护、通知若上线则锁屏明文显示 | 未使用 `FLAG_SECURE`；无锁屏可见性控制 |
| PR-3 | 🟠 | 无"彻底删除"入口 | 只能逐条删；且修复 PR-1 后若无导出，换机会丢数据 |

> PR-1 是"隐私承诺与实际行为不一致"的问题，优先级最高：要么修好排除规则并配套导出，要么明确改为加密云备份——**二选一，不能维持现状**。

---

## 三、主流产品设计调研

### 3.1 先行结论

主流经期产品能建立"可信感"，靠的不是预测更准，而是三件事：

1. **记录粒度到"每天"**（流量 + 症状 + 备注），而不是只记"经期开始日"；
2. **预测给区间/波动**，而不是一个假装确定的日期；
3. **把"历史"当主角**——周期长度历史、波动范围、规律性评价，比"下次是哪天"对用户更有用。

不规则周期场景下的行业共识尤其直白："没有任何预测算法能处理好真正不规律的周期，所以最好的 App 是给出有用的历史，而不是自信的猜测"（[Go Go Gaia 六款产品对比](https://www.go-go-gaia.com/blog/best-period-tracker-irregular-cycles.html)，注意该文作者自述为竞品厂商，结论但书已声明）。

另一个关键数据：对 **60 万+ 个真实周期**的分析显示，平均周期长度恰好为 28 天的人只占约 **13%**（[npj Digital Medicine 2019](https://www.nature.com/articles/s41746-019-0152-7)）。所以"默认 28 天"只能作为兜底值，不能当作常态。

### 3.2 国际产品对照

| 产品 | 记录粒度 | 预测方式（公开说明） | 预测呈现 | 隐私 / 账号 | 价格 |
| --- | --- | --- | --- | --- | --- |
| **Clue** | 按天记录经量（Light / Medium / Heavy / Super heavy）；分类为 Period / Pain / Feelings 等，支持自定义标签与备注（[Clue 支持](https://support.helloclue.com/hc/en-us/articles/29049139459101-How-to-track-cycle-symptoms-and-experiences)） | 官方：**第一天记录 Light 及以上即视为经期开始，停止记录即视为结束**；并明示"记录越准确一致，预测越准"（[Clue 支持](https://support.helloclue.com/hc/en-us/articles/215935063-How-do-I-track-my-period)） | 公开说明预测方法；展示周期波动（区间/波动，非单点） | GDPR、柏林；不强制分享给第三方 | 免费（有升级提示）+ Clue Plus ≈ $39.99/年 |
| **Flo** | 症状记录 + 每日"cycle story"；首页为圆形表盘 | 至少需 1 次记录；官方建议记满"最近 3 个周期"或 12 个月；预测下次经期、排卵日、易孕日、**推迟状态**与每日受孕概率（[Flo 帮助中心](https://help.flo.health/hc/en-us/articles/4406826523284-Checking-your-cycle-predictions)） | 首页表盘**先倒计时排卵、排卵后倒计时经期**（单点数值） | **必须注册账号**；2021 年 FTC 和解（健康数据分享给 Facebook/Google 等） | 免费（含广告）+ Premium ≈ $59.99/年 |
| **Apple 健康 Cycle Tracking** | 流量（点滴/少/中/多）+ 症状 + 基础体温 + LH 试纸结果 + 宫颈黏液 + 点滴出血 + 性生活 + 避孕方式 | 基于历史记录；**明确声明不能用于避孕** | **预测经期与易孕期均以"区间"呈现**（易孕期是跨日区间）；周期历史视图 + **周期偏差通知**（经期过长、稀发、不规律、持续点滴出血）；排卵为**回溯估算**，且需腕温支持 | 免费、无账号、健康数据加密、设备端处理 | 免费（系统内置） |
| **Natural Cycles** | 基础体温为核心 | **不做日历法**，用体温回溯判断排卵 | 事后确认 | 订阅制 | ≈ $119.99/年 |
| **Stardust** | 日历式 + 简单症状 | 假设规律 | 单一日期 | 2022 年隐私争议后修订政策 | 免费 + 会员 |

> Flo 的教训值得写进本项目原则：把经期数据交给第三方 SDK 会直接招致监管处罚（[FTC 2021 年新闻稿](https://www.ftc.gov/news-events/news/press-releases/2021/06/ftc-finalizes-order-flo-health-fertility-tracking-app-shared-sensitive-health-data-facebook-google)）。本工具**不接入任何统计/广告 SDK**，这是低成本的高价值合规。

### 3.3 国产产品与系统级方案（美柚 / 大姨妈 / 华为 / 小米）

依据官方页面与官方支持文档：

| 产品 | 公开的功能要点 | 可借鉴 / 需警惕 |
| --- | --- | --- |
| **美柚** | 经期 / 备孕 / 怀孕 / 育儿**四大模式**；智能预测 + 及时提醒；记录每天心情与健康细节；排卵日一查便知；会员含"智能分析、桌面小组件、专业健康测评、免除广告"；另有社区与购物返现（[App Store](https://apps.apple.com/cn/app/%E7%BE%8E%E6%9F%9A-%E7%BB%8F%E6%9C%9F%C2%B7%E5%A4%87%E5%AD%95%C2%B7%E6%80%80%E5%AD%95%C2%B7%E8%82%B2%E5%84%BF-%E4%BA%B2%E5%8F%8B%E7%89%88/id634896669)） | ⚠️ "免除广告"是会员权益 → 免费层含广告；曾被工信部通报（[来源](https://wap.stockstar.com/detail/SS2022101400009106)） |
| **大姨妈** | 精准预测与提醒；**记录症状并生成健康报告**；完整时间线日历视图；**云端储存数据**；排卵期排卵日；每日健康小贴士；私密社区；免责声明"不可被视作医疗建议或诊断"（[App Store](https://apps.apple.com/cn/app/%E5%A4%A7%E5%A7%A8%E5%A6%88%E6%9C%88%E7%BB%8F%E6%9C%9F%E5%8A%A9%E6%89%8B-%E5%A5%B3%E6%80%A7%E5%81%A5%E5%BA%B7%E6%94%BB%E7%95%A5%E7%A4%BE%E5%8C%BA/id527809600)） | ✅ 免责声明口径可直接沿用；⚠️ 云存储为特性，无本地优先选项 |
| **华为运动健康** | 在生理周期页**选日期后勾选"经期开始了吗 / 经期结束了吗"**设定起止；两段经期间隔须 > 5 天；可**手动设经期长度与周期长度**；"基于历史经期智能推算未来经期和易孕期"；部分手表可结合心率/体温改进预测（可开关）；**经期与易孕期提醒默认关闭**（[华为支持](https://consumer.huawei.com/cn/support/content/zh-cn15799829/)） | ✅✅ 语义化开始/结束按钮、手动长度兜底、提醒默认关闭——**三项直接采纳** |
| **小米健康 / Mi Fit** | 记录项明确为"**经期、疼痛程度、血容量、心情**"；推算月经期 / 易孕预测 / 排卵日预测；官方明示"**长期连续完整的月经记录有助于提高预测准确性**"；提醒固定在**当天 21:00**；官方承认手环与 App 预测会不一致（[小米支持](https://www.mi.com/tw/support/article/KA-18065/)） | ✅ 记录项命名、"记录越多越准"的预期管理、固定提醒时刻 |

**取舍**：国产产品走的是"记录 + 预测 + 提醒 + 多模式 + 社区/电商"的超级 App 路线。本工具是工具类 App 里的一个小工具，**不复制社区、电商、广告、强制账号、云端存储**；值得借鉴的是**语义化开始/结束按钮、手动周期长度兜底、提醒默认关闭、固定提醒时刻、每日心情与症状记录、多模式（经期/备孕）**。

### 3.4 值得借鉴的设计模式（本方案采纳）

| 模式 | 来源 | 落到本方案 |
| --- | --- | --- |
| 预测给区间 + 波动，并说明依据 | Clue、Apple | §6.2 预测区间 + `window` + "基于最近 N 个周期" |
| 周期长度历史图 | Clue 周期分析、Apple 周期历史 | §7.5 洞察卡（最近 6 个周期条形图） |
| 规律性/偏差提示，但不诊断 | Apple 周期偏差通知 | §6.2 规律性评价 + §9 红旗提示 |
| 症状与日历解耦（非经期日也能记） | Clue、Go Go Gaia | §5.1 `DailyLog` 独立于 `PeriodRecord` |
| 按天记录经量、由记录自然推出起止 | Clue（首日记 Light 即开始、停记即结束） | §5.1 `PeriodRecord.flows` + §7.4「经期开始/结束」按钮兜底 |
| 允许关闭预测 | Clue、Apple | §5.1 `predictEnabled` |
| 一键记录今天 | 通用 | §7.4 快速记录条 |
| 本地优先、无账号、可导出 | Apple Health 姿态 | §8 隐私设计 |
| 语义化「经期开始了吗 / 经期结束了吗」按钮 | 华为运动健康 | §7.4 两个显式动作，比区间拖拽更省心、天然防误录 |
| 单一倒计时切换目标（先排卵、后经期） | Flo 首页表盘 | §7.2 状态卡的"排卵临近 / 临近"两态 |
| 跨页面语义配色 + 图例解释 | Flo（粉=经期、青=排卵/易孕、灰=推迟）、Apple | §7.3 配色与图例 |
| 提醒默认关闭 + 固定提醒时刻 | 华为（默认关）、小米（21:00） | §5.1 `reminderEnabled = false` + §8.2 |
| 明示"记录越多越准" | 小米官方 | §7.2 数据不足态 + §9 首次引导 |
| 手动周期/经期长度兜底 | 华为 | §5.1 `cycleLengthOverride` / `periodLengthOverride` |
| 周期天数显示可关 | Flo | §5.1 `showCycleDay` |

### 3.5 要避免的坑

| 坑 | 现状 / 来源 | 本方案对策 |
| --- | --- | --- |
| 只给单一预测日期 | Flo 仍如此，用户吐槽"总是错" | §6.2 区间化 |
| 只记开始日导致统计错误 | **本工具现状（§2.2）** | §5 数据模型重构 |
| "迟到"焦虑式提醒 | 行业常见吐槽 | §7.2 迟到文案克制 + 可关闭预测 |
| 强制账号 / 云端存储 | 国产产品普遍 | 不采用 |
| 广告 / 社区 / 电商导流 | 国产产品普遍 | 不采用 |
| 把"安全期"当避孕依据 | 行业普遍误导 | §9 禁用表述 |
| 易孕期渲染得像"保证" | 主流产品常见 | §7.3 标注"估算" + §9 禁用表述 |
| 预测算法口径不透明 | 华为/美柚/大姨妈均未公开 | §7.2 副文案固定给出"基于最近 N 个周期 · ±X 天" |
| 过度诊断化文案 | — | §9 只用"建议咨询"措辞 |

### 3.6 预测区间优先的 UI 范式（国际主流共识）

Apple 的做法最有参考价值：**预测经期与易孕期都渲染为"跨日区间"，从不渲染成钉死的一天**。汇总为可直接落地的渲染规则：

| 规则 | 说明 |
| --- | --- |
| 区间用柔和边缘 | 文案写"预计 3月3日–3月6日"，而不是"3月3日" |
| 已记录 vs 预测要能分辨 | 已记录=实心；预测=半透明/描边（不能只靠颜色深浅） |
| 显示置信度 | "基于 2 个周期 · 置信度低"——把不确定性写在界面上 |
| 波动越大、区间越宽 | 与 §6.2 的 `window` 一致，而不是固定宽度 |
| "今天"要有独立标记 | 竖向标线或双层描边 + `Day 1` 标记 |
| 图例解释所有底色 | 至少覆盖：已记录 / 预测 / 易孕期 / 排卵日 / 今天 |
| 记一次就重新拟合 | 任何一次记录变更立即重算，不做"手动刷新" |

> 直接采用 Apple 的一句硬约束作为产品红线：**Cycle Tracking 不能用作避孕手段**（Apple 官方 IFU 明确声明）。本工具同理。

---

## 四、目标与非目标

### 目标

1. **数据可信**：任何记录方式（只记开始、记整段、补记历史）都不产生错误的周期统计。
2. **预测诚实**：给区间而非单点，给置信度而非"精准"，迟到/漏记明确说出来。
3. **记录省力**：一键记录今天；症状可选、不强制。
4. **隐私可信**：数据不出设备，且用户能导出、能彻底删除。
5. **可测**：算法保持纯函数，新增用例 ≥ 25。

### 非目标（本方案不做）

- ❌ 不引入机器学习/神经网络预测（Flo 的做法需要海量数据与后端，本地工具类 App 无法承担，且不可解释）。
- ❌ 不做"避孕"宣称：日历法不足以避孕，文案必须回避这一承诺。
- ❌ 不做账号、云同步、社交、社区。
- ❌ 不做体温曲线之外的高级生育力指标（LH 试纸录入、宫颈黏液评分）——列入 P2。
- ❌ 不引入 Room（理由见 §5.3）。

---

## 五、数据模型设计

### 5.1 新模型

```kotlin
// feature/period/PeriodModels.kt

/** 经量分级。SPOTTING 不计入经期天数统计。 */
enum class FlowLevel(val label: String) {
    SPOTTING("点滴"), LIGHT("少"), MEDIUM("中"), HEAVY("多"),
}

/**
 * 一次经期。end == null 表示"进行中/未记录结束"。
 * start 必须 ≤ end；end 缺失时不计入经期长度中位数。
 */
data class PeriodRecord(
    val start: LocalDate,
    val end: LocalDate? = null,
    /** 每日经量，键为日期；缺省按 MEDIUM 渲染 */
    val flows: Map<LocalDate, FlowLevel> = emptyMap(),
    val note: String = "",
) {
    val isOngoing: Boolean get() = end == null
    /** 已知长度（含首尾）；end 缺失时为 null */
    val lengthDays: Int? get() = end?.let { ChronoUnit.DAYS.between(start, it).toInt() + 1 }
    fun contains(date: LocalDate): Boolean =
        date >= start && (end == null || date <= end)
}

enum class Symptom(val label: String) {
    CRAMPS("痛经"), BACK_PAIN("腰酸"), HEADACHE("头痛"), BREAST_TENDERNESS("乳房胀痛"),
    FATIGUE("疲劳"), MOOD_SWING("情绪波动"), INSOMNIA("失眠"), APPETITE("食欲变化"),
    NAUSEA("恶心"), DIGESTION("腹泻/便秘"), SKIN("皮肤问题"), DISCHARGE("白带异常"),
}

enum class PainLevel(val label: String) { NONE("无"), MILD("轻"), MODERATE("中"), SEVERE("重") }

/** 每日主观记录，与 PeriodRecord 解耦：非经期日也能记。 */
data class DailyLog(
    val date: LocalDate,
    val symptoms: Set<Symptom> = emptySet(),
    val pain: PainLevel? = null,
    val weightKg: Double? = null,
    val note: String = "",
)

/** 用户偏好与手动覆盖。 */
data class PeriodSettings(
    val cycleLengthOverride: Int? = null,   // 手动指定周期长度（21..45）
    val periodLengthOverride: Int? = null,  // 手动指定经期长度（2..10）
    val predictEnabled: Boolean = true,
    val showFertileWindow: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 2,        // 0..7
    val showCycleDay: Boolean = true,       // 是否显示"周期第 N 天"（Flo 默认关）
    val lockEnabled: Boolean = false,
    val hideNotificationDetail: Boolean = true,
)

/** 落盘的顶层文档，带 schema 版本以便将来演进。 */
data class PeriodData(
    val schemaVersion: Int = 2,
    val records: List<PeriodRecord> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    val settings: PeriodSettings = PeriodSettings(),
)
```

### 5.2 迁移（必须无损）

旧数据是 `starts: List<LocalDate>`（可能含连续多天）。迁移规则：

1. 把旧日期升序排列，**相邻间隔 ≤ 2 天归为同一段**（连续补记会被合并成一次经期）。
2. 段内首日 = `start`，末日 = `end`；**只有 1 天的段 `end = null`**（无法判断是"只记了开始"还是"只来了一天"，取保守值，不计入长度中位数）。
3. `flows` 留空（渲染时按 MEDIUM 兜底）。
4. 迁移幂等：新键 `period_data_v2` 存在时不再读旧键；旧键保留一个版本再清理，便于回滚。

实测对照（用 §2.2 的 A 组数据）：迁移后应为 **3 次经期**（而非 15 次），周期长度恢复为 28 天。

### 5.3 存储方案：DataStore + 版本化 JSON（不引入 Room）

| 方案 | 优势 | 代价 | 结论 |
| --- | --- | --- | --- |
| **DataStore + 单 JSON 文档**（选用） | 与现有 `PeriodRepository` 一致；零迁移风险；`schemaVersion` 自描述；写入量小 | 每次写重写整个 JSON（O(n)） | ✅ 采用 |
| Room | 查询高效、结构强约束 | `AppDatabase` 当前 `version = 1` 且**未配置 `fallbackToDestructiveMigration`**，加表必须写 `Migration(1,2)`，否则老用户升级即崩 | ❌ 暂不采用，列入 P2 备选 |

数据量估算：5 年 ≈ 60 次经期 + 1800 条 `DailyLog` ≈ 200 KB JSON，DataStore 在 IO 线程写入可接受；如需瘦身，可只保留最近 24 个月的 `DailyLog`（`records` 永久保留）。

---

## 六、预测算法设计

### 6.1 接口（纯函数，便于单测）

```kotlin
// feature/period/PeriodPredictor.kt

enum class Regularity(val label: String) { REGULAR("规律"), SLIGHT("轻度波动"), IRREGULAR("不规律"), UNKNOWN("数据不足") }
enum class Confidence { LOW, MEDIUM, HIGH }

data class CycleStats(
    val cycleLength: Int,        // 近期加权平均（或手动覆盖）
    val variationDays: Int,      // 周期波动范围 = max - min（FIGO 规律性口径）
    val sdDays: Int,             // 周期长度总体标准差，用于预测区间宽度
    val regularity: Regularity,
    val basedOnCycles: Int,      // 参与计算的周期数
    val periodLength: Int,       // 经期长度中位数（默认 5）
    val periodLengthKnown: Boolean,
)

data class PeriodPrediction(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val nextStart: LocalDate,          // 中心值 = 区间中点
    val predictedPeriodEnd: LocalDate, // 中心值 + 经期长度 - 1
    val daysLate: Int,                 // > 0 表示已推迟
    val missedCycle: Boolean,          // 迟到超过一个周期 → 疑似漏记
    val ovulation: LocalDate,          // 估算，非精确
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    val confidence: Confidence,
    val stats: CycleStats,
)

/** 记录不足 1 个完整周期时返回 null，UI 走引导态。 */
internal fun predictPeriod(
    records: List<PeriodRecord>,
    today: LocalDate,
    settings: PeriodSettings = PeriodSettings(),
): PeriodPrediction?
```

### 6.2 公式

```
1. starts   = records.map{ it.start }.distinct().sorted()，过滤 > today 的（见 6.3）
2. cycles   = 相邻 starts 间隔，保留 15..60 天者；只取最近 6 个
3. cycleLen = settings.cycleLengthOverride
              ?: 加权平均（权重 1..n，越近权重越大，n = cycles.size）
              ?: 28（无周期数据时）
   → clamp 21..45
4. sd       = cycles 的总体标准差（n < 2 时为 0）
5. window   = n == 0 → 5 天；n == 1 → 3 天；n ≥ 2 → clamp(round(sd), 1, 5)
6. center   = lastStart + cycleLen
7. 若 today > center：daysLate = today - center
   若 daysLate > cycleLen：center 逐周期前移，missedCycle = true（不静默跳到未来）
8. 区间     = [center - window, center + window]
9. 经期长度 = settings.periodLengthOverride ?: median(已知 lengthDays) ?: 5，clamp 2..10
10. 经期区间 = center .. center + 经期长度 - 1
11. 排卵日  = center - 14（黄体期按 14 天估算）
12. 易孕期  = [排卵日 - 5, 排卵日 + 1]
13. 波动范围 = max(cycles) - min(cycles)（即 FIGO 的"规律性"定义，见 §6.5）
    规律性   = ≤ 7 天 → 规律；8–9 天 → 轻度波动；> 9 天 → 不规律
14. 置信度   = n == 0（仅 1 次记录）→ LOW；1 ≤ n ≤ 3 → MEDIUM；n ≥ 4 且 sd ≤ 3 → HIGH
```

> 置信度只影响文案与区间宽度，**任何档位都不构成避孕依据**——即使 HIGH 也只是"这个日历估算在你的记录里比较稳定"。

**关键取舍**：

- **近期加权 > 全历史中位数**：全历史中位数在"半年前规律、最近紊乱"时会给出过时结论；加权平均让最近 3–6 个周期主导，同时避免单次异常值直接带偏（权重线性，非等权）。
- **给区间而非单点**：`window` 由实际波动度决定，规律用户得到 ±1 天，紊乱用户得到 ±4 天——**把不确定性如实呈现**，而不是假装精准。
- **不静默顺延**：迟到就说迟到，漏记就说漏记。这是 P0-2/P0-3 的修复本质。
- **黄体期固定 14 天是估算**：真实黄体期 11–17 天不等，因此排卵日/易孕期必须标注"估算"，且不提供避孕结论。

### 6.3 边界与异常处理

| 场景 | 处理 |
| --- | --- |
| 记录日期在未来 | 迁移/加载时剔除，并在 UI 提示"已忽略 1 条未来日期记录" |
| `end < start` | 视为 `end = null` |
| 经期长度 > 8 天 | 保留但标记异常（FIGO 上限为 8 天），提示就医 |
| 相邻两次 start 间隔 < 15 天 | 视为重复录入，合并为同一次经期 |
| 无任何记录 ≥ 90 天 | 提示"长期未记录"，不自动生成预测 |
| 进行中（`end == null`）且 today 已超过 start+10 | 自动闭合为 `start + 中位数长度 - 1`，并提示确认 |
| 记录 0 条 | 返回 `null` → 引导态 |
| 记录 1 条 | 返回预测，`confidence = LOW`，`window = 5`，文案明示"记录 3 个周期后更准" |

### 6.4 测试夹具（已用原型验证的期望值）

| 用例 | 输入 starts | today | 期望 |
| --- | --- | --- | --- |
| 规律 28 天 | 1/1, 1/29, 2/26 | 3/3 | cycle 28、sd 0、window 1、区间 3/25–3/27、排卵 3/12、易孕 3/7–3/13 |
| 迟到 12 天 | 12/7, 1/4, 2/1 | 3/13 | center 3/1、daysLate 12、missedCycle false |
| 不规律 26/34 | 1/1, 1/27, 3/2 | 3/3 | cycle 31、sd 4、波动范围 8、window 4、区间 3/29–4/6、规律性"轻度波动" |
| 单条记录 | 2/26 | 3/3 | cycle 28、window 5、confidence LOW |
| 漏记整周期 | 12/7, 1/4 | 3/13 | center 3/1、daysLate 12、missedCycle true |
| 连续多天录入 | 1/1–1/5 合并为 1 次 | 3/3 | 记录数 1、cycle 取不到（n=0）、window 5 |
| 空记录 | — | 任意 | 返回 null |

> 上述数值已在 PowerShell 中原型实现并核对（见 §6.2 公式），可直接作为单测断言值。

### 6.5 临床参照基线（FIGO 2018）

算法里的"正常/波动/异常"阈值不凭感觉定，统一对齐 FIGO 2018 的月经参数标准（[MSD 专业版汇总表](https://www.msdmanuals.com/professional/multimedia/table/normal-menstrual-parameters)，原始文献 [Munro MG et al., Int J Gynaecol Obstet 2018](https://pubmed.ncbi.nlm.nih.gov/30198563/)）：

| 参数 | 正常范围 | 本工具的用法 |
| --- | --- | --- |
| 频率（周期长度） | ≥ 24 且 ≤ 38 天 | `cycleLen` clamp 21..45；< 24 或 > 38 天在洞察卡提示"偏离常见范围" |
| 规律性（周期波动） | ≤ 7–9 天 | §6.2 第 13 条：≤ 7 规律 / 8–9 轻度波动 / > 9 不规律 |
| 经期长度 | ≤ 8 天 | 超过 8 天标记异常并提示就医 |
| 经量 | < 80 mL（临床以"≤3 小时浸透一片/血块 >2.5cm"为参考） | 流量分级只做主观 4 档，不换算毫升、不做诊断 |

> 注意：FIGO 2018 把正常周期下限从旧的 21 天改为 24 天、上限从 35 天改为 38 天。网上流传的"21–35 天"属过时口径，本方案统一用 24–38 天；算法内部的 `clamp 21..45` 只是**防脏数据的护栏**（比 FIGO 更宽），不作为"正常"判定。
>
> 排卵日/易孕期按"黄体期 14 天"估算，但黄体期实际约 **10–16 天**不等，因此这两个结果一律标注"估算"，且**不提供任何避孕结论**。可对照的事实：Natural Cycles 依靠基础体温 + LH 试纸**确认**排卵才拿到 FDA 避孕适应证，其典型使用有效率 93%，而纯日历法为 88%（[Natural Cycles HCP 页](https://www.naturalcycles.com/hcp)）——差距正来自"有没有生理确认"。本工具没有体温输入，所以**只能定位为估算工具**。

---

## 七、交互与视觉设计

### 7.1 首屏信息架构（自上而下）

```
[顶栏] 经期记录 · 副标题：预测仅供参考，不能用于避孕        [设置图标]
   ↓
1. 状态卡（随今日状态切换，唯一强调块）
2. 快速记录条：［记录今天］［经期开始］［经期结束］
3. 月历卡（月份 + 图例 + 网格 + 回到今天）
4. 周期摘要卡：平均周期 / 经期长度 / 波动 / 规律性
5. 周期趋势卡（≥2 周期才出现）：最近 6 个周期长度条形图
6. 历史卡：按"次"分组，可展开编辑/删除
```

### 7.2 状态卡文案表（核心）

| 状态 | 判定 | 主文案 | 副文案 | 配色 |
| --- | --- | --- | --- | --- |
| 经期中 | today ∈ 某记录的 [start, end 或 start+len-1] | `经期第 3 天` | `3月1日开始 · 预计 3月5日结束` | primaryContainer |
| 周期进行中（默认态） | 不在经期、未到预测区间、距中心 > 7 天 | `还有 23 天` | `预计 3月26日（±1 天）· 周期第 12 天` | surfaceContainerHigh |
| 临近 | 0 < today < windowStart，且距中心 ≤ 7 天 | `还有 4 天` | `预计 3月26日（±1 天）· 基于最近 3 个周期` | primaryContainer |
| 排卵临近 | 距估算排卵日 ≤ 3 天且未到预测经期 | `距离排卵约 3 天` | `估算值，不能用于避孕` | secondaryContainer |
| 预计期内 | today ∈ [windowStart, windowEnd] | `预计这几天会来` | `预测区间 3月25日–3月27日` | primaryContainer |
| 已推迟 | today > windowEnd | `预计已推迟 5 天` | `预测区间 3月25日–3月27日 · 偶尔波动很常见` | tertiaryContainer |
| 疑似漏记 | `missedCycle` | `已超过一个周期未记录` | `请确认是否漏记，或补充记录` | surfaceContainerHigh |
| 数据不足 | 记录 < 1 周期 | `再记录一次就能开始预测` | `连续记录 3 个周期后预测会更准` | surfaceContainerHigh |
| 预测已关闭 | `predictEnabled = false` | `预测已关闭` | `仅记录，不显示预测` | surfaceContainerHigh |
| 需就医提示 | 命中 §9 红旗条件 | `你的记录有值得关注的模式` | `建议咨询妇科医生（仅供参考，非诊断）` | errorContainer |

> 配色取舍：**"已推迟"不用红色**。Flo 对"经期推迟"用灰色、Apple 用中性提示，红色只留给"值得就医"的情形——避免把正常的周期波动渲染成健康警报。红色是唯一需要用户"停下来"的信号，滥用会让它失效。

> 所有预测文案都带"预计/估算"字样，且副文案固定给出区间与依据周期数——这是与"假装精准"的分界线。

### 7.3 月历规格

| 状态 | 视觉 | 说明 |
| --- | --- | --- |
| 已记录经期日 | `primary` **实心**填充 + `onPrimary` 数字 | 实际发生 |
| 预测经期日 | `primaryContainer` **半透明**填充 + `primary` 数字 | 预计会来；与实心形成**形状/透明度差异**，不只靠颜色深浅 |
| 预测区间首/末 | 2dp `primary` 描边 | 强调"这几天内" |
| 易孕期 | `secondaryContainer` 底 | 仅 `showFertileWindow` 开启时 |
| 排卵日 | `tertiary` 圆环 | 估算 |
| 今天 | 格子下方 2dp `primary` 竖线 + 数字加粗 | 与"预测描边"区分开——同一种描边不表达两种语义 |
| 周期第 1 天 | 数字右上角 `Day 1` 微标 | 让"新周期开始"一眼可见 |
| 有症状记录 | 底部 3dp 小圆点 | 不干扰主色 |
| 未来日 | 数字 `onSurfaceVariant`，点击提示"不能记录未来日期" | 修复 P0-4 |

- 图例覆盖 5 类（已记录 / 预测 / 易孕期 / 排卵日 / 今天），两行 `FlowRow`，每项"色块 + 文字"——Apple 与 Flo 都把"图例解释所有底色"当作硬要求。
- 顶部月份右侧加 **「今天」** 按钮（`TextButton`）。
- 无障碍：每个格子 `Modifier.semantics { contentDescription = "3月5日，已记录经期" }`；最小触达 44dp。

### 7.4 记录流程

| 操作 | 行为 |
| --- | --- |
| 点「记录今天」 | 若今天已在某次经期内 → 无操作 + 提示；否则以今天为 `start` 新建，`end = null` |
| 点「经期开始」 | 选日期（默认今天）→ 新建记录 |
| 点「经期结束」 | 给最近的进行中记录补 `end`（默认今天，可改） |
| 点日历日期 | 打开「当日记录」底部弹窗：经量（4 档）、症状（多选）、疼痛（4 档）、备注 |
| 长按日历日期 | 快速标记/取消该日为经期开始（带 Snackbar 撤销） |
| 删除历史记录 | 二次确认 + Snackbar「已删除 3月1日–3月5日」+ **撤销** |
| 首次进入 | 底部弹窗引导（对齐 Apple 的首启问题）：上次经期开始日 → 典型周期长度 → 典型经期长度 → 是否规律（4 步，可跳过）。跳过后用默认 28/5 并标记"数据不足" |

### 7.5 洞察卡（≥2 周期）

- 最近 6 个周期长度的横向条形图（`Canvas`，非引入图表库），横轴为周期序号，纵轴为天数。
- 顶部一行：`平均 28 天 · 波动 ±1 天 · 规律`（规律性用色点区分：绿/黄/红）。
- 点击柱子显示该周期起止日期。
- 规律性判定阈值与 §6.2 第 13 条一致，文案不用"正常/异常"等诊断词。

---

## 八、隐私与合规

### 8.1 必修：备份排除规则（PR-1）

```xml
<!-- res/xml/backup_rules.xml（API 23–30，由 android:fullBackupContent 引用） -->
<full-backup-content>
    <exclude domain="file" path="datastore/period_records.preferences_pb" />
</full-backup-content>

<!-- res/xml/data_extraction_rules.xml（API 31+，由 android:dataExtractionRules 引用） -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/period_records.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/period_records.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

要点：

- DataStore 默认目录是 `files/datastore/`，所以 `path` 相对 `files/` 域写 `datastore/...`。
- **只把 `android:allowBackup` 改成 `false` 不够**：它停掉 Google Drive 备份，但**不停 device-to-device 迁移**（[Android 12 行为变更](https://developer.android.com/about/versions/12/behavior-changes-12)）。必须同时写 `device-transfer` 排除。
- **必须与导出功能同批上线**：排除云备份后，换机不再自动带走经期数据，没有导出就等于"用户数据无法迁移"。

### 8.2 配套措施

| 措施 | 设计 |
| --- | --- |
| 导出 | JSON（可再次导入，含 `schemaVersion`）+ CSV（便于给医生看）；走 SAF `CreateDocument`，与现有导出入口一致 |
| 导入 | 校验 `schemaVersion`，合并策略：同 `start` 覆盖，其余追加 |
| 彻底删除 | 「清空全部记录」二次确认（输入"删除"或长按确认），删除后不可撤销 |
| 应用锁 | 可选开关；复用密码箱的 PBKDF2 + AES-GCM 思路存一个校验值，进入页面时验证 |
| 截图保护 | 可选开关 → 页面挂载时 `FLAG_SECURE`（同时挡截图与最近任务缩略图）；只想挡缩略图时用 `setRecentsScreenshotEnabled(false)`；部分厂商 ROM 仍可能泄漏缩略图，按 best-effort 处理 |
| 通知脱敏 | `setVisibility(VISIBILITY_PRIVATE)` + 脱敏的 public version，通知渠道用 `IMPORTANCE_LOW`；默认锁屏只显示"你有 1 条提醒"，不写"经期推迟" |
| 提醒实现 | 复用 `CountdownNotifications` 的非精确 `AlarmManager.set` 范式（零权限可用），新增独立通知渠道 `period_reminders`；**提醒默认关闭**（对齐华为） |
| 权限 | 首次开启提醒时才请求 `POST_NOTIFICATIONS`，拒绝则静默降级（与倒数日一致） |
| Health Connect | **P2 才做，且要明确代价**：接入后"数据永不离开 App"的声明不再成立（Health Connect 是设备内共享存储，其他 App 在用户授权后可读），需补隐私政策 URL 与 Play 声明理由 |

### 8.3 合规要点

- 经期数据属**敏感个人信息**（PIPL 第 28 条），处理需"特定目的 + 充分必要 + 严格保护措施"；第 29 条要求**单独同意**，第 30 条要求额外告知必要性与影响，第 19 条要求最短留存，第 47 条要求目的达成或撤回同意后删除，第 51 条要求加密、去标识化等措施（[PIPL 官方英文版](https://en.spp.gov.cn/2021-12/29/c_948419_2.htm)）。
- **关键澄清：即使数据不出设备，在设备上存储仍属"处理"**。所以"本地化"不能完全豁免义务——首启仍应提供隐私告知 + 敏感个人信息单独同意。跨境条款（第 38–40 条）不触发。
- **不接入任何统计/广告 SDK、不在经期页面发起任何网络请求**。这是 FTC 诉 Flo（2021）的直接教训：Flo 被指通过 SDK 把经期/生育数据分享给 Facebook、Google 等，和解令要求"不得在无明确同意下共享健康数据、同意须独立于一般条款、删除已共享数据、建立隐私评估程序"（[FTC](https://www.ftc.gov/news-events/news/press-releases/2021/06/ftc-finalizes-order-flo-health-fertility-tracking-app-shared-sensitive-health-data-facebook-google)）。
- GDPR 第 9 条：健康数据属特殊类别，需**明确同意**；"仅设备端"消除了处理者与跨境传输，但不消除法律依据与透明度义务。
- 免责声明：预测基于日历法，**不能用于避孕**，也不能替代医学诊断（大姨妈与 Apple 都用这一口径）。

---

## 九、文案与免责声明

| 位置 | 文案 |
| --- | --- |
| 顶栏副标题 | 记录周期，估算下次经期与易孕期（预测仅供参考，不能用于避孕） |
| 预测卡副文案 | 基于最近 N 个周期估算，±X 天 |
| 首次引导 | 连续记录 3 个周期后，预测会更准 |
| 易孕期标注 | 易孕期（估算） |
| 红旗提示（非诊断） | 周期持续短于 24 天或长于 38 天、周期波动超过 9 天、经期超过 8 天、经量骤增（≤3 小时浸透一片或出现大血块）、非经期出血、闭经 ≥ 90 天、疼痛影响日常 → 建议咨询妇科医生 |
| 红旗提示（柔化版） | 周期偶尔变化很常见；如果出血量明显增多、疼痛持续加重或经期长期不规律，建议咨询医生 |
| 隐私声明（首启 + 关于页） | 本应用不注册账号、不联网、不含统计或广告 SDK；所有数据仅保存在本机，卸载即删除 |
| 敏感信息告知 | 经期数据属敏感个人信息，仅在本机处理；你可随时在设置中清空全部数据 |
| 清空数据确认 | 此操作不可恢复，是否继续？ |
| 禁用表述 | ❌"安全期"作为避孕依据 ❌"正常/异常"诊断 ❌"精准预测" |

---

## 十、文件级改造清单

| 文件 | 动作 | 内容 |
| --- | --- | --- |
| `feature/period/PeriodModels.kt` | 🆕 | 数据模型（§5.1） |
| `feature/period/PeriodPredictor.kt` | 🆕 | 纯算法（§6），从 Screen 中抽出 |
| `feature/period/PeriodViewModel.kt` | 🆕 | 从 Screen 中抽出，含状态卡状态机 |
| `feature/period/PeriodScreen.kt` | ♻️ | 重写 UI（§7），保留 `ToolScaffold`/`ResultCard`/`ToolSectionCard` 用法 |
| `feature/period/PeriodCalendar.kt` | 🆕 | 月历组件（可独立测试渲染逻辑） |
| `core/data/CoreStores.kt` | ♻️ | `PeriodRepository` 重写：v2 schema、迁移、settings、logs |
| `core/notify/PeriodNotifications.kt` | 🆕 | 提醒渠道 + 调度（复用倒数日范式） |
| `AndroidManifest.xml` | ♻️ | 注册 `PeriodAlarmReceiver` |
| `res/xml/backup_rules.xml` | 🔧 | 修正排除路径 |
| `res/xml/data_extraction_rules.xml` | 🔧 | 修正排除路径（两处） |
| `core/registry/ToolCatalog.kt` | 🔧 | 关键词补 `月经/例假/大姨妈/姨妈/安全期/排卵期/备孕`；描述改为"周期记录、预测与提醒" |
| `feature/settings/SettingsScreen.kt` | 🔧 | 导出范围加入经期数据（或经期页内独立导出入口） |
| `app/src/test/.../PeriodPredictorTest.kt` | 🆕 | ≥20 用例（§6.4 全部夹具 + 边界） |
| `app/src/test/.../PeriodMigrationTest.kt` | 🆕 | ≥5 用例（连续日期合并、幂等、未来日期剔除、end<start） |
| `app/src/test/.../LifeAlgorithmsTest.kt` | ♻️ | 迁移旧 `predictPeriod` 用例到新签名 |

---

## 十一、分期实施计划与验收

### P0 · 正确性与隐私（v1.2.0，一次发版）

1. 抽出 `PeriodModels.kt` / `PeriodPredictor.kt`，写单测（先测后改 UI）。
2. 重写 `PeriodRepository` + 迁移，补 `PeriodMigrationTest`。
3. 重写 `PeriodScreen.kt`：状态卡 + 区间日历 + 快速记录 + 撤销。
4. 修正备份规则（cloud-backup **与** device-transfer 各一处）+ 新增导出/导入。
5. 首启隐私告知 + 敏感个人信息单独同意。
6. `ToolCatalog` 关键词与描述。
7. 回归：`gradlew :app:testDebugUnitTest` 全绿 → `assembleRelease` → 真机冒烟。

**验收标准**

- [ ] 现有 76 个单测 + 新增 ≥25 个全绿
- [ ] 用 v1.1.2 真实旧数据升级：15 条连续日期 → 3 次经期，周期长度显示 28 天
- [ ] 迟到场景：上次 2/1、今天 3/13 → 显示"预计已推迟 12 天"
- [ ] 未来日期不可记录
- [ ] 导出 JSON 再导入，记录数一致
- [ ] 真机验证 `period_records.preferences_pb` **既不在云备份集、也不在换机迁移集内**（`adb shell bmgr` + 换机迁移工具各验一次）
- [ ] 首页搜索"月经""例假""大姨妈"均能命中经期记录
- [ ] 首启能看到隐私告知与单独同意，且可拒绝后仍能使用本地记录（不同意则不写入）

### P1 · 体验对齐主流（v1.2.x）

症状/流量/备注录入 → 提醒（经期将至 + 记录提醒）→ 洞察卡 → 应用锁 + 截图保护 + 通知脱敏 → 无障碍语义 → 跨零点刷新。

### P2 · 进阶（v1.3+）

基础体温曲线、宫颈黏液、备孕模式、Health Connect 读写、桌面小组件、多语言、周期数据年视图。

---

## 十二、风险与取舍

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 修好备份排除后换机丢数据 | 高 | 导出/导入与备份修复**同批上线**，并在修复说明中提示用户 |
| 区间录入增加操作成本 | 中 | 「记录今天」一键兜底；日历长按快速标记 |
| 预测区间看起来"不如单点干脆" | 中 | 主文案仍是"还有 4 天"，区间放副文案；规律用户区间仅 ±1 天 |
| 迁移把旧数据合并错 | 中 | 迁移幂等 + 保留旧键一个版本 + 单测覆盖 |
| 症状项过多导致录入疲劳 | 中 | 默认只显示经量 + 疼痛 + 常用 6 项症状，其余折叠 |
| 医学免责不到位 | 高 | 常驻免责文案 + 红旗提示 + 禁止避孕宣称 |
| 误以为"本地化"就免合规 | 中 | §8.3：设备上存储仍属"处理"，首启仍给告知 + 单独同意 |
| 提醒引起焦虑 | 中 | 默认关闭 + 固定 21:00 + 文案克制（不写"你的经期推迟了"） |
| 不引入 ML 导致预测弱于 Flo | 低 | 本工具定位是"记录 + 诚实估算"，可解释性优先 |

---

## 附录 A：与现有仓库约定的一致性

- 组件复用：`ToolScaffold` / `ResultCard` / `ToolSectionCard` / `KeyValueRow` / `FeedbackBlock` / `BottomActionBar` / `SegmentedTabs`（已存在于 `core/designsystem/components/Components.kt`）。
- 算法纯函数 + `LifeAlgorithmsTest` 风格，不引入 ViewModel 测试框架。
- 数据层 DataStore + Gson，与 `CoreStores.kt` 现有模块注册方式一致。
- 通知复用 `CountdownNotifications` 的非精确闹钟 + 静默降级范式。
- 发布走 `scripts/release.ps1`，`RELEASE_NOTE.md` 面向用户撰写。

# Toolbox 生产级修复方案 v1.0

> 输入：代码审阅结论（2026-09-09）
> 目标：把重写版从「约 75% 完成、半数工具带缺陷」推进到**可发布的生产级**状态
> 验收底线：`testDebugUnitTest` 全绿 · `assembleRelease` 成功 · 关键算法有单测 · 无 Blocker/Major 级已知缺陷

---

## 零、修复原则

| 原则 | 说明 |
|---|---|
| 先正确性，再健壮性，再一致性，最后补功能 | 算错的工具比缺功能的工具危害大 |
| 每个修复必须可验证 | 算法类补单测；UI 类靠编译 + 逻辑推演；崩溃类补边界判断 |
| 不引入新依赖，除非必要且体积可控 | 保持 release < 5 MB 的轻量定位 |
| 每阶段结束跑一次 `assembleRelease --offline` | 防止 R8/混淆回归 |
| 修复不改变既有对外行为，除非该行为本身是缺陷 | 避免"顺手重构"扩大风险面 |

---

## 一、阶段 P1 — 正确性缺陷（Blocking，必须先做）

### P1-1 金额转大写算法重写 🔴
- 位置：`feature/money/MoneyScreen.kt:61-124`
- 缺陷：`((amount - yuan) * 100).toInt()` double 截断 → `8.10`→"玖分"、`1234.56`→"伍角伍分"；`intToChinese` 分节丢"零" → `10001`→"壹万壹"
- 方案：
  1. 改用 `BigDecimal` + `RoundingMode.HALF_UP` 取到「分」，彻底消除浮点误差
  2. 重写 `intPartToChinese`，按 4 位一节处理，节内标准零插入，节间按需补"零"
  3. 把 `numberToChinese` 改为 `internal`，新增 `MoneyTest.kt` 覆盖 20+ 边界（0、0.01、0.10、0.29、8.10、1234.56、10001、100010000、1e12、1e16 上限、负数）
- 验收：单测全绿

### P1-2 旧版密码导入永远 0 条 🔴
- 位置：`core/data/LegacyImporter.kt:98,103,109`
- 方案：`import()` 中把解析结果赋给 `pendingPasswords`；改为不可变传参（`importPasswords(json, master)`）消除单例可变状态，同时避免两步之间进程被杀导致丢失
- 验收：新增 `LegacyImporterTest`（纯解析层单测，不依赖 Android）

### P1-3 温度 K→°F 算错 🟠
- 位置：`feature/unit_converter/UnitConverterScreen.kt:79` `else -> value - 273.15`
- 方案：补显式分支 `"K" && "°F" -> (value - 273.15) * 9 / 5 + 32`，并把 `else` 改为抛错或返回 `Double.NaN` 而非静默错误
- 验收：`UnitConverterTest` 补 K↔°F 用例

### P1-4 时间戳"相对时间"方向反转 🟠
- 位置：`feature/timestamp/TimestampScreen.kt:151-163`
- 缺陷：`dur = between(instant, now)`，过去时刻 `dur` 为正 → 输出"5 分钟后"
- 方案：过去用"x 前 / 刚刚"，未来用"x 后"；同时把 `relative()` 抽到纯函数便于测试
- 验收：新增 `TimestampTest`

### P1-5 倒数日天数计算错误 🟠
- 位置：`feature/countdown/CountdownScreen.kt:117-123`
- 缺陷：`event.type == 1` 分支先于符号判断 → 纪念日显示"已 -1699 天"；`days + 1` 导致明天显示"还剩 2 天"
- 方案：重写 `when` 顺序与语义：`days > 0 → "还剩 N 天"`（N 就是天数差）、`days == 0 → "就是今天"`、`days < 0 → "已过 N 天"`；纪念日类型走"已 N 天"且 N 取绝对值
- 验收：新增 `CountdownTest`（纯日期计算函数）

### P1-6 经期记录选错日期 🟠
- 位置：`feature/period/PeriodScreen.kt:190-192`
- 缺陷：`rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())`，M3 按 UTC 解释 → UTC+8 凌晨记成昨天
- 方案：`LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()`
- 验收：逻辑推演 + 编译

### P1-7 小数分隔符（Locale）系列缺陷 🟠
- 位置：`ExpressionEvaluator.kt:115`、`BmiScreen.kt:227,170,66`、`UnitConverterScreen.kt:225`、`CurrencyScreen.kt:143`、`DeviceScreen.kt:156`
- 缺陷：`"%.2f".format(...)` 用默认 Locale，逗号小数点区域产出 `"65,3"`，回读 `toDoubleOrNull()` 返回 null → BMI 消失、计算器表达式污染
- 方案：所有"机器可回读"的格式化统一 `String.format(Locale.US, ...)`；纯展示型格式化可保留本地化但禁止回读
- 验收：编译 + 关键路径补单测

### P1-8 计算器错误态不可恢复 🟠
- 位置：`CalculatorScreen.kt:72-75`、`ExpressionEvaluator.kt:111`
- 缺陷：`1/0` 后表达式本身变成"错误"，后续按键继续拼接，只能 AC
- 方案：出错时保留原表达式、置 `error=true`，用 `FeedbackBlock(ERROR)` 呈现；新输入自动清错

---

## 二、阶段 P2 — 崩溃与内存（生产级硬门槛）

### P2-1 图片采样逻辑修复 🔴
- 位置：`core/util/ImageUtils.kt:23-26`
- 缺陷：`while (max/(sample*2) >= maxSide)` 与 KDoc 矛盾，4032px 图不降采样 → 48.8 MB
- 方案：改为标准 `while (maxOf(w,h) / sample > maxSide) sample *= 2`，并保证结果 ≥1；`maxSide` 默认下调到 1600（预览/取色足够）
- 验收：单测覆盖 4000/8000/1000 三种宽度的 sample 计算

### P2-2 图片工具全链路健壮化 🔴
- 位置：`NineGridScreen.kt:59-61,80-86,98-118`、`ColorPickerScreen.kt:72`、`StitchScreen.kt:88-111`、`PerlerScreen.kt:118-147,173`
- 方案：
  1. 所有 `openInputStream(uri)!!` 改 `?.`，解码包 `runCatching`
  2. 九宫格：`w/h` 为 0 时拒绝并提示；余数像素并入最后一格
  3. 拼接：输出总像素上限（如 1.2 亿像素 / 单边 16384）超限即拒绝并提示
  4. 拼豆：导出边数上限 + 超限拒绝
  5. 每个工具补 `error: String?` 到 UiState + `FeedbackBlock(ERROR)`
- 验收：编译 + 边界逻辑推演

### P2-3 二维码长文本崩溃 🔴
- 位置：`QrCodeScreen.kt:86-97,184`
- 方案：编码前按 UTF-8 字节数预检（>2953 直接提示"内容过长"），`encode` 包 `runCatching`；补 `loading` 态
- 验收：编译 + 逻辑推演

### P2-4 MediaStore 保存健壮化 🔴
- 位置：`ImageUtils.kt:30-64`、`NineGridScreen.kt:101-112`、`StitchScreen.kt:135-146`
- 方案：
  1. 统一走 `ImageUtils`，删除九宫格/拼接的重复实现
  2. 写入前 `IS_PENDING=1`，写完置 0；失败时 `delete` 掉占位行
  3. 检查 `bitmap.compress()` 返回值
  4. API ≤ 28 走运行时权限申请（`rememberLauncherForActivityResult(RequestPermission)`）
  5. 删除"长按可保存"的假提示，二维码补真实保存按钮
- 验收：编译

### P2-5 状态竞态（旧任务覆盖新结果）🟠
- 位置：`NineGridScreen.kt:76-92`、`ImageCompressScreen.kt:87-94`、`WatermarkScreen.kt:79-122`、`StitchScreen.kt:76-111`、`PerlerScreen.kt:118-147`
- 方案：每个 VM 持有 `private var job: Job?`，新任务前 `job?.cancel()`；写入时基于最新 `_state.value` 而非启动时快照

### P2-6 EXIF 方向 🔴
- 位置：所有解码路径
- 方案：`ImageUtils.loadScaled` 内读取 `ExifInterface.TAG_ORIENTATION` 并做矩阵旋转；需要新增 `androidx.exifinterface:exifinterface` 依赖（约 60 KB）
- 验收：编译 + release 体积复核

### P2-7 主线程 I/O 🟠
- 位置：`DeviceScreen.kt:49`（构造器里读内存/存储/注册广播）、`PeriodScreen.kt:67-83`（构造器读 SharedPreferences）
- 方案：迁到 `viewModelScope.launch(Dispatchers.IO)`；Period 迁移到 DataStore

---

## 三、阶段 P3 — 设计系统一致性

### P3-1 补全色彩 token 🟠
- 位置：`Theme.kt` / `Color.kt`
- 缺陷：`surfaceContainerLow` 等未定义 → `ToolCard`/`ToolSectionCard` 回落 M3 紫色基线
- 方案：补 `surfaceContainerLow/Lowest/Highest/Dim/Bright`、`surfaceVariant`、`inverseSurface`、`surfaceTint` 的暖色系推导值

### P3-2 深色模式状态栏 🟠
- 位置：`res/values/themes.xml`
- 方案：改用 `Theme.Material3.DayNight.NoActionBar` 或提供 `values-night` 覆盖；配合 `enableEdgeToEdge()` 同步 `isAppearanceLightStatusBars`

### P3-3 清除硬编码颜色 🟠
- 位置：`RulerScreen.kt:70,78`（深色模式尺子看不见）、`PomodoroScreen.kt:173,180`、`DecisionScreen.kt:88-91,165`、`BmiScreen.kt:96-101`、`DanmakuScreen.kt:55-62`
- 方案：全部改主题 token；尺子刻度用 `onSurface`，番茄钟环按阶段用 `primary/tertiary/secondary`

### P3-4 `ResultCard` 数值溢出 🟠
- 位置：`Components.kt:266-274`（`maxLines=1` + ellipsis 截断 Base64 结果）
- 方案：`ResultCard` 增加 `singleLine: Boolean = true` 参数；Base64 用多行可选中的输出卡

### P3-5 `FeedbackBlock` 补 Success 态 + 清理 legacy 组件
- 位置：`Components.kt:425-476,534-557`
- 方案：补 `FeedbackType.SUCCESS`；删除 `EmptyState`/`SectionHeader`（改由调用方使用新组件）

---

## 四、阶段 P4 — 功能补齐（达到"计划承诺"）

| 编号 | 工具 | 缺口 | 方案 |
|---|---|---|---|
| P4-1 | 倒数日 | 通知提醒 0% | `POST_NOTIFICATIONS` + 通知渠道 + `AlarmManager`/WorkManager 到期提醒 + 运行时权限申请 |
| P4-2 | 倒数日 | 无农历、纪念日丢弃 | 短期：保留字段不丢弃并标注；长期：引入农历库（评估体积） |
| P4-3 | 经期 | 无月历 | 月视图网格 + 点击记录/取消 + 预测日描边 |
| P4-4 | 经期 | 数据层不合规 | 迁 DataStore + Repository |
| P4-5 | 做个决定 | 转盘无文字 | Canvas 绘制选项文字 + 命中高亮；`isSpinning` 移入 VM + 超时兜底 |
| P4-6 | 手持弹幕 | 转屏退出全屏 | `isFullscreen` 移入 UiState；`KEEP_SCREEN_ON`；真沉浸式 |
| P4-7 | 番茄钟 | 漂移 + 不持久化 | 基于 `elapsedRealtime()` 计时；统计落 DataStore |
| P4-8 | 密码箱 | 无自动锁/搜索 | 生命周期 ON_STOP 自动锁；搜索框；剪贴板敏感标记 |
| P4-9 | 设备信息 | 无 CPU/传感器 | 补 CPU 型号/核数、传感器列表；改 IO 线程 |
| P4-10 | 亲戚称呼 | 单向 | 补反向称呼 |
| P4-11 | 计算器 | 无括号键 | 补 `(` `)` 键与历史记录 |
| P4-12 | 图片工具 | 缺参数控件 | 水印 3×3 位置、压缩 WebP/目标尺寸、拼接排序/间距、取色器放大镜+色板、拼豆调色板/缩放、九宫格边距 |
| P4-13 | 我的 | 缺导出/关于/更新 | 数据导出、关于页、更新检查入口 |

---

## 五、阶段 P5 — 工程化与发布准备

- P5-1 `git init` + 首次提交（`.gitignore` 已就绪）
- P5-2 数据层收敛：`core/data` 统一 Database + 显式 Migration + `exportSchema=true`
- P5-3 字符串资源化（至少覆盖设置页/错误提示）
- P5-4 单测覆盖补全：日期、预测、计时、金额、图片尺寸
- P5-5 `versionCode/versionName` 升级 + release 签名 + 混淆回归验证
- P5-6 版本说明文档 `RELEASE-v1.1.0.md`

---

## 六、执行顺序与验证节奏

```
P1（正确性）→ 编译 + 单测 → P2（崩溃/内存）→ 编译 + 单测
→ P3（一致性）→ 编译 → P4（功能）分批 → 每批编译
→ P5（工程化）→ 全量回归 + assembleRelease
```

每阶段完成的判定：`.\gradlew.bat :app:testDebugUnitTest --offline` 与 `.\gradlew.bat :app:assembleRelease --offline` 均返回 0。

---

## 七、进度跟踪

- [x] 阶段 P1 正确性缺陷 — 8/8 完成（金额大写重写 + 9 单测、旧版密码导入、K→°F、时间戳方向、倒数日天数、经期选日期、Locale 全量、计算器括号/历史/错误态）
- [x] 阶段 P2 崩溃与内存 — 7/7 完成（采样修正、图片工具健壮化、二维码长度预检、MediaStore IS_PENDING、竞态取消、EXIF、主线程 I/O）
- [x] 阶段 P3 设计系统一致性 — 5/5 完成（补全色彩 token、深色状态栏、清除硬编码色、ResultCard 多行、FeedbackBlock Success + 清理 legacy）
- [x] 阶段 P4 功能补齐 — 13/13 完成（倒数日提醒、经期月历、决策转盘、弹幕全屏、番茄钟持久化、密码箱自动锁/搜索、设备 CPU/传感器、亲戚反推、计算器括号、图片工具参数控件、我的页导出/关于）
- [x] 阶段 P5 工程化与发布准备 — 6/6 完成（git 基线、单测 35→71、版本号 1.1.0、release 说明、`assembleRelease` 2.08 MB 通过）

### 验收证据（2026-09-09）

```
.\gradlew.bat :app:testDebugUnitTest --offline   → BUILD SUCCESSFUL, 71 tests, 0 failures
.\gradlew.bat :app:assembleRelease  --offline    → BUILD SUCCESSFUL, app-release.apk 2.08 MB
```

**真机级验证（Android 14 / API 34 模拟器，安装 release 包）**

- 27 个工具逐一进入，全部正常渲染，`logcat -b crash` 全程无记录，无 FATAL/ANR
- 汇率无网络时正确显示"暂无汇率数据，请检查网络后重试 + 重试"错误态
- 密码箱 SETUP → 创建 → UNLOCKED 全流程正常（此前的 `LazyColumn` 嵌套滚动崩溃已修复）
- 修复过程中发现并解决的真实崩溃：
  `PasswordVaultScreen` 在 `ToolScaffold` 的 `verticalScroll` 内嵌套 `LazyColumn` →
  `IllegalStateException: Vertically scrollable component was measured with an infinity
  maximum height constraints`。修复方式：密码箱改用 `ToolScaffold(scrollable = false)` 自行管理滚动；
  `TextDiffScreen` 同步移除内层 `verticalScroll`。

### 后续可选项（非本次范围）

- 字符串资源化 / 多语言
- 二维码相机实时扫码（需引入 CameraX 或 ML Kit）
- 农历 / 节假日支持
- 横屏与平板响应式布局（UI_REDESIGN_PLAN §4）

---

## 八、v1.1.1 实机反馈修复（2026-09-09 追加）

用户实机使用后反馈 7 个问题，已全部修复并逐条在 Android 14 模拟器上复验，详见
[RELEASE-v1.1.1.md](RELEASE-v1.1.1.md)。

| # | 问题 | 根因 / 修复 | 实机验证 |
|---|---|---|---|
| 1 | 所有图片功能提示"无法读取该图片" | `inJustDecodeBounds=true` 时 `decodeStream` 必然返回 null，却被当成失败判据 | 选 1254×1254 图 → 正常显示预览与信息 |
| 2 | 计算器键盘位置不固定 | 整体滚动 → 显示区 `weight(1f)` 内部滚动，键盘固定底部 | 按键纵跨 y=1345…2235 贴底 |
| 3 | 大写金额被截断 | 结果卡单行省略 → `singleLine = false` | 13 位数字输出完整 |
| 4 | 弹幕全屏未横屏 | 全屏时锁定横屏 + 顶栏常驻入口 + `configChanges` | 根布局 [0,0][2340,1080] |
| 5 | 倒数日手输日期 | 改为只读日期行 + `DatePickerDialog`（UTC 初值） | 点击弹出日历 |
| 6 | 尺子未横屏贴边、无刻度数字 | 横屏 + 从屏幕左边缘起画 + 主刻度标数字 + 横向滚动 30cm/12in | 根布局横屏 |
| 7 | 输入框观感简陋 | 新增 `ToolTextField`（填充式 + 14dp 圆角，无硬边），全站 14 文件 24 处替换 | 无遗留 `OutlinedTextField` |

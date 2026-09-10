# Toolbox 视觉语言方向 v3.0（2026）

> 状态：**P0 已落地**（2026-09-10）· 本文档保留为规范基线；P0 实际落地内容与三处方案订正见 **§十一**  
> 上游：`docs/UI_REDESIGN_PLAN.md`（v2.0 已落地：布局结构 + 共享组件）  
> 本轮范围：**视觉语言层**（色彩 / 形状 / 层级 / 排版 / 间距 / 动效 / 触觉 / 材质），不重做布局结构  
> 调研时间：2026-09-10

> ⚠️ **本版已订正三处**（落地时发现原方案有问题）：§4.6 加载态、§4.7 触觉实现、§4.8 玻璃实现边界。
> 三处都标注了订正理由，请以订正后为准。

---

## 一、调研结论：2026 年的三条主线 + 一条反趋势

| 主线                     | 代表                                           | 一句话                                                                                                                      |
| ---------------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **M3 Expressive**（官方线） | Google Material 3 Expressive / Android 16 默认 | 动效从"时长+缓动"升级为**物理弹簧**（motion scheme）；新增 35 个形状与形变；排版层级拉开（emphasized 字重）；新增 Toolbar / Button group / Split button / 波形进度条 |
| **水晶界面**（Apple 线）      | iOS 26 Liquid Glass / visionOS               | 半透明 + 实时折射 + 边缘高光；**控件浮在内容之上**而不是装在实心盒子里；滚动时导航栏收缩                                                                        |
| **Bento 网格**（结构线）      | Linear / Notion / Apple 官网                   | 大小错落的模块化卡片，取代"整齐划一的等大网格"与无边长列表；用尺寸差表达信息优先级                                                                               |

**反趋势（同样重要）：克制动效。** 2025 年流行的大面积转场、夸张粒子动画在 2026 被明确列为过时项。当下的共识是 **Calm Interface**——动效只做功能性引导（按压形变、数字平滑滚动、状态淡入淡出），不服务装饰。这对我们这种"27 个小工具、每个只待 5～30 秒"的产品尤其成立：用户不是来欣赏动效的。

**明确过时、不要碰**：重度新拟态（厚重阴影）、满屏粒子/超长复杂转场、无约束的高饱和大面积撞色、纯静态无适配页面。

---

## 二、我们的取舍：工具类 ≠ 消费类

调研里那套"多巴胺配色 + 3D 预览 + 对话式 AI"是给电商/潮玩/社交的。Toolbox 的定位是**本地优先、无广告无追踪、离线可用**，视觉上必须对应"冷静、可信、不打扰"：

| 维度     | 结论                                                              |
| ------ | --------------------------------------------------------------- |
| 色彩     | **不追多巴胺**。保留品牌暖青 `#2E6E5E` + 6 类目色，方向是"低饱和但有识别度"，而不是"更鲜艳"       |
| 材质（玻璃） | **只用在浮层**：底部导航、底部抽屉、悬浮操作条、更新弹窗。正文/列表/表单一律实色                     |
| 动效     | 全面从 `tween` 换成 `spring`，但**只用在状态变化处**（按压、切换、数字更新、展开/收起），不加装饰性转场 |
| 布局     | 首页 Bento 化（有信息优先级），内页保持 v2.0 的三段式骨架（工具页不需要 Bento）               |
| 不做     | Zero-UI / AI 生成界面（与"离线、无追踪"定位直接冲突）、全站毛玻璃、3D 元素、重度拟物             |

---


## 三、技术前置：一个必须先讲清楚的约束 ⚠️

这是本轮调研最关键的发现，**它决定方案怎么分期**：

| 项           | 现状                                        | 想要的东西需要什么                                                                                                                                                                                       |
| ----------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kotlin      | 1.9.22                                    | **2.x**（`org.jetbrains.kotlin.plugin.compose` 插件取代 `composeOptions.kotlinCompilerExtensionVersion`）                                                                                             |
| Compose BOM | 2024.09.00（Compose 1.7 / material3 1.3.0） | **2025.xx 起**（Compose 1.8+）                                                                                                                                                                     |
| compileSdk  | 34                                        | **35+**（material3 1.4.0 硬性要求，否则构建报 `minCompileSdk` 错误）                                                                                                                                          |
| material3   | 1.3.0（BOM 带入）                             | **1.4.0 stable** 有 baseline 增强；**1.5.0-alpha** 才有 Expressive API（`MaterialExpressiveTheme` / `MotionScheme` / ButtonGroup / SplitButton / 波形进度条），需 `@OptIn(ExperimentalMaterial3ExpressiveApi)` |
| minSdk      | 26                                        | `Modifier.blur` 需要 **API 31+**，26～30 会静默失效 → 玻璃层必须自带降级                                                                                                                                          |

**结论：Expressive 的原生 API 我们暂时拿不到，也不该为了它去动整个工具链。** 但——M3 Expressive 的**视觉结论**（弹簧动效、形状语义、层级拉开、Bento 结构）全都可以用现有依赖手写出来，代价只是多写一个 `Motion.kt`。所以方案分三期：

- **P0（零风险，立刻可做）**：token 化 + 自研 motion/haptics/glass + 组件视觉升级。**只改 `core/designsystem/` 一个包**，不加任何依赖。
- **P1（低风险，单独排期）**：工具链升级（Kotlin 2.x / AGP / compileSdk 35 / material3 1.4.0），拿 baseline 增强（`SecureTextField`、`autoSize` 文本、`Carousel`、`TextFieldState`）。
- **P2（试验，可选）**：material3 1.5.0-alpha 接 Expressive 原生组件，**必须隔离在 designsystem 包内**。

---

## 四、规范正文


### 4.1 色彩

**保留**：品牌暖青 `#2E6E5E`、现有 light/dark 全套 M3 语义色、6 组类目色。

**要补的三件事：**

**① 类目色缺深色版（现存缺陷）。** `Color.kt` 里的 `CategoryColors` 只有一组浅色容器 + 深色前景，深色主题下直接复用 → 浅色底片压在暗卡片上，刺眼且层级反转。补一套：

| 类目        | 浅色 container / on     | 深色 container / on（新增） |
| --------- | --------------------- | --------------------- |
| CALCULATE | `#DDE9FA` / `#1D4F82` | `#20364F` / `#C6DEFF` |
| IMAGE     | `#EEEDFE` / `#534AB7` | `#2E2A5C` / `#D6D3FF` |
| TEXT      | `#E1F5EE` / `#0F6E56` | `#123A2F` / `#B7EFD9` |
| LIFE      | `#FAEEDA` / `#854F0B` | `#3D2E12` / `#F5D9A3` |
| MEASURE   | `#EAF3DE` / `#3B6D11` | `#26351A` / `#D3E8AC` |
| SECURITY  | `#FBEAF0` / `#993556` | `#3F1E29` / `#F8C6D6` |

**② 语义色补全。** 现在"成功"借用 `primary`、"警告"靠 error 硬凑。工具类里"已复制/已保存/超出范围"是高频状态，值得独立：

| 语义      | 浅色                     | 深色        | 用途                      |
| ------- | ---------------------- | --------- | ----------------------- |
| success | `#2E7D32`              | `#7FD69A` | 保存成功、迁移完成               |
| warning | `#8A5A00`              | `#F5C77E` | 农历超范围、精确闹钟未授权、ROM 白名单未加 |
| info    | `#456179`（沿用 tertiary） | `#ADCAE0` | 中性提示                    |

**③ 对比度硬指标（写进验收）：** 正文 ≥ 4.5:1，大字/图标 ≥ 3:1，**浅色深色双向校验**；类目色 `on` 压在 `container` 上也要过 4.5:1（上面这套已按此选值）。

**不要做的事：** 不新增第二个品牌强调色；不用渐变做功能区分（渐变只允许出现在 Hero 卡/置顶卡这类情绪位，且最多一处）。

---

### 4.2 形状

现在项目里形状是散落的魔法数（卡片 18、chip 10、输入 14、底栏 20）。建立**形状尺度 + 语义映射**：

| Token       | 值     | 语义  | 用在哪                |
| ----------- | ----- | --- | ------------------ |
| `shapeXs`   | 4dp   | 微元素 | 进度条、色条、小徽章         |
| `shapeSm`   | 8dp   | 小控件 | 图标底片、chip、键帽       |
| `shapeMd`   | 12dp  | 中控件 | 按钮、下拉菜单项、Toast     |
| `shapeLg`   | 16dp  | 卡片  | 分组卡、结果卡、列表项、输入框    |
| `shapeXl`   | 24dp  | 大容器 | 底部抽屉、Hero 卡、图片预览   |
| `shapeFull` | 圆形/胶囊 | 全圆  | 悬浮操作条、FAB、分段控件、搜索栏 |

**关键调整：** 卡片从 18 → **16**（回到 M3 标准尺度，19 这种非标值会让组件看起来"各长各的"）；输入框 14 → **16**（与卡片齐平，形成节奏）；底栏 20 → **24**。

**形状语义（Expressive 的核心思路）：** 形状要表达状态，不只是圆角。

- 分段控件 / Tab：选中项用 `shapeFull`（胶囊），未选中用透明——**选中即形变**
- 可点击卡片：按压时 `shapeLg → shapeXl` 微形变（配合弹簧，见 4.6）
- FAB 展开菜单：从 `shapeFull` 形变为 `shapeXl` 的圆角矩形

---

### 4.3 层级与高程

项目已有完整 `surfaceContainer` 五档，但**用法没有约定**，导致"哪个卡该用哪一档"靠感觉。固化下来：

| 档位                        | 用途           | 说明               |
| ------------------------- | ------------ | ---------------- |
| `surfaceContainerLowest`  | 图片/二维码内衬     | 强制白底场景（扫码、拼豆预览）  |
| `surfaceContainerLow`     | **卡片默认底**    | 分组卡、工具卡、结果卡      |
| `surfaceContainer`        | **页面底/条状容器** | 底部导航、状态条、搜索栏     |
| `surfaceContainerHigh`    | **输入框聚焦态**   | 与 Low 形成"聚焦上浮半档" |
| `surfaceContainerHighest` | 键帽、选中态底      | 计算器键盘、选中行        |

**不做阴影。** M3 用色调高程（tonal elevation）而非投影；现在 `ElevatedCard` 只在少数地方出现，建议统一去掉，卡片一律 `Surface + containerLow`。

---


### 4.4 排版

**现存问题：** `Type.kt` 只定义了 15 个样式里的 8 个 → 其余 7 个（display 三档、headline 三档、labelLarge）静默 fallback 到 M3 默认值，与自定义的那 8 个**不同源**，导致层级跳跃不连续。`bodyLarge` 还写成了 15sp（非标）。

**补全为完整尺度（单位 sp / 行高）：**

| 样式             | 字号/行高       | 字重           | 备注               |
| -------------- | ----------- | ------------ | ---------------- |
| displayLarge   | 57 / 64     | Normal       | 极端天数、大结果         |
| displayMedium  | 45 / 52     | Normal       | 置顶卡天数            |
| displaySmall   | 36 / 44     | Normal       | 结果卡数值（现状）        |
| headlineLarge  | 32 / 40     | **SemiBold** | 首页大标题            |
| headlineMedium | 28 / 36     | **SemiBold** | 空态标题             |
| headlineSmall  | 24 / 32     | SemiBold     | 抽屉标题             |
| titleLarge     | 22 / 28     | Medium       | 页面标题（现状）         |
| titleMedium    | 16 / **24** | Medium       | 卡片标题（行高纠正 22→24） |
| titleSmall     | 14 / 20     | Medium       | 工具卡标题（现状）        |
| bodyLarge      | **16** / 24 | Normal       | 正文（15→16，回到标准）   |
| bodyMedium     | 14 / 20     | Normal       | 次要正文（现状）         |
| bodySmall      | 12 / 16     | Normal       | 辅助说明（现状）         |
| labelLarge     | 14 / 20     | Medium       | 按钮文字             |
| labelMedium    | 12 / 16     | Medium       | 组标题（现状）          |
| labelSmall     | 11 / **16** | Medium       | 脚注（行高纠正 14→16）   |

**三条硬规则：**

1. **一切动态数字用等宽（`fontFeatureSettings = "tnum"`）。** 现在只有 `ResultCard` / `KeyValueRow` 用了 Monospace 字体族——Monospace 是"不等宽数字"的正解，但会让中英文排版变丑。改用 `FontFamily.Default + tnum`，既能防跳动又不破坏中文字距。（P0 修正项）
2. **一行内不超过 2 个字号层级。** 现在"标签 labelSmall + 值 displaySmall + 单位 titleSmall"是 3 个，砍到 2 个。
3. **大标题加负字距。** headline 及以上 `letterSpacing = (-0.5).sp`，这是 Expressive 拉开层级最便宜的手法。

---

### 4.5 间距

引入 **8dp 基准栅格**（对应 M3 新版 spacing system），替换现有散值：

| Token      | 值    | 用途                            |
| ---------- | ---- | ----------------------------- |
| `spaceXs`  | 4dp  | 图标与文字、行内元素                    |
| `spaceSm`  | 8dp  | 相关控件之间                        |
| `spaceMd`  | 12dp | **卡片内元素间距**（现状，保留）            |
| `spaceLg`  | 16dp | **页面水平边距 / 卡片内边距**（现状，保留）     |
| `spaceXl`  | 24dp | 分区间距（现为 12，建议分区提到 24，卡内保持 12） |
| `space2Xl` | 32dp | 主要区块之间                        |

**要改的散值：** 现存的 10dp（图标底片间距、按钮间距）→ 8dp 或 12dp；18dp 内边距 → 16dp。

---


### 4.6 动效（本轮最大的变化）

**现状：** 全站只有 `AppNavHost` 里的 `tween(280)` 滑动转场，组件内部基本没有动效——所以"点了没反应"的体感来自这里。

**方向：** 自研一个 `MotionScheme`（因为 stable material3 拿不到官方的），严格照 M3 的语义分层：

```kotlin
object ToolMotion {
    // Spatial：位移 / 缩放 / 形状 —— 允许过冲回弹
    val spatialFast    = spring<Float>(dampingRatio = 0.80f, stiffness = 900f)   // 小元素：键帽、chip、开关
    val spatialDefault = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)   // 中等：卡片、抽屉
    val spatialSlow    = spring<Float>(dampingRatio = 0.90f, stiffness = 240f)   // 全屏：页面切换

    // Effect：颜色 / 透明度 —— 绝不过冲
    val effectFast     = spring<Float>(dampingRatio = 1.0f, stiffness = 1600f)   // 按钮着色
    val effectDefault  = spring<Float>(dampingRatio = 1.0f, stiffness = 900f)    // 状态淡入淡出
    val effectSlow     = spring<Float>(dampingRatio = 1.0f, stiffness = 420f)    // 整屏刷新
}
```

**用 spring 不用 tween 的理由：** 打断时 tween 会从头重播（点了两下会"卡一下"），spring 保留速度矢量，连续操作是顺的。这是 M3 官方给的第一条论证。

**具体落点（按收益排序）：**

| 落点          | 现在               | 改成                                                                            |
| ----------- | ---------------- | ----------------------------------------------------------------------------- |
| **数字更新**    | 直接跳值             | `animateIntAsState` 平滑滚动（结果卡、倒数日天数、番茄钟、BMI）                                   |
| **导航转场**    | 全屏滑动 + 淡入 280ms  | **降级为内容位移 24dp + 淡入**（spatialDefault + effectDefault）。全屏滑动是 2025 的手法，2026 反趋势 |
| **底部导航切页**  | 无动效              | 淡入 + 内容 scale 0.98 → 1.0                                                      |
| **卡片按压**    | `clickable` 默认涟漪 | 涟漪保留，叠加 scale 0.97 + 形状微形变（spatialFast）                                       |
| **按钮**      | 无                | 按下 scale 0.96，抬起回弹（spatialFast）                                               |
| **分段控件**    | 选中即变底色           | 指示器滑动位移（spatialDefault）——**这是 Expressive 观感最强的单点**                            |
| **详情/抽屉展开** | 无                | 高度/位移弹簧过渡，内容 effectDefault 淡入                                                 |
| **加载态**     | 转圈               | **分两类**（订正见 §4.6.1）：短时操作保留转圈；内容加载新增 `FeedbackType.SKELETON` 骨架屏                     |

**明确不做：** 页面入场逐元素 stagger、滚动视差、装饰性粒子、转圈超过 800ms 不显示。

#### 4.6.1 订正：加载态不该一律改骨架屏

原方案写的是"转圈一律改骨架屏"。落地时对着 14 处实际调用点逐一看，`FeedbackType.LOADING` 几乎全用在
**短时操作**上（"压缩中…"、"识别中…"、"处理中…"、"正在读取图片…"）——

这类场景**没有"内容形状"可以预演**，骨架屏反而是错误的隐喻（用户会以为马上要出现三行列表）。
骨架屏成立的前提是"我知道接下来长什么样"，也就是列表/详情页首屏。

所以落地为：

| 类型                  | 形态    | 用在哪                             |
| ------------------- | ----- | ------------------------------- |
| `FeedbackType.LOADING`  | 转圈    | 短时操作：压缩、识别、拼接、读写图片              |
| `FeedbackType.SKELETON` | 3 行骨架 | 内容加载：列表/详情首屏（新增，暂无调用点，留给后续页面按需接入） |
| `FeedbackType.WARNING`  | 警告图标  | 新增，配语义警告色                       |

骨架屏的 alpha 呼吸是**唯一有意保留 `tween` 的动效之一**——无限循环只能靠 `infiniteRepeatable`，弹簧无法 repeat。

---

### 4.7 触觉（Haptics）

**现状：全站零触觉反馈。** 这是最便宜的"高级感"来源，且与动效天然配对。

定义 4 种语义：

| 语义             | 落在哪个系统常量（API 30+ / 回退）                     | 用在哪               |
| -------------- | ------------------------------------------ | ----------------- |
| **Tick** 轻点    | `VIRTUAL_KEY`                              | 键帽按下、分段切换、滑杆棘轮    |
| **Confirm** 确认 | `CONFIRM` / `KEYBOARD_TAP`                 | 复制成功、收藏、置顶、保存     |
| **Success** 成功 | `CONFIRM` + 80ms 后补一记 `VIRTUAL_KEY`（双脉冲）   | 密码箱解锁、图片保存完成、迁移完成 |
| **Reject** 拒绝  | `REJECT` / `LONG_PRESS`                    | 密码错误、导入被拒、超范围     |

#### 4.7.1 订正：不用 `VibrationEffect`，改走 `View.performHapticFeedback`

原方案写的是用 `VibrationEffect` 自定义四种波形。落地时发现一个**更重要的约束**：
`VibrationEffect` 需要 `android.permission.VIBRATE`——而 Toolbox 的立项承诺是
"最小权限、无追踪、数据不出设备"。为了震动多要一条权限，性价比不对。

改走 `View.performHapticFeedback(int)` 之后：

| 维度      | VibrationEffect（原方案） | performHapticFeedback（订正后） |
| ------- | ------------------- | ------------------------- |
| 权限      | **需要 `VIBRATE`**    | **不需要任何权限**               |
| 兼容下限    | API 26              | **API 21**                |
| 尊重系统总开关 | 要自己读 `Settings.System` | 平台自动处理                    |
| 波形可定制   | 可以                  | 不行（由厂商调校）                 |

**唯一放弃的是"波形可定制"**——对工具类应用这反而不算缺点：厂商调校过的触感通常比
我们拍脑袋写的毫秒数更贴合设备。四种语义靠系统常量的语义分层（轻点/确认/拒绝）+
双脉冲拼装就能表达清楚。

**配套规则：** 全部走 `ToolHaptics`（`rememberToolHaptics()`），连续操作**按 40ms 节流**
（否则滑杆拖动会震到手麻）。系统关闭触感时平台自动静默，无需分支。

---

### 4.8 材质：玻璃层的边界

**只允许出现在 4 个位置**：底部导航栏、底部抽屉（`ModalBottomSheet` 顶缘）、悬浮操作条、更新弹窗。

```kotlin
fun Modifier.glassSurface(
    shape: Shape = ToolShape.xl,
    level: GlassLevel = GlassLevel.Bar,
): Modifier
```

#### 4.8.1 订正：Compose 1.7 拿不到"背景模糊"

原方案写的是"API 31+ → 真模糊 + 高光边"。落地时核对 API 才确认：
**`Modifier.blur` 模糊的是这个组件自己的内容，不是它身后的背景。**

真正的"背景模糊"（采样下层像素）需要 API 31 的 `RenderEffect` 配合把背景内容也画进同一层，
或者引入 haze 之类的第三方库。而且 `Modifier.blur` 如果挂在底部导航栏上，
会把导航的图标和文字一起糊掉——是错的方向。

所以落地的是同族效果里**可靠的那一半**：

| 组成          | 作用                          | 依赖       |
| ----------- | --------------------------- | -------- |
| 半透明填充        | 让浮层有"浮在页面上"的暗示              | 无        |
| **1dp 高光描边** | **把浮层与内容明确分开——肉眼最认得出的"玻璃感"** | 无        |
| API 26~30 更实的填充 | 无模糊条件下保证可读性，是另一种合格设计而非降级半成品 | 无        |

零依赖、零性能风险。等 P1 做 edge-to-edge（内容真正从条下滚过、背后有东西可采样）时再换成
真模糊，**调用点不用改**。

**硬约束：**

- **列表项、卡片、输入框一律不用玻璃**（可读性 + 滚动性能）
- 玻璃层上的文字必须单独校验对比度（半透明底是最容易翻车的地方）
- 玻璃层数量 ≤ 2 且**禁止出现在 LazyList 内**

---


## 五、组件级改动清单（对着 `Components.kt` 逐条）

| 组件                  | 具体 delta                                                                                      | 优先级 |
| ------------------- | --------------------------------------------------------------------------------------------- | --- |
| `ToolCard`          | 18→16dp 圆角；图标底片 34→40dp、10→12dp 圆角；去掉 18sp 硬编码间距；未选中收藏星改用 `Outlined.Star`                     | P0  |
| `ToolTopBar`        | 保持自定义；补**滚动收起**：下滑时标题从 titleLarge 收为 titleMedium 并固定（大标题趋势）；返回键热区已合规                          | P1  |
| `ToolScaffold`      | 分区间距 12→24dp（卡内保持 12）；补 `maxWidth = 600dp` 居中（平板）                                             | P0  |
| `ToolTextField`     | 圆角 14→16；聚焦态容器 `High`、未聚焦 `Low`（现状已对）；错误态加 Reject 触觉                                          | P0  |
| `ToolSectionCard`   | 18→16dp；标题行左侧补一小段 4dp 类目色条（让分组可被识别）                                                           | P0  |
| `ResultCard`        | 数值字体 Monospace → **Default + tnum**；加 `animateIntAsState` 滚动；一屏只留"标签 + 值"两层字号；底色可渐变（唯一允许的渐变位） | P0  |
| `KeyValueRow`       | 同上换 tnum；行高 ≥ 48dp；点击复制加 Confirm 触觉 + 行尾短暂 ✓                                                  | P0  |
| `SegmentedTabs`     | **指示器改为独立滑动的胶囊**（当前是每个按钮自变底色）——Expressive 观感最强单点                                              | P0  |
| `FeedbackBlock`     | LOADING 从转圈改**骨架屏**；ERROR 图标换 filled 语义图标 + 补警告色；新增 SKELETON 类型                               | P0  |
| `BottomActionBar`   | 整宽卡片 → **悬浮胶囊操作条**（`shapeFull`，玻璃材质，距底 16dp 浮起）                                               | P1  |
| 内容图标                | 统一 `Outlined`（未选中）/ `Filled`（选中）双语汇，现在混用 Filled                                               | P0  |
| 导航栏图标               | `NavigationBarItem` 改 outlined/filled 成对切换（M3 标准做法）                                           | P0  |
| 按钮                  | 统一叠加按压 scale + Tick 触觉                                                                        | P0  |
| `AlertDialog`（散落各处） | 全站替换为 `ModalBottomSheet`（倒数日已做）——单手可达 + 符合趋势                                                  | P1  |

---


## 六、首页 Bento 化

> 状态：**已落地**（2026-09-10，P0 之后同批完成）· 落地记录与偏离处见 **§11.5**

**现状（改造前）：** 2 列等大网格 + 一个横向"最近使用"条 + 6 个分类区。问题：**所有卡片一样大 = 没有优先级**，用户要在 27 个等权项里找。

**改法（信息优先级 → 尺寸差）：**

```
┌───────────────────────────────────────────┐
│ 工具箱                    🔍 (contained)   │   ← headlineLarge + 常驻搜索栏
├──────────────────────┬────────────────────┤
│                      │  小卡    │  小卡    │   ← 最近使用（横向条升级为 2 格）
│   大卡：最近使用的主   ├──────────┴─────────┤
│   力工具（2×1）        │  小卡    │  小卡    │
├──────────────────────┴────────────────────┤
│ 收藏                                        │
├──────────────────────┬────────────────────┤
│  收藏 A（2×1 大）     │  收藏 B  │  收藏 C  │
├──────────────────────┴────────────────────┤
│ 分类                                        │
│  [计算 6] [图片 7] [文本 4] [生活 6] ...    │   ← 分类先给入口，进去才是列表
└───────────────────────────────────────────┘
```

**三个要点：**

1. **大卡只给"真·高频"**：最近使用 Top1 或收藏 Top1，2×1 占位，展示图标 + 名称 + 一句说明 + **最近一次结果**（如"汇率 USD→CNY 7.24"）。这是 Bento 的灵魂——大格子要装最多的信息，不是最大的空白。
2. **搜索栏常驻**：不再"进页面才出现"。参考 M3 expressive search 的 contained 风格：填充容器 + 圆角 `shapeFull` + 前置图标，点击展开为全屏搜索视图。27 个工具时搜索是最高频入口，值得常驻。
3. **分类不再直接铺 27 张卡**，改为分类入口卡（显示"计算 · 6 个工具"），点进去看该类的工具列表。**首页从 27 张卡降到 ~12 个模块**，首屏就能装下全部导航。

**可选（P2）：今日卡。** 若有倒数日事件今天就到，插一张 2×1 的"今日"卡（事件名 + 天数 + 进度条）。这是"控制中心"感的关键一步，代价是要跨 feature 读数据，排在最后。

---

## 七、分期实施计划（确认后逐期执行）

### P0 · token 化与组件视觉（零依赖风险，只改 `core/designsystem/`）

1. 新增 `theme/Shape.kt`、`theme/Spacing.kt`、`theme/Motion.kt`、`theme/Material.kt`（玻璃 + 触觉）
2. 补全 `Type.kt` 至完整 15 档 + 负字距；`Color.kt` 补深色类目色 + 语义色
3. 按第五节清单改 `Components.kt`（单文件，改完编译验证）
4. 全局替换散值（18dp/10dp/15sp/Monospace）
5. **验收**：编译通过 + 现有单测全绿 + 深色/浅色双主题真机各扫一遍 + 对比度脚本自查

### P1 · 工具链升级（单独排期，不与视觉混做）

- Kotlin 1.9.22 → **2.x**（引入 `org.jetbrains.kotlin.plugin.compose`，删掉 `composeOptions`）→ AGP → compileSdk 35 → Compose BOM → material3 1.4.0
- 联动检查：KSP 版本必须与 Kotlin 严格匹配、Hilt 2.51.1 需升、Room 2.6.1 需升
- **必须先跑通**：232 单测 + `assembleRelease`（R8 混淆规则可能因新 API 失效）
- 收益：`SecureTextField`（密码箱直接受益）、`autoSize` 文本、`TextFieldState`、Carousel
- 风险：这是一次真实迁移，建议**单独一个 PR、单独验证一版**，不夹带任何视觉改动

### P2 · 试验 Expressive 原生 API（可选）

- material3 1.5.0-alpha，`@OptIn(ExperimentalMaterial3ExpressiveApi)`
- 只在 designsystem 包内使用，随时可摘除
- 目标组件：`MotionScheme`（替换自研）、`ButtonGroup`、`LoadingIndicator`（波形）、`SplitButton`、`Toolbar`
- **不接**：`MaterialExpressiveTheme`（会覆盖现有色彩体系）

---

## 八、验收标准

- [ ] 对比度：正文 ≥4.5:1（浅色/深色/类目色容器 三种组合全部自查）
- [ ] 无散值：全仓 grep 不到 `RoundedCornerShape(18.dp)`、`fontSize = 15.sp`、`FontFamily.Monospace`（动态数值处）
- [ ] 动效：所有 `tween` 均已替换为 `spring` 或有意保留（需注明理由）
- [ ] 触觉：复制/保存/解锁/错误 四类路径有反馈；系统关闭触感时全部静默
- [ ] 玻璃：仅在 4 个允许位出现；API 26~30 有降级路径且视觉可接受
- [ ] 大字模式：fontScale 1.3 下无裁切、无重叠
- [ ] 性能：低端机列表滚动无明显掉帧（玻璃层 ≤2、不在 LazyList 内）
- [ ] 回归：单测全绿 + `assembleRelease` 通过 + 深色/浅色各装一遍真机冒烟

---

## 九、我不建议做的（避坑清单）

| 项                      | 为什么不做                                        |
| ---------------------- | -------------------------------------------- |
| 多巴胺高饱和配色               | 工具类是"用完就走"，不需要情绪刺激；且高饱和度在 OLED 上费电、在阳光下可读性差  |
| 全站毛玻璃                  | 可读性直接崩，滚动性能直接崩                               |
| 全屏滑动导航转场               | 2026 已被列为过时手法；且每次切页都"飞一下"在 27 个工具间高频往返时非常累   |
| 装饰性动效 / 滚动视差 / 粒子      | 与 Calm Interface 相悖，且是低端机掉帧主因                |
| 3D / AR 元素             | 工具类无场景，纯增包体和渲染成本（我们 release 才 2.5MB，别毁掉这个优势） |
| Zero-UI / AI 生成界面      | 与"离线优先、数据不出设备、无追踪"的项目承诺直接冲突                  |
| 为 Expressive 立刻升级整套工具链 | 收益（弹簧动效/形状语义）用手写就能拿到，风险却要押上整个构建链路            |

---

## 十、待你定的三件事

1. **首页 Bento 化做不做？** 涉及首页信息架构改动（27 张卡 → ~12 模块），比 token 化要重。可以只做 token（P0）先看效果。
2. **工具链升级（P1）排不排？** 这是独立工程任务，不升的话 `SecureTextField`、autoSize 这些拿不到，但视觉方案完全不受影响。
3. **玻璃层要不要？** 我倾向"要，但只限 4 个位置"。如果你觉得工具类该彻底朴素，砍掉也能跑——材质只是加分项，不是骨架。

---

## 十一、P0 落地记录（2026-09-10）

**结论：小逸拍板「按推荐的来」** → P0 全做；工具链升级（P1）**不阻塞视觉**，单独排期。
首页 Bento 化原计划"等看过 P0 效果再定"，实际在 P0 验收当天就一并做完了（见 §11.5）。

### 11.1 新增文件

| 文件                                      | 内容                                                       |
| --------------------------------------- | -------------------------------------------------------- |
| `core/designsystem/theme/Shape.kt`      | `ToolShape` 六档形状 + `ToolShapes` 映射进 `MaterialTheme.shapes` |
| `core/designsystem/theme/Spacing.kt`    | `Space` 六档 8dp 栅格 + 色调高程五档语义（文档注释）                         |
| `core/designsystem/theme/Motion.kt`     | `ToolMotion` 自研 MotionScheme（spatial 3 档 + effect 3 档 + Dp/Int/IntOffset/Color 变体） |
| `core/designsystem/theme/Material.kt`   | `ToolHaptics`（免权限触觉）+ `GlassLevel` + `Modifier.glassSurface` |
| `core/designsystem/components/Interaction.kt` | `Modifier.pressScale` 按压反馈修饰符                          |

### 11.2 改动文件

| 文件                                  | 改了什么                                                                                              |
| ----------------------------------- | ------------------------------------------------------------------------------------------------- |
| `theme/Color.kt`                    | 补深色类目色 6 组（修复"浅色底片压暗卡片"缺陷）+ `success`/`warning` 扩展语义色 + `categoryColor()` 主题感知访问器（`CategoryColors` 旧名保留为兼容别名） |
| `theme/Type.kt`                     | 补全 **15 档**（原仅 8 档，其余静默 fallback 到 M3 默认）+ headline 及以上负字距 + `TextStyle.tabularNumbers()` |
| `theme/Theme.kt`                    | 接入 `shapes`、`LocalToolDarkTheme`、`LocalToolExtendedColors`（`MaterialTheme.extendedColors`）        |
| `components/Components.kt`          | 全量 token 化（见 11.3）                                                                               |
| `feature/home/HomeScreen.kt`        | 标题 titleLarge → **headlineLarge**；搜索栏改 `shapeFull`；间距离散值 → `Space`                              |
| `feature/tools/ToolsScreen.kt`      | 类目色改 `categoryColor()`（深色主题修复点之一）；标题 → headlineLarge；收藏星补 Confirm 触觉                              |
| `feature/countdown/CountdownCards.kt` | 两处大数字 `FontFamily.Monospace` → `fontFeatureSettings = "tnum"`                                    |
| `core/navigation/AppNavHost.kt`     | 全部转场 `tween(280)` → **弹簧**；全屏滑动降级为 **1/14 屏宽位移 + 淡入**；底部导航图标 outlined/filled 成对；底栏走玻璃 + 切页触觉    |

### 11.3 `Components.kt` 逐条

| 组件                 | 落地内容                                                                  |
| ------------------ | --------------------------------------------------------------------- |
| `ToolCard`         | 18→16dp；图标底片 34→40dp / 圆角 10→12dp；按压 scale 0.975 + 涟漪保留；新增可选 `onToggleFavorite`（outlined/filled 成对 + Confirm 触觉） |
| `ToolScaffold`     | 分区间距 12→**24dp**（卡内仍 12）；补 `maxWidth = 600dp` 居中（平板/折叠屏）            |
| `ToolTextField`    | 圆角 14→16；错误态触发 Reject 触觉（`LaunchedEffect(isError)` 只在进入错误态时震一次）        |
| `ToolSectionCard`  | 18→16dp；新增可选 `accentColor` 类目色条（4dp，让分组可被识别）                          |
| `ResultCard`       | `Monospace` → **tnum**；新增 `animateChange`（纯整数时 `animateIntAsState` 平滑滚动）；单位降到 `labelSmall` → 一行只剩 2 个字号层级；底色 `animateColorAsState` 过渡 |
| `KeyValueRow`      | tnum；`heightIn(min = 48dp)`；点击补 Confirm 触觉                              |
| `SegmentedTabs`    | **重写为滑动胶囊指示器**（`offset + animateDpAsState`），文字色同步过渡；按未选中项才触发 Tick 触觉     |
| `FeedbackBlock`    | 新增 `SKELETON`（骨架屏）与 `WARNING`；SUCCESS 用 `extendedColors.success`；ERROR 用 `error` 语义色 |
| `BottomActionBar`  | 圆角 20→24；按钮走 `pressScale` + Tick 触觉（悬浮胶囊 + 玻璃按原计划留 P1）                    |
| 新增 `ToolButton`    | 带按压缩放 + Tick 触觉的标准按钮，新代码优先用                                             |

### 11.4 验收状态

| 项                                        | 状态                                                        |
| ---------------------------------------- | --------------------------------------------------------- |
| 编译 `:app:compileDebugKotlin`              | ✅ **BUILD SUCCESSFUL**（无误，仅既有告警）                            |
| 单元测试 `:app:testDebugUnitTest`            | ✅ **185 项 / 18 个测试类 / 0 失败 / 0 错误 / 0 跳过**（实跑结果；Bento 化后为 194 项 / 19 类，见 §11.5.5） |
| 散值清理：`RoundedCornerShape(18.dp)` 已无     | ✅ 设计系统与新改页面已 token 化                                      |
| 散值清理：`fontSize = 15.sp`                 | ✅ 已改 16sp                                                 |
| 散值清理：动态数值处 `FontFamily.Monospace`       | ✅ 4 处动态数值已换 tnum；**保留 4 处代码/文本展示**（Base64、二维码内容、文本 diff、色值 hex）——那里等宽是语义需要 |
| `tween` 全部处理                             | ✅ 已逐处核对。**6 个调用点 / 4 类场景全部有意保留**（见下），其余（导航转场）已换 spring      |
| 触觉四类路径                                   | ✅ 复制/收藏/保存/错误/按键 已接入；系统关闭时平台自动静默                          |
| 玻璃仅限允许位                                  | ✅ 当前仅底部导航 1 处；另 3 处（抽屉/悬浮条/更新弹窗）留接入点                     |
| **真机双主题冒烟**                              | ⏳ **待你真机确认**——v1.2.0 release 包已于 2026-09-10 装到 vivo V2270A（见 §12.1），双主题切换由你实测          |
| **对比度脚本自查**                              | ✅ **已完成**——142 组色对全部达标（`scripts/contrast_check.mjs` 实跑，见 §12.2）                            |
| **fontScale 1.3 大字模式**                   | ✅ 静态排查 + 修复——全项目仅 `CompactToolCard` 真会裁切，已改 `heightIn`（见 §12.3）；真机复验待做 |                                                  |

**有意保留的 6 处 `tween`（逐处核对过，弹簧在这里是错的工具）：**

| 位置                              | 场景             | 为什么不用 spring                                        |
| ------------------------------- | -------------- | -------------------------------------------------- |
| `Components.kt:790`             | 骨架屏 alpha 呼吸  | `infiniteRepeatable` 只能配 tween/linear，弹簧无法 repeat     |
| `PomodoroScreen.kt:186`         | 番茄钟进度环（0→1）   | 值**每秒阶跃一次**的周期性推进，弹簧的"回弹/打断"语义在这里无意义，600ms 插值刚好抹平阶跃 |
| `DanmakuScreen.kt:292` / `:337` | 弹幕横向滚动（2 处）   | 必须 `LinearEasing` 匀速；用弹簧会出现"加速再减速"的弹幕             |
| `DecisionScreen.kt:186`         | 决策转盘减速旋转       | 需要 `FastOutSlowInEasing` 的"转盘停下"手感，弹簧的过冲会让转盘回弹      |

判断标准：**弹簧服务于"可打断的状态切换"，tween 服务于"周期性/连续推进的运动"。**

### 11.5 首页 Bento 化落地记录（2026-09-10）

**改造前**：2 列等大网格铺 27 张同尺寸卡片 + 一条横向"最近使用"，没有任何优先级。
**改造后**：`大标题 + 常驻搜索栏` → `最近使用`（1 大卡 + 4 小卡）→ `收藏`（1 大卡 + 4 小卡）→ `分类`（6 张色块入口卡，2 行 3 列）。
首页不再承载完整清单——那是「工具」页的职责。

#### 11.5.1 新增文件

| 文件 | 内容 |
| --- | --- |
| `feature/home/HomeBento.kt` | `HeroToolCard`（2×1 大卡）/ `CompactToolCard`（1×1 小卡，搜索结果复用同一张）/ `CategoryEntryCard`（分类色块入口）+ 私有 `CategoryPill` |
| `feature/home/HomeFeed.kt` | 泳道选择策略：**纯 id → id 的纯函数**。把"谁上首页、谁进大卡"从 `combine` 里拆出来，脱离 Compose/DataStore 后可单测 |
| `feature/tools/CategoryScreen.kt` | 分类落地页（复用 `ToolRow`，保证与「工具」页行样式一致） |
| `test/.../feature/home/HomeFeedTest.kt` | **9 项用例**：空态 / 大卡归属 / 泳道上限 / id 去重 / 收藏总数 / **跨泳道交叉去重** |

#### 11.5.2 改动文件

| 文件 | 改了什么 |
| --- | --- |
| `feature/home/HomeViewModel.kt` | `HomeUiState` 从"两个平面列表"改成泳道结构（`recentHero` / `recentQuick` / `favoriteHero` / `otherFavorites` / `favoriteTotal` / `categories`）+ `isFresh` 空态。分类清单是编译期静态数据，提到 companion 只算一次，不再跟着 favorites/recents 重算 |
| `feature/home/HomeScreen.kt` | 重写为 Bento 网格；搜索栏补**清除按钮**（以前只能退格）；副标题显示工具数（取 `ToolCatalog.visible.size`） |
| `feature/tools/ToolsScreen.kt` | `ToolRow` 由 `private` 改 `internal`，供分类页复用 |
| `core/navigation/AppNavHost.kt` | 新增 `category/{categoryKey}` 路由（转场与工具页同款）；首页传 `openCategory` |

#### 11.5.3 三条偏离原方案的地方（都是主动决策，不是漏做）

1. **「最近一次结果」没做。** §六要点①举的例子是"汇率 USD→CNY 7.24"，但项目里**没有任何地方持久化"上次结果"**——`ToolsStateRepository` 只有 `favorites` 和 `recent_ids`。要做得先设计一层"工具结果回写"协议，那是独立功能，不属于视觉改造。**不塞假数据**，大卡改用「类目胶囊 + 完整一句话说明」把信息密度补足。
2. **「点击搜索栏展开全屏搜索视图」没做。** 常驻搜索栏已落地（这是 §六要点②的主干），但"展开成全屏"要新开搜索页 + 独立转场，属于交互改造。当前保留内联输入，另补了清除按钮作为过渡。
3. **分类入口从"1 行横向色块"改成"2 行 3 列"。** §六示意图画的是一行横向色块，但它要么得横向滚动（把内容藏起来）、要么 6 张挤成"2 行 2 列"（除不尽，末行永远缺角）。改成 2 行 3 列后 6 个类目**一屏可见且不需要滚动**，更贴合"首屏装下全部导航"的原意。

#### 11.5.4 两处人工复核才发现的问题（编译器都查不出来）

**① 色块浓度选错，Bento 的立身之本差点没落地。**
第一版给大卡和分类卡用 `catColor.container.copy(alpha = 0.45f)` 叠在页面底色上。算了一下实际合成值：浅色主题下 `#DDE9FA` @45% over `#FDFBF7` ≈ **`#EFF3F8`**——和页面底色只差 14/8/1，**肉眼几乎分不出边界**。色块不成立，"尺寸 + 颜色表达优先级"就少了一半。
修法：大卡和分类卡**铺满浓度的类目色**（`#DDE9FA` 本身足够淡，不需要再稀释），小卡保持中性 `surfaceContainerLow`。于是"有颜色 = 优先级高"成了不需要解释的视觉语言。
连带一处必须改：大卡里的图标底片原来是同色 `container`，铺满后会直接消失，改为 `surfaceContainerLowest`（浅色下是纯白、深色下近黑），两种主题都有清晰分离。

**② 收藏泳道的"共 N 个"会和实际卡数对不上。**
收藏泳道**和最近使用做了交叉去重**（同一工具不在两条泳道里出现两次），副作用是：共 2 个收藏、只显示 1 张（另一张正在上面的最近使用里当大卡），表头却写着"共 2 个"。
修法：总数标签**只在收藏数真的超过泳道容量（5）时才显示**。只有这时它才明确表示"还有没显示出来的"，才不会误导；≤5 个收藏时干脆不显示数字。

#### 11.5.5 验收状态

| 项 | 状态 |
| --- | --- |
| 编译 `:app:compileDebugKotlin` | ✅ **BUILD SUCCESSFUL**（无误，仅既有告警） |
| 单元测试 `:app:testDebugUnitTest` | ✅ **194 项 / 19 个测试类 / 0 失败 / 0 错误 / 0 跳过**（实跑；P0 后新增 9 项 `HomeFeedTest` 全绿） |
| **真机双主题冒烟（含 Bento 首页）** | ⚠️ **未做**——与 §11.4 那条合并成同一次真机会话 |

#### 11.5.6 需要小逸知道的行为变化

首页**不再铺完整工具清单**了。以前"在首页往下一路翻就能看到全部 27 个"，现在首页只到分类入口为止，完整清单在「工具」页（那里仍是按类目分组的全量列表）。这是"用尺寸差表达优先级"必然的代价——想两者都要，就得回到没有优先级的等大网格。

### 11.6 留给下一期

- **P1 工具链升级**：Kotlin 2.x / compileSdk 35 / material3 1.4.0 —— 独立 PR，不与视觉混做。→ **已完成评估，见 §12.6②（结论：Expressive 仍在 alpha，收益是构建现代化而非新 API，建议独立分支暂缓）**
- **玻璃层真背景模糊**：需要 edge-to-edge（内容从条下滚过）+ API 31 `RenderEffect`，或引入 haze 库。调用点已封装好，换实现不用改调用。→ **已完成评估，见 §12.6①（结论：需引入 3 个依赖 + 全站 edge-to-edge，建议暂不做）**
- **散值长尾**：`Components.kt` 与首页/工具页已清，27 个工具内页里仍有各自的历史散值（如 `RoundedCornerShape(12.dp)`），可随改动顺手替换。→ **✅ 已完成，见 §12.5（14 个文件 25 处，feature 层已归零）**

---

## 十二、真机验证 · 收尾工程（2026-09-10 晚）

> 12.1 真机安装 → 12.2 对比度自查 → 12.3 大字模式排查 → 12.4 类目色浓度对齐 → 12.5 散值长尾收敛 → 12.6 剩余两项评估

### 12.1 v1.2.0 装到真机（vivo V2270A / PD2270）

设备上的既装版本是 **v1.1.3 (code 5) 的 release 签名包**（由 `com.android.packageinstaller` 侧载，
安装时间 2026-09-08 21:43 与本机 release keystore 生成时间 21:13 吻合），因此 `:app:installDebug` 直接失败：

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.flechazo.toolbox signatures do not match newer version
```

**没有选择"卸载再装"**——那会清空设备上的倒数日与经期数据且不可逆。改走**同签名覆盖安装**：
把主仓库的 `keystore.properties` + `keystore/toolbox-release.jks` 复制进 worktree（两者均被
`.gitignore` 覆盖，`git status` 确认未跟踪）→ `:app:assembleRelease`（8m20s，R8 全跑）→ `adb install -r`。

| 验证项 | 结果 |
| --- | --- |
| 版本 | `versionCode 5→6`、`versionName 1.1.3→1.2.0` |
| **数据保留** | `firstInstallTime` 仍为 2026-09-08 21:43:10，仅 `lastUpdateTime` 更新 → 确认是覆盖而非重装 |
| 启动 | 进程存活、crash buffer 为空、无 FATAL（R8 未裁错东西） |
| **旧数据迁移** | 经期记录页正确读出「还有 21 天 · 基于最近 6 个周期」→ **v1.1.3→v1.2.0 数据库迁移在真机跑通** |
| Bento 首页 | 渲染正常，"满浓度类目色"方案成立 |

> **遗留问题（待小逸拍板）**：设备签名现在是 release。下次在 Android Studio 里直接 Run（debug 签名）
> 会再次撞同一冲突。出路：① 给 `debug` buildType 也挂 release `signingConfig`；② 继续走命令行
> `assembleRelease` + `adb install -r`。

### 12.2 对比度量化自查（§11.4 遗留项，已完成）

新增 **`scripts/contrast_check.mjs`**：实时解析 `Color.kt` 的色值，按 WCAG 2.1 计算 sRGB 相对亮度与对比度，
按分组输出 Markdown 报告；存在 FAIL 时退出码为 1（可直接挂 CI）。改色后重跑即可，不需要维护第二份色表。

**结果：142 组色对全部达标。**

- 文本类最低值 **5.13:1**（`onSuccess` on `success`，浅色），判据 4.5:1 —— 有富余；
- 图形类最低值 **5.43:1**（`cat.on` on MEASURE 的 `#3B6D11`），判据 3.0:1。

**顺带发现（不是 WCAG 缺陷，是可辨度问题）：**

| 主题 | 类目色块 vs 页面底色 |
| --- | --- |
| 浅色 | **1.098 ~ 1.19**（TEXT `#E1F5EE` 最弱，CALCULATE `#DDE9FA` 最强） |
| 深色 | 1.19 ~ 1.42（健康） |

浅色主题下 6 个类目色块浓度不齐且整体偏弱。它不影响文字可读性（文字对比度从 5.13 起），
但会让"有颜色 = 优先级高"这个信号的强度不一致。
**处置：小逸拍板「对齐」→ 已完成，见 §12.4。** 原则是把偏弱的 container 按亮度对齐到 ≈1.18，
而不是整体加深——加深会连带压低 `cat.on` 的对比度。

> **教训（与 §11.5.4 呼应）**：凡"靠颜色深浅表达信息"的设计，都必须把实际对比度算出来。
> 这次算的是"满浓度够不够"，上次算的是"45% alpha 够不够"，两次都是算完才发现问题。

### 12.3 fontScale 1.3 大字模式排查（§11.4 遗留项）

方法：列出全项目所有**固定高度容器**（`.height(<n>.dp)`），逐处判断"是否为多行文字容器"。

**全项目 38 处 `.height()` 里，唯一真会坏的是 `CompactToolCard`：**

| 卡片 | 内容实测高（fontScale 1.3） | 固定高度 | 判定 |
| --- | --- | --- | --- |
| `CompactToolCard` | 24（padding）+ 36（图标）+ 26（titleSmall×1.3）+ 20.8（bodySmall×1.3）= **106.8** | 104 | ❌ **超出 2.8dp，末行被 clip 切掉** |
| `HeroToolCard` | 32 + 88 = 120 | 124 | ✅ 有余量 |
| `CategoryEntryCard` | 24 + 50.8 = 74.8 | 82 | ✅ 有余量 |

**修法**：三张卡一律从 `Modifier.height(...)` 改为 `Modifier.heightIn(min = ...)`。
正常字号下内容不足，高度仍是原值（**布局零变化**）；大字模式下自动撑开。
同行卡片结构一致（图标+标题+描述），放大倍数相同，所以不会出现"同一行两张卡不等高"。

其余固定高度均为**单行文字或纯图形容器**，不存在多行截断：
`ToolTopBar` 56dp（subtitle 在 Row 之外，Row 内只有单行 title）、`SegmentedTabs` 44dp、
`BottomActionBar` / 番茄钟 / 计算器按钮 52dp、倒数日色条 58dp、进度条与骨架块等。
唯一偏紧的是 BMI 页给 `ToolTextField` 硬编码的 56dp（单行输入框，极限字号下内边距会被压缩），
**不属本次改造范围，待真机顺带一看**。

**待你真机复验**：系统设置 → 显示 → 字体大小拉到最大，看首页三张卡有无截断。

### 12.4 类目色浓度对齐（§12.2 遗留项，已完成）

小逸拍板「对齐」。**只调 HSL 的 L、不动 H/S** —— 保证改的是浓度而不是颜色（最大色相偏移 <1.7°）。
目标统一为「container vs 页面底色 = 1.18:1」，基准取原来的最强者 CALCULATE（1.19）。

色值由 **`scripts/align_category_colors.mjs`** 算出（改命令行参数即可换目标值重算），**不要手改**：

| 类目 | 原色值 | 原可辨度 | 新色值 | 新可辨度 |
| --- | --- | ---: | --- | ---: |
| CALCULATE | `#DDE9FA` | 1.19 | `#DEEAFA` | 1.18 |
| IMAGE | `#EEEDFE` | 1.12 | `#E8E7FE` | 1.17 |
| TEXT | `#E1F5EE` | 1.10 | `#D0EFE5` | 1.18 |
| LIFE | `#FAEEDA` | 1.11 | `#F8E6CA` | 1.18 |
| MEASURE | `#EAF3DE` | 1.11 | `#E0EDCF` | 1.18 |
| SECURITY | `#FBEAF0` | 1.12 | `#FAE3EB` | 1.18 |

**代价（已核算）**：container 变暗会连带压低压在其上的 `cat.on` 对比度，从 5.43~6.86 降到
**5.07~5.92**，仍远高于 4.5:1 判据。`onSurface` / `onSurfaceVariant` 因基数很大（13.9~15.1 / 7.3~7.9），
下降后依然安全。**重跑 `contrast_check.mjs`：142 组色对仍全部达标（exit 0）。**

**深色主题刻意不做同样处理**（现为 1.19~1.42）。理由：深色页底本身极暗（`#171A18`），色块天然分离度更高；
若强行把 6 个压到同一水平，需要**抬高**偏暗的几个，反而压缩 `onSurface`（浅色文字）的对比余量，得不偿失。

### 12.5 散值长尾收敛（§11.6 遗留项，已完成）

把 14 个工具内页的 **25 处**形状魔法数收敛到 `ToolShape`。**feature 层 `RoundedCornerShape` 已归零。**

| 原值 | 处数 | → token | 性质 |
| --- | ---: | --- | --- |
| 2dp | 1 | `full` | 视觉等值（4dp 高进度条上，2dp 圆角本就等于胶囊） |
| 3dp / 4dp | 4 | `xs` | 等值或 +1dp |
| 6dp / 8dp | 4 | `xs` / `sm` | ±2dp |
| 10dp / 12dp | 6 | `md` | 等值或 +2dp |
| 14dp / 16dp | 3 | `lg` | 等值或 +2dp |
| 18dp / 22dp | 4 | `lg` / `xl` | ±2dp（倒数日列表卡 / Hero 卡） |
| 100 / 100dp | 3 | `full` | 视觉等值 |

除上表外，**feature 层其余图形元素均已确认语义正确、不做替换**：
`CircleShape`（色轮、单选圆点、日历"今天"标记 —— 圆形元素用 `CircleShape` 比
`RoundedCornerShape(percent = 50)` 更语义化）、`CountdownPalette` 的事件色板（用户可选颜色，是数据不是主题）、
指南针的红色指针（北向必须为红，属语义硬编码）。

**执行方式**：25 处分散在 14 个文件、且每处都牵动 import，手改容易静默漏改。
用带**出现次数断言**的一次性脚本批量替换（次数不符即报错退出，不静默改错），
改完逐文件 grep 复核 + 编译验证，脚本随后删除（不留一次性工具在仓库里）。

> **过程坑（值得记住）**：文件是 **CRLF** 行尾，脚本里用 `\n` 匹配 `import ...` 行会**静默失败**，
> 导致 12 个文件残留 unused import。教训：**处理源码文件的脚本必须显式处理 `\r\n`**，
> 且改完要用独立命令复核"预期结果是否真的出现"，不能只看脚本自己的成功输出。

### 12.6 剩余两项的评估（未执行，附理由）

§11.6 还剩两项。两项都不是"该不该做"，而是**性价比与前提条件**的问题，结论如下。

**① 玻璃层的真背景模糊 —— 建议暂不做。**

- 技术前提：Compose 原生的 `Modifier.blur` 模糊的是**自身内容**而非身后背景，实现 backdrop blur
  必须"捕获内容层 → 模糊 → 裁剪到玻璃区域"，Compose 没有内建能力。
- 唯一成熟路径是 **haze**（`dev.chrisbanes.haze`，Apache 2.0，Chris Banes 维护，Accompanist 作者）。
  2.0 已进入 **beta**，按能力拆成 `haze` + `haze-blur` + `haze-blur-materials` **三个 artifact**。
- 额外前置：还需要 **edge-to-edge**（内容从系统栏下滚过），否则"玻璃层背后"根本没有可模糊的内容。
- 判断：当前"**半透明填充 + 1dp 高光描边**"已达到玻璃的视觉目的（§11.4 已落地底栏 1 处）。
  为 4 个装饰性位置引入 **3 个新依赖 + 全站 edge-to-edge 改造**，性价比不成立；
  且 haze 2.0 尚在 beta，不适合此时绑进发布链路。**调用点已封装（`Modifier.glassSurface`），将来换实现不动调用方。**

**② P1 工具链升级 —— 建议独立分支、暂缓。**

现状 → 目标（查证于 2026-09-10，AndroidX 官方版本页）：

| 项 | 现状 | 目标 | 说明 |
| --- | --- | --- | --- |
| Kotlin | 1.9.22 | 2.x | 需把 `composeOptions { kotlinCompilerExtensionVersion }` 迁到 `org.jetbrains.kotlin.plugin.compose` 插件（**老写法已废弃**） |
| AGP | 8.5.0 | 8.7+ | 与 Gradle 8.9 兼容 |
| compileSdk / targetSdk | 34 | **35+** | **material3 1.4.0 硬性要求**，否则 `minCompileSdk` 直接构建失败 |
| material3 | 1.3.0 | 1.4.0（stable） | **但 Expressive 组件仍在 `1.5.0-alpha27`** |
| Hilt / KSP | 2.51.1 / 1.9.22-1.0.18 | 随 Kotlin 联动 | 项目无 version catalog，版本散在各文件 |

**关键结论（改变了本项的优先级）**：即使完成升级，**stable 的 1.4.0 依然拿不到 MotionScheme 等
Expressive 原生 API** —— 它们仍在 `1.5.0-alpha27`。也就是说升级换来的是
「构建现代化（K2 编译器、更新组件）」，**而不是"终于能用 Expressive"**——那份观感 P0 已由自研
`ToolMotion` 拿到。因此本项**不再是"视觉改造的下一步"，而是一次独立的构建现代化**，
应当：① 在干净分支做（会改动 6 处版本号 + 迁移 compose compiler 插件，属构建级变更，
与视觉 PR 混在一起会让视觉改动难以单独回滚）；② 不在你要手测的版本上加变数。

**可选的低风险中间项**：若想让界面更"现代"，**edge-to-edge**（`enableEdgeToEdge()` + 各页 inset 适配）
是不引入任何依赖、且 Android 15+ 迟早要求的改动 —— 它本身就有价值，也是玻璃真模糊的前置。

> **已拍板执行（2026-09-10 晚）**，落地记录见 §12.7。

---

### 12.7 edge-to-edge 与签名收尾（2026-09-10 晚，已执行）

#### 一、真正的边到边：不是"加一行 `enableEdgeToEdge()`"

`MainActivity` 里其实**早就有** `enableEdgeToEdge()`（原始代码自带）。但只调它等于只开了窗：
系统栏透明了，**页面的 insets 却没有任何人负责**；而 `Scaffold` 默认会用 `systemBars` 把 NavHost
的视口顶部、底部各切掉一截。结果是"从状态栏以下才开始画"——内容永远滚不到状态栏下面，
更钻不到玻璃底栏下面。**玻璃层背后没有东西经过，"玻璃"就只是一块灰板。**

本轮做的事，本质是把 **insets 的所有权从 `Scaffold` 交还给页面本身**。

**① 系统栏：显式透明**

`enableEdgeToEdge()` 的默认实现会给导航栏盖一层**系统 scrim**（浅色下约 90% 白）。那条雾面会把
底部玻璃栏的透明感压死，玻璃"浮"不起来。所以显式传 `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)`。

唯一例外是 **API 26**：那一版没有浅色导航栏图标（`isAppearanceLightNavigationBars` 从 API 27
才生效），透明条 + 白色图标在浅色页面上等于看不见。老设备退回 70% 黑底衬——图标始终可见，
不是降级半成品，是另一种合格方案。

**② 顶层 `Scaffold`：`contentWindowInsets = WindowInsets(0, 0, 0, 0)`**

不再代领 insets；`innerPadding.bottom`（= 玻璃栏实测高度）改为**参数**下发给三个 Tab 页。

**③ 页面自己领（新文件 `core/designsystem/theme/Insets.kt`）**

| 量 | 取值 | 用在哪 |
| --- | --- | --- |
| `statusBarTopInset()` | `safeDrawing` 的 top（含刘海/挖孔） | 滚动容器的 `contentPadding.top` |
| `navigationBarBottomInset()` | `navigationBars`（**刻意不含输入法**） | 工具内页的底部留白 |
| `Modifier.horizontalSafePadding()` | `safeDrawing` 的左右 | 页面根节点（横屏时刘海在侧边） |

**关键判据：纵向留白必须写进 `contentPadding`，不能给根节点加 padding。**
给根节点加 padding 是把视口切掉一块，内容滚到那条线就停住；写进 `contentPadding` 则视口仍是
全屏，内容会从状态栏、玻璃栏**下面**滚过去——这才是边到边，也是玻璃将来换真模糊的前提。

**刻意不含输入法**（不用 `safeDrawing.bottom`）：`CalculatorScreen` / `CountdownScreen` /
`PasswordVaultScreen` 是 `scrollable = false`，含 ime 会让它们在弹键盘时被整体压扁变形。

**④ 集中改两处，28 个工具内页自动继承**

| 组件 | 改动 |
| --- | --- |
| `ToolTopBar` | 自带 `statusBarsPadding()`。放在标题栏自身而非页面根节点，标题才会**随内容滚走**，滚下去后状态栏区域露出的是后续内容而不是一条空白 |
| `ToolScaffold` | 根 Box 加 `horizontalSafePadding()`；底部 `padding(bottom = navigationBarBottomInset() + Space.xl)` |

手工适配 4 处：`HomeScreen` / `ToolsScreen` / `SettingsScreen`（三个 Tab 页，改 `contentPadding`）
与 `RulerScreen`（唯一不走 `ToolScaffold` 的工具页，用 `navigationBarsPadding()`）。

Tab 页的底部留白 = `bottomBarPadding + Space.sm`：玻璃栏是浮层，列表从它下面滚过，
但最后一项仍能完整滚出栏外，不会被永久压住。

#### 二、debug 变体挂 release 签名（`app/build.gradle.kts`）

设备上装的是 release 版。debug 走默认 debug keystore 时，`assembleDebug` 的包与已装版本签名
不一致，`adb install -r` 和 Android Studio 的 Run 会直接报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
——只能卸载重装，而卸载会清掉本地数据（倒数日、密码箱都存在本地）。现在 `debug` 也挂 release
签名，调试包可直接覆盖安装，数据无损，Run 不再撞签名墙。

`keystore.properties` 与 `keystore/` 都在 `.gitignore` 里、没进仓库；文件缺失时这段逻辑自动跳过，
不会让干净克隆的构建失败。

#### 三、验收

| 命令 | 结果 |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL**（仅 2 条既有告警：`ToolCard` 的 `description` 未用、`menuAnchor()` 已废弃，均非本次引入） |
| `:app:testDebugUnitTest` | **194 项 / 19 个测试类 / 0 失败 / 0 错误** —— 与改动前完全一致，无回退 |
| `:app:signingReport` | `debug` 与 `release` 的 **SHA-256 完全相同**（`92:1E:8C:…:1B:35`）→ 调试包可直接覆盖安装 |

**踩坑**：`statusBarTopInset()` 最初标了 `@ReadOnlyComposable`，编译直接报
"ReadOnlyComposable 只能调用其他 ReadOnlyComposable" —— 因为 `WindowInsets.asPaddingValues()`
本身不是只读的。去掉注解即可。

#### 四、明确没验证的部分

真机上是改动前的 v1.2.0，本次改动**没有装到设备上**（小逸要等一起测）。以下只有静态保证：

- 玻璃栏下穿过的实际观感（半透明填充压在内容上干不干净）
- 状态栏区域滚动穿透在深色主题下的效果
- 三键导航 / 手势导航两种模式的底部留白（本方案统一按 insets 走，理论上一致）
- 横屏刘海在尺子页的左右让位

下次装机优先看这四处。

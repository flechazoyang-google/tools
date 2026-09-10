package com.flechazo.toolbox.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * 8dp 基准栅格（规范 §4.5）。
 *
 * 取代现有散值：10dp（图标底片间距、按钮间距）→ [sm] 或 [md]；18dp 内边距 → [lg]。
 * 注意分区间距用 [xl]（24dp），**卡片内部**仍保持 [md]（12dp）——分区要拉开，卡内要紧凑。
 */
object Space {
    /** 4dp · 图标与文字、行内元素 */
    val xs = 4.dp

    /** 8dp · 相关控件之间 */
    val sm = 8.dp

    /** 12dp · 卡片内元素间距 */
    val md = 12.dp

    /** 16dp · 页面水平边距 / 卡片内边距 */
    val lg = 16.dp

    /** 24dp · 分区间距 */
    val xl = 24.dp

    /** 32dp · 主要区块之间 */
    val xxl = 32.dp
}

/*
 * 层级（色调高程）语义映射（规范 §4.3）——项目不做投影，用 surfaceContainer 五档表达层次。
 * 项目已有完整五档，问题只是"哪个卡用哪一档"没有约定，靠感觉。固化如下：
 *
 *   surfaceContainerLowest   → 图片 / 二维码内衬（扫码、拼豆预览这类强制白底场景）
 *   surfaceContainerLow      → 卡片默认底（分组卡、工具卡、结果卡）
 *   surfaceContainer         → 页面底 / 条状容器（底部导航、状态条、搜索栏）
 *   surfaceContainerHigh     → 输入框聚焦态（与 Low 形成"聚焦上浮半档"）
 *   surfaceContainerHighest  → 键帽 / 选中态底（计算器键盘、选中行）
 *
 * 不做阴影：M3 用色调高程而非投影，卡片一律 Surface + containerLow。
 */

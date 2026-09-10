package com.flechazo.toolbox.core.designsystem.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 动效语义（规范 §4.6）—— 自研 MotionScheme。
 *
 * 为什么自研：stable 的 material3 1.3.0 里没有官方 `MotionScheme`（那是 1.5.0-alpha），
 * 但 M3 Expressive 的**视觉结论**（弹簧动效）用现有依赖手写就能拿到，代价只是这个文件。
 *
 * 两条铁律：
 * 1. **用弹簧不用 tween。** 打断时 spring 保留速度矢量，连续操作是顺的；
 *    tween 会从头重播（连点两下会"卡一下"）。这是 M3 官方给的第一条论证。
 * 2. **Spatial 允许过冲、Effect 绝不过冲。** 位移/缩放/形状可以回弹；
 *    颜色/透明度回弹会让人眼以为"闪了一下"。
 *
 * 无障碍：系统开启「移除动画」时 Compose 的 MotionDurationScale 归零，
 * 弹簧会瞬时完成，无需手动降级。
 *
 * 只有三处**有意保留 tween**，且都注明在调用点：
 * 弹幕匀速滚动、转盘减速旋转、骨架屏无限呼吸（弹簧无法 repeat）。
 */
object ToolMotion {

    // ---------------------------------------------------------------- Spatial
    // 位移 / 缩放 / 形状 —— 允许过冲回弹

    /** 小元素：键帽、chip、开关、按压反馈 */
    val spatialFast: SpringSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 900f)

    /** 中等：卡片、抽屉、分段指示器 */
    val spatialDefault: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 500f)

    /** 全屏：页面切换 */
    val spatialSlow: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 240f)

    /** [Dp] 版本：`animateDpAsState` 用（分段指示器滑动） */
    val spatialFastDp: SpringSpec<Dp> = spring(0.80f, 900f, visibilityThreshold = 0.01.dp)
    val spatialDefaultDp: SpringSpec<Dp> = spring(0.85f, 500f, visibilityThreshold = 0.01.dp)
    val spatialSlowDp: SpringSpec<Dp> = spring(0.90f, 240f, visibilityThreshold = 0.01.dp)

    /** [IntOffset] 版本：`slideIntoContainer` 用（导航内容位移） */
    val spatialOffset: SpringSpec<IntOffset> = spring(0.90f, 600f, visibilityThreshold = IntOffset(1, 1))

    // ----------------------------------------------------------------- Effect
    // 颜色 / 透明度 —— 绝不过冲

    /** 按钮着色 */
    val effectFast: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 1600f)

    /** 状态淡入淡出 */
    val effectDefault: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 900f)

    /** 整屏刷新 */
    val effectSlow: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 420f)

    // ------------------------------------------------------------------ 其它

    /** 整数计数：结果卡、倒数日天数。visibilityThreshold = 1 避免小数抖动。 */
    val countDefault: SpringSpec<Int> = spring(1.0f, 300f, visibilityThreshold = 1)

    /** [Color] 版本：`animateColorAsState` 用（分段控件文字换色） */
    val effectFastColor: SpringSpec<Color> = spring(1.0f, 1600f)
    val effectDefaultColor: SpringSpec<Color> = spring(1.0f, 900f)

    /**
     * 导航内容位移**只走全屏宽度的 1/14**（约 24～28dp）。
     * 2026 的反趋势之一就是全屏滑动转场——在 27 个工具间高频往返时会非常累。
     */
    const val NAV_SLIDE_DIVISOR = 14

    /** Tab 切换时内容从 0.98 放大到 1.0，比"什么都不做"多一点确认感。 */
    const val TAB_ENTER_SCALE = 0.98f

    /** 按压反馈缩放 */
    const val PRESS_SCALE = 0.96f

    /** 卡片按压反馈缩放（比按钮略轻） */
    const val PRESS_SCALE_CARD = 0.975f

    /**
     * 骨架屏呼吸周期。**有意用 tween 而非 spring**：
     * 无限循环动画只能靠 tween/linear 的 `infiniteRepeatable`，弹簧无法 repeat。
     */
    const val SKELETON_BREATH_MS = 900
}

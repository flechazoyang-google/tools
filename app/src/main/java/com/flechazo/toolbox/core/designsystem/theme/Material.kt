package com.flechazo.toolbox.core.designsystem.theme

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// 触觉（Haptics）—— 规范 §4.7
// ---------------------------------------------------------------------------

/**
 * 触觉语义。全站原本**零触觉反馈**，而这是最便宜的"高级感"来源，且与动效天然配对。
 *
 * ### 为什么不用 `VibrationEffect`
 * 原方案写的是 `VibrationEffect` 四种波形，但那需要申请 `android.permission.VIBRATE`。
 * 对一个主打"最小权限、无追踪"的应用，为震动多要一条权限不划算。改走
 * [View.performHapticFeedback]：
 *
 * - **不需要任何权限**（走的是 View 的系统触感通道）
 * - 兼容面从 API 26 拉到 **API 21**（`CONFIRM`/`REJECT` 在 API 30 才有，已做分支回退）
 * - 系统「触感反馈」总开关由平台自动尊重，不用自己读 `Settings.System`
 *
 * 唯一的取舍：脉冲模式由厂商调校，不能自定义波形——对工具类应用这是优点不是缺点。
 */
@Stable
class ToolHaptics internal constructor(private val view: View?) {

    private var lastTickAt = 0L

    /**
     * 轻点：键帽按下、分段切换、滑杆棘轮。
     * **连续操作按 [TICK_THROTTLE_MS] 节流**，否则滑杆拖动会震到手麻。
     */
    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt < TICK_THROTTLE_MS) return
        lastTickAt = now
        perform(HAPTIC_VIRTUAL_KEY)
    }

    /** 确认：复制成功、收藏、置顶、保存 */
    fun confirm() = perform(HAPTIC_CONFIRM)

    /**
     * 成功：密码箱解锁、图片保存完成、迁移完成。
     * 用「确认 + 80ms 后补一记轻点」拼出双脉冲——比单次更"完成"。
     */
    fun success() {
        perform(HAPTIC_CONFIRM)
        view?.postDelayed({ perform(HAPTIC_VIRTUAL_KEY) }, SUCCESS_SECOND_PULSE_MS)
    }

    /** 拒绝：密码错误、导入被拒、超范围 */
    fun reject() = perform(HAPTIC_REJECT)

    /** 不传 flag 即尊重系统总开关；view 已分离时静默返回。 */
    private fun perform(constant: Int) {
        view?.performHapticFeedback(constant)
    }

    private companion object {
        const val TICK_THROTTLE_MS = 40L
        const val SUCCESS_SECOND_PULSE_MS = 80L
    }
}

/** API 30 才有 CONFIRM，低版本回退到轻点（KEYBOARD_TAP 在 API 5 就有）。 */
private val HAPTIC_CONFIRM: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    HapticFeedbackConstants.CONFIRM
} else {
    HapticFeedbackConstants.KEYBOARD_TAP
}

/** API 30 才有 REJECT，低版本回退到长按。 */
private val HAPTIC_REJECT: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    HapticFeedbackConstants.REJECT
} else {
    HapticFeedbackConstants.LONG_PRESS
}

private val HAPTIC_VIRTUAL_KEY: Int = HapticFeedbackConstants.VIRTUAL_KEY

@Composable
fun rememberToolHaptics(): ToolHaptics {
    val view = LocalView.current
    return remember(view) { ToolHaptics(view) }
}

// ---------------------------------------------------------------------------
// 玻璃材质（Glass）—— 规范 §4.8
// ---------------------------------------------------------------------------

/**
 * 玻璃层的使用位置。**只允许这 4 处**，列表项 / 卡片 / 输入框一律不用
 * （可读性 + 滚动性能双杀）。
 */
enum class GlassLevel(
    /** API 31+ 的填充不透明度（更透） */
    val modernAlpha: Float,
    /** API 26~30 的填充不透明度（更实）——不是降级半成品，是另一种合格设计 */
    val legacyAlpha: Float,
) {
    /** 底部导航栏 */
    Bar(modernAlpha = 0.82f, legacyAlpha = 0.96f),

    /** 底部抽屉顶缘 */
    Sheet(modernAlpha = 0.88f, legacyAlpha = 0.97f),

    /** 悬浮操作条 */
    Floating(modernAlpha = 0.86f, legacyAlpha = 0.97f),

    /** 更新弹窗 */
    Dialog(modernAlpha = 0.92f, legacyAlpha = 0.98f),
}

/**
 * 玻璃表面修饰符。
 *
 * ### 诚实的实现边界
 * Compose 1.7 的 `Modifier.blur` 模糊的是**这个组件自己的内容**，不是它身后的背景
 * （真"背景模糊"要用 API 31 的 `RenderEffect` 采样下层内容，或引入 haze 之类的库）。
 * 所以这里交付的是同族效果里**可靠的那一半**：
 *
 * 1. 半透明填充（让内容有"浮在页面上"的暗示）
 * 2. 1dp 高光描边（把浮层与内容明确分开——**这一条才是肉眼最认得出的"玻璃感"**）
 *
 * 零依赖、零性能风险。
 *
 * ### 真模糊的前置已经补齐（2026-09-10）
 * 边到边已落地（见 `Insets.kt`）：底栏现在是**浮在内容之上**的，列表会从它下面滚过，
 * 玻璃层背后真的有东西可以采样了。但真 backdrop blur 仍然只能靠 haze（尚在 2.0 beta，
 * 拆成 3 个 artifact），为 4 处装饰位绑一个 beta 依赖不划算，暂时维持现状。
 * 换实现时**调用点不用改**——这是把 `glassSurface` 抽出来的意义。
 *
 * @param shape 形状，须与容器实际形状一致，否则描边会错位
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = ToolShape.xl,
    level: GlassLevel = GlassLevel.Bar,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val modernSurface = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fill = scheme.surfaceContainer.copy(
        alpha = if (modernSurface) level.modernAlpha else level.legacyAlpha,
    )
    return this
        .background(color = fill, shape = shape)
        .border(
            width = 1.dp,
            color = scheme.outlineVariant.copy(alpha = if (modernSurface) 0.55f else 0.75f),
            shape = shape,
        )
}

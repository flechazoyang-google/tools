package com.flechazo.toolbox.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.flechazo.toolbox.core.designsystem.theme.ToolMotion

/**
 * 按压反馈：按下缩到 [pressedScale]，抬起按弹簧回弹（规范 §4.6「卡片按压 / 按钮」）。
 *
 * 为什么用缩放而不只是涟漪：涟漪负责"我点到了**哪里**"，缩放负责"我点到了"。
 * 两者不冲突，叠加起来才没有"点了没反应"的体感。
 *
 * 这是一个 `@Composable` 修饰符工厂——1.7 起 `composed {}` 已不推荐，直接这么写即可。
 *
 * 用法：
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * Modifier
 *     .pressScale(interaction)
 *     .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = ...)
 * ```
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = ToolMotion.PRESS_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = ToolMotion.spatialFast,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

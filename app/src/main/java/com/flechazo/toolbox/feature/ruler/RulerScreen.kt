package com.flechazo.toolbox.feature.ruler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTopBar

enum class RulerUnit(val label: String) {
    CM("厘米"), INCH("英寸"),
}

/**
 * 屏幕尺子。
 *
 * 设计要点：
 * - 横屏展示，刻度从屏幕最左边缘开始（贴边才好对准物体）
 * - 刻度带数字，厘米模式每格 1mm，英寸模式每格 1/16 in
 * - 刻度区可横向滚动，最长 30cm / 12in
 */
@Composable
fun RulerScreen(onBack: () -> Unit) {
    var unit by remember { mutableStateOf(RulerUnit.CM) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    // 横屏展示（尺子横着才好用）
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? android.app.Activity
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Android 名义定义：160dp = 1 英寸
    val pxPerInch = with(density) { 160.dp.toPx() }
    val pxPerCm = pxPerInch / 2.54f
    val unitPx = if (unit == RulerUnit.CM) pxPerCm else pxPerInch
    val totalUnits = if (unit == RulerUnit.CM) 30f else 12f
    val subdivisions = if (unit == RulerUnit.CM) 10 else 16
    val canvasWidthDp = with(density) { (unitPx * totalUnits).toDp() }

    val tickColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.Medium)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ToolTopBar(
                title = "尺子",
                onBack = onBack,
                subtitle = "横屏贴边测量 · 160dp = 1 inch（按屏幕密度换算）",
            )
        }

        // 刻度区：从屏幕最左边缘开始，可横向滚动
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.horizontalScroll(scrollState)) {
                Canvas(
                    Modifier
                        .width(canvasWidthDp)
                        .fillMaxHeight(),
                ) {
                    val step = unitPx / subdivisions
                    val majorLen = 64f
                    val midLen = 40f
                    val minorLen = 22f

                    // 贴边的基准线
                    drawLine(
                        color = tickColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 3f,
                    )

                    var index = 0
                    var x = 0f
                    while (x <= size.width) {
                        val isMajor = index % subdivisions == 0
                        val isMid = !isMajor && index % (subdivisions / 2) == 0
                        val len = when {
                            isMajor -> majorLen
                            isMid -> midLen
                            else -> minorLen
                        }
                        drawLine(
                            color = tickColor,
                            start = Offset(x, 0f),
                            end = Offset(x, len),
                            strokeWidth = when {
                                isMajor -> 3f
                                isMid -> 2f
                                else -> 1.2f
                            },
                            cap = StrokeCap.Butt,
                        )
                        if (isMajor) {
                            val label = (index / subdivisions).toString()
                            val layout = measurer.measure(AnnotatedString(label), style = labelStyle)
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(x + 6f, majorLen + 6f),
                            )
                        }
                        x += step
                        index++
                    }
                }
            }
        }

        // 单位切换固定底部
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedTabs(
                    options = RulerUnit.entries,
                    selected = unit,
                    label = { it.label },
                    onSelect = { unit = it },
                )
            }
        }
    }
}

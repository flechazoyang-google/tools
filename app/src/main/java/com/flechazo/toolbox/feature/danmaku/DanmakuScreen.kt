package com.flechazo.toolbox.feature.danmaku

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.BottomActionBar
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import javax.inject.Inject

data class DanmakuUiState(
    val text: String = "Hello World",
    val speed: Speed = Speed.NORMAL,
    val textSize: TextSize = TextSize.LARGE,    val backgroundColor: BgColor = BgColor.BLACK,
    val textColor: TxtColor = TxtColor.WHITE,
    val isPlaying: Boolean = false,
    val isFullscreen: Boolean = false,
)

enum class Speed(val label: String, val duration: Int) {
    SLOW("慢", 8000), NORMAL("中", 5000), FAST("快", 3000),
}

enum class TextSize(val label: String, val size: Int) {
    SMALL("小", 48), MEDIUM("中", 72), LARGE("大", 96), HUGE("超大", 128),
}

enum class BgColor(val label: String, val color: Long) {
    BLACK("黑", 0xFF000000), RED("红", 0xFFE53935),
    GREEN("绿", 0xFF43A047), BLUE("蓝", 0xFF1E88E5),
}

enum class TxtColor(val label: String, val color: Long) {
    WHITE("白", 0xFFFFFFFF), YELLOW("黄", 0xFFFFEB3B),
    CYAN("青", 0xFF00BCD4), MAGENTA("洋红", 0xFFE040FB),
}

@HiltViewModel
class DanmakuViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DanmakuUiState())
    val state: StateFlow<DanmakuUiState> = _state

    fun setText(text: String) { _state.value = _state.value.copy(text = text) }
    fun setSpeed(speed: Speed) { _state.value = _state.value.copy(speed = speed) }
    fun setTextSize(size: TextSize) { _state.value = _state.value.copy(textSize = size) }
    fun setBgColor(color: BgColor) { _state.value = _state.value.copy(backgroundColor = color) }
    fun setTextColor(color: TxtColor) { _state.value = _state.value.copy(textColor = color) }
    fun togglePlay() { _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying) }
    fun stop() { _state.value = _state.value.copy(isPlaying = false) }
    fun setFullscreen(fullscreen: Boolean) {
        _state.value = _state.value.copy(isFullscreen = fullscreen)
    }
}

@Composable
fun DanmakuScreen(onBack: () -> Unit, viewModel: DanmakuViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 全屏时隐藏系统栏、保持常亮，并强制横屏（手持弹幕的用法就是横着举手机）
    val view = LocalView.current
    DisposableEffect(state.isFullscreen) {
        val activity = view.context as? android.app.Activity
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (state.isFullscreen) {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (state.isFullscreen) {
        FullscreenDanmaku(
            state = state,
            onExit = { viewModel.setFullscreen(false) },
        )
    } else {
        ToolScaffold(
            title = "手持弹幕",
            onBack = onBack,
            subtitle = "全屏滚动文字，演唱会/接机必备",
            actions = {
                // 顶栏常驻全屏入口：底部按钮在内容较长时需要滚动才能点到
                IconButton(onClick = { viewModel.setFullscreen(true) }) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "全屏")
                }
            },
        ) {
            // 预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Color(state.backgroundColor.color)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isPlaying) {
                    ScrollingText(
                        text = state.text,
                        textColor = Color(state.textColor.color),
                        textSize = state.textSize.size,
                        duration = state.speed.duration,
                    )
                } else {
                    Text(
                        text = state.text,
                        color = Color(state.textColor.color),
                        fontSize = (state.textSize.size / 2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 全屏按钮
                IconButton(
                    onClick = { viewModel.setFullscreen(true) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color.White,
                    )
                }
            }

            // 设置
            ToolSectionCard(title = "文字内容") {
                ToolTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ToolSectionCard(title = "滚动速度") {
                SegmentedTabs(
                    options = Speed.entries.toList(),
                    selected = state.speed,
                    label = { it.label },
                    onSelect = viewModel::setSpeed,
                )
            }

            ToolSectionCard(title = "文字大小") {
                SegmentedTabs(
                    options = TextSize.entries.toList(),
                    selected = state.textSize,
                    label = { it.label },
                    onSelect = viewModel::setTextSize,
                )
            }

            ToolSectionCard(title = "背景颜色") {
                ColorPicker(
                    options = BgColor.entries.toList(),
                    selected = state.backgroundColor,
                    onSelect = viewModel::setBgColor,
                )
            }

            ToolSectionCard(title = "文字颜色") {
                ColorPicker(
                    options = TxtColor.entries.toList(),
                    selected = state.textColor,
                    onSelect = viewModel::setTextColor,
                )
            }

            BottomActionBar(
                primaryLabel = if (state.isPlaying) "停止" else "开始滚动",
                onPrimary = viewModel::togglePlay,
                secondaryLabel = "全屏",
                onSecondary = { viewModel.setFullscreen(true) },
            )
        }
    }
}

@Composable
private fun <T> ColorPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val color = when (option) {
                is BgColor -> option.color
                is TxtColor -> option.color
                else -> 0xFF000000
            }
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(Color(color))
                    .then(if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    } else Modifier)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                val label = when (option) {
                    is BgColor -> option.label
                    is TxtColor -> option.label
                    else -> ""
                }
                Text(
                    label,
                    color = if (color == 0xFF000000L || color == 0xFF1E88E5L) Color.White else Color.Black,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ScrollingText(
    text: String,
    textColor: Color,
    textSize: Int,
    duration: Int,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val sizeSp = textSize.sp
        // 按实际文字宽度计算滚动距离，保证整段文字完全进出画面（旧实现用屏幕宽度，
        // 长文本永远走不完、短文本有空档）
        val textWidthPx = remember(text, sizeSp) {
            measurer.measure(AnnotatedString(text), style = TextStyle(fontSize = sizeSp))
                .size.width.toFloat()
        }
        val infiniteTransition = rememberInfiniteTransition(label = "scroll")
        val offsetX by infiniteTransition.animateFloat(
            initialValue = boxWidthPx,
            targetValue = -textWidthPx,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "offset",
        )
        Text(
            text = text,
            color = textColor,
            fontSize = sizeSp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) },
        )
    }
}

@Composable
private fun FullscreenDanmaku(
    state: DanmakuUiState,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(state.backgroundColor.color))
            .clickable { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        // 字号同时受屏幕高度约束，避免横屏时文字超出屏幕
        val sizeSp = minOf(state.textSize.size.toFloat(), maxHeight.value * 0.75f).sp

        if (state.isPlaying) {
            val textWidthPx = remember(state.text, sizeSp) {
                measurer.measure(AnnotatedString(state.text), style = TextStyle(fontSize = sizeSp))
                    .size.width.toFloat()
            }
            val infiniteTransition = rememberInfiniteTransition(label = "fullscreen_scroll")
            val offsetX by infiniteTransition.animateFloat(
                initialValue = boxWidthPx,
                targetValue = -textWidthPx,
                animationSpec = infiniteRepeatable(
                    animation = tween(state.speed.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "offset",
            )
            Text(
                text = state.text,
                color = Color(state.textColor.color),
                fontSize = sizeSp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) },
            )
        } else {
            Text(
                text = state.text,
                color = Color(state.textColor.color),
                fontSize = sizeSp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

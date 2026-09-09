package com.flechazo.toolbox.feature.color_picker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** 放大镜直径。 */
private val LOUPE_SIZE = 96.dp

/** 放大倍率：放大镜内 1 个源像素占多少屏幕像素。 */
private const val LOUPE_MAGNIFICATION = 6f

/** 放大镜取样的最小源边长（像素），避免小图时取样区域退化。 */
private const val LOUPE_MIN_SOURCE_SIDE = 8

/** 预览区最大高度，长图不至于把整页撑爆（与设计稿"预览区固定最大高度"一致）。 */
private val PREVIEW_MAX_HEIGHT = 420.dp

/** 放大镜默认抬高量，避免被手指挡住。 */
private val LOUPE_LIFT = 72.dp

/**
 * 取色器状态。不可变数据类，全部由 [ColorPickerViewModel] 持有，
 * 因此取色结果与历史色板都能跨重组存活。
 */
data class ColorUiState(
    val bitmap: Bitmap? = null,
    val argb: Int? = null,
    /** 最近取到的颜色，去重、最近优先、最多 [ColorPickerViewModel.MAX_RECENT] 个。 */
    val recent: List<Int> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ColorPickerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ColorUiState())
    val state: StateFlow<ColorUiState> = _state

    /** 正在进行的解码任务；换图时先取消，避免旧图覆盖新图、也避免重复解码。 */
    private var loadJob: Job? = null

    /** 请求序号，配合 [loadJob] 判定回调是否属于"当前这次选图"。 */
    private var requestSeq = 0

    /**
     * 解码 [uri] 进入取色状态。
     *
     * 使用 [ImageUtils.loadScaled]：内部按需降采样 + 纠正 EXIF，且失败返回 null 而不是抛异常，
     * 因此不会像原来的 `openInputStream(uri)!! + decodeStream` 那样 OOM / 崩溃。
     * 历史色板在换图时保留。
     */
    fun load(uri: Uri, context: Context) {
        loadJob?.cancel()
        val requestId = ++requestSeq
        val appContext = context.applicationContext
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(loading = true, error = null, bitmap = null, argb = null)
            }
            val bmp = ImageUtils.loadScaled(appContext, uri)
            // loadScaled 内部用 runCatching 包住了 withContext，取消也会被吞成 null，
            // 所以这里必须自己确认这次请求仍然是当前请求。
            if (requestId != requestSeq || !isActive) return@launch
            _state.update { current ->
                if (bmp == null) {
                    current.copy(loading = false, bitmap = null, error = "图片加载失败，请换一张图片重试")
                } else {
                    current.copy(loading = false, bitmap = bmp, error = null)
                }
            }
        }
    }

    /** 取色：入参为图片显示区域内的相对坐标（0..1），像素映射使用解码后位图的实际尺寸。 */
    fun pickAt(fractionX: Float, fractionY: Float) {
        val bmp = _state.value.bitmap ?: return
        if (bmp.width <= 0 || bmp.height <= 0) return
        val x = pixelIndex(fractionX, bmp.width)
        val y = pixelIndex(fractionY, bmp.height)
        commit(bmp.getPixel(x, y))
    }

    /** 点击历史色板重新选中该颜色（不改变历史顺序）。 */
    fun selectRecent(argb: Int) {
        _state.update { it.copy(argb = argb) }
    }

    /** 清空历史色板。 */
    fun clearRecent() {
        _state.update { it.copy(recent = emptyList()) }
    }

    /** 清空当前图片与取色结果，保留历史色板。 */
    fun reset() {
        loadJob?.cancel()
        requestSeq++
        _state.update {
            it.copy(bitmap = null, argb = null, loading = false, error = null)
        }
    }

    private fun commit(argb: Int) {
        _state.update { current ->
            val recent = (listOf(argb) + current.recent.filterNot { it == argb }).take(MAX_RECENT)
            current.copy(argb = argb, recent = recent)
        }
    }

    companion object {
        /** 历史色板保留的最近颜色数量。 */
        const val MAX_RECENT = 8
    }
}

/** 把 0..1 的相对坐标映射为像素下标，越界时夹紧到有效范围。 */
private fun pixelIndex(fraction: Float, size: Int): Int {
    if (size <= 0) return 0
    return (fraction * (size - 1)).toInt().coerceIn(0, size - 1)
}

/**
 * 图片在布局框内的实际绘制矩形（[ContentScale.Fit] 居中留白）。
 * 取色与放大镜都用它做坐标映射，因此预览区被限高时像素定位依然准确。
 */
private fun imageDrawRect(layout: IntSize, sourceWidth: Int, sourceHeight: Int): Rect {
    if (layout.width <= 0 || layout.height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return Rect.Zero
    val scale = minOf(layout.width.toFloat() / sourceWidth, layout.height.toFloat() / sourceHeight)
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    val left = (layout.width - width) / 2f
    val top = (layout.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/** 布局坐标 -> 图片内相对坐标（0..1）。 */
private fun Rect.toFraction(position: Offset): Offset {
    if (width <= 0f || height <= 0f) return Offset.Zero
    return Offset(
        ((position.x - left) / width).coerceIn(0f, 1f),
        ((position.y - top) / height).coerceIn(0f, 1f),
    )
}

@Composable
fun ColorPickerScreen(onBack: () -> Unit, viewModel: ColorPickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.load(it, context) } }

    val openPicker: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    ToolScaffold(
        title = "取色器",
        subtitle = "从图片任意位置采样颜色，支持 HEX 与 RGB 复制",
        onBack = onBack,
    ) {
        val bmp = state.bitmap
        val error = state.error
        when {
            bmp != null -> {
                val argb = state.argb
                if (argb != null) {
                    PickedColorSection(
                        argb = argb,
                        onCopyHex = {
                            clipboard.setText(AnnotatedString(hexOf(argb)))
                        },
                        onCopyRgb = {
                            clipboard.setText(AnnotatedString(rgbOf(argb)))
                        },
                    )
                }
                if (state.recent.isNotEmpty()) {
                    RecentSwatchesCard(
                        recent = state.recent,
                        selected = argb,
                        onSelect = viewModel::selectRecent,
                        onClear = viewModel::clearRecent,
                    )
                }
                PickerImageCard(
                    bitmap = bmp,
                    onPick = viewModel::pickAt,
                    onChangeImage = openPicker,
                    onReset = viewModel::reset,
                )
            }

            state.loading -> FeedbackBlock(
                text = "正在加载图片…",
                type = FeedbackType.LOADING,
            )

            error != null -> FeedbackBlock(
                text = error,
                type = FeedbackType.ERROR,
                actionLabel = "重新选择图片",
                onAction = openPicker,
            )

            else -> ToolSectionCard(title = "开始") {
                Text(
                    "从相册选择一张图片，按住图片拖动可放大取色，松开即取色",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = openPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) { Text("选择图片") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 取色结果 + 复制
// ---------------------------------------------------------------------------

private fun hexOf(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

private fun rgbOf(argb: Int): String =
    "${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}"

@Composable
private fun PickedColorSection(
    argb: Int,
    onCopyHex: () -> Unit,
    onCopyRgb: () -> Unit,
) {
    val color = Color(argb)
    val hex = hexOf(argb)
    ResultCard(
        label = "已选颜色",
        value = hex,
        caption = "RGB(${rgbOf(argb)})",
    )
    ToolSectionCard(title = "操作") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(Modifier.size(48.dp).clip(CircleShape)) { drawCircle(color) }
            Column(Modifier.weight(1f)) {
                Text(hex, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopyHex, modifier = Modifier.weight(1f)) { Text("复制 HEX") }
            OutlinedButton(onClick = onCopyRgb, modifier = Modifier.weight(1f)) { Text("复制 RGB") }
        }
    }
}

// ---------------------------------------------------------------------------
// 最近取色色板条
// ---------------------------------------------------------------------------

@Composable
private fun RecentSwatchesCard(
    recent: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
) {
    ToolSectionCard(
        title = "最近取色（最多 ${ColorPickerViewModel.MAX_RECENT} 个）",
        trailing = {
            TextButton(onClick = onClear) { Text("清空") }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            recent.forEach { argb ->
                val isSelected = argb == selected
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(argb) },
                )
            }
        }
        Text(
            "点击色块可重新选中该颜色",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 图片预览 + 放大镜跟随取色
// ---------------------------------------------------------------------------

@Composable
private fun PickerImageCard(
    bitmap: Bitmap,
    onPick: (Float, Float) -> Unit,
    onChangeImage: () -> Unit,
    onReset: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    // 手指在布局坐标系中的位置；null 表示当前没有按下，放大镜隐藏。
    var touch by remember(bitmap) { mutableStateOf<Offset?>(null) }
    var layoutSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val drawRect = remember(layoutSize, bitmap) {
        imageDrawRect(layoutSize, bitmap.width, bitmap.height)
    }
    val loupePx = with(density) { LOUPE_SIZE.toPx() }
    val liftPx = with(density) { LOUPE_LIFT.toPx() }

    ToolSectionCard(title = "图片（按住拖动可放大取色）") {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .onSizeChanged { layoutSize = it },
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "待取色图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PREVIEW_MAX_HEIGHT)
                    .pointerInput(bitmap, drawRect) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            touch = down.position
                            // 消费按下事件，避免拖动取色时外层页面跟着滚动
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // 松开：确定最终取色点（点击也是按下即抬起）
                                    touch = change.position
                                    val fraction = drawRect.toFraction(change.position)
                                    onPick(fraction.x, fraction.y)
                                    change.consume()
                                    break
                                }
                                if (change.position != change.previousPosition) {
                                    touch = change.position
                                    change.consume()
                                }
                            }
                            touch = null
                        }
                    },
            )

            val current = touch
            if (current != null && drawRect.width > 0f && drawRect.height > 0f) {
                val half = loupePx / 2f
                val minX = drawRect.left + half
                val maxX = drawRect.right - half
                val centerX = if (maxX >= minX) current.x.coerceIn(minX, maxX) else drawRect.center.x
                val above = current.y - liftPx
                val preferredY = if (above < drawRect.top + half) current.y + liftPx else above
                val minY = drawRect.top + half
                val maxY = drawRect.bottom - half
                val centerY = if (maxY >= minY) preferredY.coerceIn(minY, maxY) else drawRect.center.y

                ColorLoupe(
                    image = imageBitmap,
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    fraction = drawRect.toFraction(current),
                    modifier = Modifier
                        .offset { IntOffset((centerX - half).roundToInt(), (centerY - half).roundToInt()) }
                        .size(LOUPE_SIZE)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }

        Text(
            "按住图片拖动可放大取色，松开即取色；直接点击也可取色",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChangeImage, modifier = Modifier.weight(1f)) { Text("换一张图片") }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("清空") }
        }
    }
}

/**
 * 放大镜：把手指附近 [LOUPE_MIN_SOURCE_SIDE]..n 个源像素放大到 96dp 圆内，
 * 中心十字线 + 像素描边框标出将被取色的那一个像素。
 */
@Composable
private fun ColorLoupe(
    image: ImageBitmap,
    sourceWidth: Int,
    sourceHeight: Int,
    fraction: Offset,
    modifier: Modifier = Modifier,
) {
    val crosshairColor = MaterialTheme.colorScheme.surface
    val crosshairOutline = MaterialTheme.colorScheme.outline
    val strokeWidth = 1.dp

    Canvas(modifier) {
        val maxSide = minOf(sourceWidth, sourceHeight).coerceAtLeast(1)
        val desired = (size.width / LOUPE_MAGNIFICATION).toInt()
        val sourceSide = desired.coerceIn(minOf(LOUPE_MIN_SOURCE_SIDE, maxSide), maxSide)

        // 被取色像素的中心（与 ViewModel.pickAt 的映射完全一致）
        val centerPixelX = pixelIndex(fraction.x, sourceWidth) + 0.5f
        val centerPixelY = pixelIndex(fraction.y, sourceHeight) + 0.5f
        val left = (centerPixelX - sourceSide / 2f).roundToInt()
            .coerceIn(0, (sourceWidth - sourceSide).coerceAtLeast(0))
        val top = (centerPixelY - sourceSide / 2f).roundToInt()
            .coerceIn(0, (sourceHeight - sourceSide).coerceAtLeast(0))

        drawImage(
            image = image,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(sourceSide, sourceSide),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None,
        )

        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val stroke = strokeWidth.toPx()
        // 先画深色描边再画浅色线，保证在任何底色上都看得清
        drawLine(crosshairOutline, Offset(centerX, 0f), Offset(centerX, size.height), stroke * 3)
        drawLine(crosshairOutline, Offset(0f, centerY), Offset(size.width, centerY), stroke * 3)
        drawLine(crosshairColor, Offset(centerX, 0f), Offset(centerX, size.height), stroke)
        drawLine(crosshairColor, Offset(0f, centerY), Offset(size.width, centerY), stroke)

        // 标出被取色像素的边界
        val pixelScale = size.width / sourceSide
        drawRect(
            color = crosshairOutline,
            topLeft = Offset(centerX - pixelScale / 2f, centerY - pixelScale / 2f),
            size = Size(pixelScale, pixelScale),
            style = Stroke(width = stroke),
        )
    }
}

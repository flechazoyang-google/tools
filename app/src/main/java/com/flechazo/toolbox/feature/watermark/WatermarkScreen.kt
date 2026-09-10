package com.flechazo.toolbox.feature.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class WatermarkStyle(val label: String) { SINGLE("单枚"), TILE("平铺") }

/** 九宫格锚点，顺序即 3×3 网格的阅读顺序（左上 → 右下）。 */
enum class WatermarkPosition(val label: String) {
    TOP_LEFT("左上"),
    TOP_CENTER("上中"),
    TOP_RIGHT("右上"),
    CENTER_LEFT("左中"),
    CENTER("居中"),
    CENTER_RIGHT("右中"),
    BOTTOM_LEFT("左下"),
    BOTTOM_CENTER("下中"),
    BOTTOM_RIGHT("右下"),
}

/** 直接字号（输出图像素），与图宽无关，滑杆全程有效。 */
internal const val MIN_TEXT_SIZE = 12f
internal const val MAX_TEXT_SIZE = 160f
private const val DEFAULT_TEXT_SIZE = 48f

/** 边距 = 字号 × 该系数，即水印墨迹到画布边缘的实际间隙。 */
private const val MARGIN_FACTOR = 0.6f

/** 连续拖动文字/滑杆时的合并窗口，避免每帧都分配一张全尺寸 Bitmap。 */
private const val RENDER_DEBOUNCE_MS = 80L

data class WatermarkUiState(
    val source: Bitmap? = null,
    val text: String = "@ Toolbox",
    val style: WatermarkStyle = WatermarkStyle.SINGLE,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val opacity: Float = 0.5f,
    val textSize: Float = DEFAULT_TEXT_SIZE,
    val result: Bitmap? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    val savedMessage: String? = null,
    val savedOk: Boolean? = null,
)

/** 文字墨迹的绘制原点：x 为 drawText 的起点，baseline 为基线 y。 */
internal data class TextAnchor(val x: Float, val baseline: Float)

/**
 * 由 [Paint.getTextBounds] 得到的墨迹包围盒（坐标相对基线）反推绘制锚点，
 * 保证九个格子的水印墨迹到画布边缘的间隙都等于 [margin]。
 */
internal fun watermarkAnchor(
    position: WatermarkPosition,
    canvasWidth: Int,
    canvasHeight: Int,
    bounds: Rect,
    margin: Float,
): TextAnchor {
    val inkWidth = bounds.width().toFloat()
    val inkHeight = bounds.height().toFloat()
    val inkLeft = bounds.left.toFloat()
    val inkTop = bounds.top.toFloat()

    val x = when (position) {
        // 墨迹左边缘落在 margin
        WatermarkPosition.TOP_LEFT,
        WatermarkPosition.CENTER_LEFT,
        WatermarkPosition.BOTTOM_LEFT -> margin - inkLeft
        // 墨迹水平居中
        WatermarkPosition.TOP_CENTER,
        WatermarkPosition.CENTER,
        WatermarkPosition.BOTTOM_CENTER -> (canvasWidth - inkWidth) / 2f - inkLeft
        // 墨迹右边缘落在 width - margin
        else -> canvasWidth - margin - bounds.right.toFloat()
    }

    val baseline = when (position) {
        // 墨迹上边缘落在 margin
        WatermarkPosition.TOP_LEFT,
        WatermarkPosition.TOP_CENTER,
        WatermarkPosition.TOP_RIGHT -> margin - inkTop
        // 墨迹垂直居中
        WatermarkPosition.CENTER_LEFT,
        WatermarkPosition.CENTER,
        WatermarkPosition.CENTER_RIGHT -> (canvasHeight - inkHeight) / 2f - inkTop
        // 墨迹下边缘落在 height - margin（用 bounds.bottom 而不是 descent）
        else -> canvasHeight - margin - bounds.bottom.toFloat()
    }

    return TextAnchor(x, baseline)
}

@HiltViewModel
class WatermarkViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(WatermarkUiState())
    val state: StateFlow<WatermarkUiState> = _state

    /** 只保留最新一次渲染：新任务启动前先取消旧任务。 */
    private var renderJob: Job? = null

    /** 渲染代次，用于丢弃已被取消但已算出结果的旧任务。 */
    private var renderToken = 0

    /** 解码任务：连续选图时只保留最后一次，避免旧图覆盖新图。 */
    private var loadJob: Job? = null

    fun load(context: Context, uri: Uri) {
        renderJob?.cancel()
        renderJob = null
        loadJob?.cancel()
        // 让仍在途的旧渲染失效，防止旧图的水印结果写回新状态。
        renderToken++
        val previous = _state.value
        _state.value = previous.copy(
            source = null,
            result = null,
            loading = true,
            error = null,
            savedMessage = null,
            savedOk = null,
        )
        loadJob = viewModelScope.launch {
            val bmp = ImageUtils.loadScaled(context, uri)
            if (bmp == null) {
                _state.value = _state.value.copy(
                    source = null,
                    result = null,
                    loading = false,
                    error = "图片读取失败，请换一张图片重试",
                )
                return@launch
            }
            _state.value = WatermarkUiState(
                source = bmp,
                text = previous.text,
                style = previous.style,
                position = previous.position,
                opacity = previous.opacity,
                textSize = previous.textSize,
                loading = false,
            )
            render()
        }
    }

    fun onTextChange(v: String) {
        _state.value = _state.value.copy(text = v)
        render()
    }

    fun onStyleChange(v: WatermarkStyle) {
        _state.value = _state.value.copy(style = v)
        render()
    }

    fun onPositionChange(v: WatermarkPosition) {
        _state.value = _state.value.copy(position = v)
        render()
    }

    fun onOpacityChange(v: Float) {
        _state.value = _state.value.copy(opacity = v)
        render()
    }

    fun onTextSizeChange(v: Float) {
        _state.value = _state.value.copy(textSize = v.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
        render()
    }

    private fun render() {
        // 先取消上一次渲染：慢的旧任务不会再写回预览。
        renderJob?.cancel()
        renderJob = null

        val snapshot = _state.value
        val src = snapshot.source ?: return
        if (snapshot.text.isBlank()) {
            _state.value = snapshot.copy(result = null)
            return
        }

        val token = ++renderToken
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            // 合并连续输入，避免每次按键/每帧滑杆都分配一张全尺寸 Bitmap。
            delay(RENDER_DEBOUNCE_MS)
            if (!isActive) return@launch

            val out = drawWatermark(src, snapshot)
            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                // 期间若有更新的渲染，代次已变，丢弃本次结果。
                if (token != renderToken) return@withContext
                _state.value = _state.value.copy(
                    result = out,
                    savedMessage = null,
                    savedOk = null,
                )
            }
        }
    }

    private fun drawWatermark(src: Bitmap, s: WatermarkUiState): Bitmap {
        val config = src.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
        val out = src.copy(config, true) ?: Bitmap.createBitmap(src.width, src.height, config).also {
            Canvas(it).drawBitmap(src, 0f, 0f, null)
        }
        val canvas = Canvas(out)
        val alpha = (s.opacity * 255f).roundToInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 255, 255, 255)
            textSize = s.textSize
            setShadowLayer(s.textSize / 12f, 0f, 0f, Color.argb(alpha, 0, 0, 0))
        }

        // 真实墨迹包围盒：宽高与 top/bottom 都用于定位，避免用 descent 造成的偏移。
        val bounds = Rect()
        paint.getTextBounds(s.text, 0, s.text.length, bounds)
        val inkWidth = bounds.width().toFloat()
        val inkHeight = bounds.height().toFloat()
        val margin = s.textSize * MARGIN_FACTOR

        if (s.style == WatermarkStyle.SINGLE) {
            val anchor = watermarkAnchor(s.position, out.width, out.height, bounds, margin)
            canvas.drawText(s.text, anchor.x, anchor.baseline, paint)
        } else {
            val stepX = inkWidth + s.textSize * 1.2f
            val stepY = inkHeight + s.textSize * 1.6f
            var row = 0
            var baseline = margin - bounds.top.toFloat()
            while (baseline - bounds.top < out.height + stepY) {
                var x = margin - bounds.left.toFloat() - (stepX / 2f) * (row % 2)
                while (x - bounds.left < out.width + stepX) {
                    canvas.drawText(s.text, x, baseline, paint)
                    x += stepX
                }
                baseline += stepY
                row++
            }
        }
        return out
    }

    fun save(context: Context) {
        val s = _state.value
        val bmp = s.result ?: return
        if (s.saving) return
        _state.value = s.copy(saving = true, savedMessage = null, savedOk = null)
        viewModelScope.launch {
            // saveBitmap 内部走 Dispatchers.IO，并真实返回成功/失败。
            val uri = ImageUtils.saveBitmap(context, bmp, mime = "image/png")
            _state.value = _state.value.copy(
                saving = false,
                savedOk = uri != null,
                savedMessage = if (uri != null) {
                    "已保存到相册 Pictures/Toolbox"
                } else {
                    "保存失败，请检查存储权限后重试"
                },
            )
        }
    }
}

@Composable
fun WatermarkScreen(onBack: () -> Unit, viewModel: WatermarkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.load(context, it) }
    }
    val launchPicker: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    ToolScaffold(
        title = "图片加水印",
        subtitle = "九宫格定位 · 单枚/平铺 · 实时预览",
        onBack = onBack,
    ) {
        // 输入
        ToolSectionCard(title = "输入") {
            OutlinedButton(
                onClick = launchPicker,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.source == null) "选择图片" else "换一张图片") }
        }

        if (state.loading) {
            FeedbackBlock(text = "正在读取图片…", type = FeedbackType.LOADING)
        }

        state.error?.let { message ->
            FeedbackBlock(
                text = message,
                type = FeedbackType.ERROR,
                actionLabel = "重新选择",
                onAction = launchPicker,
            )
        }

        // 配置
        ToolSectionCard(title = "水印文字") {
            ToolTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("水印文字") },
                singleLine = true,
                isError = state.text.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.text.isBlank()) {
                Text(
                    "请输入水印文字",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SegmentedTabs(
                options = WatermarkStyle.entries,
                selected = state.style,
                label = { it.label },
                onSelect = viewModel::onStyleChange,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 位置 + 样式
        ToolSectionCard(title = "位置与样式") {
            val tiled = state.style == WatermarkStyle.TILE
            Text(
                if (tiled) "平铺模式自动铺满，无需选择位置" else "水印位置（九宫格点选）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PositionGrid(
                selected = state.position,
                enabled = !tiled,
                onSelect = viewModel::onPositionChange,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Text("不透明度：${(state.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.opacity,
                onValueChange = viewModel::onOpacityChange,
                valueRange = 0.1f..1f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            Text(textSizeLabel(state), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.textSize,
                onValueChange = viewModel::onTextSizeChange,
                valueRange = MIN_TEXT_SIZE..MAX_TEXT_SIZE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        // 预览
        val show = state.result ?: state.source
        show?.let { bmp ->
            ToolSectionCard(title = if (state.result != null) "预览（带水印）" else "预览（原图）") {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 保存
        state.result?.let {
            ToolSectionCard(title = "保存") {
                Button(
                    onClick = { viewModel.save(context) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.saving) "正在保存…" else "保存到相册")
                }
                state.savedMessage?.let { msg ->
                    Text(
                        msg,
                        color = if (state.savedOk == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** 3×3 位置选择器：小格子点选，平铺模式下整体禁用。 */
@Composable
private fun PositionGrid(
    selected: WatermarkPosition,
    enabled: Boolean,
    onSelect: (WatermarkPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellShape = ToolShape.sm
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WatermarkPosition.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { position ->
                    val isSelected = position == selected
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(cellShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, cellShape)
                                }
                            )
                            .alpha(if (enabled) 1f else 0.4f)
                            .selectable(
                                selected = isSelected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(position) },
                            )
                            .semantics { contentDescription = "水印位置${position.label}" }
                    )
                }
            }
        }
    }
}

/** 显示真实字号，并附带相对图宽的比例，方便判断视觉效果。 */
private fun textSizeLabel(state: WatermarkUiState): String {
    val px = state.textSize.roundToInt()
    val width = state.source?.width ?: 0
    if (width <= 0) return "文字大小：${px}px"
    val ratio = state.textSize / width * 100f
    return "文字大小：${px}px（约占图宽 ${String.format(Locale.ROOT, "%.1f", ratio)}%）"
}

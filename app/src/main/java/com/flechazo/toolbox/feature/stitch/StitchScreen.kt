package com.flechazo.toolbox.feature.stitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 单张已选图片：原图 Uri + 用于列表展示的小缩略图。 */
data class StitchItem(val uri: Uri, val thumb: Bitmap)

data class StitchUiState(
    val items: List<StitchItem> = emptyList(),
    val vertical: Boolean = true,
    val spacing: Int = 0,
    val result: Bitmap? = null,
    val loading: Boolean = false,
    val savedMessage: String? = null,
    val saveFailed: Boolean = false,
    val error: String? = null,
)

/** 输出上限：约 4000 万像素、单边 16384px，超出直接拒绝而不是 OOM。 */
private const val MAX_OUTPUT_PIXELS = 40_000_000L
private const val MAX_OUTPUT_SIDE = 16_384
private const val MAX_ITEMS = 9

@HiltViewModel
class StitchViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(StitchUiState())
    val state: StateFlow<StitchUiState> = _state

    private var job: Job? = null

    fun setDirection(vertical: Boolean) {
        if (vertical == _state.value.vertical) return
        _state.value = _state.value.copy(vertical = vertical, result = null, savedMessage = null)
        stitch()
    }

    fun setSpacing(value: Int) {
        _state.value = _state.value.copy(spacing = value, result = null, savedMessage = null)
    }

    /** 间距滑杆松手后重新拼接。 */
    fun restitch() {
        stitch()
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val existing = _state.value.items.map { it.uri }.toSet()
            val fresh = uris.filter { it !in existing }
            val room = (MAX_ITEMS - _state.value.items.size).coerceAtLeast(0)
            val accepted = fresh.take(room)
            val newItems = accepted.mapNotNull { uri ->
                ImageUtils.loadScaled(context, uri, maxSide = 160)?.let { StitchItem(uri, it) }
            }
            val overflow = fresh.size - accepted.size
            _state.value = _state.value.copy(
                items = _state.value.items + newItems,
                loading = false,
                error = when {
                    newItems.isEmpty() -> "未能读取所选图片"
                    overflow > 0 -> "最多 $MAX_ITEMS 张，已忽略多出的 $overflow 张"
                    else -> null
                },
            )
            stitch()
        }
    }

    fun removeAt(index: Int) {
        val items = _state.value.items.toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        _state.value = _state.value.copy(items = items, result = null, savedMessage = null)
        stitch()
    }

    fun move(from: Int, to: Int) {
        val items = _state.value.items.toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
        _state.value = _state.value.copy(items = items, result = null, savedMessage = null)
        stitch()
    }

    fun clear() {
        job?.cancel()
        _state.value = StitchUiState(vertical = _state.value.vertical, spacing = _state.value.spacing)
    }

    private fun stitch() {
        val snapshot = _state.value
        if (snapshot.items.size < 2) return
        val uris = snapshot.items.map { it.uri }
        val vertical = snapshot.vertical
        val spacing = snapshot.spacing
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val decoded = uris.mapNotNull { ImageUtils.loadScaled(context, it, maxSide = 2048) }
            if (decoded.size < 2) {
                _state.value = _state.value.copy(loading = false, error = "至少需要 2 张有效图片")
                return@launch
            }
            val gaps = spacing * (decoded.size - 1)
            val merged = withContext(Dispatchers.Default) {
                if (vertical) {
                    val width = decoded.minOf { it.width }
                    val scaled = decoded.map { scaleToWidth(it, width) }
                    val height = scaled.sumOf { it.height } + gaps
                    if (width > MAX_OUTPUT_SIDE || height > MAX_OUTPUT_SIDE ||
                        width.toLong() * height > MAX_OUTPUT_PIXELS
                    ) {
                        null
                    } else {
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { out ->
                            val canvas = Canvas(out).apply { drawColor(Color.WHITE) }
                            var y = 0
                            scaled.forEach {
                                canvas.drawBitmap(it, 0f, y.toFloat(), null)
                                y += it.height + spacing
                            }
                        }
                    }
                } else {
                    val height = decoded.minOf { it.height }
                    val scaled = decoded.map { scaleToHeight(it, height) }
                    val width = scaled.sumOf { it.width } + gaps
                    if (height > MAX_OUTPUT_SIDE || width > MAX_OUTPUT_SIDE ||
                        width.toLong() * height > MAX_OUTPUT_PIXELS
                    ) {
                        null
                    } else {
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { out ->
                            val canvas = Canvas(out).apply { drawColor(Color.WHITE) }
                            var x = 0
                            scaled.forEach {
                                canvas.drawBitmap(it, x.toFloat(), 0f, null)
                                x += it.width + spacing
                            }
                        }
                    }
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                result = merged,
                error = if (merged == null) "拼接结果过大（超过 4000 万像素），请减少图片数量或间距" else null,
            )
        }
    }

    private fun scaleToWidth(bmp: Bitmap, width: Int): Bitmap =
        if (bmp.width == width) bmp
        else Bitmap.createScaledBitmap(bmp, width, maxOf(1, bmp.height * width / bmp.width), true)

    private fun scaleToHeight(bmp: Bitmap, height: Int): Bitmap =
        if (bmp.height == height) bmp
        else Bitmap.createScaledBitmap(bmp, maxOf(1, bmp.width * height / bmp.height), height, true)

    fun save() {
        val bmp = _state.value.result ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, savedMessage = null)
            val uri = ImageUtils.saveBitmap(context, bmp, "image/png")
            _state.value = _state.value.copy(
                loading = false,
                savedMessage = if (uri != null) "已保存到相册 Pictures/Toolbox" else "保存失败，请检查相册权限",
                saveFailed = uri == null,
            )
        }
    }
}

@Composable
fun StitchScreen(onBack: () -> Unit, viewModel: StitchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_ITEMS),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    ToolScaffold(
        title = "图片拼接",
        subtitle = "最多 $MAX_ITEMS 张本地拼接，导出到 Pictures/Toolbox",
        onBack = onBack,
    ) {
        ToolSectionCard(title = "方向与间距") {
            SegmentedTabs(
                options = listOf(true, false),
                selected = state.vertical,
                label = { if (it) "纵向" else "横向" },
                onSelect = viewModel::setDirection,
            )
            Text("间距：${state.spacing} px", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = state.spacing.toFloat(),
                onValueChange = { viewModel.setSpacing(it.toInt()) },
                onValueChangeFinished = viewModel::restitch,
                valueRange = 0f..64f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.items.isEmpty()) "添加图片" else "再加几张") }
                if (state.items.isNotEmpty()) {
                    OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) { Text("清空") }
                }
            }
        }

        if (state.items.isNotEmpty()) {
            ToolSectionCard(title = "已选 ${state.items.size} 张（可删除 / 调整顺序）") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.items, key = { _, item -> item.uri.toString() }) { index, item ->
                        Box(Modifier.size(96.dp)) {
                            Image(
                                bitmap = item.thumb.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = { viewModel.move(index, index - 1) },
                                    enabled = index > 0,
                                ) { Text("←") }
                                TextButton(onClick = { viewModel.removeAt(index) }) { Text("✕") }
                                TextButton(
                                    onClick = { viewModel.move(index, index + 1) },
                                    enabled = index < state.items.lastIndex,
                                ) { Text("→") }
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { FeedbackBlock(text = it, type = FeedbackType.ERROR) }

        if (state.loading) {
            FeedbackBlock(text = "拼接中…", type = FeedbackType.LOADING)
        }

        state.result?.let { bmp ->
            ToolSectionCard(title = "拼接结果（${bmp.width} × ${bmp.height}）") {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "拼接结果",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ToolSectionCard(title = "保存") {
                OutlinedButton(
                    onClick = { viewModel.save() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存到相册") }
                state.savedMessage?.let {
                    Text(
                        it,
                        color = if (state.saveFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (state.items.isEmpty() && !state.loading) {
            FeedbackBlock(text = "添加至少 2 张图片开始拼接", type = FeedbackType.EMPTY)
        }
    }
}

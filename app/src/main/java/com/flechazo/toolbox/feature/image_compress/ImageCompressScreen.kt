package com.flechazo.toolbox.feature.image_compress

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
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
import java.util.Locale
import javax.inject.Inject

/** 输出格式。PNG 忽略质量参数，WebP 需要 API 30+ 才有无损/有损细分。 */
enum class CompressFormatOption(val label: String) {
    JPEG("JPEG"), PNG("PNG"), WEBP("WebP"),
    ;

    fun toAndroidFormat(): Bitmap.CompressFormat = when (this) {
        JPEG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    fun mime(): String = when (this) {
        JPEG -> "image/jpeg"
        PNG -> "image/png"
        WEBP -> "image/webp"
    }
}

data class CompressUiState(
    val source: Bitmap? = null,
    val quality: Int = 70,
    val format: CompressFormatOption = CompressFormatOption.JPEG,
    val limitSize: Boolean = false,
    val maxSide: Int = 2048,
    val resultBytes: ByteArray? = null,
    val sourceSize: Long = 0,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val loading: Boolean = false,
    val savedMessage: String? = null,
    val saveFailed: Boolean = false,
    val error: String? = null,
) {
    val outputWidth: Int get() = minOf(source?.width ?: 0, if (limitSize) maxSide else Int.MAX_VALUE)
    val outputHeight: Int get() = minOf(source?.height ?: 0, if (limitSize) maxSide else Int.MAX_VALUE)
}

@HiltViewModel
class CompressViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state

    private var job: Job? = null

    fun load(context: Context, uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = CompressUiState(loading = true)
            val bitmap = ImageUtils.loadScaled(context, uri)
            if (bitmap == null) {
                _state.value = CompressUiState(error = "图片读取失败")
                return@launch
            }
            val originalSize = withContext(Dispatchers.IO) { uriSize(context, uri) }
            val bounds = ImageUtils.readBounds(context, uri)
            _state.value = CompressUiState(
                source = bitmap,
                sourceSize = originalSize,
                originalWidth = bounds?.first ?: bitmap.width,
                originalHeight = bounds?.second ?: bitmap.height,
            )
            recompress()
        }
    }

    fun setQuality(q: Int) {
        _state.value = _state.value.copy(quality = q)
    }

    fun setFormat(f: CompressFormatOption) {
        _state.value = _state.value.copy(format = f)
        recompress()
    }

    fun setLimitSize(enabled: Boolean) {
        _state.value = _state.value.copy(limitSize = enabled)
        recompress()
    }

    fun setMaxSide(side: Int) {
        _state.value = _state.value.copy(maxSide = side)
    }

    /** 质量/尺寸滑杆松手后调用，避免每一帧都重新编码。 */
    fun recompress() {
        val snapshot = _state.value
        val src = snapshot.source ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, savedMessage = null, saveFailed = false)
            val bytes = withContext(Dispatchers.Default) {
                val target = if (snapshot.limitSize && maxOf(src.width, src.height) > snapshot.maxSide) {
                    val scale = snapshot.maxSide.toFloat() / maxOf(src.width, src.height)
                    Bitmap.createScaledBitmap(
                        src,
                        (src.width * scale).toInt().coerceAtLeast(1),
                        (src.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    src
                }
                ImageUtils.compressToBytes(target, snapshot.format.toAndroidFormat(), snapshot.quality)
            }
            _state.value = _state.value.copy(
                resultBytes = bytes,
                loading = false,
                error = if (bytes == null) "压缩失败" else null,
            )
        }
    }

    fun save(context: Context) {
        val snapshot = _state.value
        val bytes = snapshot.resultBytes ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, savedMessage = null)
            val uri = ImageUtils.saveBytes(context, bytes, snapshot.format.mime())
            _state.value = _state.value.copy(
                loading = false,
                savedMessage = if (uri != null) "已保存到相册 Pictures/Toolbox" else "保存失败，请检查相册权限",
                saveFailed = uri == null,
            )
        }
    }

    private fun uriSize(context: Context, uri: Uri): Long =
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0
}

@Composable
fun ImageCompressScreen(onBack: () -> Unit, viewModel: CompressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.load(context, it) }
    }

    ToolScaffold(
        title = "图片压缩",
        subtitle = "本地压缩，零上传",
        onBack = onBack,
    ) {
        ToolSectionCard(title = "输入") {
            OutlinedButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.source == null) "选择图片" else "换一张图片") }
        }

        state.error?.let { FeedbackBlock(text = it, type = FeedbackType.ERROR) }

        state.source?.let { bmp ->
            ToolSectionCard(title = "预览") {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ToolSectionCard(title = "原图信息") {
                KeyValueRow(
                    label = "原始尺寸",
                    value = "${state.originalWidth} × ${state.originalHeight}",
                )
                KeyValueRow(label = "原始大小", value = formatSize(state.sourceSize))
                KeyValueRow(
                    label = "输出尺寸",
                    value = "${state.outputWidth} × ${state.outputHeight}",
                )
            }

            ToolSectionCard(title = "压缩配置") {
                SegmentedTabs(
                    options = CompressFormatOption.entries,
                    selected = state.format,
                    label = { it.label },
                    onSelect = viewModel::setFormat,
                )
                if (state.format == CompressFormatOption.PNG) {
                    Text(
                        "PNG 为无损格式，质量参数不生效",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("质量：${state.quality}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { viewModel.setQuality(it.toInt()) },
                    onValueChangeFinished = viewModel::recompress,
                    enabled = state.format != CompressFormatOption.PNG,
                    valueRange = 10f..100f,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                )
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("限制最大边", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "超出时等比缩小到 ${state.maxSide}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.limitSize, onCheckedChange = viewModel::setLimitSize)
                }
                if (state.limitSize) {
                    Slider(
                        value = state.maxSide.toFloat(),
                        onValueChange = { viewModel.setMaxSide(it.toInt()) },
                        onValueChangeFinished = viewModel::recompress,
                        valueRange = 640f..4096f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.loading) {
                FeedbackBlock(text = "压缩中…", type = FeedbackType.LOADING)
            }

            state.resultBytes?.let { bytes ->
                ResultCard(
                    label = "压缩后",
                    value = formatSize(bytes.size.toLong()),
                    caption = "${formatSize(state.sourceSize)} → ${formatSize(bytes.size.toLong())} · ${percent(state.sourceSize, bytes.size.toLong())}",
                )
                ToolSectionCard(title = "保存") {
                    Button(
                        onClick = { viewModel.save(context) },
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
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.2f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
}

private fun percent(before: Long, after: Long): String {
    if (before <= 0) return "无法比较"
    if (after >= before) return "体积变大 ${((after - before) * 100 / before)}%"
    return "压缩 ${((before - after) * 100 / before)}%"
}

package com.flechazo.toolbox.feature.qr_code

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class QrMode(val label: String) { GENERATE("生成"), SCAN("识别") }

data class QrUiState(
    val mode: QrMode = QrMode.GENERATE,
    val text: String = "",
    val bitmap: Bitmap? = null,
    val scanResult: String? = null,
    val scanError: Boolean = false,
    val loading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/** QR 版本 40 / 纠错等级 L / 字节模式的理论上限。 */
private const val MAX_QR_BYTES = 2953

@HiltViewModel
class QrViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(QrUiState())
    val state: StateFlow<QrUiState> = _state

    private var job: Job? = null

    fun onModeChange(mode: QrMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun onTextChange(value: String) {
        _state.value = _state.value.copy(text = value, saved = false, error = null)
    }

    fun generate() {
        val text = _state.value.text
        if (text.isBlank()) return
        val byteLength = text.toByteArray(Charsets.UTF_8).size
        if (byteLength > MAX_QR_BYTES) {
            _state.value = _state.value.copy(
                bitmap = null,
                error = "内容过长（$byteLength 字节，上限 $MAX_QR_BYTES 字节），请缩短后重试",
            )
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, saved = false)
            val bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val size = 760
                    val matrix = QRCodeWriter().encode(
                        text, BarcodeFormat.QR_CODE, size, size,
                        mapOf(
                            com.google.zxing.EncodeHintType.MARGIN to 4,
                            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                        ),
                    )
                    val pixels = IntArray(size * size)
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            pixels[y * size + x] = if (matrix[x, y]) {
                                android.graphics.Color.BLACK
                            } else {
                                android.graphics.Color.WHITE
                            }
                        }
                    }
                    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                        setPixels(pixels, 0, size, 0, 0, size, size)
                    }
                }.getOrNull()
            }
            _state.value = _state.value.copy(
                bitmap = bitmap,
                loading = false,
                error = if (bitmap == null) "生成失败，请缩短内容后重试" else null,
            )
        }
    }

    /** 把当前二维码保存到相册。 */
    fun saveToGallery(context: android.content.Context) {
        val bitmap = _state.value.bitmap ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val uri = ImageUtils.saveBitmap(context, bitmap, "image/png")
            _state.value = _state.value.copy(
                loading = false,
                saved = uri != null,
                error = if (uri == null) "保存失败，请检查相册权限" else null,
            )
        }
    }

    fun decode(uri: Uri, context: android.content.Context) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, scanResult = null, scanError = false)
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = ImageUtils.loadScaled(context, uri, maxSide = 2048)
                        ?: error("无法读取图片")
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val source = RGBLuminanceSource(width, height, pixels)
                    val hints = mapOf(
                        com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8",
                        com.google.zxing.DecodeHintType.TRY_HARDER to true,
                    )
                    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                scanResult = result.getOrNull()?.text,
                scanError = result.isFailure,
            )
        }
    }

    fun clearScan() { _state.value = _state.value.copy(scanResult = null, scanError = false) }
}

@Composable
fun QrCodeScreen(onBack: () -> Unit, viewModel: QrViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.decode(it, context) }
    }

    ToolScaffold(
        title = "二维码",
        subtitle = "UTF-8 编码，中文可正常识别",
        onBack = onBack,
    ) {
        SegmentedTabs(
            options = QrMode.entries,
            selected = state.mode,
            label = { it.label },
            onSelect = viewModel::onModeChange,
        )

        when (state.mode) {
            QrMode.GENERATE -> GeneratePanel(state, viewModel, context)
            QrMode.SCAN -> ScanPanel(state, viewModel, clipboard, pickImage)
        }
    }
}

@Composable
private fun GeneratePanel(
    state: QrUiState,
    viewModel: QrViewModel,
    context: android.content.Context,
) {
    ToolSectionCard(title = "文本内容") {
        ToolTextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            label = { Text("文字或链接") },
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )
        Button(
            onClick = viewModel::generate,
            enabled = state.text.isNotBlank() && !state.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("生成二维码") }
    }

    if (state.loading) {
        FeedbackBlock(text = "处理中…", type = FeedbackType.LOADING)
    }

    state.bitmap?.let { bmp ->
        ToolSectionCard(title = "结果") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ToolShape.md)
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "二维码",
                    modifier = Modifier.size(240.dp),
                )
            }
            OutlinedButton(
                onClick = { viewModel.saveToGallery(context) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(if (state.saved) "已保存到相册" else "保存到相册") }
        }
    }
}

@Composable
private fun ScanPanel(
    state: QrUiState,
    viewModel: QrViewModel,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    pickImage: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
) {
    ToolSectionCard(title = "从相册识别") {
        OutlinedButton(
            onClick = {
                viewModel.clearScan()
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("选择二维码图片") }
        Text(
            "建议选择清晰、未压缩的二维码图片",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    when {
        state.loading -> FeedbackBlock(text = "识别中…", type = FeedbackType.LOADING)
        state.scanError -> FeedbackBlock(text = "未能在图片中识别到二维码", type = FeedbackType.ERROR)
        state.scanResult != null -> ToolSectionCard(title = "识别结果") {
            Text(
                state.scanResult.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(state.scanResult.orEmpty())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("复制内容") }
        }
    }
}
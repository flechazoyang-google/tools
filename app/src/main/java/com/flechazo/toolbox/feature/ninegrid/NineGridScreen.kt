package com.flechazo.toolbox.feature.ninegrid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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

data class NineGridUiState(
    val source: Bitmap? = null,
    val grid: Int = 3,
    val pieces: List<Bitmap> = emptyList(),
    val withMargin: Boolean = false,
    val loading: Boolean = false,
    val savedCount: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class NineGridViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(NineGridUiState())
    val state: StateFlow<NineGridUiState> = _state

    private var splitJob: Job? = null

    fun load(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, savedCount = 0)
            val bitmap = ImageUtils.loadScaled(context, uri)
            if (bitmap == null) {
                _state.value = _state.value.copy(loading = false, error = "无法读取该图片")
                return@launch
            }
            _state.value = _state.value.copy(source = bitmap, loading = false)
            split()
        }
    }

    fun setGrid(grid: Int) {
        if (grid == _state.value.grid) return
        _state.value = _state.value.copy(grid = grid, savedCount = 0)
        split()
    }

    fun setMargin(enabled: Boolean) {
        _state.value = _state.value.copy(withMargin = enabled, savedCount = 0)
        split()
    }

    private fun split() {
        val bmp = _state.value.source ?: return
        val n = _state.value.grid
        val margin = _state.value.withMargin
        // 取消上一个切分任务，避免旧结果覆盖新规格
        splitJob?.cancel()
        splitJob = viewModelScope.launch(Dispatchers.Default) {
            val baseW = bmp.width / n
            val baseH = bmp.height / n
            if (baseW <= 0 || baseH <= 0) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(pieces = emptyList(), error = "图片太小，无法切分为 ${n}×${n}")
                }
                return@launch
            }
            val pieces = mutableListOf<Bitmap>()
            for (row in 0 until n) {
                val y = row * baseH
                // 最后一行/列吸收余数像素，保证不丢边
                val h = if (row == n - 1) bmp.height - y else baseH
                for (col in 0 until n) {
                    val x = col * baseW
                    val w = if (col == n - 1) bmp.width - x else baseW
                    val piece = Bitmap.createBitmap(bmp, x, y, w, h)
                    pieces.add(if (margin) applyMargin(piece) else piece)
                }
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(pieces = pieces, error = null)
            }
        }
    }

    /** Save pieces to the gallery. Returns the number successfully written. */
    fun saveAll(context: android.content.Context) {
        val pieces = _state.value.pieces
        if (pieces.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, savedCount = 0)
            var ok = 0
            pieces.forEach { piece ->
                val bytes = ImageUtils.compressToBytes(piece, Bitmap.CompressFormat.PNG, 100)
                if (bytes != null && ImageUtils.saveBytes(context, bytes, "image/png") != null) ok++
            }
            _state.value = _state.value.copy(
                loading = false,
                savedCount = ok,
                error = if (ok == 0) "保存失败，请检查相册权限" else null,
            )
        }
    }

    private fun applyMargin(bitmap: Bitmap, ratio: Float = 0.04f): Bitmap {
        val padX = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val padY = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(
            bitmap.width + padX * 2,
            bitmap.height + padY * 2,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, padX.toFloat(), padY.toFloat(), null)
        }
        return out
    }
}

private val GRID_OPTIONS = listOf(2, 3, 4)

@Composable
fun NineGridScreen(onBack: () -> Unit, viewModel: NineGridViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.load(it, context) }
    }

    ToolScaffold(
        title = "九宫格切图",
        subtitle = "支持 2×2 / 3×3 / 4×4，导出到 Pictures/Toolbox",
        onBack = onBack,
    ) {
        ToolSectionCard(title = "切分规格") {
            SegmentedTabs(
                options = GRID_OPTIONS,
                selected = state.grid,
                label = { "$it×$it" },
                onSelect = viewModel::setGrid,
            )
            SegmentedTabs(
                options = listOf(false, true),
                selected = state.withMargin,
                label = { if (it) "带白边" else "无白边" },
                onSelect = viewModel::setMargin,
            )
            OutlinedButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(if (state.source == null) "选择图片" else "重新选择图片")
            }
        }

        state.error?.let { FeedbackBlock(text = it, type = FeedbackType.ERROR) }

        if (state.loading) {
            FeedbackBlock(text = "处理中…", type = FeedbackType.LOADING)
        }

        if (state.pieces.isNotEmpty()) {
            ToolSectionCard(title = "预览（${state.grid}×${state.grid}）") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.pieces.chunked(state.grid).forEach { rowPieces ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowPieces.forEach { piece ->
                                Image(
                                    bitmap = piece.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
            }

            ToolSectionCard(title = "保存") {
                OutlinedButton(
                    onClick = { viewModel.saveAll(context) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存 ${state.pieces.size} 张到相册") }
                if (state.savedCount > 0) {
                    Text(
                        "已保存 ${state.savedCount} 张到 Pictures/Toolbox",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else if (!state.loading && state.error == null) {
            FeedbackBlock(text = "选择一张图片开始切分", type = FeedbackType.EMPTY)
        }
    }
}

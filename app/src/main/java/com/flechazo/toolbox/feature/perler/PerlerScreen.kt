package com.flechazo.toolbox.feature.perler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.LabeledDropdown
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A reduced perler-bead color (close to common Mard colors). */
data class BeadColor(val code: String, val name: String, val rgb: Int)

/** A named bead palette the user can switch between. */
data class BeadPalette(val id: String, val name: String, val colors: List<BeadColor>)

object PerlerPalette {

    /** 24 色基础调色板（与旧版一致，保持默认行为）。 */
    private val base24 = listOf(
        BeadColor("A01", "白", 0xFFF7F7F7.toInt()),
        BeadColor("A02", "浅灰", 0xFFD6D6D6.toInt()),
        BeadColor("A03", "灰", 0xFF9E9E9E.toInt()),
        BeadColor("A04", "黑", 0xFF333333.toInt()),
        BeadColor("B01", "红", 0xFFD7263D.toInt()),
        BeadColor("B02", "深红", 0xFF8C1F28.toInt()),
        BeadColor("B03", "粉", 0xFFF48FB1.toInt()),
        BeadColor("B04", "深粉", 0xFFEC407A.toInt()),
        BeadColor("C01", "橙", 0xFFF4651F.toInt()),
        BeadColor("C02", "杏", 0xFFFFC49B.toInt()),
        BeadColor("C03", "肤", 0xFFF2C9A0.toInt()),
        BeadColor("D01", "黄", 0xFFFFD23F.toInt()),
        BeadColor("D02", "金", 0xFFD4AF37.toInt()),
        BeadColor("E01", "草绿", 0xFF7CB342.toInt()),
        BeadColor("E02", "深绿", 0xFF2E7D32.toInt()),
        BeadColor("E03", "浅绿", 0xFFA5D6A7.toInt()),
        BeadColor("F01", "天蓝", 0xFF42A5F5.toInt()),
        BeadColor("F02", "深蓝", 0xFF1565C0.toInt()),
        BeadColor("F03", "藏青", 0xFF283593.toInt()),
        BeadColor("G01", "紫", 0xFF7E57C2.toInt()),
        BeadColor("G02", "浅紫", 0xFFB39DDB.toInt()),
        BeadColor("H01", "棕", 0xFF795548.toInt()),
        BeadColor("H02", "浅棕", 0xFFA1887F.toInt()),
        BeadColor("I01", "咖啡", 0xFF4E342E.toInt()),
    )

    /** 高对比调色板：纯色系，适合标志/扁平插画。 */
    private val highContrast = listOf(
        BeadColor("K01", "白", 0xFFFFFFFF.toInt()),
        BeadColor("K02", "黑", 0xFF000000.toInt()),
        BeadColor("K03", "红", 0xFFFF0000.toInt()),
        BeadColor("K04", "深红", 0xFF8B0000.toInt()),
        BeadColor("K05", "橙", 0xFFFF7F00.toInt()),
        BeadColor("K06", "黄", 0xFFFFFF00.toInt()),
        BeadColor("K07", "青柠", 0xFF80FF00.toInt()),
        BeadColor("K08", "绿", 0xFF00C000.toInt()),
        BeadColor("K09", "深绿", 0xFF006400.toInt()),
        BeadColor("K10", "青", 0xFF00FFFF.toInt()),
        BeadColor("K11", "蓝", 0xFF0000FF.toInt()),
        BeadColor("K12", "深蓝", 0xFF00008B.toInt()),
        BeadColor("K13", "紫", 0xFF8A2BE2.toInt()),
        BeadColor("K14", "品红", 0xFFFF00FF.toInt()),
        BeadColor("K15", "粉", 0xFFFF69B4.toInt()),
        BeadColor("K16", "棕", 0xFF8B4513.toInt()),
    )

    /** 灰度调色板：12 级灰阶，适合线稿/照片阴影练习。 */
    private val gray12 = (0 until 12).map { i ->
        val v = (i * 255) / 11
        BeadColor("G%02d".format(i + 1), if (i == 0) "黑" else if (i == 11) "白" else "灰${i}", 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v)
    }

    /** 可选调色板列表，第一项为默认。 */
    val palettes: List<BeadPalette> = listOf(
        BeadPalette("base24", "24 色基础", base24),
        BeadPalette("contrast", "高对比", highContrast),
        BeadPalette("gray12", "灰度 12 级", gray12),
    )

    /** 默认调色板。 */
    val default: BeadPalette = palettes.first()

    /** 旧版入口，保留为 24 色基础调色板。 */
    val colors: List<BeadColor> = base24
}

/** 导出位图硬上限：像素总数与单边尺寸，防止分配无界位图导致 OOM（16M px ≈ 64 MB ARGB_8888）。 */
private const val MAX_EXPORT_PIXELS = 16_000_000
private const val MAX_EXPORT_SIDE = 16384
private const val EXPORT_CELL = 40
private const val MIN_EXPORT_CELL = 4

data class PerlerUiState(
    val columns: Int = 29,
    val grid: List<IntArray> = emptyList(),
    val rows: Int = 0,
    val counts: List<Pair<BeadColor, Int>> = emptyList(),
    val working: Boolean = false,
    val savedMessage: String? = null,
    val exportError: String? = null,
    /** 当前源图（已做 EXIF 校正与降采样），随 UiState 一起更新，避免脱离状态的裸字段竞态。 */
    val source: Bitmap? = null,
    val palette: BeadPalette = PerlerPalette.default,
)

@HiltViewModel
class PerlerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PerlerUiState())
    val state: StateFlow<PerlerUiState> = _state

    /** 当前进行中的网格计算；新一轮开始前先取消，防止旧任务把 working 置回 false。 */
    private var computeJob: Job? = null

    fun setColumns(cols: Int) {
        if (cols == _state.value.columns) return
        _state.value = _state.value.copy(columns = cols, savedMessage = null, exportError = null)
        _state.value.source?.let { generate(it) }
    }

    fun setPalette(palette: BeadPalette) {
        if (palette.id == _state.value.palette.id) return
        _state.value = _state.value.copy(palette = palette, savedMessage = null, exportError = null)
        _state.value.source?.let { generate(it) }
    }

    fun load(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = ImageUtils.loadScaled(context, uri, maxSide = 1024)
            if (bmp != null) {
                _state.value = _state.value.copy(source = bmp, exportError = null, savedMessage = null)
                generate(bmp)
            } else {
                _state.value = _state.value.copy(
                    working = false,
                    exportError = "图片读取失败，请换一张图片重试",
                )
            }
        }
    }

    private fun generate(bmp: Bitmap) {
        val s = _state.value
        val cols = s.columns
        val palette = s.palette.colors
        computeJob?.cancel()
        computeJob = viewModelScope.launch(Dispatchers.Default) {
            _state.value = _state.value.copy(working = true, savedMessage = null, exportError = null)
            // 透明像素预乘后是 (0,0,0)，直接采样会得到满屏黑珠，先合成到白底
            val flat = ImageUtils.flattenOnWhite(bmp)
            try {
                val aspect = flat.height.toDouble() / flat.width.toDouble()
                val rows = (cols * aspect).roundToInt().coerceAtLeast(1)
                val bw = flat.width
                val bh = flat.height

                val grid = List(rows) { IntArray(cols) }
                val count = IntArray(palette.size)
                for (r in 0 until rows) {
                    if (!isActive) return@launch
                    // 每个格子独立取「最近邻」区域（不跨格双线性混合），格内按面积平均，
                    // 保证采样色是真实存在的像素颜色，而不是插值出来的中间色。
                    val y0 = (r.toLong() * bh / rows).toInt().coerceIn(0, bh - 1)
                    val y1 = (((r + 1).toLong() * bh + rows - 1) / rows).toInt().coerceIn(y0 + 1, bh)
                    val outRow = grid[r]
                    for (c in 0 until cols) {
                        val x0 = (c.toLong() * bw / cols).toInt().coerceIn(0, bw - 1)
                        val x1 = (((c + 1).toLong() * bw + cols - 1) / cols).toInt().coerceIn(x0 + 1, bw)
                        val idx = nearest(sampleCell(flat, x0, y0, x1, y1), palette)
                        outRow[c] = idx
                        count[idx]++
                    }
                }
                if (!isActive) return@launch

                val counts = palette.mapIndexed { i, bead -> bead to count[i] }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        grid = grid,
                        rows = rows,
                        counts = counts,
                        working = false,
                        savedMessage = null,
                        exportError = null,
                    )
                }
            } finally {
                if (flat !== bmp) flat.recycle()
            }
        }
    }

    /**
     * 取格子 [x0,x1)×[y0,y1) 的不透明像素平均值（按 alpha 加权），
     * 全透明格子返回白色（背景）。
     */
    private fun sampleCell(bmp: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return WHITE
        if (w == 1 && h == 1) return bmp.getPixel(x0, y0)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumA = 0L
        var opaque = 0
        val row = IntArray(w)
        for (y in y0 until y1) {
            bmp.getPixels(row, 0, w, x0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                val a = (p ushr 24) and 0xFF
                if (a < 8) continue
                // 预乘还原：透明区域的颜色分量不可信，按 alpha 加权
                sumR += (((p shr 16) and 0xFF) * a)
                sumG += (((p shr 8) and 0xFF) * a)
                sumB += ((p and 0xFF) * a)
                sumA += a
                opaque++
            }
        }
        if (opaque == 0 || sumA == 0L) return WHITE
        val r = (sumR / sumA).toInt().coerceIn(0, 255)
        val g = (sumG / sumA).toInt().coerceIn(0, 255)
        val b = (sumB / sumA).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun nearest(pixel: Int, palette: List<BeadColor>): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        var best = 0
        var bestDist = Double.MAX_VALUE
        palette.forEachIndexed { i, bead ->
            val br = (bead.rgb shr 16) and 0xFF
            val bg = (bead.rgb shr 8) and 0xFF
            val bb = bead.rgb and 0xFF
            val d = 2.0 * (r - br) * (r - br) + 4.0 * (g - bg) * (g - bg) + 3.0 * (b - bb) * (b - bb)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    fun export(context: Context) {
        val s = _state.value
        if (s.grid.isEmpty() || s.rows <= 0 || s.columns <= 0) return
        viewModelScope.launch(Dispatchers.Default) {
            // 先算尺寸再分配：宽幅全景在 58 板时 rows 无上界，原实现会尝试分配几百 MB 的位图
            var cell = EXPORT_CELL
            val pixels = s.columns.toLong() * cell * s.rows.toLong() * cell
            if (pixels > MAX_EXPORT_PIXELS || s.columns * cell > MAX_EXPORT_SIDE || s.rows * cell > MAX_EXPORT_SIDE) {
                val byPixels = kotlin.math.sqrt(
                    MAX_EXPORT_PIXELS.toDouble() / (s.columns.toLong() * s.rows.toLong()).toDouble(),
                )
                val bySide = (MAX_EXPORT_SIDE.toDouble() / maxOf(s.columns, s.rows)).toDouble()
                cell = floor(minOf(byPixels, bySide)).toInt().coerceAtLeast(MIN_EXPORT_CELL)
            }
            val outW = s.columns * cell
            val outH = s.rows * cell
            if (outW.toLong() * outH.toLong() > MAX_EXPORT_PIXELS ||
                outW > MAX_EXPORT_SIDE || outH > MAX_EXPORT_SIDE
            ) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        exportError = "图纸尺寸过大（${s.columns}×${s.rows} 格），请减少板数或更换图片",
                        savedMessage = null,
                    )
                }
                return@launch
            }

            val saved = runCatching {
                val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val radius = cell / 2f - if (cell >= 12) 2f else 0.5f
                    val palette = s.palette.colors
                    for (r in s.grid.indices) {
                        for (c in 0 until s.columns) {
                            paint.color = palette[s.grid[r][c]].rgb
                            canvas.drawCircle(
                                c * cell + cell / 2f,
                                r * cell + cell / 2f,
                                radius.coerceAtLeast(0.5f),
                                paint,
                            )
                        }
                    }
                    ImageUtils.saveBitmap(context, bmp)
                } finally {
                    bmp.recycle()
                }
            }.getOrNull()

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    savedMessage = if (saved != null) {
                        "图纸已保存到相册 Pictures/Toolbox（${outW}×${outH}）"
                    } else {
                        null
                    },
                    exportError = if (saved == null) "导出失败，请重试或降低图纸尺寸" else null,
                )
            }
        }
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}

@Composable
fun PerlerScreen(onBack: () -> Unit, viewModel: PerlerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.load(context, it) }
    }

    ToolScaffold(
        title = "拼豆图纸",
        subtitle = "支持 29/52/58 板规格 · 可选调色板 · 预览可捏合缩放",
        onBack = onBack,
    ) {
        // 板型 + 调色板 + 选图
        ToolSectionCard(title = "板型与输入") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(29, 52, 58).forEach { cols ->
                    FilterChip(
                        selected = state.columns == cols,
                        onClick = { viewModel.setColumns(cols) },
                        label = { Text("$cols 板") },
                    )
                }
            }
            LabeledDropdown(
                label = "调色板",
                options = PerlerPalette.palettes,
                selected = state.palette,
                display = { "${it.name}（${it.colors.size} 色）" },
                onSelect = { viewModel.setPalette(it) },
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("选择图片生成图纸") }
        }

        if (state.working) {
            FeedbackBlock(text = "正在生成图纸…", type = FeedbackType.LOADING)
        } else if (state.grid.isEmpty()) {
            if (state.exportError != null) {
                FeedbackBlock(text = state.exportError ?: "", type = FeedbackType.ERROR)
            } else {
                FeedbackBlock(text = "选择一张图片，自动映射到拼豆调色板并生成图纸", type = FeedbackType.EMPTY)
            }
        } else {
            // 图纸预览（可捏合缩放/拖动）
            ToolSectionCard(title = "图纸预览（${state.columns}×${state.rows}）") {
                var zoom by remember { mutableFloatStateOf(1f) }
                var pan by remember { mutableStateOf(Offset.Zero) }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.8f, 4f)
                    pan += panChange / zoom
                }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(state.columns.toFloat() / state.rows)
                                .transformable(transformState)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                },
                        ) {
                            val cellW = size.width / state.columns
                            val cellH = size.height / state.rows
                            val radius = minOf(cellW, cellH) / 2f * 0.88f
                            val palette = state.palette.colors
                            for (r in state.grid.indices) {
                                for (c in 0 until state.columns) {
                                    drawCircle(
                                        color = Color(palette[state.grid[r][c]].rgb),
                                        radius = radius,
                                        center = Offset(
                                            c * cellW + cellW / 2,
                                            r * cellH + cellH / 2,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "双指缩放 · 拖动查看细节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 导出
            ToolSectionCard(title = "导出") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { viewModel.export(context) }) { Text("导出图纸 PNG") }
                    Text(
                        "共 ${state.counts.sumOf { it.second }} 颗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.savedMessage?.let { msg ->
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.exportError?.let { msg ->
                    FeedbackBlock(text = msg, type = FeedbackType.ERROR)
                }
            }

            // 耗材清单
            ToolSectionCard(title = "耗材清单（${state.counts.size} 种颜色）") {
                Column {
                    state.counts.forEach { (bead, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                drawCircle(Color(bead.rgb))
                                drawCircle(
                                    color = Color(0x33000000),
                                    radius = size.minDimension / 2,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                                )
                            }
                            KeyValueRow(
                                label = "${bead.code} ${bead.name}",
                                value = "$n 颗",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    KeyValueRow(
                        label = "合计",
                        value = "${state.counts.sumOf { it.second }} 颗",
                        emphasized = true,
                    )
                }
            }
        }
    }
}

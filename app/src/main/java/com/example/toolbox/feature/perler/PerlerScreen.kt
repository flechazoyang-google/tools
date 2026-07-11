package com.example.toolbox.feature.perler

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// 颜色编码数据
// ---------------------------------------------------------------------------

private data class BeadColor(
    val code: String,
    val name: String,
    val r: Int, val g: Int, val b: Int,
) {
    val color: Color get() = Color(r, g, b)
    fun toArgb(): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    companion object {
        val byCode: Map<String, BeadColor> by lazy { PALETTE.associateBy { it.code } }
    }
}

/** 标准拼豆颜色板 — 40 种常用色，按色系分配 A–O 字母代码 */
private val PALETTE = listOf(
    // A: 白系
    BeadColor("A1", "白", 0xFF, 0xFF, 0xFF),
    BeadColor("A2", "奶白", 0xFC, 0xF5, 0xE8),
    // B: 黄系
    BeadColor("B1", "浅黄", 0xFF, 0xFD, 0xB0),
    BeadColor("B2", "黄", 0xFF, 0xE0, 0x0F),
    BeadColor("B3", "橙黄", 0xFF, 0xC1, 0x07),
    // C: 橙系
    BeadColor("C1", "橙", 0xFF, 0x8C, 0x00),
    BeadColor("C2", "橘红", 0xE6, 0x5C, 0x00),
    // D: 红系
    BeadColor("D1", "红", 0xE6, 0x1C, 0x1C),
    BeadColor("D2", "深红", 0xAD, 0x14, 0x14),
    BeadColor("D3", "酒红", 0x7B, 0x1E, 0x3E),
    // E: 粉系
    BeadColor("E1", "粉", 0xFF, 0xAB, 0xC9),
    BeadColor("E2", "玫红", 0xF0, 0x62, 0x92),
    // F: 紫系
    BeadColor("F1", "紫", 0x9C, 0x27, 0xB0),
    BeadColor("F2", "深紫", 0x6A, 0x1B, 0x9A),
    // G: 蓝系
    BeadColor("G1", "浅蓝", 0x81, 0xD4, 0xFA),
    BeadColor("G2", "天蓝", 0x42, 0xA5, 0xF5),
    BeadColor("G3", "蓝", 0x1E, 0x88, 0xE5),
    BeadColor("G4", "深蓝", 0x15, 0x65, 0xC0),
    BeadColor("G5", "藏青", 0x0D, 0x47, 0xA1),
    // H: 青
    BeadColor("H1", "青", 0x00, 0x96, 0x88),
    // I: 绿系
    BeadColor("I1", "浅绿", 0xA5, 0xD6, 0xA7),
    BeadColor("I2", "草绿", 0x7C, 0xB3, 0x42),
    BeadColor("I3", "绿", 0x43, 0xA0, 0x47),
    BeadColor("I4", "翠绿", 0x2E, 0x7D, 0x32),
    BeadColor("I5", "墨绿", 0x1B, 0x5E, 0x20),
    // J: 棕系
    BeadColor("J1", "浅棕", 0xD7, 0xBC, 0x9C),
    BeadColor("J2", "棕", 0x8D, 0x6E, 0x63),
    BeadColor("J3", "深棕", 0x5D, 0x40, 0x37),
    BeadColor("J4", "卡其", 0xC0, 0xAA, 0x7A),
    // K: 灰系
    BeadColor("K1", "灰白", 0xE0, 0xE0, 0xE0),
    BeadColor("K2", "浅灰", 0xB0, 0xB0, 0xB0),
    BeadColor("K3", "灰", 0x80, 0x80, 0x80),
    BeadColor("K4", "深灰", 0x50, 0x50, 0x50),
    // L: 黑
    BeadColor("L1", "黑", 0x21, 0x21, 0x21),
    // M: 肤色
    BeadColor("M1", "肉色", 0xFF, 0xDC, 0xC1),
    BeadColor("M2", "驼色", 0xBC, 0x8F, 0x8F),
    // N: 金属色
    BeadColor("N1", "金", 0xFF, 0xD5, 0x4F),
    BeadColor("N2", "银", 0xC0, 0xC0, 0xC0),
    // O: 荧光色
    BeadColor("O1", "荧光绿", 0x76, 0xFF, 0x03),
    BeadColor("O2", "荧光粉", 0xFF, 0x14, 0x93),
)

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------

private val GRID_SIZES = listOf(16, 24, 32, 48, 64, 96, 128)

private val GridLineColor = Color(0xFFE0E0E0)
private val CoordColor = Color(0xFF9E9E9E)
private val GridBg = Color.White
private val SelectedBorderColor = Color(0xFF333333)
private val GridLineArgb = 0xFFE0E0E0.toInt()
private val CoordArgb = 0xFF9E9E9E.toInt()
private val BgWhiteArgb = 0xFFFFFFFF.toInt()

/** 计算某颜色的文本对比色（浅底深字 / 深底白字） */
private fun textColorForBg(bc: BeadColor): Color {
    val luminance = 0.299f * bc.r + 0.587f * bc.g + 0.114f * bc.b
    return if (luminance > 140f) Color(0xFF333333) else Color.White
}

/** 计算某颜色对应的文字 ARGB 值（用于 Canvas 绘制） */
private fun textArgbForBg(r: Int, g: Int, b: Int): Int {
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance > 140f) 0xFF333333.toInt() else 0xFFFFFFFF.toInt()
}

/** 最近色匹配（欧几里得距离） */
private fun findClosest(targetR: Int, targetG: Int, targetB: Int): BeadColor {
    return PALETTE.minBy { bc ->
        val dr = bc.r - targetR; val dg = bc.g - targetG; val db = bc.b - targetB
        dr * dr + dg * dg + db * db
    }
}

/** 将 Bitmap 缩放到 gridSize × gridSize，映射为拼豆颜色二维数组 */
private fun convertToPattern(source: Bitmap, gridSize: Int): List<List<BeadColor>> {
    val scaled = Bitmap.createScaledBitmap(source, gridSize, gridSize, true)
    val result = mutableListOf<List<BeadColor>>()
    for (y in 0 until gridSize) {
        val row = mutableListOf<BeadColor>()
        for (x in 0 until gridSize) {
            val pixel = scaled.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            row.add(findClosest(r, g, b))
        }
        result.add(row)
    }
    scaled.recycle()
    return result
}

/** 统计每种颜色的使用数量，返回按 code 排序的列表 */
private fun countColorsSorted(pattern: List<List<BeadColor>>): List<Pair<BeadColor, Int>> {
    val counts = mutableMapOf<BeadColor, Int>()
    for (row in pattern) for (bc in row) {
        counts[bc] = (counts[bc] ?: 0) + 1
    }
    return counts.entries.map { it.key to it.value }.sortedBy { it.first.code }
}

/**
 * 将拼豆图纸渲染为高清 Bitmap，含行号列号、色块、色号文字、网格线。
 * 适合保存和打印。
 */
private fun renderPatternToBitmap(pattern: List<List<BeadColor>>, gridSize: Int): Bitmap {
    // Choose cell pixel size based on grid to keep output manageable
    val cellPx = when {
        gridSize <= 32 -> 44
        gridSize <= 48 -> 36
        gridSize <= 64 -> 28
        gridSize <= 96 -> 22
        else -> 18
    }
    val coordPx = 28
    val linePx = 1
    val totalW = coordPx + gridSize * (cellPx + linePx) + linePx
    val totalH = coordPx + gridSize * (cellPx + linePx) + linePx

    val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Background
    canvas.drawColor(BgWhiteArgb)

    // Paints
    val gridLinePaint = Paint().apply {
        color = GridLineArgb
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    val coordTextPaint = Paint().apply {
        color = CoordArgb
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        textSize = (cellPx * 0.30f).coerceIn(8f, 13f)
        typeface = Typeface.DEFAULT
    }
    val codeTextPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        textSize = (cellPx * 0.38f).coerceIn(9f, 17f)
        typeface = Typeface.DEFAULT_BOLD
    }
    val cellPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    // Helper: x position of a grid column
    fun colX(col: Int): Int = coordPx + linePx + col * (cellPx + linePx)
    fun rowY(row: Int): Int = coordPx + linePx + row * (cellPx + linePx)

    // ---- Column headers ----
    for (col in 0 until gridSize) {
        val cx = colX(col) + cellPx / 2f
        val cy = coordPx / 2f + coordTextPaint.textSize / 3f
        canvas.drawText("${col + 1}", cx, cy, coordTextPaint)
    }

    // ---- Row headers + cells ----
    for (row in 0 until gridSize) {
        // Row number
        val rx = coordPx / 2f
        val ry = rowY(row) + cellPx / 2f + codeTextPaint.textSize / 3f
        canvas.drawText("${row + 1}", rx, ry, coordTextPaint)

        for (col in 0 until gridSize) {
            val bc = pattern[row][col]
            val cx = colX(col)
            val cy = rowY(row)

            // Fill cell background
            cellPaint.color = bc.toArgb()
            canvas.drawRect(
                cx.toFloat(), cy.toFloat(),
                (cx + cellPx).toFloat(), (cy + cellPx).toFloat(),
                cellPaint,
            )

            // Draw code text
            val textArgb = textArgbForBg(bc.r, bc.g, bc.b)
            codeTextPaint.color = textArgb
            val tx = cx + cellPx / 2f
            val ty = cy + cellPx / 2f + codeTextPaint.textSize / 3f
            canvas.drawText(bc.code, tx, ty, codeTextPaint)
        }
    }

    // ---- Grid lines (draw after cells to avoid being covered) ----
    // Horizontal lines
    for (row in 0..gridSize) {
        val y = coordPx + row * (cellPx + linePx)
        canvas.drawRect(
            coordPx.toFloat(), y.toFloat(),
            (coordPx + gridSize * (cellPx + linePx) + linePx).toFloat(),
            (y + linePx).toFloat(),
            gridLinePaint,
        )
    }
    // Vertical lines
    for (col in 0..gridSize) {
        val x = coordPx + col * (cellPx + linePx)
        canvas.drawRect(
            x.toFloat(), coordPx.toFloat(),
            (x + linePx).toFloat(),
            (coordPx + gridSize * (cellPx + linePx) + linePx).toFloat(),
            gridLinePaint,
        )
    }

    return bmp
}

/** Save bitmap to device gallery (Pictures/Toolbox folder). */
private fun saveBitmapToGallery(bitmap: Bitmap, context: android.content.Context): Boolean {
    return try {
        val fileName = "perler_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Toolbox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}

// ---------------------------------------------------------------------------
// 主屏幕
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerlerScreen() {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gridSize by remember { mutableStateOf(32) }
    var pattern by remember { mutableStateOf<List<List<BeadColor>>?>(null) }
    var showSizeMenu by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableStateOf<SelectedCell?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap = bmp
            pattern = null
            selectedCell = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "拼豆图纸")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ---- 图片选择 ----
            Button(
                onClick = { pickLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  选择图片", modifier = Modifier.padding(start = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (bitmap != null) {
                val bmp = bitmap!!
                // ---- 缩略图 + 尺寸选择 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "原图",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("${bmp.width} × ${bmp.height}", style = MaterialTheme.typography.titleSmall)
                        Text("点击生成 ${gridSize}×${gridSize} 图纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Box {
                        OutlinedButton(
                            onClick = { showSizeMenu = true },
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("${gridSize}×${gridSize}") }
                        DropdownMenu(
                            expanded = showSizeMenu,
                            onDismissRequest = { showSizeMenu = false },
                        ) {
                            GRID_SIZES.forEach { size ->
                                DropdownMenuItem(
                                    text = {
                                        val lbl = if (size > 64) "${size}×${size}  (大尺寸)" else "${size}×${size}"
                                        Text(lbl)
                                    },
                                    onClick = { gridSize = size; pattern = null; selectedCell = null; showSizeMenu = false },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 生成按钮 ----
                Button(
                    onClick = {
                        selectedCell = null
                        scope.launch(Dispatchers.Default) {
                            val pat = convertToPattern(bmp, gridSize)
                            withContext(Dispatchers.Main) { pattern = pat }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pattern == null) "生成拼豆图纸" else "重新生成")
                }

                if (pattern != null) {
                    val pat = pattern!!
                    val colorCounts = remember(pat) { countColorsSorted(pat) }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ---- 图纸标题 + 保存按钮 ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "图纸预览（${gridSize}×${gridSize}）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedButton(
                            onClick = {
                                if (!isSaving) {
                                    isSaving = true
                                    scope.launch(Dispatchers.Default) {
                                        val rendered = renderPatternToBitmap(pat, gridSize)
                                        val ok = saveBitmapToGallery(rendered, context)
                                        rendered.recycle()
                                        withContext(Dispatchers.Main) {
                                            isSaving = false
                                            val msg = if (ok) "图纸已保存到相册 Pictures/Toolbox" else "保存失败，请重试"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSaving,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSaving) "保存中…" else "保存图纸", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "点击方格可查看色号详情",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // ---- 工程图纸网格 ----
                    EngineeringGrid(
                        pattern = pat,
                        gridSize = gridSize,
                        selectedRow = selectedCell?.row,
                        selectedCol = selectedCell?.col,
                        onCellClick = { row, col, bc ->
                            selectedCell = SelectedCell(row, col, bc)
                        },
                    )

                    // ---- 选中格子详情卡片 ----
                    selectedCell?.let { cell ->
                        Spacer(modifier = Modifier.height(10.dp))
                        SelectedCellCard(cell)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---- 颜色图例 ----
                    Text(
                        "颜色代码（${colorCounts.size} 种）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CodeLegend(colorCounts)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 合计信息
                    Text(
                        "共 ${gridSize}×${gridSize} = ${gridSize * gridSize} 颗珠子",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // ---- 空状态 ----
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Image, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "选择一张图片\n自动转换为拼豆图纸",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 选中格子数据
// ---------------------------------------------------------------------------

private data class SelectedCell(
    val row: Int,
    val col: Int,
    val bead: BeadColor,
)

// ---------------------------------------------------------------------------
// 选中格子详情卡片
// ---------------------------------------------------------------------------

@Composable
private fun SelectedCellCard(cell: SelectedCell) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cell.bead.color)
                    .border(1.dp, GridLineColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cell.bead.code,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColorForBg(cell.bead),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cell.bead.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        cell.bead.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "位置：第 ${cell.row + 1} 行  第 ${cell.col + 1} 列",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 工程图纸网格
// ---------------------------------------------------------------------------

@Composable
private fun EngineeringGrid(
    pattern: List<List<BeadColor>>,
    gridSize: Int,
    selectedRow: Int?,
    selectedCol: Int?,
    onCellClick: (Int, Int, BeadColor) -> Unit,
) {
    val gridLineWidth = 0.5.dp
    val coordWidth = 22.dp
    val coordHeight = 18.dp

    val scrollStateH = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GridBg, RoundedCornerShape(4.dp))
            .border(gridLineWidth, GridLineColor, RoundedCornerShape(4.dp)),
    ) {
        val cellSize = calculateCellSize(gridSize)

        Box(
            modifier = Modifier
                .horizontalScroll(scrollStateH)
                .padding(2.dp),
        ) {
            Column {
                // ---- 坐标行头 ----
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(modifier = Modifier.width(coordWidth).height(coordHeight))
                    for (col in 1..gridSize) {
                        Box(
                            modifier = Modifier.width(cellSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$col",
                                fontSize = minOf(10f, (cellSize.value * 0.35f).coerceAtMost(12f)).sp,
                                color = CoordColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }

                // ---- 数据行 ----
                pattern.forEachIndexed { rowIdx, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(coordWidth)
                                .height(cellSize + gridLineWidth),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${rowIdx + 1}",
                                fontSize = minOf(10f, (cellSize.value * 0.35f).coerceAtMost(12f)).sp,
                                color = CoordColor,
                                textAlign = TextAlign.Center,
                            )
                        }

                        row.forEachIndexed { colIdx, bc ->
                            GridCell(
                                bead = bc,
                                size = cellSize,
                                lineWidth = gridLineWidth,
                                isSelected = selectedRow == rowIdx && selectedCol == colIdx,
                                onClick = { onCellClick(rowIdx, colIdx, bc) },
                            )
                        }
                    }
                }
            }
        }

        if (cellSize < 10.dp) {
            Text(
                "图纸尺寸较大，左右滑动查看，点击方格可查看色号详情",
                fontSize = 10.sp,
                color = CoordColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .background(GridBg.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
private fun GridCell(
    bead: BeadColor,
    size: Dp,
    lineWidth: Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = bead.color
    val codeTextColor = textColorForBg(bead)
    val canShowText = size >= 10.dp

    Box(
        modifier = Modifier
            .size(size)
            .background(bg)
            .then(
                if (isSelected) Modifier.border(2.dp, SelectedBorderColor)
                else Modifier.border(lineWidth, GridLineColor)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (canShowText) {
            val fs = (size.value * 0.36f).coerceIn(7f, 14f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    bead.code,
                    fontSize = fs.sp,
                    fontWeight = FontWeight.Bold,
                    color = codeTextColor.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    lineHeight = (fs * 1.1f).sp,
                )
                if (size >= 22.dp) {
                    Text(
                        bead.name,
                        fontSize = (fs * 0.65f).sp,
                        color = codeTextColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** 根据屏幕可用宽度和 gridSize 计算单元格大小，最小 12dp 保证色号可见 */
@Composable
private fun calculateCellSize(gridSize: Int): Dp {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val usableWidth = screenWidth - 32.dp - 22.dp - 8.dp
    return maxOf(usableWidth / gridSize, 12.dp)
}

// ---------------------------------------------------------------------------
// 工程风格色卡图例
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeLegend(colorCounts: List<Pair<BeadColor, Int>>) {
    val bgColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(0.5.dp, GridLineColor, RoundedCornerShape(4.dp)),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            colorCounts.forEach { (bc, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(bc.color)
                            .border(0.5.dp, GridLineColor),
                    )
                    Text(
                        "${bc.code} ×$count",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = CoordColor,
                    )
                }
            }
        }
    }
}

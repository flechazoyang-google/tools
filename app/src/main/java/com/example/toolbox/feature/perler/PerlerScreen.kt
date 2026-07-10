package com.example.toolbox.feature.perler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar
import kotlin.math.roundToInt
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

/** 计算某颜色的文本对比色（浅底深字 / 深底白字） */
private fun textColorForBg(bc: BeadColor): Color {
    val luminance = 0.299f * bc.r + 0.587f * bc.g + 0.114f * bc.b
    return if (luminance > 140f) Color(0xFF333333) else Color.White
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

// ---------------------------------------------------------------------------
// 主题色常量
// ---------------------------------------------------------------------------

private val GridWhite = Color.White
private val SelectedChip = Color(0xFF333333)

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
    var showCode by remember { mutableStateOf(true) } // true=代码模式, false=色块模式
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
                                    onClick = { gridSize = size; pattern = null; showSizeMenu = false },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 生成按钮 ----
                Button(
                    onClick = {
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

                    // ---- 模式切换 ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = showCode,
                            onClick = { showCode = true },
                            label = { Text("代码模式", fontSize = 13.sp) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !showCode,
                            onClick = { showCode = false },
                            label = { Text("色块模式", fontSize = 13.sp) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ---- 图纸标题 ----
                    Text(
                        "图纸预览（${gridSize}×${gridSize}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // ---- 工程图纸网格 ----
                    EngineeringGrid(
                        pattern = pat,
                        gridSize = gridSize,
                        showCode = showCode,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---- 颜色图例（工程风格色条） ----
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
// 工程图纸网格
// ---------------------------------------------------------------------------

@Composable
private fun EngineeringGrid(
    pattern: List<List<BeadColor>>,
    gridSize: Int,
    showCode: Boolean,
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
                    // 左上角空白
                    Box(modifier = Modifier.width(coordWidth).height(coordHeight))

                    // 列号 1..gridSize
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
                        // 行号
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

                        // 单元格
                        row.forEach { bc ->
                            GridCell(
                                bead = bc,
                                size = cellSize,
                                showCode = showCode,
                                lineWidth = gridLineWidth,
                            )
                        }
                    }
                }
            }
        }

        // 极小单元格提示
        if (cellSize < 5.dp) {
            Text(
                "图纸尺寸较大，建议左右滑动查看细节",
                fontSize = 11.sp,
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
    showCode: Boolean,
    lineWidth: Dp,
) {
    val bg = bead.color
    val codeTextColor = textColorForBg(bead)

    Box(
        modifier = Modifier
            .size(size)
            .background(if (showCode) bg.copy(alpha = 0.12f) else bg)
            .border(lineWidth, GridLineColor),
        contentAlignment = Alignment.Center,
    ) {
        if (showCode && size >= 6.dp) {
            val fs = (size.value * 0.38f).coerceIn(5f, 14f)
            Text(
                bead.code,
                fontSize = fs.sp,
                fontWeight = FontWeight.Medium,
                color = codeTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** 根据屏幕可用宽度和 gridSize 计算单元格大小 */
@Composable
private fun calculateCellSize(gridSize: Int): Dp {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val usableWidth = screenWidth - 32.dp - 22.dp - 8.dp  // padding + coord + border
    return maxOf(usableWidth / gridSize, 3.dp)
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
                    // 色块
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(bc.color)
                            .border(0.5.dp, GridLineColor),
                    )
                    // 代码 + 数量
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

package com.example.toolbox.feature.perler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar

/** 标准拼豆颜色板 — 40 种常用色 */
private val PALETTE = listOf(
    BeadColor("白", 0xFF, 0xFF, 0xFF),
    BeadColor("奶白", 0xFC, 0xF5, 0xE8),
    BeadColor("浅黄", 0xFF, 0xFD, 0xB0),
    BeadColor("黄", 0xFF, 0xE0, 0x0F),
    BeadColor("橙黄", 0xFF, 0xC1, 0x07),
    BeadColor("橙", 0xFF, 0x8C, 0x00),
    BeadColor("橘红", 0xE6, 0x5C, 0x00),
    BeadColor("红", 0xE6, 0x1C, 0x1C),
    BeadColor("深红", 0xAD, 0x14, 0x14),
    BeadColor("酒红", 0x7B, 0x1E, 0x3E),
    BeadColor("粉", 0xFF, 0xAB, 0xC9),
    BeadColor("玫红", 0xF0, 0x62, 0x92),
    BeadColor("紫", 0x9C, 0x27, 0xB0),
    BeadColor("深紫", 0x6A, 0x1B, 0x9A),
    BeadColor("浅蓝", 0x81, 0xD4, 0xFA),
    BeadColor("天蓝", 0x42, 0xA5, 0xF5),
    BeadColor("蓝", 0x1E, 0x88, 0xE5),
    BeadColor("深蓝", 0x15, 0x65, 0xC0),
    BeadColor("藏青", 0x0D, 0x47, 0xA1),
    BeadColor("青", 0x00, 0x96, 0x88),
    BeadColor("浅绿", 0xA5, 0xD6, 0xA7),
    BeadColor("草绿", 0x7C, 0xB3, 0x42),
    BeadColor("绿", 0x43, 0xA0, 0x47),
    BeadColor("翠绿", 0x2E, 0x7D, 0x32),
    BeadColor("墨绿", 0x1B, 0x5E, 0x20),
    BeadColor("浅棕", 0xD7, 0xBC, 0x9C),
    BeadColor("棕", 0x8D, 0x6E, 0x63),
    BeadColor("深棕", 0x5D, 0x40, 0x37),
    BeadColor("卡其", 0xC0, 0xAA, 0x7A),
    BeadColor("灰白", 0xE0, 0xE0, 0xE0),
    BeadColor("浅灰", 0xB0, 0xB0, 0xB0),
    BeadColor("灰", 0x80, 0x80, 0x80),
    BeadColor("深灰", 0x50, 0x50, 0x50),
    BeadColor("黑", 0x21, 0x21, 0x21),
    BeadColor("肉色", 0xFF, 0xDC, 0xC1),
    BeadColor("驼色", 0xBC, 0x8F, 0x8F),
    BeadColor("金", 0xFF, 0xD5, 0x4F),
    BeadColor("银", 0xC0, 0xC0, 0xC0),
    BeadColor("荧光绿", 0x76, 0xFF, 0x03),
    BeadColor("荧光粉", 0xFF, 0x14, 0x93),
)

private data class BeadColor(
    val name: String,
    val r: Int, val g: Int, val b: Int,
) {
    val color: Color get() = Color(r, g, b)
    fun toArgb(): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun findClosest(targetR: Int, targetG: Int, targetB: Int): BeadColor {
    return PALETTE.minBy { bc ->
        val dr = bc.r - targetR
        val dg = bc.g - targetG
        val db = bc.b - targetB
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

/** 统计每种颜色的使用数量 */
private fun countColors(pattern: List<List<BeadColor>>): Map<BeadColor, Int> {
    val counts = mutableMapOf<BeadColor, Int>()
    for (row in pattern) for (bc in row) {
        counts[bc] = (counts[bc] ?: 0) + 1
    }
    return counts
}

private val GRID_SIZES = listOf(16, 24, 32, 48, 64)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerlerScreen() {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gridSize by remember { mutableStateOf(32) }
    var pattern by remember { mutableStateOf<List<List<BeadColor>>?>(null) }
    var showSizeMenu by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 缩略图
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${bmp.width} × ${bmp.height}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "点击生成 ${gridSize}×${gridSize} 图纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 尺寸选择
                    Box {
                        OutlinedButton(
                            onClick = { showSizeMenu = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("${gridSize}×${gridSize}")
                        }
                        DropdownMenu(
                            expanded = showSizeMenu,
                            onDismissRequest = { showSizeMenu = false },
                        ) {
                            GRID_SIZES.forEach { size ->
                                DropdownMenuItem(
                                    text = { Text("${size}×${size}") },
                                    onClick = {
                                        gridSize = size
                                        pattern = null
                                        showSizeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        pattern = convertToPattern(bmp, gridSize)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pattern == null) "生成拼豆图纸" else "重新生成")
                }

                if (pattern != null) {
                    val pat = pattern!!
                    val colorCounts = remember(pat) { countColors(pat) }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "图纸预览（${gridSize}×${gridSize}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 拼豆网格 — 自适应屏幕宽度
                    val spacing = 1.dp
                    val gridPadding = 12.dp  // Column 内边距
                    val cardHPadding = 8.dp  // Card 水平外边距

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = cardHPadding),
                    ) {
                        val availWidth = maxWidth - gridPadding * 2
                        val cellSize = maxOf(
                            (availWidth - spacing * (gridSize - 1)) / gridSize,
                            4.dp,  // 最小 4dp 保证可见
                        )

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(gridPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 网格
                                Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                                    pat.forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                            row.forEach { bc ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(cellSize)
                                                        .background(bc.color, RoundedCornerShape(2.dp))
                                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 颜色图例 + 数量统计
                    Text(
                        "所需颜色（${colorCounts.size} 种）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 按使用数量降序排列
                            val sorted = colorCounts.entries.sortedByDescending { it.value }
                            sorted.forEach { (bc, count) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(bc.color)
                                            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
                                    )
                                    Text(
                                        "${bc.name}×$count",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 合计信息
                    Text(
                        "共 ${gridSize}×${gridSize} = ${gridSize * gridSize} 颗珠子",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
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

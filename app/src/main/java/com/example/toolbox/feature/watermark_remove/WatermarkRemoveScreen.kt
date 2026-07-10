package com.example.toolbox.feature.watermark_remove

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import com.example.toolbox.core.components.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable
fun WatermarkRemoveScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // 选区参数（相对于图片宽高的百分比 0..100）
    var selCenterX by remember { mutableFloatStateOf(50f) }
    var selCenterY by remember { mutableFloatStateOf(50f) }
    var selRadius by remember { mutableFloatStateOf(8f) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            sourceBitmap = bmp
            resultBitmap = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "去水印")

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

            val bmp = sourceBitmap
            if (bmp != null) {
                // 图片预览 + 选区叠加
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "原图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    // 选区 — 矩形边框
                    val pxW = bmp.width
                    val pxH = bmp.height
                    val cxPx = selCenterX / 100f * pxW
                    val cyPx = selCenterY / 100f * pxH
                    val rPx = selRadius / 100f * minOf(pxW, pxH) / 2

                    // 在 Compose 中叠加矩形（坐标映射到 Image 的实际显示尺寸）
                    // 由于 ContentScale.Fit，需按实际渲染比例映射
                    // 此处用 Box 覆盖简化表示
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                // 近似映射：选区居中显示在图片区域中
                                start = ((selCenterX - selRadius) / 100f * 200f).coerceIn(0f, 100f).dp,
                                top = ((selCenterY - selRadius) / 100f * 200f).coerceIn(0f, 100f).dp,
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .size((selRadius * 2f / 100f * 200f).coerceIn(10f, 200f).dp)
                                .border(2.dp, Color(0xFFE91E63), RoundedCornerShape(4.dp)),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 选区控制
                Text("选区位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("水平", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = selCenterX, onValueChange = { selCenterX = it }, valueRange = 0f..100f)
                Text("垂直", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = selCenterY, onValueChange = { selCenterY = it }, valueRange = 0f..100f)
                Text("选区大小", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = selRadius, onValueChange = { selRadius = it }, valueRange = 2f..40f)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            resultBitmap = withContext(Dispatchers.Default) {
                                removeWatermark(bmp, selCenterX / 100f, selCenterY / 100f, selRadius / 100f)
                            }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isProcessing) "处理中…" else if (resultBitmap == null) "去除水印" else "重新处理")
                }

                if (resultBitmap != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "处理结果",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = resultBitmap!!.asImageBitmap(),
                            contentDescription = "结果",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                saveBitmap(context, resultBitmap!!)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存到相册")
                    }
                }
            } else {
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
                            "选择一张图片\n调整选区覆盖水印区域后点击去除",
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

/**
 * Simplified watermark removal using border-pixel sampling.
 * For each pixel in the selected circular region, fills it with the weighted
 * average of pixels sampled from the region's border area.
 */
private fun removeWatermark(source: Bitmap, cxRatio: Float, cyRatio: Float, radiusRatio: Float): Bitmap {
    val w = source.width
    val h = source.height
    val cx = (cxRatio * w).toInt().coerceIn(0, w - 1)
    val cy = (cyRatio * h).toInt().coerceIn(0, h - 1)
    val r = (radiusRatio * minOf(w, h) / 2).toInt().coerceIn(2, minOf(w, h) / 3)

    val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)

    // Simple border-sampling inpainting
    for (dy in -r..r) {
        for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r) continue
            val px = cx + dx
            val py = cy + dy
            if (px !in 0 until w || py !in 0 until h) continue

            // Sample border pixels: look at pixels just outside the circle
            val borderPixels = mutableListOf<IntArray>()
            val sampleRadius = (r * 1.2f).toInt() + 1
            for (angle in 0 until 360 step 15) {
                val rad = Math.toRadians(angle.toDouble())
                val sx = (px + (sampleRadius * Math.cos(rad)).toInt()).coerceIn(0, w - 1)
                val sy = (py + (sampleRadius * Math.sin(rad)).toInt()).coerceIn(0, h - 1)
                // Only sample from outside the circle
                val ddx = sx - cx
                val ddy = sy - cy
                if (ddx * ddx + ddy * ddy > r * r) {
                    val pixel = source.getPixel(sx, sy)
                    borderPixels.add(intArrayOf(
                        (pixel shr 16) and 0xFF,
                        (pixel shr 8) and 0xFF,
                        pixel and 0xFF,
                    ))
                }
            }

            if (borderPixels.isNotEmpty()) {
                val avgR = borderPixels.map { it[0] }.average().toInt().coerceIn(0, 255)
                val avgG = borderPixels.map { it[1] }.average().toInt().coerceIn(0, 255)
                val avgB = borderPixels.map { it[2] }.average().toInt().coerceIn(0, 255)
                result.setPixel(px, py, (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB)
            }
        }
    }

    // Blur the area slightly to smooth transitions
    return applyAverageBlur(result, cx, cy, r)
}

/** Lightweight blur pass over the filled region to blend edges */
private fun applyAverageBlur(bitmap: Bitmap, cx: Int, cy: Int, r: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    val kernelSize = 3

    for (dy in -r..r) {
        for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r) continue
            val px = cx + dx
            val py = cy + dy
            if (px !in 1 until w - 1 || py !in 1 until h - 1) continue

            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (ky in -kernelSize..kernelSize) {
                for (kx in -kernelSize..kernelSize) {
                    val sx = (px + kx).coerceIn(0, w - 1)
                    val sy = (py + ky).coerceIn(0, h - 1)
                    val p = bitmap.getPixel(sx, sy)
                    sumR += (p shr 16) and 0xFF
                    sumG += (p shr 8) and 0xFF
                    sumB += p and 0xFF
                    count++
                }
            }
            result.setPixel(px, py, (0xFF shl 24) or
                    ((sumR / count) shl 16) or
                    ((sumG / count) shl 8) or
                    (sumB / count))
        }
    }
    return result
}

private suspend fun saveBitmap(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    try {
        val fileName = "watermark_removed_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
    } catch (_: Exception) { }
}

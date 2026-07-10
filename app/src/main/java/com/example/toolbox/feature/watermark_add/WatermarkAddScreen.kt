package com.example.toolbox.feature.watermark_add

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
fun WatermarkAddScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // 水印参数
    var text by remember { mutableStateOf("") }
    var posX by remember { mutableFloatStateOf(50f) }   // 0..100 %
    var posY by remember { mutableFloatStateOf(90f) }   // 0..100 %
    var textSize by remember { mutableFloatStateOf(24f) } // sp-like
    var opacity by remember { mutableFloatStateOf(60f) }  // 0..100 %
    var rotation by remember { mutableFloatStateOf(0f) }  // 0..360

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            sourceBitmap = bmp
            previewBitmap = null
            resultBitmap = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "加水印")

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
                // 预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val displayBmp = previewBitmap ?: bmp
                    androidx.compose.foundation.Image(
                        bitmap = displayBmp.asImageBitmap(),
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 水印文字
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("水印文字") },
                    placeholder = { Text("输入水印内容…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 控制参数
                Text("位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("X", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(value = posX, onValueChange = { posX = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Y", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(value = posY, onValueChange = { posY = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                }

                Text("大小: ${textSize.toInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 10f..120f)

                Text("透明度: ${opacity.toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 5f..100f)

                Text("旋转: ${rotation.toInt()}°", style = MaterialTheme.typography.bodySmall)
                Slider(value = rotation, onValueChange = { rotation = it }, valueRange = 0f..360f)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (text.isBlank()) return@Button
                            scope.launch {
                                isProcessing = true
                                val preview = withContext(Dispatchers.Default) {
                                    drawWatermark(bmp, text, posX / 100f, posY / 100f,
                                        textSize, opacity / 100f, rotation)
                                }
                                previewBitmap = preview
                                resultBitmap = preview
                                isProcessing = false
                            }
                        },
                        enabled = !isProcessing && text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isProcessing) "处理中…" else "预览水印")
                    }

                    if (resultBitmap != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    saveBitmap(context, resultBitmap!!)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("保存到相册")
                        }
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
                            "选择一张图片\n添加自定义文字水印",
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

private fun drawWatermark(
    source: Bitmap,
    text: String,
    xRatio: Float,
    yRatio: Float,
    textSizeSp: Float,
    alphaRatio: Float,
    rotationDeg: Float,
): Bitmap {
    val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSizeSp * source.width / 200f  // Scale to image size
        this.alpha = (alphaRatio * 255).toInt().coerceIn(0, 255)
        this.color = android.graphics.Color.WHITE
        this.typeface = Typeface.DEFAULT_BOLD
        this.isAntiAlias = true
        // Shadow for readability
        this.setShadowLayer(2f, 1f, 1f, android.graphics.Color.parseColor("#66000000"))
    }

    val textWidth = paint.measureText(text)
    val textHeight = paint.textSize

    val px = ((xRatio * source.width) - textWidth / 2).toFloat()
    val py = ((yRatio * source.height) + textHeight / 3).toFloat()

    canvas.save()
    canvas.rotate(rotationDeg, px + textWidth / 2, py)
    canvas.drawText(text, px, py, paint)
    canvas.restore()

    return result
}

private suspend fun saveBitmap(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    try {
        val fileName = "watermark_${System.currentTimeMillis()}.png"
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

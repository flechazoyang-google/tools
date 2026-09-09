package com.flechazo.toolbox.feature.level

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Bubble level using the accelerometer (gravity projection). */
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pitch by remember { mutableFloatStateOf(0f) } // 前后倾角（度）
    var roll by remember { mutableFloatStateOf(0f) }  // 左右倾角（度）

    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    roll = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
                    pitch = Math.toDegrees(atan2(y.toDouble(), kotlin.math.sqrt((x * x + z * z).toDouble()))).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    ToolScaffold(
        title = "水平仪",
        subtitle = "气泡居中即水平 · 使用加速度传感器",
        onBack = onBack,
    ) {
        // 罗盘视图
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = surfaceColor)
                drawCircle(color = primaryColor.copy(alpha = 0.3f), style = Stroke(width = 6f))
                drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = size.minDimension / 4)
                drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = size.minDimension / 8)
                drawLine(surfaceColor, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 3f)
                drawLine(surfaceColor, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 3f)
                val maxOffset = size.minDimension / 2.4f
                val dx = (roll / 90f).coerceIn(-1f, 1f) * maxOffset
                val dy = (pitch / 90f).coerceIn(-1f, 1f) * maxOffset
                drawCircle(
                    color = primaryColor,
                    radius = size.minDimension / 10,
                    center = Offset(center.x + dx, center.y + dy),
                )
            }
        }

        // 读数
        ToolSectionCard(title = "读数") {
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyValueRow(label = "左右倾斜", value = "${roll.roundToInt()}°", modifier = Modifier.weight(1f))
                KeyValueRow(label = "前后倾斜", value = "${pitch.roundToInt()}°", modifier = Modifier.weight(1f))
            }
            Text(
                if (kotlin.math.abs(roll) < 1f && kotlin.math.abs(pitch) < 1f) "已水平 ✓"
                else "将手机平放在被测面上",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}
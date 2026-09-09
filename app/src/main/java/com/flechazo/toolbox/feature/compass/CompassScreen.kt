package com.flechazo.toolbox.feature.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import kotlin.math.roundToInt

/** Compass using the rotation vector sensor (fused). */
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    azimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    val needle = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface

    val direction = when (azimuth.roundToInt()) {
        in 338..360, in 0..22 -> "北 N"
        in 23..67 -> "东北 NE"
        in 68..112 -> "东 E"
        in 113..157 -> "东南 SE"
        in 158..202 -> "南 S"
        in 203..247 -> "西南 SW"
        in 248..292 -> "西 W"
        else -> "西北 NW"
    }

    ToolScaffold(
        title = "指南针",
        subtitle = "基于旋转矢量传感器，红色指针指北",
        onBack = onBack,
    ) {
        // 罗盘
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = ring)
                drawCircle(color = needle.copy(alpha = 0.2f), style = Stroke(width = 4f))
                rotate(degrees = -azimuth) {
                    drawLine(
                        color = Color(0xFFD8433B),
                        start = Offset(center.x, center.y),
                        end = Offset(center.x, center.y - size.minDimension / 2.6f),
                        strokeWidth = 10f,
                    )
                    drawLine(
                        color = onSurface.copy(alpha = 0.4f),
                        start = Offset(center.x, center.y),
                        end = Offset(center.x, center.y + size.minDimension / 2.6f),
                        strokeWidth = 10f,
                    )
                }
                drawCircle(color = needle, radius = 12f)
            }
        }

        // 读数与提示
        ToolSectionCard(title = "读数") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "${azimuth.roundToInt()}°",
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    direction,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    if (context.getSystemService(android.content.Context.SENSOR_SERVICE)
                            .let { it as? SensorManager }?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null
                    ) "⚠ 本机没有旋转矢量传感器，读数不会更新"
                    else "请远离磁铁和大块金属",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

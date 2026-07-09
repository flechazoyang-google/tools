package com.example.toolbox.feature.bmi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar

@Composable
fun BmiScreen() {
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }

    val heightCm = heightText.toFloatOrNull()
    val weightKg = weightText.toFloatOrNull()

    val bmiResult by remember(heightCm, weightKg) {
        derivedStateOf {
            if (heightCm != null && weightKg != null && heightCm > 0) {
                val bmi = weightKg / ((heightCm / 100) * (heightCm / 100))
                BmiResult(bmi)
            } else null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "BMI 计算器")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = heightText,
                onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) heightText = v },
                label = { Text("身高") },
                placeholder = { Text("单位：厘米 (cm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
                label = { Text("体重") },
                placeholder = { Text("单位：公斤 (kg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            if (bmiResult != null) {
                val result = bmiResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = result.color.copy(alpha = 0.12f)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "%.1f".format(result.bmi),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = result.color,
                        )
                        Text(
                            "kg/m²",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            result.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = result.color,
                        )
                    }
                }

                // BMI 范围参考
                BmiReferenceTable()
            } else if (heightText.isNotEmpty() && weightText.isNotEmpty()) {
                Text(
                    "请输入有效的身高和体重",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BmiReferenceTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "BMI 参考标准",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReferenceRow("< 18.5", "偏瘦", BmiCategory.UNDERWEIGHT.color)
            ReferenceRow("18.5 – 24.9", "正常", BmiCategory.NORMAL.color)
            ReferenceRow("25.0 – 29.9", "超重", BmiCategory.OVERWEIGHT.color)
            ReferenceRow("≥ 30.0", "肥胖", BmiCategory.OBESE.color)
        }
    }
}

@Composable
private fun ReferenceRow(range: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(
                modifier = Modifier.padding(end = 8.dp),
                shape = RoundedCornerShape(4.dp),
                color = color,
            ) { Text("  ", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
            Text(range, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class BmiCategory(val color: androidx.compose.ui.graphics.Color) {
    UNDERWEIGHT(androidx.compose.ui.graphics.Color(0xFF42A5F5)),
    NORMAL(androidx.compose.ui.graphics.Color(0xFF66BB6A)),
    OVERWEIGHT(androidx.compose.ui.graphics.Color(0xFFFFA726)),
    OBESE(androidx.compose.ui.graphics.Color(0xFFEF5350)),
}

private data class BmiResult(val bmi: Float, val category: BmiCategory) {
    val label: String get() = when (category) {
        BmiCategory.UNDERWEIGHT -> "偏瘦"
        BmiCategory.NORMAL -> "正常"
        BmiCategory.OVERWEIGHT -> "超重"
        BmiCategory.OBESE -> "肥胖"
    }
    val color: androidx.compose.ui.graphics.Color get() = category.color

    companion object {
        operator fun invoke(bmi: Float): BmiResult {
            val c = when {
                bmi < 18.5f -> BmiCategory.UNDERWEIGHT
                bmi < 25.0f -> BmiCategory.NORMAL
                bmi < 30.0f -> BmiCategory.OVERWEIGHT
                else -> BmiCategory.OBESE
            }
            return BmiResult(bmi, c)
        }
    }
}

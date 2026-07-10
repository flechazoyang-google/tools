package com.example.toolbox.feature.relative

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar

// ---------------------------------------------------------------------------
// 数据层（必须放在使用前）
// ---------------------------------------------------------------------------

private data class RelationEntry(
    val label: String,
    val title: String,
    val children: List<String> = emptyList(),
)

private val TOP_LEVEL = listOf("爸爸", "妈妈", "哥哥", "弟弟", "姐姐", "妹妹", "爷爷", "奶奶", "外公", "外婆", "丈夫", "妻子", "儿子", "女儿", "伯父", "叔叔", "姑姑", "舅舅", "姨妈")

private val RelationMap = listOf(
    RelationEntry("爸爸", "父亲", listOf("爸爸", "妈妈", "哥哥", "弟弟", "姐姐", "妹妹", "妻子")),
    RelationEntry("妈妈", "母亲", listOf("爸爸", "妈妈", "哥哥", "弟弟", "姐姐", "妹妹", "丈夫")),
    RelationEntry("哥哥", "兄长", listOf("妻子", "儿子", "女儿")),
    RelationEntry("弟弟", "弟弟", listOf("妻子", "儿子", "女儿")),
    RelationEntry("姐姐", "姐姐", listOf("丈夫", "儿子", "女儿")),
    RelationEntry("妹妹", "妹妹", listOf("丈夫", "儿子", "女儿")),
    RelationEntry("爷爷", "祖父", emptyList()),
    RelationEntry("奶奶", "祖母", emptyList()),
    RelationEntry("外公", "外祖父", emptyList()),
    RelationEntry("外婆", "外祖母", emptyList()),
    RelationEntry("丈夫", "丈夫", emptyList()),
    RelationEntry("妻子", "妻子", emptyList()),
    RelationEntry("儿子", "儿子", listOf("妻子", "儿子", "女儿")),
    RelationEntry("女儿", "女儿", listOf("丈夫", "儿子", "女儿")),
    RelationEntry("伯父", "伯父", listOf("妻子", "儿子", "女儿")),
    RelationEntry("叔叔", "叔叔", listOf("妻子", "儿子", "女儿")),
    RelationEntry("姑姑", "姑妈", listOf("丈夫", "儿子", "女儿")),
    RelationEntry("舅舅", "舅舅", listOf("妻子", "儿子", "女儿")),
    RelationEntry("姨妈", "姨妈", listOf("丈夫", "儿子", "女儿")),
)

private fun findEntry(label: String): RelationEntry? = RelationMap.firstOrNull { it.label == label }

private fun getDirectTitle(label: String): String? = when (label) {
    "爸爸" -> "爸爸 / 父亲"; "妈妈" -> "妈妈 / 母亲"; "哥哥" -> "哥哥 / 兄长"; "弟弟" -> "弟弟"
    "姐姐" -> "姐姐"; "妹妹" -> "妹妹"; "爷爷" -> "爷爷 / 祖父"; "奶奶" -> "奶奶 / 祖母"
    "外公" -> "外公 / 姥爷 / 外祖父"; "外婆" -> "外婆 / 姥姥 / 外祖母"
    "丈夫" -> "丈夫 / 老公"; "妻子" -> "妻子 / 老婆"; "儿子" -> "儿子"; "女儿" -> "女儿"
    "伯父" -> "伯父 / 伯伯"; "叔叔" -> "叔叔"; "姑姑" -> "姑姑 / 姑妈"; "舅舅" -> "舅舅"
    "姨妈" -> "姨妈 / 阿姨"; else -> null
}

private fun getRelation(a: String, b: String): String? = when (a) {
    "爸爸" -> when (b) { "爸爸" -> "爷爷 / 祖父"; "妈妈" -> "奶奶 / 祖母"; "哥哥" -> "伯父 / 伯伯"; "弟弟" -> "叔叔"; "姐姐" -> "姑姑 / 姑妈"; "妹妹" -> "姑姑 / 姑妈"; "妻子" -> "妈妈 / 母亲"; else -> null }
    "妈妈" -> when (b) { "爸爸" -> "外公 / 姥爷 / 外祖父"; "妈妈" -> "外婆 / 姥姥 / 外祖母"; "哥哥" -> "舅舅"; "弟弟" -> "舅舅"; "姐姐" -> "姨妈 / 阿姨"; "妹妹" -> "姨妈 / 阿姨"; "丈夫" -> "爸爸 / 父亲"; else -> null }
    "哥哥" -> when (b) { "妻子" -> "嫂子"; "儿子" -> "侄子"; "女儿" -> "侄女"; else -> null }
    "弟弟" -> when (b) { "妻子" -> "弟媳 / 弟妹"; "儿子" -> "侄子"; "女儿" -> "侄女"; else -> null }
    "姐姐" -> when (b) { "丈夫" -> "姐夫"; "儿子" -> "外甥"; "女儿" -> "外甥女"; else -> null }
    "妹妹" -> when (b) { "丈夫" -> "妹夫"; "儿子" -> "外甥"; "女儿" -> "外甥女"; else -> null }
    "伯父" -> when (b) { "妻子" -> "伯母"; "儿子" -> "堂哥 / 堂弟"; "女儿" -> "堂姐 / 堂妹"; else -> null }
    "叔叔" -> when (b) { "妻子" -> "婶婶 / 叔母"; "儿子" -> "堂哥 / 堂弟"; "女儿" -> "堂姐 / 堂妹"; else -> null }
    "姑姑" -> when (b) { "丈夫" -> "姑父"; "儿子" -> "表哥 / 表弟"; "女儿" -> "表姐 / 表妹"; else -> null }
    "舅舅" -> when (b) { "妻子" -> "舅妈 / 舅母"; "儿子" -> "表哥 / 表弟"; "女儿" -> "表姐 / 表妹"; else -> null }
    "姨妈" -> when (b) { "丈夫" -> "姨父 / 姨丈"; "儿子" -> "表哥 / 表弟"; "女儿" -> "表姐 / 表妹"; else -> null }
    "儿子" -> when (b) { "妻子" -> "儿媳 / 儿媳妇"; "儿子" -> "孙子"; "女儿" -> "孙女"; else -> null }
    "女儿" -> when (b) { "丈夫" -> "女婿"; "儿子" -> "外孙"; "女儿" -> "外孙女"; else -> null }
    else -> null
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/**
 * Calculator-style Chinese kinship relationship calculator.
 * Tap buttons to build a chain (max 2 steps), result updates live.
 */
@Composable
fun RelativeScreen() {
    val chain = remember { mutableStateListOf<String>() }

    val displayText = if (chain.isEmpty()) "我的？"
    else "我的" + chain.joinToString("") { "的${it}" }

    val result = remember(chain.size, chain.toList()) {
        when (chain.size) {
            0 -> null
            1 -> getDirectTitle(chain[0])
            else -> getRelation(chain[0], chain[1])
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "亲戚称呼计算器")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 显示屏区域 ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        displayText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                    if (chain.isNotEmpty()) {
                        Text("=", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Text(
                        result ?: (if (chain.isNotEmpty()) "请继续选择…" else "点击下方按钮选择关系"),
                        fontSize = if (result != null) 32.sp else 16.sp,
                        fontWeight = if (result != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (result != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- 关系按钮网格 ----
            val buttons = if (chain.isEmpty()) {
                RelationMap.filter { it.label in TOP_LEVEL }
            } else if (chain.size == 1) {
                val entry = findEntry(chain[0])
                entry?.children?.mapNotNull { label -> findEntry(label) } ?: emptyList()
            } else {
                emptyList()
            }

            if (buttons.isNotEmpty()) {
                buttons.chunked(4).forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowButtons.forEach { entry ->
                            RelationButton(
                                label = entry.label,
                                subtitle = entry.title,
                                onClick = { if (chain.size < 2) chain.add(entry.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - rowButtons.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            } else if (chain.size >= 2 || (chain.size == 1 && buttons.isEmpty())) {
                Text(
                    if (chain.size >= 2) "已选择完整关系链" else "该关系已是最完整称呼",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // ---- 控制按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { chain.removeLastOrNull() },
                    enabled = chain.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Filled.Backspace, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  退格", fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = { chain.clear() },
                    enabled = chain.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  清除", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 常用称呼速查 ----
            Text("常用称呼速查", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            QuickReferenceTable()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---- 按钮 ----

@Composable
private fun RelationButton(label: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- 速查表 ----

@Composable
private fun QuickReferenceTable() {
    val commonRefs = listOf(
        "爸爸的爸爸" to "爷爷 / 祖父", "爸爸的妈妈" to "奶奶 / 祖母", "妈妈的爸爸" to "外公 / 姥爷",
        "妈妈的妈妈" to "外婆 / 姥姥", "爸爸的哥哥" to "伯父 / 伯伯", "爸爸的弟弟" to "叔叔",
        "爸爸的姐妹" to "姑姑 / 姑妈", "妈妈的兄弟" to "舅舅", "妈妈的姐妹" to "姨妈 / 阿姨",
        "哥哥的妻子" to "嫂子", "弟弟的妻子" to "弟媳 / 弟妹", "姐姐的丈夫" to "姐夫",
        "妹妹的丈夫" to "妹夫", "伯父/叔叔的儿子" to "堂哥 / 堂弟", "伯父/叔叔的女儿" to "堂姐 / 堂妹",
        "姑姑/舅舅/姨妈的儿子" to "表哥 / 表弟", "姑姑/舅舅/姨妈的女儿" to "表姐 / 表妹",
        "哥哥/弟弟的儿子" to "侄子", "哥哥/弟弟的女儿" to "侄女", "姐姐/妹妹的儿子" to "外甥",
        "姐姐/妹妹的女儿" to "外甥女",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            for ((relation, title) in commonRefs) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(relation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

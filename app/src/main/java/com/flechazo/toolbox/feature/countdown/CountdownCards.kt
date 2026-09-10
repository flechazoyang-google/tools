package com.flechazo.toolbox.feature.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CARD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.CHINA)

/**
 * 大数字字号分级（重构方案 RS-9）：
 * "12" 该占满一行，"12 345" 不能撑破卡片。用长度选字号，比 autoSize 便宜且可预测。
 */
internal fun bigNumberSize(digits: String): Int = when {
    digits.length <= 3 -> 44
    digits.length <= 5 -> 34
    digits.length <= 7 -> 26
    else -> 20
}

/** 卡片副行：公历日期 + 星期 +（农历）。非 Composable，无障碍语义也用它。 */
internal fun subtitleOf(item: CountdownItem): String {
    val date = item.nextDate ?: item.base ?: return ""
    val sb = StringBuilder(date.format(CARD_DATE))
    item.lunarLabel?.let { sb.append(" · 农历").append(it) }
    if (item.lunarFallback) sb.append("（回退）")
    return sb.toString()
}

/** 无障碍整卡语义：读屏一次念完"标题，日期，还有 12 天，每年重复"。 */
internal fun cardDescription(item: CountdownItem): String = buildString {
    append(item.event.title)
    append('，')
    append(subtitleOf(item))
    append('，')
    append(item.display.headline)
    if (item.event.repeatRule != RepeatRule.NONE) append("，").append(item.event.repeatRule.label)
    if (item.event.pinned) append("，已置顶")
}

/**
 * 首屏 Hero 卡：最近即将到来的那一个。
 *
 * 倒数日产品的核心视觉就是"大号剩余天数"（时间规划局甚至精确到秒），
 * 旧版只有一行 `titleMedium` 的小字（P1-6）。
 */
@Composable
fun CountdownHeroCard(
    item: CountdownItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = CountdownPalette.colors(item.event.colorKey, dark)
    val isToday = item.display.kind == DisplayKind.TODAY

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = cardDescription(item) },
        shape = RoundedCornerShape(22.dp),
        // 今天用实心强调；其余情况用同一色的浅色容器，避免整屏都是高饱和块
        color = if (isToday) container else container.copy(alpha = if (dark) 0.28f else 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val strong = if (isToday) onContainer else scheme.onSurface
            val muted = if (isToday) onContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = strong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.event.repeatRule != RepeatRule.NONE) {
                    Spacer(Modifier.size(6.dp))
                    BadgeIcon(Icons.Filled.Repeat, item.event.repeatRule.label, muted)
                }
            }
            Text(subtitleOf(item), style = MaterialTheme.typography.bodySmall, color = muted)

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (isToday) {
                    Text(
                        "就是今天",
                        style = MaterialTheme.typography.displaySmall,
                        color = strong,
                    )
                } else {
                    Text(
                        item.display.bigNumber,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = bigNumberSize(item.display.bigNumber).sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = container,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (item.display.kind == DisplayKind.PASSED) "天前" else "天后",
                        style = MaterialTheme.typography.titleMedium,
                        color = muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            item.display.breakdown?.let {
                Text("累计 $it", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            item.progress?.let { p ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = container,
                    trackColor = muted.copy(alpha = 0.25f),
                    gapSize = 0.dp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "已过 ${(p * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
    }
}

/** 列表卡：4dp 事件色条 + 左文右大数字（重构方案 §7.2 的规格）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CountdownEventCard(
    item: CountdownItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (accent, _) = CountdownPalette.colors(item.event.colorKey, dark)
    val passed = item.display.kind == DisplayKind.PASSED
    val isToday = item.display.kind == DisplayKind.TODAY

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .semantics { contentDescription = cardDescription(item) },
        shape = RoundedCornerShape(18.dp),
        color = when {
            highlighted -> scheme.secondaryContainer
            isToday -> accent.copy(alpha = if (dark) 0.3f else 0.45f)
            else -> scheme.surfaceContainerLow
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 色条即身份：用户靠"那张橙色的"定位事件，而不是读标题
            Box(
                Modifier
                    .width(4.dp)
                    .height(58.dp)
                    .background(if (passed) scheme.outlineVariant else accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (passed) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.event.pinned) BadgeIcon(Icons.Filled.PushPin, "置顶", scheme.onSurfaceVariant)
                    if (item.event.remindEnabled) BadgeIcon(Icons.Filled.Notifications, "已开提醒", scheme.onSurfaceVariant)
                }
                Text(
                    subtitleOf(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.display.breakdown?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 16.dp),
            ) {
                val numberColor = when {
                    passed -> scheme.onSurfaceVariant
                    isToday -> scheme.primary
                    else -> accent
                }
                if (!isToday) {
                    Text(
                        item.display.bigNumber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = bigNumberSize(item.display.bigNumber).sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = numberColor,
                        maxLines = 1,
                    )
                    Text(
                        when (item.display.kind) {
                            DisplayKind.PASSED -> "天前"
                            DisplayKind.SINCE -> "已"
                            else -> "天后"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text("今天", style = MaterialTheme.typography.titleLarge, color = numberColor)
                }
            }
        }
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, desc: String, tint: Color) {
    Icon(
        icon,
        contentDescription = desc,
        tint = tint,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(14.dp),
    )
}

package com.flechazo.toolbox.core.registry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val key: String, val label: String, val order: Int) {
    CALCULATE("CALCULATE", "计算", 0),
    IMAGE("IMAGE", "图片", 1),
    TEXT("TEXT", "文本", 2),
    LIFE("LIFE", "生活", 3),
    MEASURE("MEASURE", "测量", 4),
    SECURITY("SECURITY", "安全", 5),
    ;

    companion object {
        fun byKey(key: String): ToolCategory = entries.first { it.key == key }
    }
}

/**
 * Declarative tool metadata. The single source of truth that drives the home
 * grid, category browsing, global search, favorites and navigation.
 *
 * To add a tool: create Screen + ViewModel, then append one ToolDef entry.
 */
data class ToolDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
    val keywords: List<String>,
    val priority: Int, // 0 = P0 (MVP), 1 = P1, 2 = P2
    val content: @Composable (onBack: () -> Unit) -> Unit,
)

/** Icons resolved once so search results and cards can share them. */
object ToolIcons {
    val calculator = Icons.Filled.Calculate
    val converter = Icons.Filled.SwapHoriz
    val currency = Icons.Filled.CurrencyExchange
    val nineGrid = Icons.Filled.GridOn
    val qr = Icons.Filled.QrCode
    val base64 = Icons.Filled.Code
    val timestamp = Icons.Filled.AccessTime
    val countdown = Icons.Filled.Event
    val pomodoro = Icons.Filled.Timer
    val favorites = Icons.Filled.Favorite
    val lock = Icons.Filled.Lock
    val magic = Icons.Filled.AutoFixHigh

    // P1
    val bmi = Icons.Filled.MonitorWeight
    val color = Icons.Filled.Palette
    val stitch = Icons.Filled.PhotoLibrary
    val diff = Icons.Filled.Difference
    val passwordGen = Icons.Filled.Password
    val ruler = Icons.Filled.Straighten
    val level = Icons.Filled.Architecture
    val compass = Icons.Filled.Explore

    // P1 重型
    val compress = Icons.Filled.Compress
    val watermark = Icons.Filled.BrandingWatermark
    val kinship = Icons.Filled.FamilyRestroom
    val period = Icons.Filled.CalendarMonth
    val perler = Icons.Filled.Grain

    // P2
    val money = Icons.Filled.Money
    val decision = Icons.Filled.HelpOutline
    val danmaku = Icons.Filled.Subtitles
    val device = Icons.Filled.PhoneAndroid
}

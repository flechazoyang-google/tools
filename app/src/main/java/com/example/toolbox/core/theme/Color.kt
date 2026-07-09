package com.example.toolbox.core.theme

import androidx.compose.ui.graphics.Color

// ── Neutral palette (light) ──
val BackgroundLight         = Color(0xFFF5F5F7)
val OnBackgroundLight       = Color(0xFF1D1D1F)
val SurfaceLight            = Color(0xFFFFFFFF)
val OnSurfaceLight          = Color(0xFF1D1D1F)
val SurfaceVariantLight     = Color(0xFFF0F0F2)
val OnSurfaceVariantLight   = Color(0xFF86868B)
val OutlineLight            = Color(0xFFD2D2D7)
val OutlineVariantLight     = Color(0xFFE5E5EA)

// ── Neutral palette (dark) ──
val BackgroundDark         = Color(0xFF1C1C1E)
val OnBackgroundDark       = Color(0xFFF5F5F7)
val SurfaceDark            = Color(0xFF2C2C2E)
val OnSurfaceDark          = Color(0xFFF5F5F7)
val SurfaceVariantDark     = Color(0xFF3A3A3C)
val OnSurfaceVariantDark   = Color(0xFF98989D)
val OutlineDark            = Color(0xFF48484A)
val OutlineVariantDark     = Color(0xFF3A3A3C)

// ── Semantic colors for tool categories (icon + 12% bg) ──
object ToolColors {
    val calc     = Color(0xFF2563EB)  // 计算/数据类 — 蓝色
    val finance  = Color(0xFF2563EB)  // 财务类 — 蓝色
    val network  = Color(0xFF059669)  // 网络/查询类 — 翠绿
    val life     = Color(0xFF7C3AED)  // 时间/生活类 — 紫色
    val health   = Color(0xFFE11D48)  // 健康/身体类 — 玫红
    val image    = Color(0xFFD97706)  // 图片类 — 橙色
    val utility  = Color(0xFF6B7280)  // 通用/辅助类 — 灰色
    val security = Color(0xFF6B7280)  // 安全类 — 灰色
}

// ── Material3 color scheme aliases (gray-based) ──
val PrimaryLight            = OnSurfaceLight
val OnPrimaryLight          = SurfaceLight
val PrimaryContainerLight   = SurfaceVariantLight
val OnPrimaryContainerLight = OnSurfaceLight
val SecondaryLight          = OnSurfaceVariantLight
val TertiaryLight           = OnSurfaceVariantLight
val ErrorLight              = Color(0xFFDC2626)
val OnErrorLight            = Color(0xFFFFFFFF)

val PrimaryDark            = OnSurfaceDark
val OnPrimaryDark          = SurfaceDark
val PrimaryContainerDark   = SurfaceVariantDark
val OnPrimaryContainerDark = OnSurfaceDark
val SecondaryDark          = OnSurfaceVariantDark
val TertiaryDark           = OnSurfaceVariantDark
val ErrorDark              = Color(0xFFF87171)
val OnErrorDark            = Color(0xFF7F1D1D)

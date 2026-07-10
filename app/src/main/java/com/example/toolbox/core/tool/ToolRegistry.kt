package com.example.toolbox.core.tool

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import com.example.toolbox.feature.base64.Base64Screen
import com.example.toolbox.feature.bmi.BmiScreen
import com.example.toolbox.feature.calculator.CalculatorScreen
import com.example.toolbox.feature.countdown.CountdownScreen
import com.example.toolbox.feature.currency.CurrencyScreen
import com.example.toolbox.feature.ip.IpScreen
import com.example.toolbox.feature.favorites.FavoritesScreen
import com.example.toolbox.feature.ninegrid.NineGridScreen
import com.example.toolbox.feature.password.PasswordScreen
import com.example.toolbox.feature.perler.PerlerScreen
import com.example.toolbox.feature.period.PeriodScreen
import com.example.toolbox.feature.pomodoro.PomodoroScreen
import com.example.toolbox.feature.qr_code.QrCodeScreen
import com.example.toolbox.feature.relative.RelativeScreen
import com.example.toolbox.feature.timestamp.TimestampScreen
import com.example.toolbox.feature.watermark_add.WatermarkAddScreen
import com.example.toolbox.feature.watermark_remove.WatermarkRemoveScreen

/**
 * Single source of truth for all tools.
 *
 * EXTENDING THE APP: to add a new tool, create its screen composable and append one
 * [Tool] entry here. The Home grid, the Tools list, navigation and (optionally) the
 * bottom bar all pick it up automatically — no layout code changes required.
 */
object ToolRegistry {

    val tools: List<Tool> = listOf(
        Tool(
            id = "calculator",
            title = "计算器",
            description = "数学计算",
            icon = Icons.Filled.Calculate,
            category = ToolCategory.PRODUCTIVITY,
            route = "calculator",
            featured = true,
        ) { CalculatorScreen() },

        Tool(
            id = "currency",
            title = "汇率换算",
            description = "汇率计算",
            icon = Icons.Filled.CurrencyExchange,
            category = ToolCategory.FINANCE,
            route = "currency",
            featured = true,
        ) { CurrencyScreen() },

        Tool(
            id = "ip",
            title = "IP 属地查询",
            description = "IP 信息",
            icon = Icons.Filled.Public,
            category = ToolCategory.NETWORK,
            route = "ip",
            featured = true,
        ) { IpScreen() },

        Tool(
            id = "countdown",
            title = "倒数日",
            description = "重要日子",
            icon = Icons.Filled.Event,
            category = ToolCategory.LIFE,
            route = "countdown",
            featured = true,
        ) { CountdownScreen() },

        Tool(
            id = "password",
            title = "密码箱",
            description = "加密保管",
            icon = Icons.Filled.Lock,
            category = ToolCategory.PRODUCTIVITY,
            route = "password",
            featured = false,
            showInBottomNav = true,
        ) { PasswordScreen() },

        Tool(
            id = "bmi",
            title = "BMI 计算器",
            description = "身体质量指数",
            icon = Icons.Filled.Favorite,
            category = ToolCategory.HEALTH,
            route = "bmi",
            featured = true,
        ) { BmiScreen() },

        Tool(
            id = "base64",
            title = "Base64 编解码",
            description = "Base64 编解码",
            icon = Icons.Filled.Code,
            category = ToolCategory.PRODUCTIVITY,
            route = "base64",
            featured = false,
        ) { Base64Screen() },

        Tool(
            id = "timestamp",
            title = "时间戳转换",
            description = "时间戳与日期",
            icon = Icons.Filled.AccessTime,
            category = ToolCategory.PRODUCTIVITY,
            route = "timestamp",
            featured = false,
        ) { TimestampScreen() },

        Tool(
            id = "ninegrid",
            title = "九宫格切图",
            description = "图片切九宫格",
            icon = Icons.Filled.Crop,
            category = ToolCategory.IMAGE,
            route = "ninegrid",
            featured = true,
        ) { NineGridScreen() },

        Tool(
            id = "pomodoro",
            title = "番茄钟",
            description = "专注计时",
            icon = Icons.Filled.Timer,
            category = ToolCategory.PRODUCTIVITY,
            route = "pomodoro",
            featured = true,
        ) { PomodoroScreen() },

        Tool(
            id = "perler",
            title = "拼豆图纸",
            description = "图片转拼豆图",
            icon = Icons.Filled.GridOn,
            category = ToolCategory.IMAGE,
            route = "perler",
            featured = true,
        ) { PerlerScreen() },

        // ---- v1.4.0: New tools ----

        Tool(
            id = "qr_code",
            title = "二维码生成",
            description = "生成二维码",
            icon = Icons.Filled.QrCode,
            category = ToolCategory.PRODUCTIVITY,
            route = "qr_code",
            featured = true,
        ) { QrCodeScreen() },

        Tool(
            id = "relative",
            title = "亲戚称呼计算",
            description = "亲戚称呼速查",
            icon = Icons.Filled.AccountTree,
            category = ToolCategory.LIFE,
            route = "relative",
            featured = true,
        ) { RelativeScreen() },

        Tool(
            id = "period",
            title = "经期记录",
            description = "经期预测与记录",
            icon = Icons.Filled.CalendarMonth,
            category = ToolCategory.HEALTH,
            route = "period",
            featured = true,
        ) { PeriodScreen() },

        // ---- v1.5.0: Image tools ----

        Tool(
            id = "watermark_remove",
            title = "去水印",
            description = "图片去水印",
            icon = Icons.Filled.AutoFixHigh,
            category = ToolCategory.IMAGE,
            route = "watermark_remove",
            featured = true,
        ) { WatermarkRemoveScreen() },

        Tool(
            id = "watermark_add",
            title = "加水印",
            description = "图片加文字水印",
            icon = Icons.Filled.TextFields,
            category = ToolCategory.IMAGE,
            route = "watermark_add",
            featured = true,
        ) { WatermarkAddScreen() },

        // ---- Internal screens (hidden from grid) ----

        Tool(
            id = "favorites",
            title = "我的收藏",
            description = "收藏的工具",
            icon = Icons.Filled.Favorite,
            category = ToolCategory.PRODUCTIVITY,
            route = "favorites",
            featured = false,
        ) { navController -> FavoritesScreen(navController) },
    )

    fun getByRoute(route: String): Tool? = tools.firstOrNull { it.route == route }

    val featuredTools: List<Tool> get() = tools.filter { it.featured }

    val bottomNavTool: Tool? get() = tools.firstOrNull { it.showInBottomNav }
}

package com.example.toolbox.core.tool

import androidx.compose.ui.graphics.Color
import com.example.toolbox.core.theme.ToolColors

/**
 * Tool groupings shown on the Tools page.
 */
enum class ToolCategory(val label: String, val color: Color) {
    PRODUCTIVITY("效率", ToolColors.calc),
    FINANCE("财务", ToolColors.finance),
    NETWORK("网络", ToolColors.network),
    LIFE("生活", ToolColors.life),
    HEALTH("健康", ToolColors.health),
    IMAGE("图片", ToolColors.image),
}

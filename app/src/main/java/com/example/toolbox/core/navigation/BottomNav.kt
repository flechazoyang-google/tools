package com.example.toolbox.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation destinations. Note "password" is BOTH a tool (in [ToolRegistry])
 * and a pinned tab, satisfying the UI spec while keeping the plugin architecture.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem("home", "首页", Icons.Outlined.Home, Icons.Filled.Home),
    BottomNavItem("tools", "工具", Icons.Outlined.Widgets, Icons.Filled.Widgets),
    BottomNavItem("password", "密码箱", Icons.Outlined.Lock, Icons.Filled.Lock),
    BottomNavItem("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
)

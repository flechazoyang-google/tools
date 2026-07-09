package com.example.toolbox.core.tool

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController

/**
 * Unified tool descriptor. Adding a new tool = add one entry to [ToolRegistry].
 *
 * @param id          Stable unique id (also used for "recently used").
 * @param title       Display name.
 * @param description One-line summary shown on the Tools page.
 * @param icon        Material icon.
 * @param category    Grouping.
 * @param route       Navigation route (must be unique).
 * @param featured    Shown in the Home "常用工具" grid.
 * @param showInBottomNav Reserved for the password-box pinned tab.
 * @param content     The tool's root Composable. Receives the host NavController for
 *                    internal navigation (e.g. closing a detail flow).
 */
data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
    val route: String,
    val featured: Boolean = false,
    val showInBottomNav: Boolean = false,
    val content: @Composable (NavHostController) -> Unit,
)

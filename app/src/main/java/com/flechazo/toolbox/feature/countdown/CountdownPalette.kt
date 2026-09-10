package com.flechazo.toolbox.feature.countdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flechazo.toolbox.core.designsystem.theme.CategoryColor

/**
 * 倒数日事件的专属调色板。
 *
 * **为什么不用 `MaterialTheme.colorScheme.tertiaryContainer` 这类角色色**：
 * 本应用默认开启动态取色（`AppNavHost.kt` 的 `dynamicColor`），角色色会随壁纸变化，
 * 八张卡片可能全落在两个近似色上，用户分不清哪个是哪个。事件色必须是独立常量表。
 *
 * 每色给 light / dark 两档，`colorKey` 为空时回落到主题 `primary`，
 * 这样老数据（没有 colorKey）在深色模式下依然正确。
 */
object CountdownPalette {

    /** 顺序即表单里色点的排列顺序。 */
    val ORDERED_KEYS = listOf("teal", "coral", "amber", "violet", "mint", "sky", "rose", "slate")

    private val table: Map<String, CategoryColor> = mapOf(
        "teal" to CategoryColor(Color(0xFFB2ECD9), Color(0xFF0A2E24)),
        "coral" to CategoryColor(Color(0xFFFFD9D4), Color(0xFF410002)),
        "amber" to CategoryColor(Color(0xFFFFE1A8), Color(0xFF3F2900)),
        "violet" to CategoryColor(Color(0xFFE6DBFF), Color(0xFF22164B)),
        "mint" to CategoryColor(Color(0xFFD8F2CE), Color(0xFF0B2D06)),
        "sky" to CategoryColor(Color(0xFFCDE5FF), Color(0xFF002F5C)),
        "rose" to CategoryColor(Color(0xFFFFD9E8), Color(0xFF4A1230)),
        "slate" to CategoryColor(Color(0xFFE3E0DA), Color(0xFF2C2C27)),
    )

    /** 深色模式下容器色要整体压暗，否则高饱和底色会把白色文字糊成一团。 */
    private val tableDark: Map<String, CategoryColor> = mapOf(
        "teal" to CategoryColor(Color(0xFF0F5245), Color(0xFFB2ECD9)),
        "coral" to CategoryColor(Color(0xFF68302A), Color(0xFFFFD9D4)),
        "amber" to CategoryColor(Color(0xFF4E342E), Color(0xFFFFE1A8)),
        "violet" to CategoryColor(Color(0xFF3F3B6B), Color(0xFFE6DBFF)),
        "mint" to CategoryColor(Color(0xFF2F4A2A), Color(0xFFD8F2CE)),
        "sky" to CategoryColor(Color(0xFF27445D), Color(0xFFCDE5FF)),
        "rose" to CategoryColor(Color(0xFF5A2A3C), Color(0xFFFFD9E8)),
        "slate" to CategoryColor(Color(0xFF3F443F), Color(0xFFE3E0DA)),
    )

    fun isKnown(key: String): Boolean = key in table

    /**
     * 解析事件色。容器色用于卡片底色，`on` 色用于其上文字。
     * [primaryFallback] 由调用方传 `MaterialTheme.colorScheme.primaryContainer` ——
     * 在 Composable 里读 `colorScheme` 会让本对象没法被非 UI 代码（小组件）复用。
     */
    @Composable
    fun colors(key: String, dark: Boolean): Pair<Color, Color> {
        val scheme = MaterialTheme.colorScheme
        val row = (if (dark) tableDark else table)[key]
            ?: return scheme.primaryContainer to scheme.onPrimaryContainer
        return row.container to row.on
    }

    /** 非 Composable 场景（如小组件解析色值）用的裸查表。 */
    fun raw(key: String, dark: Boolean): CategoryColor? =
        (if (dark) tableDark else table)[key]

    /** 色点的圆点色（表单里展示用），取容器色即可。 */
    @Composable
    fun swatch(key: String, dark: Boolean): Color = raw(key, dark)?.container ?: MaterialTheme.colorScheme.primary
}

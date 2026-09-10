package com.flechazo.toolbox.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---- Brand seed: warm teal #2E6E5E ----

// Light scheme
val md_light_primary = Color(0xFF2E6E5E)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFB2ECD9)
val md_light_onPrimaryContainer = Color(0xFF0A2E24)
val md_light_secondary = Color(0xFF4C635C)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFCEE9DE)
val md_light_onSecondaryContainer = Color(0xFF0A2E24)
val md_light_tertiary = Color(0xFF456179)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFCCE5F5)
val md_light_onTertiaryContainer = Color(0xFF102C3D)
val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)
val md_light_background = Color(0xFFFDFBF7)
val md_light_onBackground = Color(0xFF1C1B1F)
val md_light_surface = Color(0xFFFDFBF7)
val md_light_onSurface = Color(0xFF1C1B1F)
val md_light_surfaceVariant = Color(0xFFE3E0DA)
val md_light_onSurfaceVariant = Color(0xFF4A4A45)
val md_light_surfaceDim = Color(0xFFDEDAD4)
val md_light_surfaceBright = Color(0xFFFDFBF7)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFF8F5F0)
val md_light_surfaceContainer = Color(0xFFF3F0EA)
val md_light_surfaceContainerHigh = Color(0xFFEDEAE3)
val md_light_surfaceContainerHighest = Color(0xFFE7E3DC)
val md_light_inverseSurface = Color(0xFF31302C)
val md_light_inverseOnSurface = Color(0xFFF4F0EB)
val md_light_outline = Color(0xFF79747E)
val md_light_outlineVariant = Color(0xFFD9D5CE)

// Dark scheme
val md_dark_primary = Color(0xFF8AD5C0)
val md_dark_onPrimary = Color(0xFF06382C)
val md_dark_primaryContainer = Color(0xFF0F5245)
val md_dark_onPrimaryContainer = Color(0xFFB2ECD9)
val md_dark_secondary = Color(0xFFB3CCC2)
val md_dark_onSecondary = Color(0xFF1F352E)
val md_dark_secondaryContainer = Color(0xFF354B44)
val md_dark_onSecondaryContainer = Color(0xFFCEE9DE)
val md_dark_tertiary = Color(0xFFADCAE0)
val md_dark_onTertiary = Color(0xFF153449)
val md_dark_tertiaryContainer = Color(0xFF2D495F)
val md_dark_onTertiaryContainer = Color(0xFFCCE5F5)
val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_dark_background = Color(0xFF171A18)
val md_dark_onBackground = Color(0xFFE6E1E5)
val md_dark_surface = Color(0xFF171A18)
val md_dark_onSurface = Color(0xFFE6E1E5)
val md_dark_surfaceVariant = Color(0xFF3F443F)
val md_dark_onSurfaceVariant = Color(0xFFC4C8C0)
val md_dark_surfaceDim = Color(0xFF171A18)
val md_dark_surfaceBright = Color(0xFF3A3F3C)
val md_dark_surfaceContainerLowest = Color(0xFF0E1210)
val md_dark_surfaceContainerLow = Color(0xFF1A1E1C)
val md_dark_surfaceContainer = Color(0xFF1F2422)
val md_dark_surfaceContainerHigh = Color(0xFF2A2F2D)
val md_dark_surfaceContainerHighest = Color(0xFF343A37)
val md_dark_inverseSurface = Color(0xFFE6E1E5)
val md_dark_inverseOnSurface = Color(0xFF2F312F)
val md_dark_outline = Color(0xFF938F99)
val md_dark_outlineVariant = Color(0xFF44473F)

// ---------------------------------------------------------------------------
// 语义色（规范 §4.1②）
// 原本"成功"借用 primary、"警告"靠 error 硬凑。工具类里"已复制/已保存/超出范围"
// 是高频状态，值得独立成色，否则用户分不清"操作成功"和"这是主色"。
// 刻意不跟随动态取色（dynamicColor）：语义色应当稳定，不该随壁纸变。
// ---------------------------------------------------------------------------

/** 保存成功、迁移完成、复制成功 */
val md_light_success = Color(0xFF2E7D32)
val md_light_onSuccess = Color(0xFFFFFFFF)
val md_light_successContainer = Color(0xFFC8E6C9)
val md_light_onSuccessContainer = Color(0xFF0B3D0F)
val md_dark_success = Color(0xFF7FD69A)
val md_dark_onSuccess = Color(0xFF0A3D1A)
val md_dark_successContainer = Color(0xFF1B4D2A)
val md_dark_onSuccessContainer = Color(0xFFB6F0C6)

/** 农历超范围、精确闹钟未授权、ROM 白名单未加 */
val md_light_warning = Color(0xFF8A5A00)
val md_light_onWarning = Color(0xFFFFFFFF)
val md_light_warningContainer = Color(0xFFFFE7BC)
val md_light_onWarningContainer = Color(0xFF3D2800)
val md_dark_warning = Color(0xFFF5C77E)
val md_dark_onWarning = Color(0xFF3D2800)
val md_dark_warningContainer = Color(0xFF4A3410)
val md_dark_onWarningContainer = Color(0xFFFFDFAA)

/** 中性提示 —— 直接沿用 tertiary，不新增色 */
val md_light_info = md_light_tertiary
val md_dark_info = md_dark_tertiary

/**
 * 扩展语义色集合。通过 `MaterialTheme.extendedColors` 取用。
 *
 * 对比度：正文 ≥4.5:1、大字/图标 ≥3:1，浅深双向校验（规范 §4.1③）。
 */
data class ToolExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = ToolExtendedColors(
    success = md_light_success,
    onSuccess = md_light_onSuccess,
    successContainer = md_light_successContainer,
    onSuccessContainer = md_light_onSuccessContainer,
    warning = md_light_warning,
    onWarning = md_light_onWarning,
    warningContainer = md_light_warningContainer,
    onWarningContainer = md_light_onWarningContainer,
)

internal val DarkExtendedColors = ToolExtendedColors(
    success = md_dark_success,
    onSuccess = md_dark_onSuccess,
    successContainer = md_dark_successContainer,
    onSuccessContainer = md_dark_onSuccessContainer,
    warning = md_dark_warning,
    onWarning = md_dark_onWarning,
    warningContainer = md_dark_warningContainer,
    onWarningContainer = md_dark_onWarningContainer,
)

// ---------------------------------------------------------------------------
// 类目色（规范 §4.1①）
// 原本只有一组浅色容器 + 深色前景，深色主题直接复用 → 浅色底片压在暗卡片上，
// 刺眼且层级反转。现在补一套深色版，并给 on 压在 container 上留足 4.5:1 对比。
//
// 【浅色 container 浓度对齐】规范 §11.7②
// Bento 首页靠"色块浓淡"表达优先级，所以 6 个 container 的浓度必须齐 ——
// 否则同一个信号强弱不一（原值 1.098~1.19，TEXT 几乎糊进背景）。
// 现按「vs 页面底色 = 1.18:1」对齐：只调 HSL 的 L、保持 H/S 不变，
// 所以色相没变、改的只是浓度。色值由 scripts/align_category_colors.mjs 算出，
// 不要手改 —— 要调目标值就改脚本参数重跑。
// 深色 container 未做同样处理：深色下页底本身很暗（#171A18），色块天然分离度更高
// （1.19~1.42），强行对齐反而会抬高 onSurface 的对比压力，得不偿失。
// ---------------------------------------------------------------------------

/** 类目色：图标底片背景 [container] + 其上的图标/文字色 [on] */
data class CategoryColor(val container: Color, val on: Color)

val LightCategoryColors: Map<String, CategoryColor> = mapOf(
    "CALCULATE" to CategoryColor(Color(0xFFDEEAFA), Color(0xFF1D4F82)),
    "IMAGE" to CategoryColor(Color(0xFFE8E7FE), Color(0xFF534AB7)),
    "TEXT" to CategoryColor(Color(0xFFD0EFE5), Color(0xFF0F6E56)),
    "LIFE" to CategoryColor(Color(0xFFF8E6CA), Color(0xFF854F0B)),
    "MEASURE" to CategoryColor(Color(0xFFE0EDCF), Color(0xFF3B6D11)),
    "SECURITY" to CategoryColor(Color(0xFFFAE3EB), Color(0xFF993556)),
)

val DarkCategoryColors: Map<String, CategoryColor> = mapOf(
    "CALCULATE" to CategoryColor(Color(0xFF20364F), Color(0xFFC6DEFF)),
    "IMAGE" to CategoryColor(Color(0xFF2E2A5C), Color(0xFFD6D3FF)),
    "TEXT" to CategoryColor(Color(0xFF123A2F), Color(0xFFB7EFD9)),
    "LIFE" to CategoryColor(Color(0xFF3D2E12), Color(0xFFF5D9A3)),
    "MEASURE" to CategoryColor(Color(0xFF26351A), Color(0xFFD3E8AC)),
    "SECURITY" to CategoryColor(Color(0xFF3F1E29), Color(0xFFF8C6D6)),
)

/**
 * 兼容旧名（等于浅色组）。**新代码请用 [categoryColor]**，
 * 它会按当前主题自动选深浅两套。
 */
val CategoryColors: Map<String, CategoryColor> = LightCategoryColors

/**
 * 主题感知的类目色。
 *
 * 注意用的是 [LocalToolDarkTheme] 而不是 `isSystemInDarkTheme()`——
 * 用户可以在设置里把主题锁定为深色，此时系统可能是浅色，
 * 用系统值会取错色板。
 */
@Composable
@ReadOnlyComposable
fun categoryColor(key: String): CategoryColor {
    val palette = if (LocalToolDarkTheme.current) DarkCategoryColors else LightCategoryColors
    return palette[key] ?: CategoryColor(
        container = MaterialTheme.colorScheme.secondaryContainer,
        on = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

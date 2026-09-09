package com.flechazo.toolbox.core.designsystem.theme

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

// Category accent colors (icon chip background / foreground)
data class CategoryColor(val container: Color, val on: Color)

val CategoryColors: Map<String, CategoryColor> = mapOf(
    "CALCULATE" to CategoryColor(Color(0xFFDDE9FA), Color(0xFF1D4F82)),
    "IMAGE" to CategoryColor(Color(0xFFEEEDFE), Color(0xFF534AB7)),
    "TEXT" to CategoryColor(Color(0xFFE1F5EE), Color(0xFF0F6E56)),
    "LIFE" to CategoryColor(Color(0xFFFAEEDA), Color(0xFF854F0B)),
    "MEASURE" to CategoryColor(Color(0xFFEAF3DE), Color(0xFF3B6D11)),
    "SECURITY" to CategoryColor(Color(0xFFFBEAF0), Color(0xFF993556)),
)

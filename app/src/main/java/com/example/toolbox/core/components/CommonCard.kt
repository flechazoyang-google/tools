package com.example.toolbox.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Shared card component. Uses 1px border instead of shadow, 12dp rounded corners.
 * Press animation: subtle 0.97 scale + border color shift (150ms ease-out).
 */
@Composable
fun CommonCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val defaultBorderColor = MaterialTheme.colorScheme.outlineVariant
    val pressedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant

    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            if (pressed) 0.98f else 1f,
            label = "cardScale",
        )
        val borderColor by animateColorAsState(
            if (pressed) pressedBorderColor else defaultBorderColor,
            animationSpec = tween(150),
            label = "cardBorder",
        )
        Card(
            onClick = onClick,
            modifier = modifier.scale(scale),
            interactionSource = interaction,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = elevation,
            border = BorderStroke(1.dp, borderColor),
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = elevation,
            border = BorderStroke(1.dp, defaultBorderColor),
        ) { content() }
    }
}

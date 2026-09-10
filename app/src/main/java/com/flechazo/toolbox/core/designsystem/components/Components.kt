package com.flechazo.toolbox.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flechazo.toolbox.core.designsystem.theme.Space
import com.flechazo.toolbox.core.designsystem.theme.ToolMotion
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.designsystem.theme.categoryColor
import com.flechazo.toolbox.core.designsystem.theme.extendedColors
import com.flechazo.toolbox.core.designsystem.theme.horizontalSafePadding
import com.flechazo.toolbox.core.designsystem.theme.navigationBarBottomInset
import com.flechazo.toolbox.core.designsystem.theme.rememberToolHaptics
import com.flechazo.toolbox.core.designsystem.theme.tabularNumbers

/** 平板/大屏下内容最大宽度（规范 §5「ToolScaffold」）。 */
private val ContentMaxWidth = 600.dp

/** 分段控件高度。 */
private val SegmentHeight = 44.dp

/** 触摸目标最小高度（无障碍）。 */
private val MinTouchTarget = 48.dp

/** [ToolSectionCard] 标题行左侧类目色条的宽度。 */
private val AccentBarWidth = 4.dp

// ---------------------------------------------------------------------------
// Grid card (home page)
// ---------------------------------------------------------------------------

/**
 * Compact grid card for a single tool: icon chip + title in one row.
 *
 * @param onToggleFavorite 非空时右侧常驻收藏星（未收藏 = `Outlined.StarOutline`，
 *   已收藏 = `Filled.Star`）并可点按切换；为空时仅在 [favorite] 为真时显示实心星。
 */
@Composable
fun ToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    categoryKey: String,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val catColor = categoryColor(categoryKey)
    val haptics = rememberToolHaptics()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = ToolMotion.PRESS_SCALE_CARD)
            .clip(ToolShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ToolShape.lg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ToolShape.md)
                    .background(catColor.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = catColor.on, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.md))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onToggleFavorite != null) {
                val starInteraction = remember { MutableInteractionSource() }
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ToolShape.full)
                        .clickable(interactionSource = starInteraction, indication = null) {
                            haptics.confirm()
                            onToggleFavorite()
                        }
                        .padding(7.dp),
                )
            } else if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar + page scaffold
// ---------------------------------------------------------------------------

/** Standard inner-page top bar: back arrow + title (+ optional subtitle & actions). */
@Composable
fun ToolTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val haptics = rememberToolHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 边到边：标题栏自己顶开状态栏。padding 放在这里而不是页面根节点上，
            // 于是标题栏会随内容一起滚走，滚下去后状态栏区域露出的是**后续内容**
            // 而不是一条空白——这才叫"内容从状态栏下滚过"。
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier
                    .size(44.dp)
                    .clip(ToolShape.full)
                    .clickable {
                        haptics.tick()
                        onBack()
                    }
                    .padding(Space.md),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions?.invoke(this)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = Space.lg, bottom = Space.sm - 2.dp),
            )
        }
    }
}

/**
 * Unified page scaffold for tool inner pages.
 *
 * 分区间距 **24dp**（卡内仍为 12dp——分区要拉开、卡内要紧凑）；
 * 宽屏（平板/折叠屏展开）下内容居中并限制 600dp，避免一行拉得过长。
 */
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // 刘海/挖孔：横屏时它在侧边，那个位置不该有内容，所以左右直接让开
            // （横向不追求穿透，纵向才追求）。
            .horizontalSafePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = Space.lg)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                // 底部让开系统导航条。刻意用 navigationBars 而不是 safeDrawing：
                // 后者含输入法，会让 scrollable=false 的页面（计算器、密码箱）在
                // 弹键盘时被整体压扁。
                .padding(bottom = navigationBarBottomInset() + Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            ToolTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions)
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------

/**
 * 统一输入框：填充式 + 16dp 圆角（与卡片齐平，形成节奏），无生硬的外框线条；
 * 聚焦时容器上浮半档（`Low → High`）并以主色下划线强调，错误时用 error 容器色 +
 * 一次拒绝触觉。
 *
 * 参数签名刻意与 `OutlinedTextField` 对齐，方便全站统一替换。
 */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = ToolShape.lg,
) {
    if (isError) {
        val haptics = rememberToolHaptics()
        // 只在"刚变成错误态"时震一次，而不是每次重组都震
        LaunchedEffect(isError) { haptics.reject() }
    }
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = MaterialTheme.colorScheme.error,
        ),
    )
}

// ---------------------------------------------------------------------------
// Section card
// ---------------------------------------------------------------------------

/**
 * Grouped content card with optional title row.
 *
 * @param accentColor 非空时标题行左侧出现一段 4dp 类目色条——让分组**可被识别**，
 *   而不只是一个灰标题。传入 `categoryColor(key).on` 即可。
 */
@Composable
fun ToolSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ToolShape.lg,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm + 2.dp),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (accentColor != null) {
                        Box(
                            modifier = Modifier
                                .width(AccentBarWidth)
                                .height(14.dp)
                                .clip(ToolShape.xs)
                                .background(accentColor),
                        )
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Result card
// ---------------------------------------------------------------------------

/**
 * Emphasized result display: label + big tabular value + optional unit/caption.
 *
 * 一屏只留 **2 个字号层级**（labelSmall + displaySmall）——原来的
 * "标签 + 值 + 单位"是 3 层，层级一多就看不出主次。
 *
 * @param singleLine false 时允许换行并支持选中复制（Base64 等多行结果）
 * @param animateChange 为 true 且 [value] 是纯整数时，数值会平滑滚动而不是直接跳
 *   （结果卡、天数、BMI 这类"算完就变"的地方值得开）
 */
@Composable
fun ResultCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    singleLine: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    animateChange: Boolean = false,
) {
    val haptics = rememberToolHaptics()
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = ToolMotion.effectDefaultColor,
        label = "resultContainer",
    )
    val displayValue = if (animateChange) {
        val target = value.toIntOrNull()
        if (target != null) {
            val animated by animateIntAsState(
                targetValue = target,
                animationSpec = ToolMotion.countDefault,
                label = "resultValue",
            )
            animated.toString()
        } else {
            value
        }
    } else {
        value
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(ToolShape.lg)
                        .clickable {
                            haptics.confirm()
                            onClick()
                        }
                } else {
                    Modifier
                },
            ),
        shape = ToolShape.lg,
        color = animatedContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                val valueText: @Composable () -> Unit = {
                    Text(
                        displayValue,
                        style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                        color = contentColor,
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (singleLine) {
                    valueText()
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        valueText()
                    }
                }
                if (unit != null) {
                    Spacer(Modifier.width(Space.xs + 2.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Key-value row
// ---------------------------------------------------------------------------

/** Label-left / value-right row with tabular numbers. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val haptics = rememberToolHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(ToolShape.md)
                        .clickable {
                            haptics.confirm()
                            onClick()
                        }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Space.sm - 2.dp, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Segmented tabs (single choice)
// ---------------------------------------------------------------------------

/**
 * Single-choice segmented tabs with a **滑动的胶囊指示器**。
 *
 * 这是 Expressive 观感最强的单点：选中态不是一个按钮自己变底色，而是指示器
 * 在选项之间**滑过去**（[ToolMotion.spatialDefaultDp]），文字色同步过渡。
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberToolHaptics()
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SegmentHeight)
            .clip(ToolShape.full)
            .background(scheme.surfaceContainer),
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = ToolMotion.spatialDefaultDp,
            label = "segmentIndicator",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(ToolShape.full)
                .background(scheme.primary),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = ToolMotion.effectFastColor,
                    label = "segmentContent",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ToolShape.full)
                        .clickable(interactionSource = interaction, indication = null) {
                            if (!isSelected) {
                                haptics.tick()
                                onSelect(option)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Labeled dropdown
// ---------------------------------------------------------------------------

/** Labeled dropdown for long option lists (filled style, matches [ToolTextField]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        ToolTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Feedback block (loading / error / empty / warning / success)
// ---------------------------------------------------------------------------

private object FeedbackIcons {
    val error = Icons.Filled.CloudOff
    val empty = Icons.Filled.Inbox
    val warning = Icons.Filled.WarningAmber
    val success = Icons.Filled.CheckCircle
}

/**
 * Unified feedback block.
 *
 * 关于 LOADING：规范原文写的是"转圈一律改骨架屏"，但对着实际调用点看，
 * `LOADING` 几乎都用在**短时操作**（"压缩中…"、"识别中…"、"处理中…"）——
 * 这类场景没有"内容形状"可预演，骨架屏反而是错的隐喻。
 * 所以这里保留转圈给操作类，**新增 [FeedbackType.SKELETON] 给内容加载类**
 * （列表/详情页首屏），两者各司其职。
 */
@Composable
fun FeedbackBlock(
    text: String,
    modifier: Modifier = Modifier,
    type: FeedbackType = FeedbackType.EMPTY,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm + 2.dp),
    ) {
        when (type) {
            FeedbackType.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )

            FeedbackType.SKELETON -> SkeletonLines(modifier = Modifier.fillMaxWidth())

            FeedbackType.SUCCESS -> Icon(
                FeedbackIcons.success,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.success,
                modifier = Modifier.size(36.dp),
            )

            FeedbackType.WARNING -> Icon(
                FeedbackIcons.warning,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.warning,
                modifier = Modifier.size(36.dp),
            )

            FeedbackType.ERROR -> Icon(
                FeedbackIcons.error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp),
            )

            FeedbackType.EMPTY -> Icon(
                FeedbackIcons.empty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onAction != null && actionLabel != null) {
            ToolButton(onClick = onAction, label = actionLabel)
        }
    }
}

/** 骨架屏：同形状灰块 + 微弱 alpha 呼吸。 */
@Composable
private fun SkeletonLines(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            // 有意保留 tween：无限循环的呼吸动画只能靠 infiniteRepeatable，弹簧无法 repeat
            animation = tween(durationMillis = ToolMotion.SKELETON_BREATH_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(ToolShape.sm)
                .background(blockColor),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(ToolShape.sm)
                .background(blockColor),
        )
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(16.dp)
                .clip(ToolShape.sm)
                .background(blockColor),
        )
    }
}

enum class FeedbackType { LOADING, SKELETON, ERROR, EMPTY, WARNING, SUCCESS }

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/**
 * 带按压缩放 + 轻点触觉的按钮（规范 §5「按钮」）。
 *
 * 新代码优先用它；存量页面逐步替换即可，`Button` 本身也没坏。
 */
@Composable
fun ToolButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberToolHaptics()
    Button(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Text(label)
    }
}

// ---------------------------------------------------------------------------
// Bottom action bar
// ---------------------------------------------------------------------------

/** Fixed-style bottom action bar: primary button + optional secondary + stats caption. */
@Composable
fun BottomActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    caption: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberToolHaptics()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ToolShape.xl,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm - 2.dp),
        ) {
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Button(
                    onClick = {
                        haptics.tick()
                        onPrimary()
                    },
                    enabled = enabled,
                    interactionSource = interaction,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .pressScale(interaction),
                ) { Text(primaryLabel) }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.height(52.dp),
                    ) { Text(secondaryLabel) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

/** Section header with title. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

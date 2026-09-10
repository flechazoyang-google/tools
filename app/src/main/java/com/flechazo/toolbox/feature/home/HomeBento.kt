package com.flechazo.toolbox.feature.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flechazo.toolbox.core.designsystem.components.pressScale
import com.flechazo.toolbox.core.designsystem.theme.CategoryColor
import com.flechazo.toolbox.core.designsystem.theme.Space
import com.flechazo.toolbox.core.designsystem.theme.ToolMotion
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.designsystem.theme.categoryColor
import com.flechazo.toolbox.core.designsystem.theme.rememberToolHaptics
import com.flechazo.toolbox.core.registry.ToolCategory
import com.flechazo.toolbox.core.registry.ToolDef

// ---------------------------------------------------------------------------
// Bento 卡片（规范 §六）
//
// 首页的核心变化是**用尺寸差表达优先级**：同一屏里只允许两种卡片尺寸，
// 大卡永远给"真·高频"，小卡给"其余常用"，分类则收成色块入口。
//
// 配色规则只有一条：**大卡和分类卡铺满浓度的类目色，小卡保持中性底**。
// 于是"有颜色 = 优先级高"成了不需要解释的视觉语言。
// 大卡里的图标底片要反过来用 surfaceContainerLowest（浅色下是白、深色下近黑），
// 否则底片和卡片同色会直接消失。
// ---------------------------------------------------------------------------

// 三张卡的高度都是**下限**而不是固定值（用 heightIn 而非 height）。
// 原因：fontScale 1.3 的大字模式下 CompactToolCard 内容高约 106.8dp（padding 24 +
// 图标 36 + 标题 26 + 描述 20.8），固定 104dp 会直接把最后一行 clip 掉。
// 改成下限后：正常字号下内容不足，高度仍是原值，布局零变化；
// 大字模式下自动撑开。同行卡片结构一致（图标+标题+描述），放大倍数相同，
// 因此不会出现"同一行两张卡高度不一"的问题。

/** Hero 大卡高度下限（2×1）。 */
private val HeroCardHeight = 124.dp

/** 小卡高度下限（1×1）。两列布局下宽度约 164dp，这个高度刚好放下"底片 + 两行字"。 */
private val CompactCardHeight = 104.dp

/** 分类入口卡高度下限（一行三张）。 */
private val CategoryCardHeight = 82.dp

/**
 * 「最近使用 / 收藏」泳道的**大卡**（2×1）。
 *
 * 大格子要装最多的信息，不是最大的空白——所以它比小卡多给了两样东西：
 * 类目胶囊（说明"这是哪一类工具"）和完整的一句话说明。
 */
@Composable
internal fun HeroToolCard(
    tool: ToolDef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catColor = categoryColor(tool.category.key)
    val haptics = rememberToolHaptics()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HeroCardHeight)
            .pressScale(interaction, pressedScale = ToolMotion.PRESS_SCALE_CARD)
            .clip(ToolShape.xl)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            ),
        shape = ToolShape.xl,
        color = catColor.container,
    ) {
        Row(
            modifier = Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ToolShape.lg)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tool.icon,
                    contentDescription = null,
                    tint = catColor.on,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(Space.lg))
            Column(modifier = Modifier.weight(1f)) {
                CategoryLabel(label = tool.category.label, color = catColor)
                Spacer(Modifier.height(Space.sm))
                Text(
                    tool.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 「最近使用 / 收藏」泳道的**小卡**（1×1，搜索结果为同一张卡）。
 *
 * 纵向排布：图标底片在上、两行文字在下——横向排在两列宽度里会挤掉说明文字。
 */
@Composable
internal fun CompactToolCard(
    tool: ToolDef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catColor = categoryColor(tool.category.key)
    val haptics = rememberToolHaptics()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CompactCardHeight)
            .pressScale(interaction, pressedScale = ToolMotion.PRESS_SCALE_CARD)
            .clip(ToolShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            ),
        shape = ToolShape.lg,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Space.md)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ToolShape.md)
                    .background(catColor.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tool.icon,
                    contentDescription = null,
                    tint = catColor.on,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                tool.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 分类入口卡（一行三张）。
 *
 * 首页不再直接铺 27 张工具卡，而是给 6 个类目各一张色块，点进去才看该类列表。
 * 这也是首页能"一屏装下全部导航"的关键一步。
 */
@Composable
internal fun CategoryEntryCard(
    category: ToolCategory,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catColor = categoryColor(category.key)
    val haptics = rememberToolHaptics()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .heightIn(min = CategoryCardHeight)
            .pressScale(interaction, pressedScale = ToolMotion.PRESS_SCALE_CARD)
            .clip(ToolShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            ),
        shape = ToolShape.lg,
        color = catColor.container,
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(ToolShape.full)
                        .background(catColor.on),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "$count 个工具",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 类目标签。只在大卡上出现，让"这块属于哪一类"一眼可读。
 *
 * 用纯文字上色而非"胶囊底 + 文字"：大卡底色本来就是同一个类目色，
 * 再叠一层同色底片等于没画，只会把字挤小。
 */
@Composable
private fun CategoryLabel(label: String, color: CategoryColor) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color.on,
    )
}

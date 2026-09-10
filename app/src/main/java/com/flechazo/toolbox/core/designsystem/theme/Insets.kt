package com.flechazo.toolbox.core.designsystem.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// 边到边（edge-to-edge）—— 规范 §4.8
//
// 应用整体是 edge-to-edge 的：系统栏透明，页面内容一直铺到屏幕物理边缘。
// 所以**没有任何一个页面可以假设"我从状态栏下面开始"**——insets 必须由页面自己领。
//
// 这里只放跨页面共用的三个量，避免 30 多个页面各自复述一遍 WindowInsets 的取法：
//   1. 顶部：[statusBarTopInset]（含刘海/挖孔，取 safeDrawing 的 top 端）
//   2. 底部：[navigationBarBottomInset]（用 navigationBars 而非 safeDrawing，
//      刻意**不含输入法**——包含 ime 会让不可滚动的页面在弹键盘时被压缩变形）
//   3. 左右：[Modifier.horizontalSafePadding]（横屏时刘海在侧边）
// ---------------------------------------------------------------------------

/**
 * 底部玻璃导航栏占用的总高度（含系统导航条 insets）。
 *
 * 由 `ToolboxApp` 的 `Scaffold` 测量后下发：
 * - 首页 / 工具 / 我的 三个 Tab 页 = 玻璃栏高度（80dp 上下 + 系统导航条）
 * - 工具内页 = `0.dp`（这些页面没有底栏，改用 [navigationBarBottomInset]）
 *
 * Tab 页拿它当**滚动内容的底部留白**，而不是给整个页面加 padding——这样列表
 * 中段的内容会从玻璃栏**下方**滚过（玻璃层是半透明的，内容真的透出来），
 * 而最后一项仍能完整滚出玻璃栏，不会被永久压住。
 */
val LocalBottomBarHeight = staticCompositionLocalOf { 0.dp }

/**
 * 顶部安全高度：状态栏与刘海/挖孔取大者。
 *
 * 用法是加进滚动容器的 `contentPadding.top`，**不要**给页面根节点加 padding——
 * 那样会把视口切掉一块，内容永远滚不到状态栏下面，边到边就白做了。
 */
@Composable
fun statusBarTopInset(): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

/** 底部系统导航条高度（不含输入法）。 */
@Composable
fun navigationBarBottomInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * 左右安全边距（横屏时刘海在侧边，竖屏时通常为 0）。
 *
 * 横向**不**追求内容穿透——刘海区本来就不该有内容，所以直接给根节点加 padding。
 */
@Composable
fun Modifier.horizontalSafePadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

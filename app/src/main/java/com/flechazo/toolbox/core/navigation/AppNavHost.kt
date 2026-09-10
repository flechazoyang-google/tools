package com.flechazo.toolbox.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flechazo.toolbox.core.data.SettingsRepository
import com.flechazo.toolbox.core.data.ThemeMode
import com.flechazo.toolbox.core.data.ToolsStateRepository
import com.flechazo.toolbox.core.designsystem.theme.GlassLevel
import com.flechazo.toolbox.core.designsystem.theme.ToolMotion
import com.flechazo.toolbox.core.designsystem.theme.ToolboxTheme
import com.flechazo.toolbox.core.designsystem.theme.glassSurface
import com.flechazo.toolbox.core.designsystem.theme.rememberToolHaptics
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.registry.ToolCategory
import com.flechazo.toolbox.core.update.UpdateDialog
import com.flechazo.toolbox.core.update.UpdateRepository
import com.flechazo.toolbox.core.update.UpdateUiState
import com.flechazo.toolbox.feature.home.HomeScreen
import com.flechazo.toolbox.feature.settings.SettingsScreen
import com.flechazo.toolbox.feature.tools.CategoryScreen
import com.flechazo.toolbox.feature.tools.ToolsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val toolsState: ToolsStateRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        settings.themeMode,
        settings.dynamicColor,
    ) { mode, dynamic ->
        AppUiState(mode, dynamic)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    val updateState: StateFlow<UpdateUiState> = updateRepository.state

    /** 启动时调用；内部按 24 小时限频，不会每次都联网。 */
    fun autoCheckForUpdate() {
        viewModelScope.launch { updateRepository.autoCheck() }
    }

    fun dismissUpdate() {
        updateRepository.dismiss()
    }

    fun recordRecent(toolId: String) {
        viewModelScope.launch { toolsState.recordRecent(toolId) }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    /** 未选中态用 outlined、选中态用 filled —— M3 标准做法，比"同一个图标换颜色"清楚得多。 */
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem("home", "首页", Icons.Outlined.Home, Icons.Filled.Home),
    BottomNavItem("tools", "工具", Icons.Outlined.Widgets, Icons.Filled.Widgets),
    BottomNavItem("settings", "我的", Icons.Outlined.Person, Icons.Filled.Person),
)

// ---------------------------------------------------------------------------
// 转场（规范 §4.6）
//
// 全部换成弹簧：打断时保留速度矢量，连续操作不会"卡一下"。
// 同时**降级掉全屏滑动**——2026 已把大面积转场列为过时手法，且在 27 个工具间
// 高频往返时非常累。工具页改成"内容位移约 1/14 屏宽 + 淡入"。
// ---------------------------------------------------------------------------

/** 顶级 Tab 之间：淡入 + 内容从 0.98 放大到 1.0。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(): EnterTransition =
    fadeIn(animationSpec = ToolMotion.effectDefault) +
        scaleIn(
            animationSpec = ToolMotion.spatialDefault,
            initialScale = ToolMotion.TAB_ENTER_SCALE,
        )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition =
    fadeOut(animationSpec = ToolMotion.effectFast)

/** 进入工具页：内容小幅位移 + 淡入。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.toolEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = ToolMotion.spatialOffset,
        initialOffset = { fullWidth -> fullWidth / ToolMotion.NAV_SLIDE_DIVISOR },
    ) + fadeIn(animationSpec = ToolMotion.effectDefault)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(): ExitTransition =
    fadeOut(animationSpec = ToolMotion.effectFast)

/** 从工具页返回：反向小幅位移 + 淡出。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.toolPopExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = ToolMotion.spatialOffset,
        targetOffset = { fullWidth -> fullWidth / ToolMotion.NAV_SLIDE_DIVISOR },
    ) + fadeOut(animationSpec = ToolMotion.effectFast)

/** 底部导航栏形状：只圆上面两个角，像一张从底部升起的浮层。 */
private val BottomBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@Composable
fun ToolboxApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val appViewModel: AppViewModel = hiltViewModel()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by appViewModel.updateState.collectAsStateWithLifecycle()

    // 启动时静默检查更新（内部 24 小时限频）
    LaunchedEffect(Unit) { appViewModel.autoCheckForUpdate() }

    // 通知点击交接：把用户带到对应工具页，而不是落在首页
    val deepLinkTool by DeepLink.toolId.collectAsStateWithLifecycle()
    LaunchedEffect(deepLinkTool) {
        val toolId = deepLinkTool ?: return@LaunchedEffect
        DeepLink.clearTool()
        if (ToolCatalog.byId(toolId) == null) return@LaunchedEffect
        appViewModel.recordRecent(toolId)
        navController.navigate("tool/$toolId") { launchSingleTop = true }
    }

    val darkTheme = when (uiState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // 系统栏图标明暗跟随应用主题（而非仅跟随系统设置）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    ToolboxTheme(darkTheme = darkTheme, dynamicColor = uiState.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                // 页面的 insets 一律由页面自己领（见 Insets.kt）。
                //
                // 这里若保留 Scaffold 默认的 contentWindowInsets（= systemBars），NavHost 的
                // 视口会被状态栏和导航条各切掉一截：内容永远滚不到状态栏下面，也钻不到玻璃
                // 底栏下面——"边到边"就只剩个名义，玻璃层也就透不出任何东西。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (currentRoute in bottomNavItems.map { it.route }) {
                        // 玻璃层的 4 个允许位之一：底部导航（规范 §4.8）
                        NavigationBar(
                            modifier = Modifier.glassSurface(
                                shape = BottomBarShape,
                                level = GlassLevel.Bar,
                            ),
                            containerColor = Color.Transparent,
                        ) {
                            bottomNavItems.forEach { item ->
                                key(item.route) {
                                    val selected = currentRoute == item.route
                                    val haptics = rememberToolHaptics()
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) haptics.tick()
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.iconFilled else item.iconOutlined,
                                                contentDescription = item.label,
                                            )
                                        },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                // 底栏占位高度（玻璃栏 + 系统导航条）传给三个 Tab 页：它们拿它当**滚动内容
                // 的底部留白**，于是列表中段会从玻璃栏下面滚过（玻璃是半透明的，内容真的
                // 透出来），最后一项也仍能完整滚出栏外。工具内页没有底栏，改用
                // navigationBarBottomInset()。
                val bottomBarPadding = padding.calculateBottomPadding()
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(
                        route = "home",
                        enterTransition = { tabEnter() },
                        exitTransition = { tabExit() },
                        popEnterTransition = { tabEnter() },
                        popExitTransition = { tabExit() },
                    ) {
                        HomeScreen(
                            openTool = { id ->
                                appViewModel.recordRecent(id)
                                navController.navigate("tool/$id")
                            },
                            openCategory = { category ->
                                navController.navigate("category/${category.key}")
                            },
                            bottomBarPadding = bottomBarPadding,
                        )
                    }
                    composable(
                        route = "tools",
                        enterTransition = { tabEnter() },
                        exitTransition = { tabExit() },
                        popEnterTransition = { tabEnter() },
                        popExitTransition = { tabExit() },
                    ) {
                        ToolsScreen(
                            openTool = { id ->
                                appViewModel.recordRecent(id)
                                navController.navigate("tool/$id")
                            },
                            bottomBarPadding = bottomBarPadding,
                        )
                    }
                    composable(
                        route = "category/{categoryKey}",
                        arguments = listOf(navArgument("categoryKey") { type = NavType.StringType }),
                        enterTransition = { toolEnter() },
                        exitTransition = { pageExit() },
                        popEnterTransition = { tabEnter() },
                        popExitTransition = { toolPopExit() },
                    ) { entry ->
                        val key = entry.arguments?.getString("categoryKey")
                        // 路由来自首页的入口卡；key 不合法时当作空页而不是崩溃
                        val category = key?.let { runCatching { ToolCategory.byKey(it) }.getOrNull() }
                        if (category != null) {
                            CategoryScreen(
                                category = category,
                                onBack = { navController.popBackStack() },
                                openTool = { id ->
                                    appViewModel.recordRecent(id)
                                    navController.navigate("tool/$id")
                                },
                            )
                        }
                    }
                    composable(
                        route = "settings",
                        enterTransition = { tabEnter() },
                        exitTransition = { tabExit() },
                        popEnterTransition = { tabEnter() },
                        popExitTransition = { tabExit() },
                    ) { SettingsScreen(bottomBarPadding = bottomBarPadding) }
                    composable(
                        route = "tool/{toolId}",
                        arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
                        enterTransition = { toolEnter() },
                        exitTransition = { pageExit() },
                        popEnterTransition = { tabEnter() },
                        popExitTransition = { toolPopExit() },
                    ) { entry ->
                        val toolId = entry.arguments?.getString("toolId")
                        val tool = toolId?.let { ToolCatalog.byId(it) }
                        if (tool != null) {
                            Box(Modifier.padding(bottom = 0.dp)) {
                                // 位置参数：composable 函数类型的命名参数在 Kotlin 2.0 会报错
                                tool.content({ navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }

        // 发现新版本时弹窗（覆盖任意页面）
        (updateState as? UpdateUiState.Available)?.let { available ->
            UpdateDialog(info = available.info, onDismiss = appViewModel::dismissUpdate)
        }
    }
}

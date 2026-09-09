package com.flechazo.toolbox.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import com.flechazo.toolbox.core.designsystem.theme.ToolboxTheme
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.update.UpdateDialog
import com.flechazo.toolbox.core.update.UpdateRepository
import com.flechazo.toolbox.core.update.UpdateUiState
import com.flechazo.toolbox.feature.home.HomeScreen
import com.flechazo.toolbox.feature.settings.SettingsScreen
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

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem("home", "首页", Icons.Filled.Home),
    BottomNavItem("tools", "工具", Icons.Filled.Widgets),
    BottomNavItem("settings", "我的", Icons.Filled.Person),
)

private const val TRANSITION_MS = 280

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
                bottomBar = {
                    if (currentRoute in bottomNavItems.map { it.route }) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(TRANSITION_MS),
                        ) + fadeIn(tween(TRANSITION_MS))
                    },
                    exitTransition = { fadeOut(tween(TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(TRANSITION_MS),
                        ) + fadeOut(tween(TRANSITION_MS))
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            openTool = { id ->
                                appViewModel.recordRecent(id)
                                navController.navigate("tool/$id")
                            },
                        )
                    }
                    composable("tools") {
                        ToolsScreen(
                            openTool = { id ->
                                appViewModel.recordRecent(id)
                                navController.navigate("tool/$id")
                            },
                        )
                    }
                    composable("settings") { SettingsScreen() }
                    composable(
                        route = "tool/{toolId}",
                        arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
                    ) { entry ->
                        val toolId = entry.arguments?.getString("toolId")
                        val tool = toolId?.let { ToolCatalog.byId(it) }
                        if (tool != null) {
                            Box(Modifier.padding(bottom = 0.dp)) {
                                tool.content(onBack = { navController.popBackStack() })
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

package com.example.toolbox.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.toolbox.core.theme.ThemeMode
import com.example.toolbox.core.theme.ToolboxTheme
import com.example.toolbox.core.tool.ToolRegistry
import com.example.toolbox.feature.home.HomeScreen
import com.example.toolbox.feature.settings.SettingsScreen
import com.example.toolbox.feature.settings.SettingsViewModel
import com.example.toolbox.feature.tools.ToolsScreen

private const val TRANSITION_DURATION = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val settingsVm: SettingsViewModel = hiltViewModel()
    val themeMode by settingsVm.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ToolboxTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val snackbarVm: SnackbarViewModel = hiltViewModel()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                snackbarVm.eventBus.events.collect { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }

            Scaffold(
                modifier = Modifier.imePadding(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        tonalElevation = 0.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (selected) MaterialTheme.colorScheme.onSurface
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                                alwaysShowLabel = true,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(TRANSITION_DURATION),
                            initialOffsetX = { it / 4 },
                        ) + fadeIn(animationSpec = tween(TRANSITION_DURATION))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(TRANSITION_DURATION))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(TRANSITION_DURATION))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(TRANSITION_DURATION),
                            targetOffsetX = { it / 4 },
                        ) + fadeOut(animationSpec = tween(TRANSITION_DURATION))
                    },
                ) {
                    composable("home") { HomeScreen(navController) }
                    composable("tools") { ToolsScreen(navController) }
                    composable("settings") { SettingsScreen(navController) }

                    // ---- Auto-generated destinations from the tool registry ----
                    ToolRegistry.tools.forEach { tool ->
                        composable(tool.route) { tool.content(navController) }
                    }
                }
            }
        }
    }
}

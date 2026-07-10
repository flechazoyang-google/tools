package com.example.toolbox.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.toolbox.BuildConfig
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.core.theme.ThemeMode
import kotlinx.coroutines.launch

private val Indigo = Color(0xFF6366F1)
private val Amber = Color(0xFFD97706)
private val DarkBlue = Color(0xFF1E3A5F)
private val Green = Color(0xFF10B981)
private val Blue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopBar(title = "设置") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp),
        ) {
            // ── 外观 ──
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "外观",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingContent = {
                            ThemeIcon(mode)
                        },
                        trailingContent = {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = null,
                            )
                        },
                        modifier = Modifier
                            .clickable { viewModel.setThemeMode(mode) }
                            .heightIn(min = 56.dp),
                    )
                }
            }

            // ── 数据管理 ──
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "数据管理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DataActionItem(
                    icon = Icons.Filled.Upload,
                    iconColor = Green,
                    title = "导出数据",
                    description = "备份到本地文件",
                    onClick = {
                        scope.launch {
                            val path = viewModel.exportData()
                            message = if (path != null) "已导出至 $path" else "导出失败"
                        }
                    },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                DataActionItem(
                    icon = Icons.Filled.Download,
                    iconColor = Blue,
                    title = "导入数据",
                    description = "从备份文件恢复",
                    onClick = {
                        scope.launch {
                            val ok = viewModel.importData()
                            message = if (ok) "导入成功" else "未找到备份文件"
                        }
                    },
                )
            }

            // ── 关于 ──
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            "版本号",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingContent = {
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }

            // ── 检查更新 ──
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        message = "正在检查…"
                        message = viewModel.checkUpdate()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "检查更新",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // ── Privacy notice ──
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "所有数据均存储在本地设备（Room / DataStore）。汇率换算与 IP 属地查询会经由公开 API 获取，其中 IP 查询会将 IP 地址发送至第三方服务，结果会缓存在本地。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outlineVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeIcon(mode: ThemeMode) {
    val (icon, iconColor) = when (mode) {
        ThemeMode.SYSTEM -> Icons.Filled.PhoneAndroid to Color(0xFF9CA3AF)
        ThemeMode.LIGHT -> Icons.Filled.LightMode to Amber
        ThemeMode.DARK -> Icons.Filled.DarkMode to DarkBlue
    }
    Surface(
        shape = CircleShape,
        color = iconColor,
        modifier = Modifier.size(40.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DataActionItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = iconColor,
                modifier = Modifier.size(40.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp),
    )
}

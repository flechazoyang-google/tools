package com.example.toolbox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.toolbox.BuildConfig
import com.example.toolbox.core.components.CommonButton
import com.example.toolbox.core.components.SectionHeader
import com.example.toolbox.core.theme.ThemeMode
import kotlinx.coroutines.launch

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SectionHeader("外观")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label) },
                        leadingIcon = {
                            val icon = when (mode) {
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.SYSTEM -> Icons.Filled.PhoneAndroid
                            }
                            Icon(icon, contentDescription = null)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader("数据管理")
            Spacer(modifier = Modifier.height(12.dp))
            CommonButton(
                text = "导出数据",
                onClick = {
                    scope.launch {
                        val path = viewModel.exportData()
                        message = if (path != null) "已导出至 $path" else "导出失败"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            CommonButton(
                text = "导入数据",
                onClick = {
                    scope.launch {
                        val ok = viewModel.importData()
                        message = if (ok) "导入成功" else "未找到备份文件"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader("关于")
            Spacer(modifier = Modifier.height(12.dp))
            Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            CommonButton(
                text = "检查更新",
                onClick = {
                    scope.launch {
                        message = "正在检查…"
                        message = viewModel.checkUpdate()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "所有数据均存储在本地设备（Room / DataStore）。汇率换算与 IP 属地查询会经由公开 API 获取，其中 IP 查询会将 IP 地址发送至第三方服务，结果会缓存在本地。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

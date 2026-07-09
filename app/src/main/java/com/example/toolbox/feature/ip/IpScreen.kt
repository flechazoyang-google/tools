package com.example.toolbox.feature.ip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.components.SectionHeader
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.data.local.datastore.IpInfo

@Composable
fun IpScreen(viewModel: IpViewModel = hiltViewModel()) {
    var input by remember { mutableStateOf("") }
    val current by viewModel.current.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val cache by viewModel.cache.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "IP 属地查询",
            actions = {
                IconButton(onClick = { viewModel.detect() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新检测")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入 IP（留空检测本机）") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                IconButton(onClick = { viewModel.query(input.trim().ifBlank { null }) }) {
                    Icon(Icons.Filled.Search, contentDescription = "查询")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                error != null -> CommonCard(Modifier.fillMaxWidth()) {
                    Text(
                        error!!,
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                current != null -> IpResultCard(current!!)
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("最近查询")
            Spacer(modifier = Modifier.height(8.dp))
            if (cache.isEmpty()) {
                Text(
                    "暂无缓存记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cache.forEach { info ->
                        SmallIpCard(info) { viewModel.query(info.ip) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IpResultCard(info: IpInfo) {
    CommonCard(Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            InfoRow(Icons.Filled.Public, "IP", info.ip)
            InfoRow(Icons.Filled.LocationOn, "国家", info.country ?: "—")
            InfoRow(Icons.Filled.Map, "地区", info.region ?: "—")
            InfoRow(Icons.Filled.LocationCity, "城市", info.city ?: "—")
            InfoRow(Icons.Filled.Business, "运营商", info.org ?: "—")
            InfoRow(Icons.Filled.Public, "类型", info.version ?: "—")
        }
    }
}

@Composable
private fun SmallIpCard(info: IpInfo, onClick: () -> Unit) {
    CommonCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(info.ip, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${info.country ?: ""} ${info.region ?: ""}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

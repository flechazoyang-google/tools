package com.flechazo.toolbox.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 发现新版本时的弹窗。
 *
 * 「立即下载」直接打开 GitHub Release 里的 APK 资产直链（走系统浏览器下载，
 * 安装由系统处理）；没有 APK 资产时退化为打开 Release 页面。
 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本 v${info.latestVersion}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前版本 v${info.currentVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            if (info.apkUrl != null) {
                TextButton(onClick = { openUrl(context, info.apkUrl) }) { Text("立即下载") }
            } else if (info.releaseUrl.isNotBlank()) {
                TextButton(onClick = { openUrl(context, info.releaseUrl) }) { Text("查看详情") }
            }
        },
        dismissButton = {
            Row {
                if (info.apkUrl != null && info.releaseUrl.isNotBlank()) {
                    TextButton(onClick = { openUrl(context, info.releaseUrl) }) { Text("详情") }
                }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

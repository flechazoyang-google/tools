package com.example.toolbox.feature.password

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.example.toolbox.core.util.BiometricAuthManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toolbox.core.components.CommonButton
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.components.CommonTextField
import com.example.toolbox.core.components.SectionHeader
import com.example.toolbox.data.local.entity.PasswordEntity
import com.example.toolbox.data.repository.PasswordInput

private val Indigo = Color(0xFF6366F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(viewModel: PasswordViewModel = hiltViewModel()) {
    val masterHash by viewModel.masterHash.collectAsState()
    val needsSetup = masterHash == null
    val unlocked = viewModel.unlocked
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val authManager = remember { BiometricAuthManager(context) }

    Scaffold(
        floatingActionButton = {
            if (!needsSetup && unlocked) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新增密码")
                }
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "密码箱",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            when {
                needsSetup -> SetupMasterCard(viewModel)
                !unlocked -> UnlockCard(viewModel, authManager)
                else -> PasswordList(viewModel)
            }
        }
    }

    if (showAdd && !needsSetup && unlocked) {
        AddPasswordDialog(onDismiss = { showAdd = false }) { input ->
            viewModel.add(input)
            showAdd = false
        }
    }
}

@Composable
private fun SetupMasterCard(viewModel: PasswordViewModel) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    CommonCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Lock icon
            Surface(
                shape = CircleShape,
                color = Indigo,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "设置主密码",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "用于解锁本地密码箱。主密码仅作验证，密码数据由 Android Keystore 加密，绝不上传。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            PasswordField(value = pw, label = "主密码", onValueChange = { pw = it })
            Spacer(modifier = Modifier.height(12.dp))
            PasswordField(value = confirm, label = "确认主密码", onValueChange = { confirm = it })
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(20.dp))
            CommonButton(
                text = "确认",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        pw.length < 4 -> error = "主密码至少 4 位"
                        pw != confirm -> error = "两次输入不一致"
                        else -> viewModel.setupMaster(pw)
                    }
                },
            )
        }
    }
}

@Composable
private fun UnlockCard(viewModel: PasswordViewModel, authManager: BiometricAuthManager) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error != null) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(10f, tween(40))
            shakeOffset.animateTo(-10f, tween(40))
            shakeOffset.animateTo(7f, tween(40))
            shakeOffset.animateTo(-7f, tween(40))
            shakeOffset.animateTo(4f, tween(40))
            shakeOffset.animateTo(-4f, tween(40))
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    CommonCard(
        Modifier.fillMaxWidth().offset(x = shakeOffset.value.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Lock icon
            Surface(
                shape = CircleShape,
                color = Indigo,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "解锁密码箱",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "您的密码已加密存储在本地",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(20.dp))
            PasswordField(
                value = pw,
                label = "主密码",
                onValueChange = { pw = it },
                isError = error != null,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            CommonButton(
                text = "解锁",
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (!viewModel.unlock(pw)) error = "密码错误" },
            )
            if (authManager.isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        authManager.authenticate(
                            title = "解锁密码箱",
                            subtitle = "验证指纹以解锁",
                            onSuccess = { viewModel.unlockWithBiometric() },
                            onError = { error = it },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "使用指纹解锁",
                        color = Indigo,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordList(viewModel: PasswordViewModel) {
    val items by viewModel.items.collectAsState()
    var query by remember { mutableStateOf("") }
    val revealed = remember { mutableStateListOf<Long>() }

    val filtered = if (query.isBlank()) {
        items
    } else {
        items.filter {
            it.site.contains(query, true) || it.account.contains(query, true) || it.tag.contains(query, true)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    CommonTextField(value = query, onValueChange = { query = it }, label = "搜索", placeholder = "站点 / 账号 / 标签")
    Spacer(modifier = Modifier.height(12.dp))
    SectionHeader("已保存 ${items.size} 条")
    Spacer(modifier = Modifier.height(12.dp))

    if (filtered.isEmpty()) {
        Text(
            "暂无密码，点右下角添加",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.id }) { entity ->
                PasswordItemCard(
                    entity = entity,
                    revealed = revealed.contains(entity.id),
                    onToggleReveal = {
                        if (revealed.contains(entity.id)) revealed.remove(entity.id) else revealed.add(entity.id)
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(entity) },
                    onDelete = { viewModel.delete(entity) },
                    password = viewModel.decrypt(entity),
                )
            }
        }
    }
}

@Composable
private fun PasswordItemCard(
    entity: PasswordEntity,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    password: String,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    CommonCard(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.site.ifBlank { "未命名" }, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(entity.account, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (revealed) password else "•".repeat(minOf(password.length, 12).coerceAtLeast(6)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (entity.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "收藏",
                    tint = if (entity.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleReveal) {
                Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "显示密码")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { onDelete(); menuExpanded = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPasswordDialog(onDismiss: () -> Unit, onConfirm: (PasswordInput) -> Unit) {
    var site by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(enabled = site.isNotBlank() && password.isNotBlank(), onClick = {
                onConfirm(PasswordInput(site, account, password, note, tag, favorite))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("新增密码", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = site, onValueChange = { site = it }, label = { Text("网站 / 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { password = generatePassword() }) { Icon(Icons.Filled.Add, contentDescription = "生成") }
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                            }
                        }
                    },
                )
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("标签（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("标记为收藏", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        isError = isError,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
            }
        },
    )
}

private fun generatePassword(length: Int = 16): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#\$%&*"
    return (1..length).map { chars.random() }.joinToString("")
}

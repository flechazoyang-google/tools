package com.flechazo.toolbox.feature.password_vault

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class VaultEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val note: String,
)

enum class VaultPhase { SETUP, LOCKED, UNLOCKED }

data class VaultUiState(
    val phase: VaultPhase = VaultPhase.LOCKED,
    val entries: List<VaultEntry> = emptyList(),
    val error: String? = null,
    val query: String = "",
) {
    /** 按名称/账号/备注过滤。 */
    val filtered: List<VaultEntry>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return entries
            return entries.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.username.contains(q, ignoreCase = true) ||
                    it.note.contains(q, ignoreCase = true)
            }
        }
}

/** Vault crypto: PBKDF2-derived AES-256-GCM over a JSON blob. Layout: TBV1|salt16|iv12|ciphertext. */
object VaultCrypto {

    private const val MAGIC = "TBV1"
    private const val ITERATIONS = 150_000
    private val random = SecureRandom()

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded

    fun encrypt(entries: List<VaultEntry>, password: CharArray): ByteArray {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = SecretKeySpec(deriveKey(password, salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val json = com.google.gson.Gson().toJson(entries)
        val data = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return MAGIC.toByteArray() + salt + iv + data
    }

    fun decrypt(file: ByteArray, password: CharArray): List<VaultEntry> {
        require(file.size > 4 + 16 + 12 && file.copyOfRange(0, 4).toString(Charsets.US_ASCII) == MAGIC) { "损坏" }
        val salt = file.copyOfRange(4, 20)
        val iv = file.copyOfRange(20, 32)
        val data = file.copyOfRange(32, file.size)
        val key = SecretKeySpec(deriveKey(password, salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val json = cipher.doFinal(data).toString(Charsets.UTF_8)
        return com.google.gson.Gson().fromJson(
            json,
            Array<VaultEntry>::class.java,
        ).orEmpty().toList()
    }

    fun exists(context: Context): Boolean = context.getFileStreamPath("vault.dat").exists()

    fun readFile(context: Context): ByteArray? = try {
        context.openFileInput("vault.dat").use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    fun writeFile(context: Context, bytes: ByteArray) {
        context.openFileOutput("vault.dat", Context.MODE_PRIVATE).use { it.write(bytes) }
    }
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState(phase = VaultPhase.LOCKED))
    val state: StateFlow<VaultUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val phase = if (VaultCrypto.exists(context)) VaultPhase.LOCKED else VaultPhase.SETUP
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(phase = phase) }
        }
    }

    fun setup(master: String, confirm: String) {
        if (master.length < 6) { _state.value = _state.value.copy(error = "主密码至少 6 位"); return }
        if (master != confirm) { _state.value = _state.value.copy(error = "两次输入不一致"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                VaultCrypto.writeFile(context, VaultCrypto.encrypt(emptyList(), master.toCharArray()))
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        sessionPassword = master
                        _state.value = VaultUiState(phase = VaultPhase.UNLOCKED, entries = emptyList())
                    },
                    onFailure = {
                        _state.value = _state.value.copy(error = "创建失败：${it.message}")
                    },
                )
            }
        }
    }

    fun unlock(master: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val file = VaultCrypto.readFile(context) ?: error("数据文件不存在")
                VaultCrypto.decrypt(file, master.toCharArray())
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { entries ->
                        sessionPassword = master
                        _state.value = VaultUiState(phase = VaultPhase.UNLOCKED, entries = entries)
                    },
                    onFailure = {
                        _state.value = _state.value.copy(
                            phase = _state.value.phase,
                            error = "主密码错误",
                        )
                    },
                )
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun saveEntry(entry: VaultEntry) {
        val current = _state.value.entries.toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id }
        if (idx >= 0) current[idx] = entry else current.add(0, entry)
        persist(current)
    }

    fun deleteEntry(id: String) {
        persist(_state.value.entries.filterNot { it.id == id })
    }

    private fun persist(entries: List<VaultEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { VaultCrypto.writeFile(context, VaultCrypto.encrypt(entries, sessionPassword.toCharArray())) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(entries = entries)
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(error = "保存失败：${it.message}")
                    }
                }
        }
    }

    private var sessionPassword: String = ""

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun lock() {
        // 立即清除内存中的主密码并回到锁定态
        sessionPassword = ""
        _state.value = VaultUiState(phase = VaultPhase.LOCKED)
    }
}

@Composable
fun PasswordVaultScreen(onBack: () -> Unit, viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 退到后台自动锁定，避免进程存活期间密码一直可读
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ToolScaffold(
        title = "密码箱",
        subtitle = "PBKDF2 + AES-256-GCM 加密本地存储 · 主密码仅保留在内存",
        onBack = onBack,
        // 密码箱自己管理滚动（列表用 LazyColumn），不能再套一层 verticalScroll，
        // 否则 LazyColumn 会被赋予无限高度约束并抛 IllegalStateException
        scrollable = false,
    ) {
        when (state.phase) {
            VaultPhase.SETUP -> SetupView(viewModel)
            VaultPhase.LOCKED -> LockView(viewModel)
            VaultPhase.UNLOCKED -> UnlockedView(viewModel, state)
        }
    }
}

@Composable
private fun LockView(viewModel: VaultViewModel) {
    var master by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            Text("输入主密码解锁", style = MaterialTheme.typography.titleLarge)
            Text(
                "数据使用主密码派生密钥加密，密码错误无法恢复",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ToolSectionCard(title = "主密码") {
            ToolTextField(
                value = master,
                onValueChange = { master = it; viewModel.clearError() },
                label = { Text("主密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                FeedbackBlock(text = it, type = FeedbackType.ERROR)
            }
            Button(
                onClick = { viewModel.unlock(master) },
                enabled = master.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("解锁") }
        }
    }
}

@Composable
private fun SetupView(viewModel: VaultViewModel) {
    var master by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            Text("创建密码箱", style = MaterialTheme.typography.titleLarge)
            Text(
                "主密码用于加密全部数据，忘记后数据无法找回，请牢记",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ToolSectionCard(title = "设置主密码") {
            ToolTextField(
                value = master,
                onValueChange = { master = it; viewModel.clearError() },
                label = { Text("主密码（至少 6 位）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            ToolTextField(
                value = confirm,
                onValueChange = { confirm = it; viewModel.clearError() },
                label = { Text("确认主密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { FeedbackBlock(text = it, type = FeedbackType.ERROR) }
            Button(
                onClick = { viewModel.setup(master, confirm) },
                enabled = master.isNotBlank() && confirm.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("创建密码箱") }
        }
    }
}

@Composable
private fun UnlockedView(viewModel: VaultViewModel, state: VaultUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("已解锁", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("${state.entries.size} 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::lock) { Text("锁定") }
                Button(onClick = {
                    editing = null
                    showEditor = true
                }) { Text("添加") }
            }
        }

        if (state.entries.isNotEmpty()) {
            ToolTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("搜索名称 / 账号 / 备注") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        if (state.entries.isEmpty()) {
            ToolSectionCard(title = "还没有记录") {
                Text(
                    "点右上角「添加」创建第一条密码记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.filtered.isEmpty()) {
            FeedbackBlock(text = "没有匹配「${state.query}」的记录", type = FeedbackType.EMPTY)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.filtered, key = { it.id }) { entry ->
                    ToolSectionCard(title = entry.title) {
                        if (entry.username.isNotBlank()) {
                            Text(
                                "账号：${entry.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.note.isNotBlank()) {
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                copySensitive(context, "密码箱", entry.password)
                            }) { Text("复制密码") }
                            TextButton(onClick = {
                                editing = entry
                                showEditor = true
                            }) { Text("编辑") }
                            TextButton(onClick = { viewModel.deleteEntry(entry.id) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        EntryEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = {
                viewModel.saveEntry(it)
                showEditor = false
            },
        )
    }
}

/**
 * 复制到系统剪贴板，并在 API 33+ 标记为敏感内容，
 * 避免系统剪贴板预览与其他应用读取到密码明文。
 */
private fun copySensitive(context: android.content.Context, label: String, value: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    val clip = android.content.ClipData.newPlainText(label, value)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

@Composable
private fun EntryEditorDialog(
    initial: VaultEntry?,
    onDismiss: () -> Unit,
    onSave: (VaultEntry) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加记录" else "编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = title, onValueChange = { title = it }, label = { Text("名称") }, singleLine = true)
                ToolTextField(value = username, onValueChange = { username = it }, label = { Text("账号") }, singleLine = true)
                ToolTextField(
                    value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true,
                    visualTransformation = VisualTransformation.None,
                )
                ToolTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        VaultEntry(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            title = title.trim(),
                            username = username.trim(),
                            password = password,
                            note = note.trim(),
                        ),
                    )
                },
                enabled = title.isNotBlank() && password.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
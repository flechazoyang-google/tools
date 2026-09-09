package com.flechazo.toolbox.feature.kinship

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Chain-based kinship solver. Relations are appended as single-character tokens:
 * 父/母/兄/弟/姐/妹/子/女/夫/妻 and looked up in a curated dictionary.
 */
object KinshipDict {

    // One-step and common multi-step relations.
    private val map: Map<String, String> = buildMap {
        put("父", "爸爸"); put("母", "妈妈")
        put("兄", "哥哥"); put("弟", "弟弟"); put("姐", "姐姐"); put("妹", "妹妹")
        put("子", "儿子"); put("女", "女儿"); put("夫", "丈夫"); put("妻", "妻子")

        put("父父", "爷爷"); put("父母", "奶奶"); put("母父", "外公"); put("母母", "外婆")
        put("父兄", "伯父"); put("父弟", "叔叔"); put("父姐", "姑妈"); put("父妹", "姑妈")
        put("母兄", "舅舅"); put("母弟", "舅舅"); put("母姐", "姨妈"); put("母妹", "姨妈")
        put("兄妻", "嫂子"); put("弟妻", "弟妹"); put("姐夫", "姐夫"); put("妹夫", "妹夫")
        put("子妻", "儿媳"); put("女夫", "女婿")
        put("子子", "孙子"); put("子女", "孙女"); put("女子", "外孙"); put("女女", "外孙女")
        put("夫父", "公公"); put("夫母", "婆婆"); put("妻父", "岳父"); put("妻母", "岳母")

        put("兄子", "侄子"); put("兄女", "侄女"); put("弟子", "侄子"); put("弟女", "侄女")
        put("姐子", "外甥"); put("姐女", "外甥女"); put("妹子", "外甥"); put("妹女", "外甥女")

        put("父兄子", "堂哥/堂弟"); put("父兄女", "堂姐/堂妹")
        put("父弟子", "堂哥/堂弟"); put("父弟女", "堂姐/堂妹")
        put("父姐子", "表哥/表弟"); put("父姐女", "表姐/表妹")
        put("父妹子", "表哥/表弟"); put("父妹女", "表姐/表妹")
        put("母兄子", "表哥/表弟"); put("母兄女", "表姐/表妹")
        put("母弟子", "表哥/表弟"); put("母弟女", "表姐/表妹")
        put("母姐子", "表哥/表弟"); put("母姐女", "表姐/表妹")
        put("母妹子", "表哥/表弟"); put("母妹女", "表姐/表妹")

        put("父父父", "曾祖父"); put("父父母", "曾祖母")
        put("母母父", "曾外祖父"); put("母母母", "曾外祖母")
        put("父兄妻", "伯母"); put("父弟妻", "婶婶"); put("父姐夫", "姑父"); put("父妹夫", "姑父")
        put("母兄妻", "舅妈"); put("母弟妻", "舅妈"); put("母姐夫", "姨父"); put("母妹夫", "姨父")
        put("子子子", "曾孙"); put("子子女", "曾孙女")
        put("女子子", "曾外孙"); put("女子女", "曾外孙女")
        put("子妻父", "亲家公"); put("子妻母", "亲家母")
    }

    /** Tokens that follow another token sensibly (rough chain validity). */
    fun resolve(chain: String): String = map[chain] ?: "暂无对应称呼（支持常用三层关系）"

    fun contains(chain: String): Boolean = map.containsKey(chain)
}

/**
 * 反推「对方怎么叫我」。
 *
 * [chain] 是我到对方的关系链，[selfIsMale] 是我的性别（影响 子/女、兄/弟 等）。
 * 返回 null 表示该链条暂不支持反推。
 */
internal fun reverseRelation(chain: String, selfIsMale: Boolean): String? {
    if (chain.isEmpty()) return ""
    val child = if (selfIsMale) "子" else "女"      // 对方的视角里，我是他/她的儿子/女儿
    val younger = if (selfIsMale) "弟" else "妹"    // 对方比我年长时怎么叫我
    val elder = if (selfIsMale) "兄" else "姐"      // 对方比我年幼时怎么叫我
    val parent = if (selfIsMale) "父" else "母"     // 对方是我的晚辈时，我是他/她的父亲/母亲

    return when (chain) {
        // 一层
        "父", "母" -> child
        "兄", "姐" -> younger
        "弟", "妹" -> elder
        "子", "女" -> parent
        "夫" -> "妻"
        "妻" -> "夫"

        // 祖辈
        "父父", "父母" -> "子" + child
        "母父", "母母" -> "女" + child
        // 父母的兄弟姐妹
        "父兄" -> "弟" + child
        "父弟" -> "兄" + child
        "父姐" -> "弟" + child
        "父妹" -> "兄" + child
        "母兄" -> "妹" + child
        "母弟" -> "姐" + child
        "母姐" -> "妹" + child
        "母妹" -> "姐" + child
        // 兄弟姐妹的配偶
        "兄妻", "姐夫" -> younger
        "弟妻", "妹夫" -> elder
        // 子女的配偶
        "子妻" -> if (selfIsMale) "夫父" else "夫母"
        "女夫" -> if (selfIsMale) "妻父" else "妻母"
        // 孙辈
        "子子", "子女" -> "父父"
        "女子", "女女" -> "母父"
        // 姻亲
        "夫父", "夫母" -> "子妻"
        "妻父", "妻母" -> "女夫"
        // 侄辈
        "兄子", "兄女", "弟子", "弟女" -> "父兄"
        "姐子", "姐女", "妹子", "妹女" -> if (selfIsMale) "母兄" else "母姐"

        // 父母的兄弟姐妹的配偶
        "父兄妻" -> "弟" + child
        "父弟妻" -> "兄" + child
        "父姐夫", "父妹夫" -> "兄" + child
        "母兄妻" -> "妹" + child
        "母弟妻" -> "姐" + child
        "母姐夫", "母妹夫" -> "姐" + child

        // 曾祖辈
        "父父父", "父父母" -> "子" + "子" + child
        "母母父", "母母母" -> "女" + "女" + child
        "子子子", "子子女" -> "父父父"
        "女子子", "女子女" -> "母母父"

        else -> {
            // 表/堂兄弟姐妹：父/母 + 兄弟姐妹 + 子/女
            if (chain.length == 3 && chain[0] in "父母" && chain[1] in "兄弟姐妹" && chain[2] in "子女") {
                val myParent = chain[0].toString()
                val sibling = chain[1].toString()
                // 对方的父母是我的 父/母 的 兄弟/姐妹：兄弟 → 对方父，姐妹 → 对方母
                val theirParent = if (sibling in "兄弟") "父" else "母"
                val middle = when {
                    // 双方父母都是男性 → 堂（伯父/叔叔）
                    myParent == "父" && theirParent == "父" -> if (sibling == "兄") "弟" else "兄"
                    // 我方父、对方母 → 表（姑妈方向）
                    myParent == "父" && theirParent == "母" -> if (sibling == "姐") "弟" else "兄"
                    // 我方母、对方父 → 表（舅舅方向）
                    myParent == "母" && theirParent == "父" -> if (sibling == "兄") "妹" else "姐"
                    // 双方父母都是女性 → 表（姨妈方向）
                    else -> if (sibling == "姐") "妹" else "姐"
                }
                theirParent + middle + child
            } else {
                null
            }
        }
    }
}

data class KinshipUiState(
    val chain: String = "",
    val selfIsMale: Boolean = true,
) {
    val result: String get() = if (chain.isEmpty()) "自己" else KinshipDict.resolve(chain)
    val reverse: String?
        get() = if (chain.isEmpty()) null else reverseRelation(chain, selfIsMale)
    val reverseLabel: String
        get() = reverse?.let { KinshipDict.resolve(it) }
            ?: "暂无对应称呼（该关系暂不支持反推）"
}

@HiltViewModel
class KinshipViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(KinshipUiState())
    val state: StateFlow<KinshipUiState> = _state

    fun append(token: String) {
        val next = _state.value.chain + token
        if (next.length <= 4) _state.value = _state.value.copy(chain = next)
    }

    fun backspace() {
        _state.value = _state.value.copy(chain = _state.value.chain.dropLast(1))
    }

    /** 截断到第 index 个关系（保留前 index 个 token）。 */
    fun truncateTo(index: Int) {
        _state.value = _state.value.copy(chain = _state.value.chain.take(index))
    }

    fun setSelfIsMale(isMale: Boolean) {
        _state.value = _state.value.copy(selfIsMale = isMale)
    }

    fun reset() { _state.value = _state.value.copy(chain = "") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KinshipScreen(onBack: () -> Unit, viewModel: KinshipViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "亲戚称呼计算",
        onBack = onBack,
        subtitle = "从「自己」出发逐层选择关系，自动得出称呼",
        actions = {
            IconButton(onClick = viewModel::reset) {
                Icon(Icons.Filled.Refresh, contentDescription = "重置")
            }
        },
    ) {

        // 当前链条 + 结果
        SegmentedTabs(
            options = listOf(true, false),
            selected = state.selfIsMale,
            label = { if (it) "我是男" else "我是女" },
            onSelect = viewModel::setSelfIsMale,
        )

        ResultCard(
            label = "对方称呼",
            value = state.result,
            caption = "我 → " + state.chain.chunked(1).joinToString(" → ").ifEmpty { "自己" },
        )

        if (state.chain.isNotEmpty()) {
            ResultCard(
                label = "对方怎么叫我",
                value = state.reverseLabel,
                caption = "由当前关系链反推（依赖上方性别）",
            )
        }

        // 链条编辑：已选关系面包屑
        ToolSectionCard(title = "关系链条") {
            if (state.chain.isEmpty()) {
                Text(
                    "尚未选择任何关系，从下方选择第一层",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "我",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.chain.chunked(1).forEachIndexed { index, token ->
                        Text(" → ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AssistChip(
                            onClick = { viewModel.truncateTo(index) },
                            label = { Text(token) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::backspace, modifier = Modifier.weight(1f)) { Text("退一格") }
            }
        }

        // 候选关系：按辈分分区
        ToolSectionCard(title = "长辈") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("父", "母").forEach { token ->
                    FilledTonalButton(onClick = { viewModel.append(token) }) { Text(TokenLabels[token] ?: token) }
                }
            }
        }
        ToolSectionCard(title = "同辈") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("兄", "弟", "姐", "妹", "夫", "妻").forEach { token ->
                    FilledTonalButton(onClick = { viewModel.append(token) }) { Text(TokenLabels[token] ?: token) }
                }
            }
        }
        ToolSectionCard(title = "晚辈") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("子", "女").forEach { token ->
                    FilledTonalButton(onClick = { viewModel.append(token) }) { Text(TokenLabels[token] ?: token) }
                }
            }
        }

        Text(
            "例：父→兄→子 = 堂哥/堂弟 · 点击链条中的关系词可截断到该处",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TokenLabels = mapOf(
    "父" to "爸爸", "母" to "妈妈",
    "兄" to "哥哥", "弟" to "弟弟", "姐" to "姐姐", "妹" to "妹妹",
    "子" to "儿子", "女" to "女儿", "夫" to "丈夫", "妻" to "妻子",
)

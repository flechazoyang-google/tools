package com.flechazo.toolbox.feature.currency

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.LabeledDropdown
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

// ---- Remote ----

data class RateResponse(
    val result: String?,
    val base_code: String?,
    val conversion_rates: Map<String, Double> = emptyMap(),
)

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): RateResponse
}

// ---- Cache ----

data class RateCache(val rates: Map<String, Double> = emptyMap(), val fetchedAt: Long = 0L)

@Singleton
class CurrencyCache @Inject constructor(
    @Named("currency") private val store: DataStore<Preferences>,
    private val gson: Gson,
) {
    private val keyJson = stringPreferencesKey("rates_json")
    private val keyTime = longPreferencesKey("fetched_at")

    suspend fun load(): RateCache {
        val prefs = store.data.first()
        val json = prefs[keyJson] ?: return RateCache()
        return runCatching {
            RateCache(gson.fromJson(json, Map::class.java).let { raw ->
                @Suppress("UNCHECKED_CAST")
                (raw as? Map<String, Double>) ?: emptyMap()
            }, prefs[keyTime] ?: 0L)
        }.getOrDefault(RateCache())
    }

    suspend fun save(cache: RateCache) {
        store.edit { prefs ->
            prefs[keyJson] = gson.toJson(cache.rates)
            prefs[keyTime] = cache.fetchedAt
        }
    }
}

@Singleton
class CurrencyRepository @Inject constructor(
    private val api: ExchangeRateApi,
    private val cache: CurrencyCache,
) {
    companion object {
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L // 6h
    }

    /** 缓存足够新就直接用，否则拉取网络。 */
    suspend fun loadRates(): RateCache {
        val cached = safeLoad()
        if (cached.rates.isNotEmpty() && System.currentTimeMillis() - cached.fetchedAt < MAX_AGE_MS) {
            return cached
        }
        return fetch()
    }

    /** 强制拉取网络；失败时回退到缓存（可能为空）。 */
    suspend fun fetch(): RateCache = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api.latest("CNY")
            if (resp.result == "success" && resp.conversion_rates.isNotEmpty()) {
                val fresh = RateCache(resp.conversion_rates, System.currentTimeMillis())
                cache.save(fresh)
                fresh
            } else {
                error("汇率接口返回异常")
            }
        }.getOrElse { safeLoad() }
    }

    /** 缓存读写失败不能让界面崩溃。 */
    private suspend fun safeLoad(): RateCache = runCatching { cache.load() }.getOrDefault(RateCache())
}

// ---- ViewModel / UI ----

data class CurrencyUiState(
    val rates: Map<String, Double> = emptyMap(),
    val fetchedAt: Long = 0L,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val stale: Boolean = false,
    val amount: String = "100",
    val from: String = "CNY",
    val to: String = "USD",
) {
    fun converted(): String {
        val v = amount.toDoubleOrNull() ?: return "-"
        val f = rates[from] ?: return "-"
        val t = rates[to] ?: return "-"
        return String.format(Locale.ROOT, "%.4f", v / f * t).trimEnd('0').trimEnd('.')
    }
}

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val repository: CurrencyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CurrencyUiState())
    val state: StateFlow<CurrencyUiState> = _state

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val cache = if (force) repository.fetch() else repository.loadRates()
            _state.value = _state.value.copy(
                rates = cache.rates,
                fetchedAt = cache.fetchedAt,
                loading = false,
                offline = cache.rates.isEmpty(),
                stale = cache.rates.isNotEmpty() &&
                    System.currentTimeMillis() - cache.fetchedAt >= CurrencyRepository.MAX_AGE_MS,
            )
        }
    }

    fun onAmountChange(value: String) {
        // 只允许数字和一个小数点，避免字母进入金额框
        val sb = StringBuilder()
        var dotSeen = false
        value.forEach { c ->
            when {
                c.isDigit() -> sb.append(c)
                c == '.' && !dotSeen -> { sb.append(c); dotSeen = true }
            }
        }
        _state.value = _state.value.copy(amount = sb.toString())
    }

    fun onFromChange(value: String) { _state.value = _state.value.copy(from = value) }
    fun onToChange(value: String) { _state.value = _state.value.copy(to = value) }
}

/** Currency display names (fallback to code for unknown). */
private val CurrencyNames = mapOf(
    "CNY" to "人民币", "USD" to "美元", "EUR" to "欧元", "JPY" to "日元",
    "GBP" to "英镑", "HKD" to "港币", "KRW" to "韩元", "AUD" to "澳元",
    "CAD" to "加元", "CHF" to "瑞郎", "SGD" to "新加坡元", "THB" to "泰铢",
    "RUB" to "卢布", "INR" to "卢比", "NZD" to "新西兰元",
)

@Composable
fun CurrencyScreen(onBack: () -> Unit, viewModel: CurrencyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 有网络数据时展示全部可用货币，离线时只展示缓存中存在的
    val available = state.rates.keys.filter { it in CurrencyNames || it in POPULAR_CODES }.sorted()
    val options = (if (available.size >= 4) available else POPULAR_CODES)

    ToolScaffold(
        title = "汇率换算",
        onBack = onBack,
        subtitle = "汇率来源 open.er-api.com，6 小时自动更新",
    ) {

        if (state.rates.isEmpty()) {
            if (state.loading) {
                FeedbackBlock(text = "正在获取汇率…", type = FeedbackType.LOADING)
            } else {
                FeedbackBlock(
                    text = "暂无汇率数据，请检查网络后重试",
                    type = FeedbackType.ERROR,
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                )
            }
        } else {
            // 输入与币种选择
            ToolSectionCard {
                ToolTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("金额") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = "从",
                        options = options,
                        selected = state.from,
                        display = { "$it ${CurrencyNames[it] ?: ""}".trim() },
                        onSelect = viewModel::onFromChange,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            val f = state.from; viewModel.onFromChange(state.to); viewModel.onToChange(f)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    ) { Icon(Icons.Filled.SwapHoriz, contentDescription = "交换币种") }
                    LabeledDropdown(
                        label = "到",
                        options = options,
                        selected = state.to,
                        display = { "$it ${CurrencyNames[it] ?: ""}".trim() },
                        onSelect = viewModel::onToChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 结果
            val fromRate = state.rates[state.from]
            val toRate = state.rates[state.to]
            ResultCard(
                label = "换算结果",
                value = state.converted(),
                unit = state.to,
                caption = if (fromRate != null && toRate != null && fromRate != 0.0) {
                    "1 ${state.from} = ${String.format(Locale.ROOT, "%.4f", toRate / fromRate)} ${state.to}"
                } else null,
            )

            // 更新时间 + 手动刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        if (state.fetchedAt > 0) {
                            append("更新于 ")
                            append(
                                java.time.Instant.ofEpochMilli(state.fetchedAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)),
                            )
                        } else {
                            append("暂无汇率数据")
                        }
                        if (state.loading) append("（刷新中…）")
                        if (state.stale) append(" · 数据可能已过期")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.stale) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { viewModel.refresh(force = true) },
                    enabled = !state.loading,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新汇率")
                }
            }

            // 常见货币对
            ToolSectionCard(title = "常见货币对（每 ${state.amount.ifBlank { "1" }} ${state.from} 兑换）") {
                options.forEach { code ->
                    if (code != state.from) {
                        val fr = state.rates[state.from] ?: 1.0
                        val v = (state.amount.toDoubleOrNull() ?: 0.0) / fr * (state.rates[code] ?: 0.0)
                        KeyValueRow(
                            label = "$code ${CurrencyNames[code] ?: ""}".trimEnd(),
                            value = String.format(Locale.ROOT, "%.4f", v),
                            emphasized = code == state.to,
                            onClick = { viewModel.onToChange(code) },
                        )
                    }
                }
            }
        }
    }
}

private val POPULAR_CODES = listOf("CNY", "USD", "EUR", "JPY", "GBP", "HKD", "KRW", "AUD", "CAD", "CHF")

@Module
@InstallIn(SingletonComponent::class)
object CurrencyModule {

    @Provides
    @Singleton
    fun provideExchangeRateApi(): ExchangeRateApi {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }
}

package com.flechazo.toolbox.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知点击 → 目标工具页的一次性交接。
 *
 * 为什么不用 NavDeepLink：本应用的工具路由是统一的 `tool/{toolId}`，27 个工具共用一个
 * `composable()` 声明（见 `AppNavHost.kt`），给单个工具加参数要改所有工具的内容签名。
 * 这里用一个显式的交接位，代价是"不纯"，收益是零侵入；且它只承载导航意图，不承载数据。
 */
object DeepLink {

    private val _toolId = MutableStateFlow<String?>(null)
    val toolId: StateFlow<String?> = _toolId.asStateFlow()

    /** 待高亮的事件 id；由目标页面自行消费。 */
    private var pendingEventId: Long? = null

    fun request(toolId: String, eventId: Long?) {
        this._toolId.value = toolId
        pendingEventId = eventId
    }

    /** 读取并清空事件高亮位（保证同一条通知不会在重组后反复触发滚动）。 */
    fun consumeEventId(): Long? = pendingEventId.also { pendingEventId = null }

    fun clearTool() {
        _toolId.value = null
    }
}

package com.flechazo.toolbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flechazo.toolbox.core.navigation.DeepLink
import com.flechazo.toolbox.core.navigation.ToolboxApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeNavigationIntent(intent)
        enableEdgeToEdge()
        setContent { ToolboxApp() }
    }

    /** 通知点击时 Activity 已存在的话走这里（配合 manifest 的 singleTop），否则会新建实例。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationIntent(intent)
    }

    private fun consumeNavigationIntent(source: Intent?) {
        val toolId = source?.getStringExtra(EXTRA_TOOL_ID) ?: return
        val eventId = source.getLongExtra(EXTRA_EVENT_ID, NO_EVENT).let { if (it >= 0L) it else null }
        DeepLink.request(toolId, eventId)
    }

    companion object {
        /** 与 `ToolCatalog` 里的工具 id 对应，通知点击后直达该工具页。 */
        const val EXTRA_TOOL_ID = "nav_tool_id"
        const val EXTRA_EVENT_ID = "nav_event_id"
        private const val NO_EVENT = -1L
    }
}

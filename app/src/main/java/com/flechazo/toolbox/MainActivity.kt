package com.flechazo.toolbox

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
        enableEdgeToEdge(
            // 状态栏交给页面自己画（首页大标题区会从它下面滚过），系统别叠任何底色。
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = navigationBarStyle(),
        )
        setContent { ToolboxApp() }
    }

    /**
     * 导航栏样式。
     *
     * `enableEdgeToEdge()` 的默认实现会给导航栏盖一层**系统 scrim**（浅色下约 90% 白），
     * 那条雾面会把底部玻璃导航栏的透明感彻底压死——玻璃栏"浮"不起来。所以要显式透明。
     *
     * 唯一的例外是 API 26：那一版**没有浅色导航栏图标**（`isAppearanceLightNavigationBars`
     * 从 API 27 才生效），透明条 + 白色图标在浅色页面上等于看不见。老设备退回半透明黑底，
     * 图标始终可见——不是降级半成品，是另一种合格方案。
     */
    private fun navigationBarStyle(): SystemBarStyle =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(LEGACY_NAV_BAR_SCRIM)
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

        /** API 26 的导航栏底衬：70% 黑，保证白色系统图标在任何页面都看得清。 */
        private const val LEGACY_NAV_BAR_SCRIM = 0xB3000000.toInt()
    }
}

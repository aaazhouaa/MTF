package com.aazhou.mtforum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.nav.AppRoot
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.MtfTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    /** 点了新消息通知进来：交给 AppRoot 跳消息页，看完由用户自己返回。 */
    private var openNotice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val themeStore = (application as MtfApp).container.themeStore
        setContent {
            val scope = rememberCoroutineScope()
            val themeIndex by themeStore.themeIndex
                .collectAsStateWithLifecycle(initialValue = DefaultThemeIndex)
            // darkOverride 为 null 时跟随系统
            val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = darkOverride ?: isSystemInDarkTheme()
            val dimImagesInDark by themeStore.dimImagesInDark
                .collectAsStateWithLifecycle(initialValue = true)
            // 拉伸式 overscroll 会在到达端点后先吃掉反向位移用于收回拉伸，
            // 造成「刚到顶/底就反向滑」不动；置空工厂即彻底关闭该效果。
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MtfTheme(
                    themeIndex = themeIndex,
                    darkTheme = darkTheme,
                    dimImagesInDark = dimImagesInDark,
                ) {
                    AppRoot(
                        onToggleTheme = {
                            scope.launch { themeStore.setDarkOverride(!darkTheme) }
                        },
                        openNotice = openNotice,
                    )
                }
            }
        }
    }

    /** singleTop 下重复点通知走这里：刷新标记，让 AppRoot 再跳一次。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTICE, false) == true) openNotice = true
    }

    companion object {
        /** NoticeNotifier 点通知时带上，用于直达消息页。 */
        const val EXTRA_OPEN_NOTICE = "open_notice"
    }
}

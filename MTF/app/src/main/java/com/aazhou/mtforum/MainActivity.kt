package com.aazhou.mtforum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.nav.AppRoot
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.MtfTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeStore = (application as MtfApp).container.themeStore
        setContent {
            val scope = rememberCoroutineScope()
            val themeIndex by themeStore.themeIndex
                .collectAsStateWithLifecycle(initialValue = DefaultThemeIndex)
            // darkOverride 为 null 时跟随系统
            val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = darkOverride ?: isSystemInDarkTheme()
            // 拉伸式 overscroll 会在到达端点后先吃掉反向位移用于收回拉伸，
            // 造成「刚到顶/底就反向滑」不动；置空工厂即彻底关闭该效果。
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MtfTheme(themeIndex = themeIndex, darkTheme = darkTheme) {
                    AppRoot(onToggleTheme = {
                        scope.launch { themeStore.setDarkOverride(!darkTheme) }
                    })
                }
            }
        }
    }
}

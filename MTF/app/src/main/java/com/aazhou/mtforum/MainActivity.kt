package com.aazhou.mtforum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.nav.AppRoot
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.MtfTheme
import kotlinx.coroutines.launch

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
            MtfTheme(themeIndex = themeIndex, darkTheme = darkTheme) {
                AppRoot(onToggleTheme = {
                    scope.launch { themeStore.setDarkOverride(!darkTheme) }
                })
            }
        }
    }
}

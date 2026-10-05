package com.aazhou.mtforum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aazhou.mtforum.ui.nav.AppRoot
import com.aazhou.mtforum.ui.theme.MtfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 主题切换：null 表示跟随系统，非 null 为用户强制选择
            val systemDark = isSystemInDarkTheme()
            var darkOverride by remember { mutableStateOf<Boolean?>(null) }
            val darkTheme = darkOverride ?: systemDark
            MtfTheme(darkTheme = darkTheme) {
                AppRoot(onToggleTheme = { darkOverride = !darkTheme })
            }
        }
    }
}

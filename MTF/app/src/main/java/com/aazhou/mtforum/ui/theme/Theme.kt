package com.aazhou.mtforum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 当前是否深色模式；用户可在设置里覆盖系统深浅，故不能只看 isSystemInDarkTheme。 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** 主色容器色：把主题色向底色方向压暗/提亮得到，避免为每套配色单独定义。 */
private fun containerOf(primary: Color) = Color(
    red = primary.red * 0.22f + 0.78f,
    green = primary.green * 0.22f + 0.78f,
    blue = primary.blue * 0.22f + 0.78f,
)

@Composable
fun MtfTheme(
    themeIndex: Int = DefaultThemeIndex,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val preset = ThemePresets.getOrElse(themeIndex) { ThemePresets[DefaultThemeIndex] }
    val colors = if (darkTheme) DarkMtfColors else LightMtfColors
    val primary = if (darkTheme) preset.primaryNight else preset.primaryDay
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = containerOf(primary),
            secondary = primary,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.quoteBackground,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = containerOf(primary),
            secondary = primary,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.quoteBackground,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    }
    CompositionLocalProvider(
        LocalMtfColors provides colors,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

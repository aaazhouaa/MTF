package com.aazhou.mtforum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    secondary = BrandAccent,
    background = LightSurface,
    surface = LightSurfaceVariant,
    surfaceVariant = Color(0xFFECEFF3),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF00312C),
    primaryContainer = PrimaryContainerDark,
    secondary = BrandAccent,
    background = DarkSurface,
    surface = DarkSurfaceVariant,
)

@Composable
fun MtfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

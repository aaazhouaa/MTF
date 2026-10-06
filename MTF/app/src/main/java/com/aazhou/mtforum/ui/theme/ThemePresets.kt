package com.aazhou.mtforum.ui.theme

import androidx.compose.ui.graphics.Color

/** 主题配色预设。 */
data class ThemePreset(
    val name: String,
    val primaryDay: Color,
    val primaryNight: Color,
)

val ThemePresets = listOf(
    ThemePreset("经典科技蓝", Color(0xFF2563EB), Color(0xFF60A5FA)),
    ThemePreset("极光薄荷青", Color(0xFF0D9488), Color(0xFF2DD4BF)),
    ThemePreset("幻夜紫罗兰", Color(0xFF7C3AED), Color(0xFFA78BFA)),
    ThemePreset("活力珊瑚橙", Color(0xFFEA580C), Color(0xFFFB923C)),
    ThemePreset("极简冷石灰", Color(0xFF475569), Color(0xFF94A3B8)),
    ThemePreset("灵动玫瑰粉", Color(0xFFE11D48), Color(0xFFFB7185)),
)

/** 默认主题索引：极光薄荷青。 */
const val DefaultThemeIndex = 1

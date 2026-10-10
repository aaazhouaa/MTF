package com.aazhou.mtforum.ui.theme

import androidx.compose.ui.graphics.Color

/** 主题配色预设。 */
data class ThemePreset(
    val name: String,
    val primaryDay: Color,
    val primaryNight: Color,
)

val ThemePresets = listOf(
    ThemePreset("经典科技蓝", Color(0xFF2563EB), Color(0xFF3B82F6)),
    ThemePreset("极光薄荷青", Color(0xFF0D9488), Color(0xFF14B8A6)),
    ThemePreset("幻夜紫罗兰", Color(0xFF7C3AED), Color(0xFF8B5CF6)),
    ThemePreset("活力珊瑚橙", Color(0xFFEA580C), Color(0xFFF97316)),
    ThemePreset("极简冷石灰", Color(0xFF475569), Color(0xFF64748B)),
    ThemePreset("灵动玫瑰粉", Color(0xFFE11D48), Color(0xFFF43F5E)),
)

/** 默认主题索引：极光薄荷青。 */
const val DefaultThemeIndex = 1

package com.aazhou.mtforum.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义色板：页面底色、顶栏、分隔线、引用卡等 Material3 色槽放不下的项，
 * 由 CompositionLocal 提供，改版页面统一从这里取色。
 */
@Immutable
data class MtfColors(
    val background: Color,
    val topBar: Color,
    val surface: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val iconSecondary: Color,
    val quoteBackground: Color,
    val quoteContent: Color,
    val gold: Color,
)

val LightMtfColors = MtfColors(
    background = Color(0xFFF2F2F6),
    topBar = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    divider = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textHint = Color(0xFF94A3B8),
    iconSecondary = Color(0xFF64748B),
    quoteBackground = Color(0xFFF1F5F9),
    quoteContent = Color(0xFF475569),
    gold = Color(0xFFF59E0B),
)

val DarkMtfColors = MtfColors(
    background = Color(0xFF0F1419),
    topBar = Color(0xFF161B22),
    surface = Color(0xFF1A2027),
    divider = Color(0xFF2A323D),
    textPrimary = Color(0xFFE6EDF3),
    textSecondary = Color(0xFF9BA7B4),
    textHint = Color(0xFF6B7684),
    iconSecondary = Color(0xFF8B98A5),
    quoteBackground = Color(0xFF1E262F),
    quoteContent = Color(0xFF9BA7B4),
    gold = Color(0xFFF59E0B),
)

val LocalMtfColors = staticCompositionLocalOf { LightMtfColors }

package com.aazhou.mtforum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 深色模式下亮色文字（白正文、主题色链接/徽章）统一下调的不透明度。
 *
 * 主题色本身偏亮，直接当文字在深色底上过抢眼，与压低后的正文色也脱节。
 */
const val DarkBrightTextAlpha = 0.69f

/**
 * 文字用的正文前景色：浅色模式原样，深色模式按 [DarkBrightTextAlpha] 压低。
 *
 * 新页面优先用它取正色，别直接写 `colors.textPrimary`，否则深色模式下不会被压低。
 */
@Composable
fun bodyTextColor(): Color =
    LocalMtfColors.current.textPrimary.dimmedForDark()

/**
 * 文字/图标用的主题强调色：浅色模式原样，深色模式按 [DarkBrightTextAlpha] 压低。
 *
 * 仅用于文字与图标着色；图形用途（按钮底、光标、玻璃着色、激活态）仍取
 * `MaterialTheme.colorScheme.primary`，否则会跟着变半透明。
 */
@Composable
fun accentTextColor(): Color =
    MaterialTheme.colorScheme.primary.dimmedForDark()

@Composable
private fun Color.dimmedForDark(): Color =
    if (LocalIsDarkTheme.current) copy(alpha = DarkBrightTextAlpha) else this

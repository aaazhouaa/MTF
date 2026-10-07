package com.aazhou.mtforum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/**
 * 深色模式下亮色文字（白正文、主题色链接/徽章）统一下调的不透明度。
 *
 * 主题色本身偏亮，直接当文字在深色底上过抢眼，与压低后的正文色也脱节。
 */
const val DarkBrightTextAlpha = 0.69f

/**
 * 深色模式给内容图片压暗的膜强度：论坛配图多为浅色截图，纯亮面在深色底上很刺眼。
 * 与 [DarkBrightTextAlpha] 同属深色模式的统一适配。
 */
const val DarkImageDimAlpha = 0.32f

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

/**
 * 深色模式是否压暗站点图片（头像、正文配图、封面、全屏查看），可在设置页关闭。
 * 与深浅模式一样经主题层灌注，页面直接读本地取用。
 */
val LocalDimImagesInDark = staticCompositionLocalOf { true }

/**
 * 给站点图片压一层背景色膜：浅色模式，或用户在设置里关掉压暗时原样返回。
 *
 * 论坛里的头像与配图多为浅色截图，纯亮面在深色底上很刺眼；膜色取页面底色，
 * 高亮区被拉向底色而不是统一发灰，色相与细节保留。行内表情尺寸太小，不压。
 * 修饰符必须排在 clip 之后、border 之前：圆角内才裁得干净，描边也不会被膜盖暗。
 */
@Composable
fun Modifier.dimImageInDark(): Modifier {
    if (!LocalIsDarkTheme.current || !LocalDimImagesInDark.current) return this
    val scrim = LocalMtfColors.current.background
    return drawWithContent {
        drawContent()
        drawRect(color = scrim, alpha = DarkImageDimAlpha)
    }
}

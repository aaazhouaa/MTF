package com.aazhou.mtforum.ui.nav

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.aazhou.mtforum.ui.common.BottomBarBottomMargin
import com.aazhou.mtforum.ui.common.BottomBarHeight
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

/** 底部导航项。 */
data class GlassTab(val label: String, val icon: Painter)

/**
 * 毛玻璃底部导航栏：整条底栏是一块 vibrancy + blur 磨砂面板，选中 tab 以主色着色的
 * 图标与文字标识。
 *
 * 手势：整条底栏接管按下/滑动，手指所在 tab 立即成为选中态，抬起后按落点上报选中。
 * 落点仍是当前选中的 tab 时额外回调 [onReselect]（切到别的 tab 不回调），供页面做回顶部一类的页内操作。
 */
@Composable
fun GlassBottomBar(
    backdrop: LayerBackdrop,
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 深浅必须取 LocalIsDarkTheme：用户可在设置里覆盖系统深浅，看 isSystemInDarkTheme
    // 会在应用内切主题后仍按系统值选膜色（浅色主题下给深色底糊一层白膜，毛玻璃直接被盖掉）
    val isLightTheme = !LocalIsDarkTheme.current
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    // 毛玻璃：白色覆盖保持较低 alpha（底栏内亮度 ~253 而透出内容处 ~227，白色覆盖较薄）
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.28f) else Color(0xFF121212).copy(0.28f)

    val tabsCount = tabs.size

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomBarBottomMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        val tabWidth = constraints.maxWidth.toFloat() / tabsCount

        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        // 选中态与回调都要经 state 间接读：底栏手势的 pointerInput 只认 tabWidth/tabsCount
        // 为键，切过 tab 后不会重建，直接捕获 composable 参数会一直拿到旧值
        val selectedIndexState = rememberUpdatedState(selectedIndex)
        val onSelectState = rememberUpdatedState(onSelect)
        val onReselectState = rememberUpdatedState(onReselect)

        // 外部选中态同步进 currentIndex
        LaunchedEffect(selectedIndex) { currentIndex = selectedIndex }

        // 整栏手势：按下/滑动到任意位置，该 tab 立即成为选中态，抬起后按落点上报
        val barGesture = Modifier.pointerInput(tabWidth, tabsCount) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun posToIndex(x: Float): Int =
                    (x / tabWidth - 0.5f).roundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = posToIndex(down.position.x)
                var pointer = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer }
                    if (change == null || change.changedToUpIgnoreConsumed()) break
                    currentIndex = posToIndex(change.position.x)
                    change.consume()
                    pointer = change.id
                }
                val idx = currentIndex
                onSelectState.value(idx)
                // 落点没变：这一次点击不产生导航，只当作「重复点当前 tab」上报
                if (idx == selectedIndexState.value) onReselectState.value(idx)
            }
        }

        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(barGesture)
                .height(BottomBarHeight)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val tint = if (index == currentIndex) accentColor else contentColor
                GlassTabItem {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint)
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GlassTabItem(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

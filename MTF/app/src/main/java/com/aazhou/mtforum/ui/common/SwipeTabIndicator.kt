package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp

/**
 * 跟随左右滑动距离连续移动的 tab 下划线，供 [androidx.compose.material3.TabRow] 的
 * `indicator` 槽位使用：滑动过程中下划线跟着页面走，不等落定后才跳。
 *
 * 位置由 pager 的 `currentPage + currentPageOffsetFraction` 连续算出：翻页翻转前后该
 * 和取值相同（翻转一侧的 fraction 会变号补偿），所以拖动过程中不会出现跳变；左右两端与
 * 宽度按相邻两个 tab 插值，滚动/可滚动 tab 行里宽度不一的 tab 之间滑动也不会突跳。
 */
@Composable
fun SwipeTabIndicator(
    pagerState: PagerState,
    tabPositions: List<TabPosition>,
    color: Color,
) {
    val page = pagerState.currentPage
    val fraction = pagerState.currentPageOffsetFraction
    val current = tabPositions[page]
    val from: TabPosition
    val to: TabPosition
    val progress: Float
    if (fraction >= 0f) {
        // 正滑向右侧 tab；fraction 为 0 时退化为当前 tab 自身
        from = current
        to = tabPositions.getOrNull(page + 1) ?: current
        progress = fraction
    } else {
        // 翻过临界点或回滑：起点是左侧 tab，终点是当前 tab
        from = tabPositions.getOrNull(page - 1) ?: current
        to = current
        progress = fraction + 1f
    }
    TabRowDefaults.SecondaryIndicator(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset { IntOffset(lerp(from.left, to.left, progress).roundToPx(), 0) }
            .width(lerp(from.width, to.width, progress)),
        color = color,
    )
}

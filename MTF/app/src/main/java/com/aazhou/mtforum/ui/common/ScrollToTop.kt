package com.aazhou.mtforum.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** 滚到顶的时长。 */
private const val TOP_SCROLL_MS = 300

/**
 * 顶栏双击手势：供各页顶栏挂同一个 modifier。
 *
 * 子控件（搜索胶囊/图标按钮等）自己消费手势，故双击只会命中顶栏空白处。
 */
fun Modifier.onDoubleTapToTop(onDoubleTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }

/**
 * 双击顶栏回顶部：一次连续动画滚到顶。
 *
 * 全程都是动画，不出现 [LazyListState.scrollToItem] 那种瞬时跳转——否则第一项（如版块页公告卡）
 * 会从画面外直接拍到最终位置，观感就是消失后突然跳出来。公告因此会从顶栏下方滑入并停住。
 *
 * 距离用可见帖子项的平均高度估算，并故意多滚一屏，好把索引 0 拉进可见窗口；
 * 多滚由列表自身边界收敛，不会越过顶部。最后再按索引 0 的真实位置精确对齐。
 *
 * 也不能直接用 `animateScrollToItem(0)`：长列表上它会把整段距离拆成多段连续滚，表现为一直滚到顶部。
 */
suspend fun LazyListState.animateToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return
    val viewport = layoutInfo.viewportSize.height
    val average = layoutInfo.visibleItemsInfo
        .filter { it.index > 0 }
        .takeIf { it.isNotEmpty() }
        ?.let { items -> items.sumOf { it.size } / items.size }
        ?: 0
    if (average > 0) {
        // 多滚一屏是故意的：宁可提前到顶（列表会自己停住），也不要滚不到位
        val remaining = firstVisibleItemIndex * average + firstVisibleItemScrollOffset
        animateScrollBy(
            -(remaining + viewport).toFloat(),
            animationSpec = tween(TOP_SCROLL_MS, easing = FastOutSlowInEasing),
        )
    }
    val firstItemOffset = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset ?: 0
    if (firstItemOffset < 0) animateScrollBy(-firstItemOffset.toFloat())
    // 兜底：列表里混着远高于平均高度的卡片，估算连续偏低时对齐到顶
    if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) scrollToItem(0)
}

/** [LazyListState.animateToTop] 的 `verticalScroll` 版本；内容短，直接一次性动画到顶。 */
suspend fun ScrollState.animateToTop() {
    animateScrollTo(0)
}

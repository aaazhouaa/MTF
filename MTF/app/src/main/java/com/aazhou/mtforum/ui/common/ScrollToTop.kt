package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 顶栏双击手势：供各页顶栏挂同一个 modifier。
 *
 * 子控件（搜索胶囊/图标按钮等）自己消费手势，故双击只会命中顶栏空白处。
 */
fun Modifier.onDoubleTapToTop(onDoubleTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }

/**
 * 双击顶栏回到顶部：瞬时到位，不做动画。
 *
 * 长列表上任何按时长缩放的动画都会失真——从列表底部双击时，几百毫秒要滚完十几屏，
 * 观感是一整片卡片糊过去；而「先滚一点再瞬移」会把一次到位拆成两个动作，
 * 开头的公告卡/排行卡会作为独立事件出现，反而更显眼。故直接一帧到位，与常见应用一致。
 *
 * 已在顶部时直接返回：否则 [LazyListState.scrollToItem] 仍会触发一次强制重排，顶部可见抖动。
 */
suspend fun LazyListState.scrollToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return
    scrollToItem(0)
}

/**
 * 列表内容整体替换后回到最新：仅在视口还停在顶部附近时归位。
 *
 * 中段不打扰：数据在用户已下滑后才到达（缓存先上屏、网络随后替换）也不应该把人拽回头。
 *
 * 顶部时不能提前返回：下拉刷新正是视口贴顶时发起，而下拉想看的恰是新插在头部的那几条。
 * LazyList 会把旧首条按 key 锚定回原位，新内容被顶到视口上方，此时只有执行
 * [LazyListState.scrollToItem] 才能把锚点丢掉；提前返回就会跳过它，表现为「刷完还得手动往上滑」。
 */
suspend fun LazyListState.scrollToNewest() {
    if (firstVisibleItemIndex <= 1) scrollToItem(0)
}

/** [LazyListState.scrollToTop] 的 `verticalScroll` 版本。 */
suspend fun ScrollState.scrollToTop() {
    scrollTo(0)
}

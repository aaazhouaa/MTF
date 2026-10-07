package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 玻璃底栏本体高度（不含外边距与系统导航栏）。对照通用底栏 168px@3x ≈ 56dp。 */
val BottomBarHeight = 56.dp

/** 底栏距屏幕底部的距离（在系统导航栏避让之上再留的空白）。 */
val BottomBarBottomMargin = 21.dp

/** 顶栏内容行的上下留白：对照首页与帖子详情自绘顶栏的 10dp。 */
val TopBarVerticalPadding = 10.dp

/**
 * 顶栏统一高度（不含状态栏避让）：上下各 [TopBarVerticalPadding] + 36dp 图标行。
 *
 * 自绘顶栏（首页、帖子详情）天然就是这个高度；用 Material3 TopAppBar 的页面以它为
 * `expandedHeight`，各页顶栏高度才与首页一致（TopAppBar 默认 64dp，比首页高 8dp）。
 */
val MtfTopBarHeight: Dp = TopBarVerticalPadding * 2 + 36.dp

/**
 * 页面滚动内容底部需要避让的高度。
 *
 * 内容必须铺到屏幕底部（被底栏覆盖）玻璃底栏才能采样到内容，因此不能用容器 padding，
 * 只能用列表自身的 contentPadding 留白。
 */
@Composable
fun bottomBarContentPadding(): Dp =
    BottomBarHeight + BottomBarBottomMargin +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

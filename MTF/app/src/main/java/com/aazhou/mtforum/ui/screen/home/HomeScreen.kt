package com.aazhou.mtforum.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.RankCard
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.accountIconModel
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.TopBarVerticalPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

/**
 * 首页 tab 与站点 guide 模块 view 的对应关系：[views] 与 [labels] 按下标一一对应，
 * 改一个必须同步改另一个。
 */
object HomeTabs {
    const val NEWEST = "newthread"
    const val HOT = "hot"
    const val DIGEST = "digest"
    const val REPLY = "new"
    const val SOFA = "sofa"

    val views = listOf(NEWEST, HOT, DIGEST, REPLY, SOFA)
    val labels = listOf("最新", "热门", "精华", "回复", "抢沙发")
}

/** 首页：顶栏（头像/登录图标 + 搜索胶囊 + 主题切换 + 消息）、分类 tab 行、可左右滑动的帖子流。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onMessages: () -> Unit,
    onForumClick: (fid: String, name: String) -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onProfileClick: (uid: String) -> Unit,
    onLogin: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val container = LocalContext.current.container()
    val viewModel: HomeViewModel = appViewModel { HomeViewModel(it.repository, it.contentCache) }
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val hot by viewModel.hot.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    // 当前登录用户：已登录时顶栏左侧用头像替代「MTF」，点击进个人主页
    val me by container.currentUser.collectAsStateWithLifecycle()
    // 有没有未读新消息：消息图标上那颗主题色小圆点
    val hasUnread by container.unreadNotices.collectAsStateWithLifecycle()

    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0) { HomeTabs.views.size }
    // 每页一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = HomeTabs.views.map { rememberLazyListState() }

    // 加载/操作类提示改走全局轻提示：页面级提示卡片已由 AppToast 统一承载
    LaunchedEffect(actionMessage) {
        val message = actionMessage ?: return@LaunchedEffect
        AppToast.show(message)
        viewModel.consumeActionMessage()
    }

    Scaffold(
        containerColor = colors.background,
        // 见 UserProfileScreen：语义色取不到 contentColorFor 时会退回旧值
        contentColor = bodyTextColor(),
        topBar = {
            HomeTopBar(
                avatarUrl = me?.avatarUrl,
                onProfileClick = { me?.uid?.let(onProfileClick) },
                onLogin = onLogin,
                onSearch = onSearch,
                onToggleTheme = onToggleTheme,
                onMessages = onMessages,
                // 双击回顶部作用于当前 tab 的列表
                onDoubleTap = {
                    scope.launch { listStates[pagerState.currentPage].scrollToTop() }
                },
                hasUnreadNotices = hasUnread,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HomeTabRow(
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 两侧各少预组一页：默认会把左右邻居也建出来，等于进应用就多打两个请求
                beyondViewportPageCount = 0,
            ) { page ->
                val view = HomeTabs.views[page]
                // 进入 composition 才拉数据；load 内部对已加载过的 tab 直接返回
                LaunchedEffect(view) { viewModel.load(view) }
                val feed = feeds[view] ?: FeedState()
                FeedPage(
                    feed = feed,
                    refreshing = view in refreshing,
                    listState = listStates[page],
                    rankThreads = if (view == HomeTabs.HOT) hot?.hotThreads.orEmpty() else emptyList(),
                    onRefresh = { viewModel.refresh(view) },
                    onRetry = { viewModel.retry(view) },
                    onLoadMore = { viewModel.loadMore(view) },
                    onThreadClick = onThreadClick,
                    onUserClick = onUserClick,
                    onImageClick = openImage,
                )
            }
        }
    }

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

/** 单个 tab 的帖子流：缓存优先上屏、下拉刷新、滑到底部自动翻页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(
    feed: FeedState,
    refreshing: Boolean,
    listState: LazyListState,
    rankThreads: List<Thread>,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    when (val current = feed.state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(current.message, onRetry)
        is UiState.Success -> {
            // 滑到接近末尾时自动追加下一页
            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = listState.layoutInfo.totalItemsCount
                    total > 0 && last >= total - 3
                }
            }
            LaunchedEffect(shouldLoadMore, feed.hasNext) {
                if (shouldLoadMore && feed.hasNext) onLoadMore()
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                ) {
                    if (rankThreads.isNotEmpty()) {
                        item(key = "hot-rank") {
                            RankCard("热帖排行", rankThreads, onThreadClick)
                        }
                    }
                    items(current.data, key = { it.tid }) { thread ->
                        ThreadCard(
                            thread = thread,
                            onClick = { onThreadClick(thread.tid) },
                            onImageClick = onImageClick,
                            onUserClick = onUserClick,
                        )
                    }
                    if (feed.loadingMore) {
                        item(key = "loading-more") {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** tab 行：5 等分，选中项加粗 + 主题色，下方一根短指示条。 */
@Composable
private fun HomeTabRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalMtfColors.current
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTabs.labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) primary else colors.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(
                            if (active) primary else Color.Transparent,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

/** 顶栏：已登录显示头像（点击进个人主页）、否则应用名 + 搜索胶囊 + 主题切换 + 消息提醒；双击顶栏空白处回顶部。 */
@Composable
private fun HomeTopBar(
    avatarUrl: String?,
    onProfileClick: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onMessages: () -> Unit,
    onDoubleTap: () -> Unit,
    /** 有未读新消息：消息图标右上角亮一颗主题色小圆点。 */
    hasUnreadNotices: Boolean,
) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .statusBarsPadding()
            // 子控件（搜索胶囊/图标）自己消费手势，故双击只会命中顶栏空白处
            .onDoubleTapToTop { onDoubleTap() }
            .padding(
                start = 16.dp,
                end = 12.dp,
                top = TopBarVerticalPadding,
                bottom = TopBarVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl.isNullOrBlank()) {
            // 未登录：左侧放登录动图（与「我的」页同一枚，白底已抠透明），
            // 点击直接进登录页——此前这里显示「MTF」文字且不可点
            AsyncImage(
                model = accountIconModel(logout = false),
                contentDescription = "登录",
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onLogin),
            )
        } else {
            AsyncImage(
                model = smallImageRequest(avatarUrl),
                contentDescription = "个人主页",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground)
                    .dimImageInDark()
                    .clickable(onClick = onProfileClick),
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.quoteBackground)
                .clickable(onClick = onSearch)
                .padding(horizontal = 10.dp),
        ) {
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_search_new),
                contentDescription = null,
                tint = colors.textHint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "搜索帖子、用户...",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 与搜索胶囊等分的空白：胶囊减半后，主题/消息图标仍留在右边缘
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(
                if (LocalIsDarkTheme.current) {
                    com.aazhou.mtforum.R.drawable.ic_theme_moon
                } else {
                    com.aazhou.mtforum.R.drawable.ic_theme_sun
                },
            ),
            contentDescription = "切换主题",
            tint = colors.iconSecondary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleTheme)
                .padding(7.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMessages),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp)) {
                Icon(
                    painterResource(com.aazhou.mtforum.R.drawable.ic_mail_line),
                    contentDescription = "消息提醒",
                    tint = colors.iconSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
                if (hasUnreadNotices) {
                    // 小圆点贴在信封图标右上角（6dp，位于 36dp 居中内部，完整不被 clip 裁剪）
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

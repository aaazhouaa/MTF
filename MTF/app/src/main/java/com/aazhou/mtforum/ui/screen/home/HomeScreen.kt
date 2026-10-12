package com.aazhou.mtforum.ui.screen.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.aazhou.mtforum.ui.common.RankCollapsedLimit
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.accountIconModel
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.TopBarActionIcons
import com.aazhou.mtforum.ui.common.TopBarActionsEndPadding
import com.aazhou.mtforum.ui.common.TopBarVerticalPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.screen.login.LoginDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
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
    onSettings: () -> Unit,
    onMessages: () -> Unit,
    onForumClick: (fid: String, name: String) -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onProfileClick: (uid: String) -> Unit,
    onLogin: () -> Unit,
    /** 底栏重复点击「首页」的计数，每点一次回顶一次；0 表示未触发过。 */
    scrollToTopTick: Int,
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

    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val scope = rememberCoroutineScope()
    var showLoginDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0) { HomeTabs.views.size }
    // 每页一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = HomeTabs.views.map { rememberLazyListState() }

    // 底栏重复点击「首页」：与双击顶栏同一效果，回当前分页的顶部。
    // 已处理到第几次必须记住：切 tab 时本页组合会被销毁再按 saveState 重建，不记住的话
    // 重建后同一 tick 会再触发一次，白丢恢复出来的滚动位置
    var handledScrollTopTick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > handledScrollTopTick) {
            handledScrollTopTick = scrollToTopTick
            listStates[pagerState.currentPage].scrollToTop()
        }
    }

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
                onLogin = { showLoginDialog = true },
                onSearch = onSearch,
                onToggleTheme = onToggleTheme,
                onSettings = onSettings,
                onMessages = onMessages,
                // 双击回顶部作用于当前 tab 的列表
                onDoubleTap = {
                    scope.launch { listStates[pagerState.currentPage].scrollToTop() }
                },
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
                scrollFraction = pagerState.currentPageOffsetFraction,
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

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                container.refreshCurrentUserAsync()
                HomeTabs.views.forEach { viewModel.refresh(it) }
            },
        )
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
    // 排行折叠态只活在本页组合里：离开热门 tab 或首页即恢复默认折叠
    var rankExpanded by remember { mutableStateOf(false) }
    // 兜底：排行快照缺失时帖子流先上屏，后插入的排行卡会被 LazyList 按 key 锚定顶到视口外
    // （已锚定的首条目留在原处，卡片整个落在视口上方）。只有「这轮才到位」且用户还停在
    // 顶部时才归位；这里不能用 scrollToTop——它「已在顶部直接返回」会跳过 scrollToItem，
    // 而锚点正是靠 scrollToItem 才会被忘掉
    var rankWasMissing by remember { mutableStateOf(rankThreads.isEmpty()) }
    LaunchedEffect(rankThreads.isNotEmpty()) {
        if (rankThreads.isNotEmpty() && rankWasMissing && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
        rankWasMissing = rankThreads.isEmpty()
    }
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
                            RankCard(
                                "热帖排行",
                                rankThreads,
                                onThreadClick,
                                collapsedLimit = RankCollapsedLimit,
                                expanded = rankExpanded,
                                onToggleExpanded = { rankExpanded = !rankExpanded },
                            )
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

/**
 * tab 行：5 等分，选中项加粗 + 主题色，下方一根短指示条。
 *
 * 指示条位置由 [selectedIndex] 与 [scrollFraction] 连续算出：pager 的
 * `currentPage + currentPageOffsetFraction` 在翻页翻转前后取值相同（翻转一侧的
 * fraction 会变号补偿），所以拖动过程中指示条跟着页面走，不会等落定后才跳。
 */
@Composable
private fun HomeTabRow(selectedIndex: Int, scrollFraction: Float, onSelect: (Int) -> Unit) {
    val colors = LocalMtfColors.current
    val accent = accentTextColor()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = 8.dp),
    ) {
        val tabWidth = maxWidth / HomeTabs.labels.size
        val indicatorWidth = 18.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTabs.labels.forEachIndexed { index, label ->
                val active = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(top = 8.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) accent else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        // 指示条是覆盖层：位置只跟 pager 进度有关，不随某个 tab 的选中态跳变
        val indicatorOffset = tabWidth * (selectedIndex + scrollFraction) +
            (tabWidth - indicatorWidth) / 2
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 2.5.dp)
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(indicatorWidth)
                .height(2.dp)
                .background(accent, RoundedCornerShape(1.dp)),
        )
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
    onSettings: () -> Unit,
    onMessages: () -> Unit,
    onDoubleTap: () -> Unit,
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
                end = TopBarActionsEndPadding,
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
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.quoteBackground)
                .clickable(onClick = onSearch)
                .padding(horizontal = 12.dp),
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.textHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        TopBarActionIcons(
            onToggleTheme = onToggleTheme,
            onSettings = onSettings,
            onMessages = onMessages,
        )
    }
}

package com.aazhou.mtforum.ui.screen.threadlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.repo.ForumSort
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.RankCard
import com.aazhou.mtforum.ui.common.SwipeTabIndicator
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.launch

/** 版块页：公告卡（同首页热帖排行）+ 帖子卡片流，滑到底自动追加下一页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    fid: String,
    title: String,
    onBack: () -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onNewPost: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: ThreadListViewModel = appViewModel { ThreadListViewModel(fid, it.repository, it.contentCache) }
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val scope = rememberCoroutineScope()
    val sorts = ForumSort.entries
    val pagerState = rememberPagerState(initialPage = 0) { sorts.size }
    // 每个档位一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = sorts.map { rememberLazyListState() }

    // 加载/操作类提示改走全局轻提示：页面级提示卡片已由 AppToast 统一承载
    LaunchedEffect(actionMessage) {
        val message = actionMessage ?: return@LaunchedEffect
        AppToast.show(message)
        viewModel.consumeActionMessage()
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            Column(Modifier.background(colors.topBar)) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "返回",
                                tint = bodyTextColor(),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNewPost) {
                            Icon(
                                painterResource(R.drawable.ic_new_thread),
                                contentDescription = "发帖",
                                tint = bodyTextColor(),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.topBar,
                        titleContentColor = bodyTextColor(),
                    ),
                    modifier = Modifier.onDoubleTapToTop {
                        scope.launch { listStates[pagerState.currentPage].scrollToTop() }
                    },
                    expandedHeight = MtfTopBarHeight,
                )
                val accent = accentTextColor()
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = colors.topBar,
                    contentColor = accent,
                    indicator = { tabPositions ->
                        SwipeTabIndicator(pagerState, tabPositions, accent)
                    },
                ) {
                    sorts.forEachIndexed { index, sort ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    sort.label,
                                    fontWeight = if (pagerState.currentPage == index) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    fontSize = 15.sp,
                                    color = if (pagerState.currentPage == index) accent else colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            // 两侧各少预组一页：默认会把左右邻居也建出来，等于进页面就多打一个请求
            beyondViewportPageCount = 0,
        ) { page ->
            val sort = sorts[page]
            // 进入 composition 才拉数据；load 内部对已加载过的档位直接返回
            LaunchedEffect(sort) { viewModel.load(sort) }
            val tab = tabStates[sort] ?: ForumTabState()
            ForumThreadList(
                tab = tab,
                listState = listStates[page],
                onRetry = { viewModel.retry(sort) },
                onLoadMore = { viewModel.loadMore(sort) },
                onThreadClick = onThreadClick,
                onUserClick = onUserClick,
                onImageClick = openImage,
            )
        }
    }

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

/** 单个档位的帖子流：公告卡 + 帖子卡片流，滑到底自动追加下一页。 */
@Composable
private fun ForumThreadList(
    tab: ForumTabState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    when (val current = tab.state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(current.message, onRetry)
        is UiState.Success -> {
            val data = current.data
            if (data.threads.isEmpty()) {
                EmptyBox("本页没有主题")
            } else {
                // 滑到接近末尾时自动追加下一页
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && last >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMore, tab.hasNext) {
                    if (shouldLoadMore && tab.hasNext) onLoadMore()
                }
                LazyColumn(
                    state = listState,
                    // 发帖入口已移到顶栏，底部只留普通收尾留白
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (data.announcements.isNotEmpty()) {
                        item(key = "announcements") {
                            RankCard("公告", data.announcements, onThreadClick)
                        }
                    }
                    items(data.threads, key = { it.tid }) { thread ->
                        // 列表本身已限定在当前版块，卡片不再重复显示版块名
                        ThreadCard(
                            thread = thread,
                            onClick = { onThreadClick(thread.tid) },
                            onImageClick = onImageClick,
                            onUserClick = onUserClick,
                            showForumName = false,
                        )
                    }
                    if (tab.loadingMore) {
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

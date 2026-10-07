package com.aazhou.mtforum.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.TopBarVerticalPadding
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.launch

/** 首页：顶栏（应用名 + 搜索胶囊 + 主题切换 + 消息）、热帖排行头部、帖子卡片流。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onMessages: () -> Unit,
    onForumClick: (fid: String, name: String) -> Unit,
    onThreadClick: (tid: String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: HomeViewModel = appViewModel { HomeViewModel(it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hot by viewModel.hot.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    // 列表状态提升到页面作用域：顶栏双击回顶部时刷新/加载态也要能拿到它
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch { listState.animateScrollToItem(0) }
    }

    Scaffold(
        containerColor = colors.background,
        // 见 UserProfileScreen：语义色取不到 contentColorFor 时会退回旧值
        contentColor = bodyTextColor(),
        topBar = {
            HomeTopBar(
                onSearch = onSearch,
                onToggleTheme = onToggleTheme,
                onMessages = onMessages,
                onDoubleTap = scrollToTop,
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                // 滑到接近末尾时自动追加下一页
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && last >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMore, hasNext) {
                    if (shouldLoadMore && hasNext) viewModel.loadMore()
                }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                    ) {
                        hot?.takeIf { it.hotThreads.isNotEmpty() }?.let { data ->
                            item(key = "hot-rank") {
                                HotRankCard(data.hotThreads, onThreadClick)
                            }
                        }
                        items(current.data, key = { it.tid }) { thread ->
                            ThreadCard(
                                thread = thread,
                                onClick = { onThreadClick(thread.tid) },
                                onImageClick = openImage,
                            )
                        }
                        if (loadingMore) {
                            item(key = "loading-more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

/** 顶栏：「MTF」应用名 + 搜索胶囊 + 主题切换 + 消息提醒；双击顶栏空白处回顶部。 */
@Composable
private fun HomeTopBar(
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
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
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
            .padding(
                start = 16.dp,
                end = 12.dp,
                top = TopBarVerticalPadding,
                bottom = TopBarVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "MTF",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
        )
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
        Icon(
            painterResource(com.aazhou.mtforum.R.drawable.ic_mail_line),
            contentDescription = "消息提醒",
            tint = colors.iconSecondary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMessages)
                .padding(7.dp),
        )
    }
}

/** 热帖排行卡片：15dp 圆角，彩色序号 + 单行标题 + 内部分隔线。 */
@Composable
private fun HotRankCard(threads: List<Thread>, onThreadClick: (String) -> Unit) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            "热帖排行",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        threads.forEachIndexed { index, thread ->
            val rank = index + 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onThreadClick(thread.tid) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rankColor(rank)),
                ) {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    thread.subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 序号底色：1 红 / 2 黄 / 3 绿 / 其余灰。 */
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFF705E)
    2 -> Color(0xFFFFB900)
    3 -> Color(0xFFA8C500)
    else -> Color(0xFFCCCCCC)
}


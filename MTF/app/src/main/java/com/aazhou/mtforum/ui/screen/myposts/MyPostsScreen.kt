package com.aazhou.mtforum.ui.screen.myposts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.MyThreadPage
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.SwipeTabIndicator
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.reportViewportAtTop
import com.aazhou.mtforum.ui.common.scrollToNewest
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 单个 tab（主题/回复）的列表状态。 */
data class MyThreadsTabState(
    val state: UiState<List<Thread>> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
    /** 第 1 页数据替换上屏时、替换前视口贴顶才递增；界面据此把视口拉回最新。 */
    val resetScrollTick: Int = 0,
)

class MyPostsViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    /** 各 tab 的列表状态，key 为类型；未加载过的 tab 不在表内。 */
    private val _tabStates = MutableStateFlow<Map<String, MyThreadsTabState>>(emptyMap())
    val tabStates: StateFlow<Map<String, MyThreadsTabState>> = _tabStates.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /**
     * 各 tab 的视口是否贴顶（由界面持续回报）。
     *
     * 归位判据必须在数据替换**之前**取：替换后 LazyList 已按 item key 把旧首条锚回原处，
     * 那时读到的 index 是「头部新插了几条」而不是用户位置。
     */
    private val viewportAtTop = mutableMapOf<String, Boolean>()

    /** 界面回报某 tab 的视口位置；只在贴顶状态变化时被调用。 */
    fun noteViewportAtTop(type: String, atTop: Boolean) {
        viewportAtTop[type] = atTop
    }

    /** 下拉刷新中的 tab：与 [tabStates] 独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    /** 各 tab 追加下一页的 Job：刷新时取消，避免旧页又拼回新拉的第 1 页后面。 */
    private val loadMoreJobs = mutableMapOf<String, Job>()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /**
     * 首次进入某个 tab：有缓存就先摆出来（不走全屏 Loading），网络回来再原地替换。
     *
     * 已加载过的 tab 直接返回：切 tab 不重新请求，滚动位置与已翻页的数据都留着。
     */
    fun load(type: String) {
        if (_tabStates.value.containsKey(type)) return
        update(type) { MyThreadsTabState() }
        viewModelScope.launch {
            val uid = sessionStore.uid.first()
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(
                CacheKeys.myThreads(uid, type),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<MyThreadPage>(it)
            }
            if (cached != null) {
                update(type) {
                    it.copy(
                        state = UiState.Success(cached.threads),
                        page = cached.page,
                        hasNext = cached.hasNext,
                    )
                }
            }
            reload(type, uid)
        }
    }

    /** 加载失败后的重试：不受「已加载过」限制，直接重拉第一页。 */
    fun retry(type: String) {
        viewModelScope.launch { reload(type, sessionStore.uid.first()) }
    }

    /**
     * 下拉刷新：回到第 1 页，已追加的页一并作废。
     *
     * 不复用 [load]：那会先把磁盘缓存摆回来再请求，刷新的意义就没了。
     */
    fun refresh(type: String) {
        if (type in _refreshing.value) return
        // 翻页 Job 取消后不会自己复位 loadingMore，这里补上，否则该 tab 再也翻不了页
        loadMoreJobs.remove(type)?.cancel()
        if (_tabStates.value.containsKey(type)) update(type) { it.copy(loadingMore = false) }
        _refreshing.value = _refreshing.value + type
        viewModelScope.launch {
            reload(type, sessionStore.uid.first())
            _refreshing.value = _refreshing.value - type
        }
    }

    private suspend fun reload(type: String, uid: String) {
        runCatching { repository.myThreads(type = type, page = 1) }
            .onSuccess { data ->
                update(type) {
                    it.copy(
                        state = UiState.Success(data.threads),
                        page = 1,
                        hasNext = data.hasNext,
                        // 替换前贴顶才置位：中段用户不打扰，且由数据层在替换时取真值
                        resetScrollTick = it.resetScrollTick +
                            if (viewportAtTop[type] == true) 1 else 0,
                    )
                }
                saveToCache(uid, type)
            }
            .onFailure { e ->
                // 有缓存就留着旧内容，只提示这次没拿到新的
                if (_tabStates.value[type]?.state is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                } else {
                    update(type) { it.copy(state = UiState.Error(e.message ?: "加载失败")) }
                }
            }
    }

    /** 滑到底部时追加下一页。 */
    fun loadMore(type: String) {
        val current = _tabStates.value[type] ?: return
        if (current.loadingMore || !current.hasNext) return
        if (current.state !is UiState.Success) return
        update(type) { it.copy(loadingMore = true) }
        loadMoreJobs[type] = viewModelScope.launch {
            val next = current.page + 1
            runCatching { repository.myThreads(type = type, page = next) }
                .onSuccess { data ->
                    // 合并时现读，避免把刚替换上的新列表又拼回旧数据后面
                    val existing = (_tabStates.value[type]?.state as? UiState.Success)?.data.orEmpty()
                    val known = existing.mapTo(HashSet()) { it.tid }
                    val fresh = data.threads.filter { known.add(it.tid) }
                    update(type) {
                        it.copy(
                            state = UiState.Success(existing + fresh),
                            page = next,
                            hasNext = data.hasNext && fresh.isNotEmpty(),
                        )
                    }
                    saveToCache(sessionStore.uid.first(), type)
                }
                .onFailure { update(type) { it.copy(hasNext = false) } }
            update(type) { it.copy(loadingMore = false) }
        }
    }

    private fun update(type: String, transform: (MyThreadsTabState) -> MyThreadsTabState) {
        val current = _tabStates.value[type] ?: MyThreadsTabState()
        _tabStates.value = _tabStates.value + (type to transform(current))
    }

    /** 列表缓存 = 当前已加载的帖子流（含翻页追加的部分）与其页码。 */
    private suspend fun saveToCache(uid: String, type: String) {
        val tab = _tabStates.value[type] ?: return
        val threads = (tab.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.myThreads(uid, type),
            ttlMillis = CacheKeys.LIST_TTL,
            value = MyThreadPage(threads = threads, page = tab.page, hasNext = tab.hasNext),
            encode = { cacheJson.encodeToString(MyThreadPage.serializer(), it) },
        )
    }
}

private val MY_POSTS_TABS = listOf("主题" to "thread", "回复" to "reply")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: MyPostsViewModel = appViewModel {
        MyPostsViewModel(it.repository, it.contentCache, it.sessionStore)
    }
    val tabs = MY_POSTS_TABS
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    // 每页一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = tabs.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()

    // 加载/操作类提示改走全局轻提示
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "返回",
                                tint = bodyTextColor(),
                            )
                        }
                    },
                    title = {
                        Text(
                            "我的帖子",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = bodyTextColor(),
                        )
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
                    tabs.forEachIndexed { index, (title, _) ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
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
            val type = tabs[page].second
            // 进入 composition 才拉数据；load 内部对已加载过的 tab 直接返回
            LaunchedEffect(type) { viewModel.load(type) }
            val tab = tabStates[type] ?: MyThreadsTabState()
            MyThreadsTab(
                tab = tab,
                refreshing = type in refreshing,
                listState = listStates[page],
                emptyText = "暂无${tabs[page].first}数据",
                onViewportAtTopChange = { viewModel.noteViewportAtTop(type, it) },
                onRetry = { viewModel.retry(type) },
                onRefresh = { viewModel.refresh(type) },
                onLoadMore = { viewModel.loadMore(type) },
                onThreadClick = onThreadClick,
                onUserClick = onUserClick,
            )
        }
    }
}

/** 单个 tab 的帖子流：缓存优先上屏、滑到底部自动翻页。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MyThreadsTab(
    tab: MyThreadsTabState,
    refreshing: Boolean,
    listState: LazyListState,
    emptyText: String,
    onViewportAtTopChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onThreadClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
) {
    // 视口位置持续回报给数据层：归位判据只能在数据替换前取，替换后 LazyList 已按 key
    // 把旧首条锚回原处，那时读到的 index 是「头部新插了几条」而非用户位置
    listState.reportViewportAtTop(onViewportAtTopChange)
    // 第 1 页数据替换后归位：要不要跳已由数据层在替换前定好，这里无条件执行。
    // 记上次处理到的 tick（初值取当前值）：返回或重建时初值已是最新值，不会误跳
    var lastResetTick by remember { mutableIntStateOf(tab.resetScrollTick) }
    LaunchedEffect(tab.resetScrollTick) {
        if (tab.resetScrollTick > lastResetTick) {
            lastResetTick = tab.resetScrollTick
            listState.scrollToNewest()
        }
    }
    when (val current = tab.state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(current.message, onRetry)
        is UiState.Success -> {
            val threads = current.data
            if (threads.isEmpty()) {
                EmptyBox(emptyText)
            } else {
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
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 2.5.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(threads, key = { it.tid }) { thread ->
                            ThreadCard(
                                thread = thread,
                                onClick = { onThreadClick(thread.tid) },
                                onImageClick = { _, _ -> },
                                onUserClick = onUserClick,
                                showForumName = true,
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
}

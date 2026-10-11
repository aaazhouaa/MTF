package com.aazhou.mtforum.ui.screen.myfavorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.FavoriteItem
import com.aazhou.mtforum.data.model.FavoritePage
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.SwipeTabIndicator
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
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

/** 单个 tab（全部/帖子/版块）的收藏列表状态。 */
data class FavoritesTabState(
    val state: UiState<List<FavoriteItem>> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
)

class MyFavoritesViewModel(
    private val repository: ForumRepository,
    private val actionStore: ActionStore,
    private val contentCache: ContentCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    /** 各 tab 的列表状态，key 为收藏类型；未加载过的 tab 不在表内。 */
    private val _tabStates = MutableStateFlow<Map<String, FavoritesTabState>>(emptyMap())
    val tabStates: StateFlow<Map<String, FavoritesTabState>> = _tabStates.asStateFlow()

    /** 正在取消收藏的条目 key（favid，取不到时退到 id）：行内转圈并防连点。 */
    private val _removing = MutableStateFlow("")
    val removing: StateFlow<String> = _removing.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
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
        update(type) { FavoritesTabState() }
        viewModelScope.launch {
            val uid = sessionStore.uid.first()
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(
                CacheKeys.myFavorites(uid, type),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<FavoritePage>(it)
            }
            if (cached != null) {
                update(type) {
                    it.copy(
                        state = UiState.Success(cached.items),
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
        runCatching { repository.myFavorites(type = type, page = 1) }
            .onSuccess { data ->
                update(type) {
                    it.copy(state = UiState.Success(data.items), page = 1, hasNext = data.hasNext)
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
            runCatching { repository.myFavorites(type = type, page = next) }
                .onSuccess { data ->
                    // 合并时现读，避免把刚替换上的新列表又拼回旧数据后面
                    val existing = (_tabStates.value[type]?.state as? UiState.Success)?.data.orEmpty()
                    val known = existing.mapTo(HashSet()) { favoriteKey(it) }
                    val fresh = data.items.filter { known.add(favoriteKey(it)) }
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

    /** 取消收藏：先摘掉各 tab 里的同一条目（「全部」与「帖子」可能同时有它），失败再放回去。 */
    fun removeFavorite(item: FavoriteItem) {
        val key = item.favid.ifEmpty { item.id }
        if (_removing.value.isNotEmpty() || key.isEmpty()) return
        val affected = _tabStates.value.keys.filter { type -> listOf(type).any { sameFavorite(it, item) } }
        if (affected.isEmpty()) return
        val snapshot = affected.associateWith { listOf(it) }
        _removing.value = key
        affected.forEach { type ->
            update(type) {
                it.copy(
                    state = UiState.Success(snapshot[type].orEmpty().filterNot { fav -> sameFavorite(fav, item) }),
                )
            }
        }
        viewModelScope.launch {
            val ok = runCatching { repository.deleteFavorite(item.favid) }
                .getOrElse { false }
            if (ok) {
                AppToast.show("已取消收藏")
                // 详情页的收藏态读本地记录，不同步会与「已取消」矛盾
                if (item.type == "thread" && item.id.isNotBlank()) {
                    actionStore.setFavorited(item.id, false)
                }
                val uid = sessionStore.uid.first()
                affected.forEach { type -> saveToCache(uid, type) }
            } else {
                AppToast.show("取消收藏失败")
                snapshot.forEach { (type, list) ->
                    update(type) { it.copy(state = UiState.Success(list)) }
                }
            }
            _removing.value = ""
        }
    }

    private fun listOf(type: String): List<FavoriteItem> =
        (_tabStates.value[type]?.state as? UiState.Success)?.data ?: emptyList()

    private fun favoriteKey(item: FavoriteItem): String = item.favid.ifEmpty { item.id }

    private fun sameFavorite(a: FavoriteItem, b: FavoriteItem): Boolean =
        (a.favid.isNotEmpty() && a.favid == b.favid) || (a.id.isNotEmpty() && a.id == b.id)

    private fun update(type: String, transform: (FavoritesTabState) -> FavoritesTabState) {
        val current = _tabStates.value[type] ?: FavoritesTabState()
        _tabStates.value = _tabStates.value + (type to transform(current))
    }

    /** 列表缓存 = 当前已加载的收藏流（含翻页追加的部分）与其页码。 */
    private suspend fun saveToCache(uid: String, type: String) {
        val tab = _tabStates.value[type] ?: return
        val items = (tab.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.myFavorites(uid, type),
            ttlMillis = CacheKeys.LIST_TTL,
            value = FavoritePage(items = items, page = tab.page, hasNext = tab.hasNext),
            encode = { cacheJson.encodeToString(FavoritePage.serializer(), it) },
        )
    }
}

private val MY_FAVORITES_TABS = listOf("全部" to "all", "帖子" to "thread", "版块" to "forum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFavoritesScreen(
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
    onForumClick: (String, String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: MyFavoritesViewModel = appViewModel {
        MyFavoritesViewModel(it.repository, it.actionStore, it.contentCache, it.sessionStore)
    }
    val tabs = MY_FAVORITES_TABS
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val removingKey by viewModel.removing.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    // 每页一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = tabs.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()

    // 取消收藏前弹窗确认，避免误触
    var pendingRemove by remember { mutableStateOf<FavoriteItem?>(null) }

    // 加载类提示改走全局轻提示
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
                            "我的收藏",
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
            val tab = tabStates[type] ?: FavoritesTabState()
            FavoritesTab(
                tab = tab,
                refreshing = type in refreshing,
                listState = listStates[page],
                removingKey = removingKey,
                onRetry = { viewModel.retry(type) },
                onRefresh = { viewModel.refresh(type) },
                onLoadMore = { viewModel.loadMore(type) },
                onClick = { fav ->
                    if (fav.type == "forum") {
                        onForumClick(fav.id, fav.title)
                    } else {
                        onThreadClick(fav.id)
                    }
                },
                onDelete = { fav -> pendingRemove = fav },
            )
        }
    }

    pendingRemove?.let { target ->
        MtfConfirmDialog(
            title = "取消收藏",
            message = "确定不再收藏「${target.title}」吗？",
            confirmText = "取消收藏",
            onDismissRequest = { pendingRemove = null },
            onConfirm = {
                viewModel.removeFavorite(target)
                pendingRemove = null
            },
        )
    }
}

/** 单个 tab 的收藏流：缓存优先上屏、滑到底部自动翻页。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FavoritesTab(
    tab: FavoritesTabState,
    refreshing: Boolean,
    listState: LazyListState,
    removingKey: String,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onClick: (FavoriteItem) -> Unit,
    onDelete: (FavoriteItem) -> Unit,
) {
    when (val current = tab.state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(current.message, onRetry)
        is UiState.Success -> {
            val items = current.data
            if (items.isEmpty()) {
                EmptyBox("暂无收藏内容")
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { if (it.favid.isNotEmpty()) it.favid else it.id }) { fav ->
                            FavoriteCard(
                                item = fav,
                                removing = removingKey == fav.favid.ifEmpty { fav.id },
                                onClick = { onClick(fav) },
                                onDelete = { onDelete(fav) },
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

@Composable
private fun FavoriteCard(
    item: FavoriteItem,
    removing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isDark) {
                    Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (item.type == "forum") colors.quoteBackground else MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(
                        if (item.type == "forum") R.drawable.ic_home else R.drawable.ic_favorite_outline,
                    ),
                    contentDescription = null,
                    tint = if (item.type == "forum") colors.iconSecondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    color = bodyTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!item.dateText.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.dateText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textHint,
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = !removing) {
                if (removing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.textHint,
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = "取消收藏",
                        tint = colors.textHint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

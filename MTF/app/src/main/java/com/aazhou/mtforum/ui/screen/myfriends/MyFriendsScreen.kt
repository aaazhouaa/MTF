package com.aazhou.mtforum.ui.screen.myfriends

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.FriendRequestItem
import com.aazhou.mtforum.data.model.FriendRequestPage
import com.aazhou.mtforum.data.model.FriendUserItem
import com.aazhou.mtforum.data.model.FriendUserPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.FriendsRepo
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.SwipeTabIndicator
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.scrollToNewest
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 单个 tab（好友/来访/看过/黑名单）的用户列表状态。 */
data class FriendsTabState(
    val state: UiState<List<FriendUserItem>> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
    /** 第 1 页数据替换上屏的次数，供界面在顶部时把视口拉回最新。 */
    val resetScrollTick: Int = 0,
)

class MyFriendsViewModel(
    private val repository: ForumRepository,
    private val friendsRepo: FriendsRepo,
    private val contentCache: ContentCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    /** 各 tab 的列表状态，key 为站点 view；未加载过的 tab 不在表内。 */
    private val _tabStates = MutableStateFlow<Map<String, FriendsTabState>>(emptyMap())
    val tabStates: StateFlow<Map<String, FriendsTabState>> = _tabStates.asStateFlow()

    /** 正在删除的好友 uid：行内转圈并防连点。 */
    private val _friendPending = MutableStateFlow("")
    val friendPending: StateFlow<String> = _friendPending.asStateFlow()

    /** 好友请求列表：站点把它放在 spacecp 下，不是 do=friend 的 view，单独一份状态。 */
    private val _requestState = MutableStateFlow<UiState<List<FriendRequestItem>>>(UiState.Loading)
    val requestState: StateFlow<UiState<List<FriendRequestItem>>> = _requestState.asStateFlow()

    private val _requestHasNext = MutableStateFlow(false)
    val requestHasNext: StateFlow<Boolean> = _requestHasNext.asStateFlow()

    private val _requestLoadingMore = MutableStateFlow(false)
    val requestLoadingMore: StateFlow<Boolean> = _requestLoadingMore.asStateFlow()

    /** 好友请求的第 1 页数据替换次数：与各 tab 独立，供界面在顶部时把视口拉回最新。 */
    private val _requestResetTick = MutableStateFlow(0)
    val requestResetTick: StateFlow<Int> = _requestResetTick.asStateFlow()

    /** 正在同意/忽略的请求 uid。 */
    private val _requestPending = MutableStateFlow("")
    val requestPending: StateFlow<String> = _requestPending.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /** 下拉刷新中的目标（tab view 或好友请求）：与列表状态独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    /** 各 tab 追加下一页的 Job：刷新时取消，避免旧页又拼回新拉的第 1 页后面。 */
    private val loadMoreJobs = mutableMapOf<String, Job>()

    /** 好友请求追加下一页的 Job，同上。 */
    private var loadMoreRequestsJob: Job? = null

    private val cacheJson = Json { ignoreUnknownKeys = true }

    private var requestFormhash: String? = null
    private var requestPage = 1
    private var requestLoaded = false

    /**
     * 首次进入某个 tab：有缓存就先摆出来（不走全屏 Loading），网络回来再原地替换。
     *
     * 已加载过的 tab 直接返回：切 tab 不重新请求，滚动位置与已翻页的数据都留着。
     */
    fun load(view: String) {
        if (_tabStates.value.containsKey(view)) return
        update(view) { FriendsTabState() }
        viewModelScope.launch {
            val uid = sessionStore.uid.first()
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(
                CacheKeys.myFriends(uid, view),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<FriendUserPage>(it)
            }
            if (cached != null) {
                update(view) {
                    it.copy(
                        state = UiState.Success(cached.users),
                        page = cached.page,
                        hasNext = cached.hasNext,
                    )
                }
            }
            reload(view, uid)
        }
    }

    /** 加载失败后的重试：不受「已加载过」限制，直接重拉第一页。 */
    fun retry(view: String) {
        viewModelScope.launch { reload(view, sessionStore.uid.first()) }
    }

    /**
     * 下拉刷新：好友请求走 [reloadRequests]，其余 tab 走 [reload]；都回到第 1 页，已追加的页一并作废。
     *
     * 不复用 [load] / [loadRequests]：那会先把磁盘缓存摆回来再请求，刷新的意义就没了。
     */
    fun refresh(view: String) {
        if (view in _refreshing.value) return
        // 翻页 Job 取消后不会自己复位 loadingMore，这里补上，否则再也翻不了页
        loadMoreJobs.remove(view)?.cancel()
        loadMoreRequestsJob?.cancel()
        if (_tabStates.value.containsKey(view)) update(view) { it.copy(loadingMore = false) }
        _requestLoadingMore.value = false
        _refreshing.value = _refreshing.value + view
        viewModelScope.launch {
            if (view == VIEW_REQUEST) {
                reloadRequests(sessionStore.uid.first())
            } else {
                reload(view, sessionStore.uid.first())
            }
            _refreshing.value = _refreshing.value - view
        }
    }

    private suspend fun reload(view: String, uid: String) {
        runCatching { repository.myFriends(view = view, page = 1) }
            .onSuccess { data ->
                update(view) {
                    it.copy(
                        state = UiState.Success(data.users),
                        page = 1,
                        hasNext = data.hasNext,
                        resetScrollTick = it.resetScrollTick + 1,
                    )
                }
                saveToCache(uid, view)
            }
            .onFailure { e ->
                // 有缓存就留着旧内容，只提示这次没拿到新的
                if (_tabStates.value[view]?.state is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                } else {
                    update(view) { it.copy(state = UiState.Error(e.message ?: "加载失败")) }
                }
            }
    }

    /** 滑到底部时追加下一页。 */
    fun loadMore(view: String) {
        val current = _tabStates.value[view] ?: return
        if (current.loadingMore || !current.hasNext) return
        if (current.state !is UiState.Success) return
        update(view) { it.copy(loadingMore = true) }
        loadMoreJobs[view] = viewModelScope.launch {
            val next = current.page + 1
            runCatching { repository.myFriends(view = view, page = next) }
                .onSuccess { data ->
                    // 合并时现读，避免把刚替换上的新列表又拼回旧数据后面
                    val existing = (_tabStates.value[view]?.state as? UiState.Success)?.data.orEmpty()
                    val known = existing.mapTo(HashSet()) { it.uid }
                    val fresh = data.users.filter { known.add(it.uid) }
                    update(view) {
                        it.copy(
                            state = UiState.Success(existing + fresh),
                            page = next,
                            hasNext = data.hasNext && fresh.isNotEmpty(),
                        )
                    }
                    saveToCache(sessionStore.uid.first(), view)
                }
                .onFailure { update(view) { it.copy(hasNext = false) } }
            update(view) { it.copy(loadingMore = false) }
        }
    }

    /** 好友请求首屏：与好友列表同一套「缓存先上屏、网络静默替换」。 */
    fun loadRequests() {
        if (requestLoaded) return
        requestLoaded = true
        viewModelScope.launch {
            val uid = sessionStore.uid.first()
            val cached = contentCache.read(
                CacheKeys.friendRequests(uid),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<FriendRequestPage>(it)
            }
            if (cached != null) {
                requestPage = cached.page
                _requestHasNext.value = cached.hasNext
                _requestState.value = UiState.Success(cached.requests)
            }
            reloadRequests(uid)
        }
    }

    /** 好友请求加载失败后的重试。 */
    fun retryRequests() {
        viewModelScope.launch { reloadRequests(sessionStore.uid.first()) }
    }

    private suspend fun reloadRequests(uid: String) {
        runCatching { friendsRepo.friendRequests(1) }
            .onSuccess { pageData ->
                requestFormhash = pageData.formhash
                requestPage = 1
                _requestHasNext.value = pageData.hasNext
                _requestState.value = UiState.Success(pageData.requests)
                _requestResetTick.value = _requestResetTick.value + 1
                saveRequestsToCache(uid)
            }
            .onFailure { e ->
                if (_requestState.value is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                } else {
                    _requestState.value = UiState.Error(e.message ?: "加载失败")
                }
            }
    }

    fun loadMoreRequests() {
        if (_requestLoadingMore.value || !_requestHasNext.value) return
        if (_requestState.value !is UiState.Success) return
        _requestLoadingMore.value = true
        loadMoreRequestsJob = viewModelScope.launch {
            val next = requestPage + 1
            runCatching { friendsRepo.friendRequests(next) }
                .onSuccess { pageData ->
                    val current = (_requestState.value as? UiState.Success)?.data.orEmpty()
                    val known = current.mapTo(HashSet()) { it.uid }
                    val fresh = pageData.requests.filter { known.add(it.uid) }
                    _requestState.value = UiState.Success(current + fresh)
                    requestPage = next
                    _requestHasNext.value = pageData.hasNext && fresh.isNotEmpty()
                    saveRequestsToCache(sessionStore.uid.first())
                }
                .onFailure { _requestHasNext.value = false }
            _requestLoadingMore.value = false
        }
    }

    fun approveRequest(item: FriendRequestItem) {
        operateRequest(item, "已同意") { friendsRepo.approveFriendRequest(item.uid, requestFormhash.orEmpty()) }
    }

    fun ignoreRequest(item: FriendRequestItem) {
        operateRequest(item, "已忽略") { friendsRepo.ignoreFriendRequest(item.uid, requestFormhash.orEmpty()) }
    }

    private fun operateRequest(item: FriendRequestItem, successText: String, op: suspend () -> Result<String>) {
        if (_requestPending.value.isNotEmpty()) return
        _requestPending.value = item.uid
        viewModelScope.launch {
            val result = runCatching { op() }.getOrElse { Result.failure(it) }
            result
                .onSuccess {
                    AppToast.show(it.ifBlank { successText })
                    val current = (_requestState.value as? UiState.Success)?.data.orEmpty()
                    _requestState.value = UiState.Success(current.filterNot { it.uid == item.uid })
                    saveRequestsToCache(sessionStore.uid.first())
                }
                .onFailure { e -> AppToast.show(e.message ?: "操作失败") }
            _requestPending.value = ""
        }
    }

    /** 删除好友：站点与忽略好友请求是同一个 op=ignore。 */
    fun removeFriend(user: FriendUserItem) {
        if (_friendPending.value.isNotEmpty()) return
        _friendPending.value = user.uid
        viewModelScope.launch {
            val result = runCatching { friendsRepo.removeFriend(user.uid, "") }
                .getOrElse { Result.failure(it) }
            result
                .onSuccess {
                    AppToast.show(it)
                    dropUserEverywhere(user.uid)
                }
                .onFailure { e -> AppToast.show(e.message ?: "删除失败") }
            _friendPending.value = ""
        }
    }

    /** 移出黑名单：成功后把该行从各 tab 里摘掉。 */
    fun unblacklistUser(user: FriendUserItem) {
        if (_friendPending.value.isNotEmpty()) return
        _friendPending.value = user.uid
        viewModelScope.launch {
            val result = runCatching { repository.removeUserBlacklist(user.uid) }
                .getOrElse { Result.failure(it) }
            result
                .onSuccess {
                    AppToast.show(it)
                    dropUserEverywhere(user.uid)
                }
                .onFailure { e -> AppToast.show(e.message ?: "移出黑名单失败") }
            _friendPending.value = ""
        }
    }

    /** 同一用户可能同时出现在多个 tab：各 tab 一起摘掉，缓存也一起更新。 */
    private suspend fun dropUserEverywhere(uid: String) {
        val affected = _tabStates.value.keys.filter { view -> listOf(view).any { it.uid == uid } }
        val snapshot = affected.associateWith { listOf(it) }
        affected.forEach { view ->
            update(view) {
                it.copy(state = UiState.Success(snapshot[view].orEmpty().filterNot { user -> user.uid == uid }))
            }
        }
        val currentUid = sessionStore.uid.first()
        affected.forEach { view -> saveToCache(currentUid, view) }
    }

    private fun listOf(view: String): List<FriendUserItem> =
        (_tabStates.value[view]?.state as? UiState.Success)?.data ?: emptyList()

    private fun update(view: String, transform: (FriendsTabState) -> FriendsTabState) {
        val current = _tabStates.value[view] ?: FriendsTabState()
        _tabStates.value = _tabStates.value + (view to transform(current))
    }

    /** 列表缓存 = 当前已加载的用户流（含翻页追加的部分）与其页码。 */
    private suspend fun saveToCache(uid: String, view: String) {
        val tab = _tabStates.value[view] ?: return
        val users = (tab.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.myFriends(uid, view),
            ttlMillis = CacheKeys.LIST_TTL,
            value = FriendUserPage(users = users, page = tab.page, hasNext = tab.hasNext),
            encode = { cacheJson.encodeToString(FriendUserPage.serializer(), it) },
        )
    }

    /**
     * 好友请求缓存：formhash 一次性有效，落盘时置空。
     * 命中缓存后同意/忽略由仓库自行抓新鲜凭据。
     */
    private suspend fun saveRequestsToCache(uid: String) {
        val requests = (_requestState.value as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.friendRequests(uid),
            ttlMillis = CacheKeys.LIST_TTL,
            value = FriendRequestPage(requests = requests, page = requestPage, hasNext = _requestHasNext.value),
            encode = { cacheJson.encodeToString(FriendRequestPage.serializer(), it.copy(formhash = null)) },
        )
    }

    companion object {
        const val VIEW_REQUEST = "request"
    }
}

private val MY_FRIENDS_TABS = listOf(
    "全部好友" to "",
    "最近来访" to "visitor",
    "我看过谁" to "trace",
    "黑名单" to "blacklist",
    "好友请求" to MyFriendsViewModel.VIEW_REQUEST,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFriendsScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: MyFriendsViewModel = appViewModel {
        MyFriendsViewModel(it.repository, FriendsRepo(it.httpClient), it.contentCache, it.sessionStore)
    }
    val tabs = MY_FRIENDS_TABS
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val friendPending by viewModel.friendPending.collectAsStateWithLifecycle()
    val requestState by viewModel.requestState.collectAsStateWithLifecycle()
    val requestHasNext by viewModel.requestHasNext.collectAsStateWithLifecycle()
    val requestLoadingMore by viewModel.requestLoadingMore.collectAsStateWithLifecycle()
    val requestPending by viewModel.requestPending.collectAsStateWithLifecycle()
    val requestResetTick by viewModel.requestResetTick.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    // 每页一个列表状态并提升到页面作用域：切 tab 不丢滚动位置，也不重建已翻页的数据
    val listStates = tabs.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()

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
                            "我的好友",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = bodyTextColor(),
                        )
                    },
                    expandedHeight = MtfTopBarHeight,
                )
                val accent = accentTextColor()
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = colors.topBar,
                    contentColor = accent,
                    edgePadding = 16.dp,
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
            val view = tabs[page].second
            if (view == MyFriendsViewModel.VIEW_REQUEST) {
                // 进入 composition 才拉数据；loadRequests 内部对已加载过的直接返回
                LaunchedEffect(view) { viewModel.loadRequests() }
                FriendRequestList(
                    requestState = requestState,
                    refreshing = view in refreshing,
                    pending = requestPending,
                    loadingMore = requestLoadingMore,
                    hasNext = requestHasNext,
                    resetScrollTick = requestResetTick,
                    listState = listStates[page],
                    onRetry = { viewModel.retryRequests() },
                    onRefresh = { viewModel.refresh(view) },
                    onApprove = { viewModel.approveRequest(it) },
                    onIgnore = { viewModel.ignoreRequest(it) },
                    onLoadMore = { viewModel.loadMoreRequests() },
                )
            } else {
                // 进入 composition 才拉数据；load 内部对已加载过的 tab 直接返回
                LaunchedEffect(view) { viewModel.load(view) }
                val tab = tabStates[view] ?: FriendsTabState()
                FriendsTab(
                    tab = tab,
                    refreshing = view in refreshing,
                    view = view,
                    listState = listStates[page],
                    emptyText = "暂无${tabs[page].first}信息",
                    pendingUid = friendPending,
                    onRetry = { viewModel.retry(view) },
                    onRefresh = { viewModel.refresh(view) },
                    onLoadMore = { viewModel.loadMore(view) },
                    onUserClick = onUserClick,
                    onDelete = { viewModel.removeFriend(it) },
                    onUnblock = { viewModel.unblacklistUser(it) },
                )
            }
        }
    }
}

/** 单个 tab 的用户流：缓存优先上屏、下拉刷新、滑到底部自动翻页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsTab(
    tab: FriendsTabState,
    refreshing: Boolean,
    view: String,
    listState: LazyListState,
    emptyText: String,
    pendingUid: String,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit,
    onDelete: (FriendUserItem) -> Unit,
    onUnblock: (FriendUserItem) -> Unit,
) {
    // 第 1 页数据替换上屏后归位：仅在顶部附近才跳，已下滑的用户不打扰。
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
            val users = current.data
            if (users.isEmpty()) {
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(users, key = { it.uid }) { user ->
                            FriendUserCard(
                                user = user,
                                removing = pendingUid == user.uid,
                                showDelete = view.isEmpty(),
                                showUnblock = view == "blacklist",
                                onClick = { onUserClick(user.uid) },
                                onDelete = { onDelete(user) },
                                onUnblock = { onUnblock(user) },
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

/** 好友请求列表：行内「同意 / 忽略」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendRequestList(
    requestState: UiState<List<FriendRequestItem>>,
    refreshing: Boolean,
    pending: String,
    loadingMore: Boolean,
    hasNext: Boolean,
    resetScrollTick: Int,
    listState: LazyListState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onApprove: (FriendRequestItem) -> Unit,
    onIgnore: (FriendRequestItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    // 第 1 页数据替换上屏后归位：仅在顶部附近才跳，已下滑的用户不打扰。
    // 记上次处理到的 tick（初值取当前值）：返回或重建时初值已是最新值，不会误跳
    var lastResetTick by remember { mutableIntStateOf(resetScrollTick) }
    LaunchedEffect(resetScrollTick) {
        if (resetScrollTick > lastResetTick) {
            lastResetTick = resetScrollTick
            listState.scrollToNewest()
        }
    }
    when (val current = requestState) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(current.message, onRetry)
        is UiState.Success -> {
            val requests = current.data
            if (requests.isEmpty()) {
                EmptyBox("暂无好友请求")
            } else {
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && last >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMore, hasNext) {
                    if (shouldLoadMore && hasNext) onLoadMore()
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
                        items(requests, key = { it.uid }) { item ->
                            FriendRequestCard(
                                item = item,
                                pending = pending == item.uid,
                                onApprove = { onApprove(item) },
                                onIgnore = { onIgnore(item) },
                            )
                        }
                        if (loadingMore) {
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
private fun FriendRequestCard(
    item: FriendRequestItem,
    pending: Boolean,
    onApprove: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AsyncImage(
            model = smallImageRequest(item.avatarUrl),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .dimImageInDark(),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.username,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Medium,
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.description.isNullOrBlank()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (pending) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.textHint)
        } else {
            TextButton(onClick = onIgnore) {
                Text("忽略", fontSize = 13.sp, color = colors.textSecondary)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                onClick = onApprove,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text("同意", fontSize = 13.sp, color = accentTextColor())
            }
        }
    }
}

@Composable
private fun FriendUserCard(
    user: FriendUserItem,
    removing: Boolean,
    showDelete: Boolean,
    showUnblock: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUnblock: () -> Unit = {},
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
        AsyncImage(
            model = smallImageRequest(user.avatarUrl),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .dimImageInDark(),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.username,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Medium,
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!user.description.isNullOrBlank()) {
                Text(
                    user.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showUnblock) {
            if (removing) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.textHint,
                )
            } else {
                Text(
                    "移出黑名单",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onUnblock)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        } else if (showDelete) {
            if (removing) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.textHint,
                )
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = "删除好友",
                        tint = colors.textHint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Icon(
                painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = colors.textHint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

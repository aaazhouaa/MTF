package com.aazhou.mtforum.ui.screen.follow

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aazhou.mtforum.data.model.FollowUserItem
import com.aazhou.mtforum.data.model.FollowUserPage
import com.aazhou.mtforum.data.repo.ForumRepository
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
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 单个 tab（关注/粉丝）的用户列表状态。 */
data class FollowTabState(
    val state: UiState<List<FollowUserItem>> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
)

class FollowListViewModel(
    initialType: String,
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    /** 各 tab 的列表状态，key 为类型；未加载过的 tab 不在表内。 */
    private val _tabStates = MutableStateFlow<Map<String, FollowTabState>>(emptyMap())
    val tabStates: StateFlow<Map<String, FollowTabState>> = _tabStates.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load(initialType)
    }

    /**
     * 首次进入某个 tab：有缓存就先摆出来（不走全屏 Loading），网络回来再原地替换。
     *
     * 已加载过的 tab 直接返回：切 tab 不重新请求，滚动位置与已翻页的数据都留着。
     */
    fun load(type: String) {
        if (_tabStates.value.containsKey(type)) return
        update(type) { FollowTabState() }
        viewModelScope.launch {
            val uid = sessionStore.uid.first()
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(
                CacheKeys.followList(uid, type),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<FollowUserPage>(it)
            }
            if (cached != null) {
                update(type) {
                    it.copy(
                        state = UiState.Success(cached.users),
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

    private suspend fun reload(type: String, uid: String) {
        runCatching { repository.followList(type = type, page = 1) }
            .onSuccess { data ->
                update(type) {
                    it.copy(state = UiState.Success(data.users), page = 1, hasNext = data.hasNext)
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
        viewModelScope.launch {
            val next = current.page + 1
            runCatching { repository.followList(type = type, page = next) }
                .onSuccess { data ->
                    // 合并时现读，避免把刚替换上的新列表又拼回旧数据后面
                    val existing = (_tabStates.value[type]?.state as? UiState.Success)?.data.orEmpty()
                    val known = existing.mapTo(HashSet()) { it.uid }
                    val fresh = data.users.filter { known.add(it.uid) }
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

    /** 关注/取消关注：先翻本地按钮，失败回滚；两个 tab 的同一个人一起翻。 */
    fun toggleFollow(type: String, user: FollowUserItem) {
        val target = !user.isFollowing
        val affected = _tabStates.value.keys.filter { key -> listOf(key).any { it.uid == user.uid } }
        val snapshot = affected.associateWith { listOf(it) }
        if (affected.isEmpty()) return
        affected.forEach { key ->
            update(key) { tab ->
                tab.copy(
                    state = UiState.Success(
                        snapshot[key].orEmpty().map {
                            if (it.uid == user.uid) it.copy(isFollowing = target) else it
                        },
                    ),
                )
            }
        }
        viewModelScope.launch {
            val ok = runCatching { repository.toggleFollowUser(user.uid, target) }
                .getOrDefault(false)
            if (ok) {
                AppToast.show(if (target) "关注成功" else "已取消关注")
                val uid = sessionStore.uid.first()
                affected.forEach { key -> saveToCache(uid, key) }
            } else {
                AppToast.show("操作失败")
                snapshot.forEach { (key, list) ->
                    update(key) { it.copy(state = UiState.Success(list)) }
                }
            }
        }
    }

    private fun listOf(type: String): List<FollowUserItem> =
        (_tabStates.value[type]?.state as? UiState.Success)?.data ?: emptyList()

    private fun update(type: String, transform: (FollowTabState) -> FollowTabState) {
        val current = _tabStates.value[type] ?: FollowTabState()
        _tabStates.value = _tabStates.value + (type to transform(current))
    }

    /** 列表缓存 = 当前已加载的用户流（含翻页追加的部分）与其页码。 */
    private suspend fun saveToCache(uid: String, type: String) {
        val tab = _tabStates.value[type] ?: return
        val users = (tab.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.followList(uid, type),
            ttlMillis = CacheKeys.LIST_TTL,
            value = FollowUserPage(users = users, page = tab.page, hasNext = tab.hasNext),
            encode = { cacheJson.encodeToString(FollowUserPage.serializer(), it) },
        )
    }
}

private val FOLLOW_TABS = listOf("我的关注" to "following", "我的粉丝" to "follower")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    initialType: String = "following",
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: FollowListViewModel = appViewModel {
        FollowListViewModel(initialType, it.repository, it.contentCache, it.sessionStore)
    }
    val tabs = FOLLOW_TABS
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = if (initialType == "follower") 1 else 0,
    ) { tabs.size }
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
                            tabs[pagerState.currentPage].first,
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
            val tab = tabStates[type] ?: FollowTabState()
            FollowTab(
                tab = tab,
                listState = listStates[page],
                emptyText = "暂无${tabs[page].first}用户",
                onRetry = { viewModel.retry(type) },
                onLoadMore = { viewModel.loadMore(type) },
                onUserClick = onUserClick,
                onToggleFollow = { user -> viewModel.toggleFollow(type, user) },
            )
        }
    }
}

/** 单个 tab 的用户流：缓存优先上屏、滑到底部自动翻页。 */
@Composable
private fun FollowTab(
    tab: FollowTabState,
    listState: LazyListState,
    emptyText: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit,
    onToggleFollow: (FollowUserItem) -> Unit,
) {
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
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(users, key = { it.uid }) { user ->
                        FollowUserCard(
                            user = user,
                            onClick = { onUserClick(user.uid) },
                            onToggleFollow = { onToggleFollow(user) },
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

@Composable
private fun FollowUserCard(
    user: FollowUserItem,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
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
            val stats = listOfNotNull(
                user.fansCount?.let { "$it 粉丝" },
                user.followCount?.let { "$it 关注" },
            ).joinToString(" · ")
            if (stats.isNotBlank()) {
                Text(
                    stats,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        OutlinedButton(
            onClick = onToggleFollow,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (user.isFollowing) colors.textSecondary else MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(
                1.dp,
                if (user.isFollowing) colors.divider else MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.6f else 0.4f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                if (user.isFollowing) "已关注" else "+ 关注",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

package com.aazhou.mtforum.ui.screen.myfriends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.aazhou.mtforum.data.model.FriendRequestItem
import com.aazhou.mtforum.data.model.FriendUserItem
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.FriendsRepo
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
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
import kotlinx.coroutines.launch

class MyFriendsViewModel(
    private val repository: ForumRepository,
    private val friendsRepo: FriendsRepo,
) : ViewModel() {

    private val _view = MutableStateFlow("")
    val view: StateFlow<String> = _view.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<FriendUserItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FriendUserItem>>> = _state.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /** 正在删除的好友 uid：行内转圈并防连点。 */
    private val _friendPending = MutableStateFlow("")
    val friendPending: StateFlow<String> = _friendPending.asStateFlow()

    private val _requestState = MutableStateFlow<UiState<List<FriendRequestItem>>>(UiState.Loading)
    val requestState: StateFlow<UiState<List<FriendRequestItem>>> = _requestState.asStateFlow()

    private val _requestHasNext = MutableStateFlow(false)
    val requestHasNext: StateFlow<Boolean> = _requestHasNext.asStateFlow()

    private val _requestLoadingMore = MutableStateFlow(false)
    val requestLoadingMore: StateFlow<Boolean> = _requestLoadingMore.asStateFlow()

    /** 正在同意/忽略的请求 uid。 */
    private val _requestPending = MutableStateFlow("")
    val requestPending: StateFlow<String> = _requestPending.asStateFlow()

    private var requestFormhash: String? = null
    private var currentPage = 1
    private var requestPage = 1

    init {
        load("")
    }

    fun switchTab(newView: String) {
        if (_view.value == newView) return
        _view.value = newView
        if (newView == VIEW_REQUEST) loadRequests() else load(newView)
    }

    fun load(view: String = _view.value) {
        currentPage = 1
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val pageData = repository.myFriends(view = view, page = 1)
                _hasNext.value = pageData.hasNext
                _state.value = UiState.Success(pageData.users)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun loadMore() {
        if (_loadingMore.value || !_hasNext.value) return
        _loadingMore.value = true
        val nextPage = currentPage + 1
        viewModelScope.launch {
            try {
                val pageData = repository.myFriends(view = _view.value, page = nextPage)
                val current = (_state.value as? UiState.Success)?.data.orEmpty()
                _state.value = UiState.Success((current + pageData.users).distinctBy { it.uid })
                currentPage = nextPage
                _hasNext.value = pageData.hasNext
            } catch (_: Exception) {
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** 好友请求列表：站点把它放在 spacecp 下，不是 do=friend 的 view。 */
    fun loadRequests() {
        requestPage = 1
        _requestState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val pageData = friendsRepo.friendRequests(1)
                requestFormhash = pageData.formhash
                requestPage = 1
                _requestHasNext.value = pageData.hasNext
                _requestState.value = UiState.Success(pageData.requests)
            } catch (e: Exception) {
                _requestState.value = UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun loadMoreRequests() {
        if (_requestLoadingMore.value || !_requestHasNext.value) return
        _requestLoadingMore.value = true
        val nextPage = requestPage + 1
        viewModelScope.launch {
            try {
                val pageData = friendsRepo.friendRequests(nextPage)
                val current = (_requestState.value as? UiState.Success)?.data.orEmpty()
                _requestState.value = UiState.Success((current + pageData.requests).distinctBy { it.uid })
                requestPage = nextPage
                _requestHasNext.value = pageData.hasNext
            } catch (_: Exception) {
            } finally {
                _requestLoadingMore.value = false
            }
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
                    val current = (_state.value as? UiState.Success)?.data.orEmpty()
                    _state.value = UiState.Success(current.filterNot { it.uid == user.uid })
                }
                .onFailure { e -> AppToast.show(e.message ?: "删除失败") }
            _friendPending.value = ""
        }
    }

    /** 移出黑名单：成功后把该行从黑名单列表里摘掉。 */
    fun unblacklistUser(user: FriendUserItem) {
        if (_friendPending.value.isNotEmpty()) return
        _friendPending.value = user.uid
        viewModelScope.launch {
            val result = runCatching { repository.removeUserBlacklist(user.uid) }
                .getOrElse { Result.failure(it) }
            result
                .onSuccess {
                    AppToast.show(it)
                    val current = (_state.value as? UiState.Success)?.data.orEmpty()
                    _state.value = UiState.Success(current.filterNot { it.uid == user.uid })
                }
                .onFailure { e -> AppToast.show(e.message ?: "移出黑名单失败") }
            _friendPending.value = ""
        }
    }

    companion object {
        const val VIEW_REQUEST = "request"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFriendsScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: MyFriendsViewModel = appViewModel { MyFriendsViewModel(it.repository, FriendsRepo(it.httpClient)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "全部好友" to "",
        "最近来访" to "visitor",
        "我看过谁" to "trace",
        "黑名单" to "blacklist",
        "好友请求" to MyFriendsViewModel.VIEW_REQUEST,
    )
    val currentView = tabs[selectedTab].second

    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val friendPending by viewModel.friendPending.collectAsStateWithLifecycle()
    val requestState by viewModel.requestState.collectAsStateWithLifecycle()
    val requestHasNext by viewModel.requestHasNext.collectAsStateWithLifecycle()
    val requestLoadingMore by viewModel.requestLoadingMore.collectAsStateWithLifecycle()
    val requestPending by viewModel.requestPending.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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
                    selectedTabIndex = selectedTab,
                    containerColor = colors.topBar,
                    contentColor = accent,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = accent,
                        )
                    },
                ) {
                    tabs.forEachIndexed { index, (title, view) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                viewModel.switchTab(view)
                            },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (selectedTab == index) accent else colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (currentView == MyFriendsViewModel.VIEW_REQUEST) {
            when (val current = requestState) {
                is UiState.Loading -> LoadingBox(Modifier.padding(padding))
                is UiState.Error -> ErrorBox(current.message, { viewModel.loadRequests() }, Modifier.padding(padding))
                is UiState.Success -> {
                    val requests = current.data
                    if (requests.isEmpty()) {
                        EmptyBox("暂无好友请求", Modifier.padding(padding))
                    } else {
                        FriendRequestList(
                            requests = requests,
                            pending = requestPending,
                            loadingMore = requestLoadingMore,
                            hasNext = requestHasNext,
                            listState = listState,
                            padding = padding,
                            onApprove = { viewModel.approveRequest(it) },
                            onIgnore = { viewModel.ignoreRequest(it) },
                            onLoadMore = { viewModel.loadMoreRequests() },
                        )
                    }
                }
            }
        } else {
            when (val current = uiState) {
                is UiState.Loading -> LoadingBox(Modifier.padding(padding))
                is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
                is UiState.Success -> {
                    val users = current.data
                    if (users.isEmpty()) {
                        EmptyBox("暂无${tabs[selectedTab].first}信息", Modifier.padding(padding))
                    } else {
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
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxSize().padding(padding),
                        ) {
                            items(users, key = { it.uid }) { user ->
                                FriendUserCard(
                                    user = user,
                                    removing = friendPending == user.uid,
                                    showDelete = currentView.isEmpty(),
                                    showUnblock = currentView == "blacklist",
                                    onClick = { onUserClick(user.uid) },
                                    onDelete = { viewModel.removeFriend(user) },
                                    onUnblock = { viewModel.unblacklistUser(user) },
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
}

/** 好友请求列表：行内「同意 / 忽略」。 */
@Composable
private fun FriendRequestList(
    requests: List<FriendRequestItem>,
    pending: String,
    loadingMore: Boolean,
    hasNext: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    padding: PaddingValues,
    onApprove: (FriendRequestItem) -> Unit,
    onIgnore: (FriendRequestItem) -> Unit,
    onLoadMore: () -> Unit,
) {
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
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
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

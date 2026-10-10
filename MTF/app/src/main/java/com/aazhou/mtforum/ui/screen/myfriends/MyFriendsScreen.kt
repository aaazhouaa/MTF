package com.aazhou.mtforum.ui.screen.myfriends

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import com.aazhou.mtforum.data.model.FriendUserItem
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyFriendsViewModel(private val repository: ForumRepository) : ViewModel() {
    private val _view = MutableStateFlow("")
    val view: StateFlow<String> = _view.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<FriendUserItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FriendUserItem>>> = _state.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var currentPage = 1

    init {
        load("")
    }

    fun switchTab(newView: String) {
        if (_view.value == newView) return
        _view.value = newView
        load(newView)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFriendsScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: MyFriendsViewModel = appViewModel { MyFriendsViewModel(it.repository) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "全部好友" to "",
        "最近来访" to "visitor",
        "我看过谁" to "trace",
        "黑名单" to "blacklist",
    )

    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
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
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.topBar,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
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
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
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
                                onClick = { onUserClick(user.uid) },
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
private fun FriendUserCard(
    user: FriendUserItem,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
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
        Icon(
            painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.textHint,
            modifier = Modifier.size(16.dp),
        )
    }
}

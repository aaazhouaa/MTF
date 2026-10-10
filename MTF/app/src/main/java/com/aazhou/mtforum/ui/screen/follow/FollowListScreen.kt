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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.aazhou.mtforum.data.model.FollowUserItem
import com.aazhou.mtforum.data.repo.ForumRepository
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
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FollowListViewModel(
    initialType: String,
    private val repository: ForumRepository,
) : ViewModel() {
    private val _type = MutableStateFlow(initialType)
    val type: StateFlow<String> = _type.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<FollowUserItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FollowUserItem>>> = _state.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var currentPage = 1

    init {
        load(initialType)
    }

    fun switchTab(newType: String) {
        if (_type.value == newType) return
        _type.value = newType
        load(newType)
    }

    fun load(type: String = _type.value) {
        currentPage = 1
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val pageData = repository.followList(type = type, page = 1)
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
                val pageData = repository.followList(type = _type.value, page = nextPage)
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

    fun toggleFollow(user: FollowUserItem) {
        val target = !user.isFollowing
        val currentList = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(
            currentList.map {
                if (it.uid == user.uid) it.copy(isFollowing = target) else it
            },
        )
        viewModelScope.launch {
            val ok = repository.toggleFollowUser(user.uid, target)
            if (ok) {
                AppToast.show(if (target) "关注成功" else "已取消关注")
            } else {
                AppToast.show("操作失败")
                // 回滚
                _state.value = UiState.Success(currentList)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    initialType: String = "following",
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: FollowListViewModel = appViewModel {
        FollowListViewModel(initialType, it.repository)
    }
    var selectedTab by remember {
        mutableIntStateOf(if (initialType == "follower") 1 else 0)
    }
    val tabs = listOf("我的关注" to "following", "我的粉丝" to "follower")

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
                            tabs[selectedTab].first,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = bodyTextColor(),
                        )
                    },
                    expandedHeight = MtfTopBarHeight,
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.topBar,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    tabs.forEachIndexed { index, (title, type) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                viewModel.switchTab(type)
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
                    EmptyBox("暂无${tabs[selectedTab].first}用户", Modifier.padding(padding))
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
                            FollowUserCard(
                                user = user,
                                onClick = { onUserClick(user.uid) },
                                onToggleFollow = { viewModel.toggleFollow(user) },
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
                user.fansCount?.let { "${it} 粉丝" },
                user.followCount?.let { "${it} 关注" },
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

package com.aazhou.mtforum.ui.screen.notice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeCategory
import com.aazhou.mtforum.data.model.PrivateMessage
import com.aazhou.mtforum.data.repo.NoticeRepo
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.screen.login.LoginDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

/** 消息提醒页：对应网页端「我的」→「消息提醒」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBack: () -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (String) -> Unit,
    onChatClick: ((uid: String, name: String) -> Unit)? = null,
) {
    val colors = LocalMtfColors.current
    val viewModel: NoticeViewModel = appViewModel {
        NoticeViewModel(
            it.repository,
            NoticeRepo(it.httpClient),
            it.contentCache,
            onRead = { ids -> it.markNoticesRead(ids) },
        )
    }
    val currentCategory by viewModel.category.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 申请通知权限
    val context = LocalContext.current
    val container = context.container()
    val isLoggedIn = container.httpClient.isLoggedIn()
    var showLoginDialog by remember { mutableStateOf(!isLoggedIn) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = colors.topBar,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                title = { Text("消息提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                        )
                    }
                },
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 顶部五个分类入口：整齐排成一排，紧凑精致
            NoticeCategoryBar(
                selected = currentCategory,
                onSelect = { cat ->
                    viewModel.selectCategory(cat)
                    scope.launch { listState.scrollToTop() }
                },
            )

            // 下方随选中的分类展示对应列表
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.background),
            ) {
                when (val current = state) {
                    is UiState.Loading -> LoadingBox(Modifier.fillMaxSize())
                    is UiState.Error -> {
                        if (!container.httpClient.isLoggedIn() || current.message.contains("登录")) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .padding(horizontal = 32.dp)
                                        .widthIn(max = 320.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.quoteBackground)
                                        .padding(horizontal = 24.dp, vertical = 28.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lock_outline),
                                            contentDescription = null,
                                            tint = accentTextColor(),
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "需要登录账号",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = bodyTextColor(),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "请先登录后查看消息提醒与互动通知",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                            color = colors.textSecondary,
                                        )
                                    }
                                    androidx.compose.material3.Button(
                                        onClick = { showLoginDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                    ) {
                                        Text("立即登录", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else {
                            ErrorBox(
                                message = current.message,
                                onRetry = { viewModel.loadCategory(currentCategory) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is UiState.Success -> {
                        val content = current.data
                        if (content.isEmpty()) {
                            val emptyText = when (currentCategory) {
                                NoticeCategory.PRIVATE_PM -> "暂无私信消息"
                                NoticeCategory.ANNOUNCE_PM -> "暂无公共消息"
                                NoticeCategory.MY_POST -> "暂无帖子提醒"
                                NoticeCategory.INTERACTIVE -> "暂无互动提醒"
                                NoticeCategory.SYSTEM -> "暂无系统提醒"
                            }
                            EmptyBox(emptyText, Modifier.fillMaxSize())
                        } else {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    viewModel.refresh()
                                    // 刷新会把列表换回第 1 页：不复位滚动位置的话，
                                    // 原先停在很后面的视口会被钳到新列表末尾，而新提醒
                                    // 按时间倒序排在最前面，正好被推到视口外看不见
                                    scope.launch { listState.scrollToTop() }
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Crossfade(targetState = content, label = "notice_content") { target ->
                                    when (target) {
                                        is NoticeContentState.Notices -> {
                                            NoticeListView(
                                                listState = listState,
                                                notices = target.items,
                                                onUserClick = onUserClick,
                                                onThreadClick = onThreadClick,
                                                hasNext = hasNext,
                                                loadingMore = loadingMore,
                                                onLoadMore = { viewModel.loadMore() },
                                            )
                                        }
                                        is NoticeContentState.PrivateMessages -> {
                                            PrivateMessageListView(
                                                listState = listState,
                                                items = target.items,
                                                onUserClick = onUserClick,
                                                onChatClick = onChatClick,
                                            )
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

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                container.refreshCurrentUserAsync()
                viewModel.loadCategory(currentCategory)
            },
        )
    }
}

/**
 * 顶部固定分类区：排成一排 5 列，扁平紧凑、圆润雅致，与顶栏自然衔接。
 */
@Composable
private fun NoticeCategoryBar(
    selected: NoticeCategory,
    onSelect: (NoticeCategory) -> Unit,
) {
    val colors = LocalMtfColors.current

    Surface(
        color = colors.topBar,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryItem(
                    category = NoticeCategory.PRIVATE_PM,
                    iconRes = R.drawable.ic_notice_pm,
                    isSelected = selected == NoticeCategory.PRIVATE_PM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.PRIVATE_PM) },
                )
                CategoryItem(
                    category = NoticeCategory.ANNOUNCE_PM,
                    iconRes = R.drawable.ic_notice_announce,
                    isSelected = selected == NoticeCategory.ANNOUNCE_PM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.ANNOUNCE_PM) },
                )
                CategoryItem(
                    category = NoticeCategory.MY_POST,
                    iconRes = R.drawable.ic_notice_mypost,
                    isSelected = selected == NoticeCategory.MY_POST,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.MY_POST) },
                )
                CategoryItem(
                    category = NoticeCategory.INTERACTIVE,
                    iconRes = R.drawable.ic_notice_interactive,
                    isSelected = selected == NoticeCategory.INTERACTIVE,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.INTERACTIVE) },
                )
                CategoryItem(
                    category = NoticeCategory.SYSTEM,
                    iconRes = R.drawable.ic_notice_system,
                    isSelected = selected == NoticeCategory.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.SYSTEM) },
                )
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        }
    }
}

/** 单个分类图标项：选中统一采用主题主色。 */
@Composable
private fun CategoryItem(
    category: NoticeCategory,
    iconRes: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val itemBg = if (isSelected) {
        primaryColor.copy(alpha = if (isDark) 0.22f else 0.12f)
    } else {
        if (isDark) colors.quoteBackground.copy(alpha = 0.5f) else colors.quoteBackground
    }

    val iconTint = if (isSelected) primaryColor else colors.iconSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        // 圆形图标
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(itemBg)
                .then(
                    if (isSelected) {
                        Modifier.border(1.5.dp, primaryColor, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = category.title,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = category.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isSelected) accentTextColor() else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 提醒列表（我的帖子 / 坛友互动 / 系统提醒）。 */
@Composable
private fun NoticeListView(
    listState: androidx.compose.foundation.lazy.LazyListState,
    notices: List<Notice>,
    onUserClick: (String) -> Unit,
    onThreadClick: (tid: String) -> Unit,
    hasNext: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    // 滑到接近末尾时自动追加下一页
    val shouldLoadMore by remember(notices) {
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarContentPadding()),
    ) {
        items(notices, key = { it.id }) { notice ->
            NoticeRow(
                notice = notice,
                onUserClick = onUserClick,
                onClick = {
                    notice.tid?.let { tid -> onThreadClick(tid) }
                },
            )
        }
        if (loadingMore) {
            item(key = "notice-loading-more") {
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

/** 私信消息列表。 */
@Composable
private fun PrivateMessageListView(
    listState: androidx.compose.foundation.lazy.LazyListState,
    items: List<PrivateMessage>,
    onUserClick: (String) -> Unit,
    onChatClick: ((String, String) -> Unit)? = null,
) {
    val colors = LocalMtfColors.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarContentPadding()),
    ) {
        items(items, key = { it.plid ?: it.touid ?: it.username }) { pm ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val touid = pm.touid
                        if (!touid.isNullOrBlank() && onChatClick != null) {
                            onChatClick(touid, pm.username)
                        } else {
                            touid?.let(onUserClick)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp)) {
                        AsyncImage(
                            model = smallImageRequest(pm.avatarUrl),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(colors.quoteBackground)
                                .dimImageInDark(),
                        )
                        if (pm.unread) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF4D4F))
                                    .align(Alignment.TopEnd),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = pm.username,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = bodyTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!pm.timeText.isNullOrBlank()) {
                                Text(
                                    text = pm.timeText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        if (pm.summary.isNotBlank()) {
                            Text(
                                text = pm.summary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier
                        .padding(start = 54.dp)
                        .fillMaxWidth()
                        .height(0.5.dp),
                ) {
                    drawLine(
                        color = colors.divider,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                    )
                }
            }
        }
    }
}

/** 单条提醒：头像 + 正文 + 时间，站点给了主题链接才可点击。 */
@Composable
private fun NoticeRow(notice: Notice, onUserClick: (String) -> Unit, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    val clickable = notice.tid != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(
                if (clickable) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            val avatarUrl = notice.avatarUrl
            if (avatarUrl != null) {
                AsyncImage(
                    model = smallImageRequest(avatarUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.quoteBackground)
                        .dimImageInDark()
                        .then(
                            notice.uid?.let { uid -> Modifier.clickable { onUserClick(uid) } }
                                ?: Modifier,
                        ),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.quoteBackground),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_mail_line),
                        contentDescription = null,
                        tint = accentTextColor(),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notice.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = bodyTextColor(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!notice.timeText.isNullOrBlank()) {
                    Text(
                        notice.timeText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Canvas(
                    Modifier
                        .padding(end = 4.dp)
                        .fillMaxWidth()
                        .height(0.5.dp),
                ) {
                    drawLine(
                        color = colors.divider,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                    )
                }
            }
        }
    }
}

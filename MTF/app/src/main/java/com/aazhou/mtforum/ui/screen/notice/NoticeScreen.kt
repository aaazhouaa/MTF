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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.aazhou.mtforum.data.model.FollowerUser
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeCategory
import com.aazhou.mtforum.data.model.PrivateMessage
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
    onThreadClick: (tid: String, pid: String?) -> Unit,
    onUserClick: (String) -> Unit,
    onChatClick: ((uid: String, name: String) -> Unit)? = null,
) {
    val colors = LocalMtfColors.current
    val viewModel: NoticeViewModel = appViewModel {
        NoticeViewModel(it.repository, it.contentCache, onRead = { ids -> it.markNoticesRead(ids) })
    }
    val currentCategory by viewModel.category.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
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
            // 固定在顶部的六个分类入口卡片
            NoticeCategoryGrid(
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
                    .weight(1f),
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
                                NoticeCategory.PM -> "暂无私信消息"
                                NoticeCategory.FOLLOWER -> "暂无粉丝关注"
                                NoticeCategory.MY_POST -> "暂无帖子提醒"
                                NoticeCategory.INTERACTIVE -> "暂无互动提醒"
                                NoticeCategory.SYSTEM -> "暂无系统提醒"
                                NoticeCategory.APP -> "暂无应用提醒"
                            }
                            EmptyBox(emptyText, Modifier.fillMaxSize())
                        } else {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = { viewModel.refresh() },
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
                                                onVisibleChanged = { visible ->
                                                    viewModel.ensureContents(visible)
                                                },
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
                                        is NoticeContentState.Followers -> {
                                            FollowerListView(
                                                listState = listState,
                                                items = target.items,
                                                onUserClick = onUserClick,
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
 * 顶部固定分类区：2 行 3 列网格，紧凑精致，与软件设计统一。
 */
@Composable
private fun NoticeCategoryGrid(
    selected: NoticeCategory,
    onSelect: (NoticeCategory) -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isDark) 0.dp else 0.5.dp,
        border = if (isDark) androidx.compose.foundation.BorderStroke(0.5.dp, colors.divider) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 第一行：我的消息、我的粉丝、我的帖子
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CategoryItem(
                    category = NoticeCategory.PM,
                    iconRes = R.drawable.ic_notice_pm,
                    iconBgColor = Color(0xFF53BCF5),
                    isSelected = selected == NoticeCategory.PM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.PM) },
                )
                CategoryItem(
                    category = NoticeCategory.FOLLOWER,
                    iconRes = R.drawable.ic_notice_follower,
                    iconBgColor = Color(0xFFF37D7D),
                    isSelected = selected == NoticeCategory.FOLLOWER,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.FOLLOWER) },
                )
                CategoryItem(
                    category = NoticeCategory.MY_POST,
                    iconRes = R.drawable.ic_notice_mypost,
                    iconBgColor = Color(0xFF9DCA06),
                    isSelected = selected == NoticeCategory.MY_POST,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.MY_POST) },
                )
            }

            // 第二行：坛友互动、系统提醒、应用提醒
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CategoryItem(
                    category = NoticeCategory.INTERACTIVE,
                    iconRes = R.drawable.ic_notice_interactive,
                    iconBgColor = Color(0xFFFFB300),
                    isSelected = selected == NoticeCategory.INTERACTIVE,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.INTERACTIVE) },
                )
                CategoryItem(
                    category = NoticeCategory.SYSTEM,
                    iconRes = R.drawable.ic_notice_system,
                    iconBgColor = Color(0xFFFF6565),
                    isSelected = selected == NoticeCategory.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.SYSTEM) },
                )
                CategoryItem(
                    category = NoticeCategory.APP,
                    iconRes = R.drawable.ic_notice_app,
                    iconBgColor = Color(0xFF91B9EB),
                    isSelected = selected == NoticeCategory.APP,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(NoticeCategory.APP) },
                )
            }
        }
    }
}

/** 单个分类图标项。 */
@Composable
private fun CategoryItem(
    category: NoticeCategory,
    iconRes: Int,
    iconBgColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val actualBg = if (isDark) iconBgColor.copy(alpha = 0.22f) else iconBgColor
    val iconTint = if (isDark) iconBgColor else Color.White

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
                .size(42.dp)
                .clip(CircleShape)
                .background(actualBg)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = category.title,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = category.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isSelected) accentTextColor() else bodyTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 提醒列表（我的帖子 / 坛友互动 / 系统提醒 / 应用提醒）。 */
@Composable
private fun NoticeListView(
    listState: androidx.compose.foundation.lazy.LazyListState,
    notices: List<Notice>,
    onUserClick: (String) -> Unit,
    onThreadClick: (tid: String, pid: String?) -> Unit,
    onVisibleChanged: (List<Notice>) -> Unit,
) {
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
                    notice.tid?.let { tid -> onThreadClick(tid, notice.pid) }
                },
            )
        }
    }
    val visibleNotices by remember(notices) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { notices.getOrNull(it.index) }
        }
    }
    LaunchedEffect(visibleNotices) {
        onVisibleChanged(visibleNotices)
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
        items(items) { pm ->
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

/** 粉丝列表。 */
@Composable
private fun FollowerListView(
    listState: androidx.compose.foundation.lazy.LazyListState,
    items: List<FollowerUser>,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarContentPadding()),
    ) {
        items(items) { follower ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserClick(follower.uid) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                AsyncImage(
                    model = smallImageRequest(follower.avatarUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.quoteBackground)
                        .dimImageInDark(),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = follower.username,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = bodyTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val fansStr = follower.fansCount?.let { "$it 粉丝" }.orEmpty()
                    val followStr = follower.followCount?.let { "$it 关注" }.orEmpty()
                    val stats = listOf(fansStr, followStr).filter { it.isNotBlank() }.joinToString("  ")
                    if (stats.isNotBlank()) {
                        Text(
                            text = stats,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = colors.textHint,
                    modifier = Modifier.size(16.dp),
                )
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
                val content = notice.content
                if (!content.isNullOrBlank()) {
                    Text(
                        content,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
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

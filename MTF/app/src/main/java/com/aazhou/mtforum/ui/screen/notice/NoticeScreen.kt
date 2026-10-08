package com.aazhou.mtforum.ui.screen.notice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

/** 消息提醒页：对应网页端「我的」→「消息提醒」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBack: () -> Unit,
    onThreadClick: (tid: String, pid: String?) -> Unit,
    onUserClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: NoticeViewModel = appViewModel {
        NoticeViewModel(it.repository, it.contentCache, onRead = { ids -> it.markNoticesRead(ids) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 通知权限在消息页申请：能说清新消息从哪来，比启动时先弹一锤子容易通过。
    // 不授权也不影响页面本身，只是后台新消息弹不出系统通知，红点照旧。
    val context = LocalContext.current
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
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                if (current.data.isEmpty()) {
                    EmptyBox("暂无消息提醒", Modifier.padding(padding))
                } else {
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = bottomBarContentPadding(),
                        ),
                    ) {
                        items(current.data, key = { it.id }) { notice ->
                            NoticeRow(
                                notice = notice,
                                onUserClick = onUserClick,
                                onClick = {
                                    notice.tid?.let { tid -> onThreadClick(tid, notice.pid) }
                                },
                            )
                        }
                    }
                    // 回复正文摘要按可见范围加载：进页面只问首屏这几条，滚下去再问后面的
                    val visibleNotices by remember(current.data) {
                        derivedStateOf {
                            listState.layoutInfo.visibleItemsInfo
                                .mapNotNull { current.data.getOrNull(it.index) }
                        }
                    }
                    LaunchedEffect(visibleNotices) {
                        viewModel.ensureContents(visibleNotices)
                    }
                    }
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
                    model = avatarUrl,
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
                // 回复内容摘要：与上方正文同一左边缘（即顶部用户名的左边缘），最多两行
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
                // 右端距屏幕右侧 20dp：所在 Column 已有左右各 16dp 外边距，向右再收 4dp 即 20dp，
                // 左端仍留在正文左边缘，故用定长分隔线而不去量正文最后一行
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

package com.aazhou.mtforum.ui.screen.forumlist

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.TabSecondaryActionIcons
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(
    onForumClick: (fid: String, name: String) -> Unit,
    onSettings: () -> Unit = {},
    onMessages: () -> Unit = {},
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val viewModel: ForumListViewModel = appViewModel { ForumListViewModel(it.repository, it.contentCache) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(actionMessage) {
        val message = actionMessage ?: return@LaunchedEffect
        AppToast.show(message)
        viewModel.consumeActionMessage()
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            ForumListTopBar(
                onSettings = onSettings,
                onMessages = onMessages,
                onDoubleTap = {
                    scope.launch { listState.scrollToTop() }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = bottomBarContentPadding() + 16.dp,
                            start = 12.dp,
                            end = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(current.data, key = { it.name }) { group ->
                            ForumGroupCard(
                                group = group,
                                onForumClick = onForumClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 顶栏：版块导航标题 + 设置与消息图标（已移除搜索图标）。 */
@Composable
private fun ForumListTopBar(
    onSettings: () -> Unit,
    onMessages: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val colors = LocalMtfColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .statusBarsPadding()
            .height(MtfTopBarHeight)
            .onDoubleTapToTop(onDoubleTap)
            .padding(start = 16.dp, end = 10.dp),
    ) {
        Text(
            text = "版块导航",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = bodyTextColor(),
        )

        Spacer(Modifier.weight(1f))

        TabSecondaryActionIcons(
            onSettings = onSettings,
            onMessages = onMessages,
        )
    }
}

/** 分组卡片：圆角卡片包裹分组标题与内部版块行。 */
@Composable
private fun ForumGroupCard(
    group: ForumGroup,
    onForumClick: (fid: String, name: String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .then(
                if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(16.dp))
                else Modifier
            ),
    ) {
        // 分组标题栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        ) {
            // 主题色垂直小指示条
            Box(
                modifier = Modifier
                    .size(width = 3.5.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = bodyTextColor(),
                modifier = Modifier.weight(1f),
            )
        }

        // 分组内版块列表
        group.forums.forEachIndexed { index, forum ->
            ForumItemRow(
                forum = forum,
                onClick = { onForumClick(forum.fid, forum.name) },
            )
            if (index < group.forums.size - 1) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.divider.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** 单个版块项：图标容器 + 版块名 + 右侧小箭头引导。 */
@Composable
private fun ForumItemRow(
    forum: Forum,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // 版块图标或优雅首字头像兜底
        ForumIconBadge(forum = forum)

        Spacer(Modifier.width(12.dp))

        // 版块名称
        Text(
            text = forum.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = bodyTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // 浅灰右箭头
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.iconSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 版块图标徽章：网络图片加载，或根据版块名提取首字符与配色优雅兜底。 */
@Composable
private fun ForumIconBadge(forum: Forum) {
    val colors = LocalMtfColors.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.quoteBackground),
    ) {
        if (!forum.icon.isNullOrBlank()) {
            AsyncImage(
                model = smallImageRequest(forum.icon),
                contentDescription = forum.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            // 首字字符占位
            val firstChar = forum.name.firstOrNull()?.toString() ?: "版"
            Text(
                text = firstChar,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = primaryColor,
            )
        }
    }
}

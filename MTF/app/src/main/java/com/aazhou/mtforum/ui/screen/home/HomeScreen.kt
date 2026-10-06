package com.aazhou.mtforum.ui.screen.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors

/** 首页：顶栏（应用名 + 搜索胶囊 + 主题切换）、热帖排行头部、帖子卡片流。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onForumClick: (fid: String, name: String) -> Unit,
    onThreadClick: (tid: String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: HomeViewModel = appViewModel { HomeViewModel(it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hot by viewModel.hot.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.background,
        topBar = { HomeTopBar(onSearch = onSearch, onToggleTheme = onToggleTheme) },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val listState = rememberLazyListState()
                // 滑到接近末尾时自动追加下一页
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
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                ) {
                    hot?.takeIf { it.hotThreads.isNotEmpty() }?.let { data ->
                        item(key = "hot-rank") {
                            HotRankCard(data.hotThreads, onThreadClick)
                        }
                    }
                    items(current.data, key = { it.tid }) { thread ->
                        ThreadCard(thread, onClick = { onThreadClick(thread.tid) })
                    }
                    if (loadingMore) {
                        item(key = "loading-more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 顶栏：「MTF」应用名 + 搜索胶囊 + 主题切换。 */
@Composable
private fun HomeTopBar(onSearch: () -> Unit, onToggleTheme: () -> Unit) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "MTF",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.quoteBackground)
                .clickable(onClick = onSearch)
                .padding(horizontal = 10.dp),
        ) {
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_search_new),
                contentDescription = null,
                tint = colors.textHint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "搜索帖子、用户...",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textHint,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(
                if (LocalIsDarkTheme.current) {
                    com.aazhou.mtforum.R.drawable.ic_theme_moon
                } else {
                    com.aazhou.mtforum.R.drawable.ic_theme_sun
                },
            ),
            contentDescription = "切换主题",
            tint = colors.iconSecondary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleTheme)
                .padding(7.dp),
        )
    }
}

/** 热帖排行卡片：15dp 圆角，彩色序号 + 单行标题 + 内部分隔线。 */
@Composable
private fun HotRankCard(threads: List<Thread>, onThreadClick: (String) -> Unit) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            "热帖排行",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        threads.forEachIndexed { index, thread ->
            val rank = index + 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onThreadClick(thread.tid) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rankColor(rank)),
                ) {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    thread.subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 序号底色：1 红 / 2 黄 / 3 绿 / 其余灰。 */
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFF705E)
    2 -> Color(0xFFFFB900)
    3 -> Color(0xFFA8C500)
    else -> Color(0xFFCCCCCC)
}

/** 帖子卡片：作者行（头像/昵称/等级/时间/版块）+ 标题 + 摘要 + 封面图 + 三项统计。 */
@Composable
private fun ThreadCard(thread: Thread, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thread.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(38.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.authorName ?: "匿名",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!thread.level.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            thread.level,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.5.dp)) {
                    Text(
                        thread.timeText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textSecondary,
                    )
                    if (!thread.forumName.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            thread.forumName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.quoteBackground)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Text(
            thread.subject,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        if (!thread.excerpt.isNullOrBlank()) {
            Text(
                thread.excerpt,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (!thread.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = thread.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 11.dp),
        ) {
            StatItem("浏览", thread.viewCount)
            Spacer(Modifier.width(24.dp))
            StatItem("回复", thread.replyCount)
            Spacer(Modifier.width(24.dp))
            StatItem("点赞", thread.likeCount)
        }
    }
}

/** 卡片底部统计项：无图标的「标签 数值」，数值为空时不显示。 */
@Composable
private fun StatItem(label: String, value: String?) {
    val colors = LocalMtfColors.current
    if (value.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textHint,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textSecondary,
        )
    }
}

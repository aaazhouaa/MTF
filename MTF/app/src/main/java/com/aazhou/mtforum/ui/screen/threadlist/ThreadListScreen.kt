package com.aazhou.mtforum.ui.screen.threadlist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.theme.accentTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    fid: String,
    title: String,
    onBack: () -> Unit,
    onThreadClick: (tid: String) -> Unit,
    onNewPost: () -> Unit,
) {
    val viewModel: ThreadListViewModel = appViewModel { ThreadListViewModel(fid, it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(com.aazhou.mtforum.R.drawable.ic_back),
                            contentDescription = "返回",
                        )
                    }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPost) {
                Icon(
                    painterResource(com.aazhou.mtforum.R.drawable.ic_post_edit),
                    contentDescription = "发帖",
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val data = current.data
                if (data.threads.isEmpty()) {
                    EmptyBox("本页没有主题", Modifier.padding(padding))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                        if (data.announcements.isNotEmpty()) {
                            item {
                                Text(
                                    "公告",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accentTextColor(),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            items(data.announcements, key = { "ann-${it.tid}" }) { thread ->
                                ThreadRow(thread) { onThreadClick(thread.tid) }
                            }
                            item { HorizontalDivider() }
                        }
                        items(data.threads, key = { it.tid }) { thread ->
                            ThreadRow(thread) { onThreadClick(thread.tid) }
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.prevPage() },
                                    enabled = viewModel.page > 1,
                                ) { Text("上一页") }
                                Text(
                                    "第 ${viewModel.page} 页",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                TextButton(
                                    onClick = { viewModel.nextPage() },
                                    enabled = data.hasNext,
                                ) { Text("下一页") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: Thread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        AsyncImage(
            model = thread.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = thread.subject,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!thread.excerpt.isNullOrBlank()) {
                Text(
                    text = thread.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = thread.authorName ?: "匿名",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!thread.timeText.isNullOrBlank()) {
                    Text(
                        text = " · ${thread.timeText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!thread.replyCount.isNullOrBlank()) {
                    Text(
                        text = " · 回复 ${thread.replyCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

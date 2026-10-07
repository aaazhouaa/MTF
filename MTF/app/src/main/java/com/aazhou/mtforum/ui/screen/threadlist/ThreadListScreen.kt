package com.aazhou.mtforum.ui.screen.threadlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.ThreadCard
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
    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }

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
                                AnnouncementRow(thread) { onThreadClick(thread.tid) }
                            }
                            item { HorizontalDivider() }
                        }
                        items(data.threads, key = { it.tid }) { thread ->
                            // 列表本身已限定在当前版块，卡片不再重复显示版块名
                            ThreadCard(
                                thread = thread,
                                onClick = { onThreadClick(thread.tid) },
                                onImageClick = openImage,
                                showForumName = false,
                            )
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

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

/**
 * 公告行：置顶帖只解析出标题（无作者/时间/统计），套卡片会留下一整排空位，
 * 故保持紧凑的单行形态，与「公告」标题一起作为版块页的公告区。
 */
@Composable
private fun AnnouncementRow(thread: Thread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = thread.subject,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

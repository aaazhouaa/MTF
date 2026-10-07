package com.aazhou.mtforum.ui.screen.threadlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.RankCard
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.animateToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import kotlinx.coroutines.launch

/** 版块页：公告卡（同首页热帖排行）+ 帖子卡片流，滑到底自动追加下一页。 */
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
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.animateToTop() }
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
                        contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                        if (data.announcements.isNotEmpty()) {
                            item(key = "announcements") {
                                RankCard("公告", data.announcements, onThreadClick)
                            }
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

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

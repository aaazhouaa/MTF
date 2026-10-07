package com.aazhou.mtforum.ui.screen.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.common.ActionHint
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onThreadClick: (tid: String) -> Unit) {
    val viewModel: SearchViewModel = appViewModel { SearchViewModel(it.repository, it.contentCache) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    // 点击卡片配图一律进内置全屏查看器；urls 为本卡全部配图，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        // 提示走中偏上的轻卡片：应用底栏是浮层，贴底的 Snackbar 会被它整块压住
        snackbarHost = {
            Box(Modifier.fillMaxSize()) {
                ActionHint(
                    actionMessage,
                    { viewModel.consumeActionMessage() },
                    Modifier.align(BiasAlignment(0f, -0.45f)),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("输入关键词") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.search(input) }) {
                    Icon(
                        painterResource(com.aazhou.mtforum.R.drawable.ic_search_new),
                        contentDescription = "搜索",
                    )
                }
            }
            when (val current = state) {
                null -> EmptyBox("输入关键词开始搜索")
                is UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(current.message, { viewModel.search(input) })
                is UiState.Success -> {
                    val result = current.data
                    if (result.threads.isEmpty()) {
                        EmptyBox("没有找到相关主题")
                    } else {
                        // 滑到接近末尾时自动追加下一页
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                val total = listState.layoutInfo.totalItemsCount
                                total > 0 && last >= total - 3
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) viewModel.loadMore()
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(result.threads, key = { it.tid }) { thread ->
                                ThreadCard(
                                    thread = thread,
                                    onClick = { onThreadClick(thread.tid) },
                                    onImageClick = openImage,
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

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

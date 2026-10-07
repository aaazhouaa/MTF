package com.aazhou.mtforum.ui.screen.guide

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.ActionHint
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

/** 导读-最新发表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onThreadClick: (tid: String) -> Unit) {
    val viewModel: GuideViewModel = appViewModel { GuideViewModel(it.repository, it.contentCache) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

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
                title = { Text("导读") },
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
                val data = current.data
                if (data.threads.isEmpty()) {
                    EmptyBox("暂无主题", Modifier.padding(padding))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                    ) {
                        items(data.threads, key = { it.tid }) { thread ->
                            GuideRow(thread) { onThreadClick(thread.tid) }
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.load(viewModel.page - 1) },
                                    enabled = viewModel.page > 1,
                                ) { Text("上一页") }
                                Text("第 ${viewModel.page} 页", modifier = Modifier.padding(horizontal = 8.dp))
                                TextButton(
                                    onClick = { viewModel.load(viewModel.page + 1) },
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
private fun GuideRow(thread: Thread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        AsyncImage(
            model = thread.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape).dimImageInDark(),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = thread.subject,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text(thread.authorName ?: "匿名", style = MaterialTheme.typography.labelSmall)
                if (!thread.timeText.isNullOrBlank()) {
                    Text(
                        " · ${thread.timeText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

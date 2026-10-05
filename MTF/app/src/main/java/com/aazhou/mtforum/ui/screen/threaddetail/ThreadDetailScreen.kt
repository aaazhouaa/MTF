package com.aazhou.mtforum.ui.screen.threaddetail

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.PostContent
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    tid: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ThreadDetailViewModel = appViewModel { ThreadDetailViewModel(tid, it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replyState by viewModel.replyState.collectAsStateWithLifecycle()

    var replyText by remember { mutableStateOf("") }

    // 回复成功/失败提示由 Snackbar 承担
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(replyState) {
        when (val r = replyState) {
            is ThreadDetailViewModel.ReplyState.Success -> {
                replyText = ""
                snackbarHostState.showSnackbar("回复成功")
                viewModel.consumeReplyState()
                viewModel.load()
            }
            is ThreadDetailViewModel.ReplyState.Error -> {
                snackbarHostState.showSnackbar(r.message)
                viewModel.consumeReplyState()
            }
            else -> Unit
        }
    }

    val openImage: (String) -> Unit = { url ->
        if (url.startsWith("http")) context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = (state as? UiState.Success)?.data?.subject ?: "帖子"
                    Text(titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state is UiState.Success) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("回复楼主…") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    if (replyState is ThreadDetailViewModel.ReplyState.Sending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        IconButton(
                            onClick = { if (replyText.isNotBlank()) viewModel.reply(replyText) },
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val data = current.data
                if (data.posts.isEmpty()) {
                    EmptyBox("没有内容", Modifier.padding(padding))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        items(data.posts, key = { it.pid }) { post ->
                            PostItem(post, onImageClick = openImage)
                            HorizontalDivider()
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(
                                    onClick = { viewModel.load(viewModel.page - 1) },
                                    enabled = viewModel.page > 1,
                                ) { Text("上一页") }
                                Text("第 ${viewModel.page} / ${data.totalPages} 页")
                                TextButton(
                                    onClick = { viewModel.load(viewModel.page + 1) },
                                    enabled = viewModel.page < data.totalPages,
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
private fun PostItem(post: Post, onImageClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = post.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.authorName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (post.isOp) {
                        Text(
                            " 楼主",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!post.level.isNullOrBlank()) {
                        Text(
                            "  ${post.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row {
                    if (!post.timeText.isNullOrBlank()) {
                        Text(
                            post.timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!post.location.isNullOrBlank()) {
                        Text(
                            "  ${post.location}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PostContent(html = post.contentHtml, onImageClick = onImageClick)
    }
}

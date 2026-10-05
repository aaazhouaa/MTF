package com.aazhou.mtforum.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onThreadClick: (tid: String) -> Unit) {
    val viewModel: SearchViewModel = appViewModel { SearchViewModel(it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("搜索") })
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
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
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
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(result.threads, key = { it.tid }) { thread ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onThreadClick(thread.tid) }
                                        .padding(16.dp),
                                ) {
                                    Text(
                                        thread.subject,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!thread.authorName.isNullOrBlank()) {
                                        Text(
                                            thread.authorName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { viewModel.search(viewModel.keyword, viewModel.page - 1) },
                                        enabled = viewModel.page > 1,
                                    ) { Text("上一页") }
                                    Text("第 ${viewModel.page} 页", modifier = Modifier.padding(horizontal = 8.dp))
                                    TextButton(
                                        onClick = { viewModel.search(viewModel.keyword, viewModel.page + 1) },
                                        enabled = result.hasNext,
                                    ) { Text("下一页") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

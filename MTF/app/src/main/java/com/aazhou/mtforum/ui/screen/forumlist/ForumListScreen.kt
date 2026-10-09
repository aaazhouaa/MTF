package com.aazhou.mtforum.ui.screen.forumlist

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.theme.accentTextColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(onForumClick: (fid: String, name: String) -> Unit) {
    val viewModel: ForumListViewModel = appViewModel { ForumListViewModel(it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MT论坛") },
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
            is UiState.Success -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                current.data.forEach { group ->
                    item(key = "group-${group.name}") {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = accentTextColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(group.forums, key = { it.fid }) { forum ->
                        ForumRow(forum, onClick = { onForumClick(forum.fid, forum.name) })
                        HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumRow(forum: Forum, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (forum.icon != null) {
            AsyncImage(
                model = smallImageRequest(forum.icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(forum.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

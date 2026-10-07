package com.aazhou.mtforum.ui.screen.postedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditScreen(fid: String, forumName: String, onBack: () -> Unit, onPosted: () -> Unit) {
    val viewModel: PostEditViewModel = appViewModel { PostEditViewModel(fid, it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is PostEditViewModel.PostState.Success -> {
                snackbarHostState.showSnackbar("发布成功")
                viewModel.consume()
                onPosted()
            }
            is PostEditViewModel.PostState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.consume()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发帖 · $forumName") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (!viewModel.canPost) {
                Text("请先登录后再发帖", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("内容") },
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.submit(subject, message) },
                enabled = state !is PostEditViewModel.PostState.Sending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is PostEditViewModel.PostState.Sending) "发布中…" else "发布")
            }
        }
    }
}

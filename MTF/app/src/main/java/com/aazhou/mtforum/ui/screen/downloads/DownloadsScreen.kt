package com.aazhou.mtforum.ui.screen.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.DownloadedThread
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val viewModel: DownloadsViewModel = appViewModel {
        DownloadsViewModel(it.downloadStore, it.archiveStore)
    }
    val items by viewModel.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<DownloadedThread?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "已下载的帖子",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBar,
                    titleContentColor = bodyTextColor(),
                ),
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        containerColor = colors.background,
        contentColor = bodyTextColor(),
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无下载的帖子",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = bodyTextColor(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在帖子详情页右上角「更多」菜单中点击「下载」即可离线保存",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.textHint,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = bottomBarContentPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "location-note") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.quoteBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "文件保存位置: Download / MTF 目录",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "共 ${items.size} 篇",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(items, key = { it.tid }) { entry ->
                    DownloadedRow(
                        entry = entry,
                        onOpen = { onOpenThread(entry.tid) },
                        onDelete = { deleteTarget = entry },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        MtfConfirmDialog(
            title = "删除离线副本",
            message = "《${target.subject}》的离线副本将被删除，且不可恢复，如需浏览需要重新下载。",
            confirmText = "确认删除",
            cancelText = "取消",
            onDismissRequest = { deleteTarget = null },
            onConfirm = {
                viewModel.delete(target.tid)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun DownloadedRow(
    entry: DownloadedThread,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(
                if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // 左侧下载徽章
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = if (isDark) 0.18f else 0.10f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.subject,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                ),
                color = bodyTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 版块胶囊
                if (!entry.forumName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.quoteBackground)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = entry.forumName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = primary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val metaParts = buildList {
                    entry.replyCount?.let { add("$it 评论") }
                    add(formatSize(entry.sizeBytes))
                    add(formatSavedAt(entry.savedAt))
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "删除",
                tint = colors.iconSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatSavedAt(millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    return if (sameYear) {
        String.format(
            "%02d-%02d %02d:%02d",
            then.get(java.util.Calendar.MONTH) + 1,
            then.get(java.util.Calendar.DAY_OF_MONTH),
            then.get(java.util.Calendar.HOUR_OF_DAY),
            then.get(java.util.Calendar.MINUTE),
        )
    } else {
        String.format(
            "%d-%02d-%02d",
            then.get(java.util.Calendar.YEAR),
            then.get(java.util.Calendar.MONTH) + 1,
            then.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

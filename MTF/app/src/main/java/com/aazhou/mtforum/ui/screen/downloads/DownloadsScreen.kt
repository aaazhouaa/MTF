package com.aazhou.mtforum.ui.screen.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.accentTextColor
import kotlinx.coroutines.launch

/** 「已下载的帖子」：保存位置固定为下载目录下的 MTF 文件夹（写入时自动创建）。 */
private const val SAVE_LOCATION_NOTE = "保存位置：Download / MTF 目录下"

/**
 * 已下载帖子列表页。
 *
 * 点条目进离线详情页（格式与正常看帖一致）；删除需二次确认：副本不可恢复，
 * 且应用不会代替用户清理，删了就只能重新下载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
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
                title = { Text("已下载的帖子") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                        )
                    }
                },
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        containerColor = LocalMtfColors.current.background,
        contentColor = bodyTextColor(),
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyBox("还没有下载的帖子", Modifier)
                    Text(
                        "在帖子详情「更多」菜单里点下载即可保存",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = LocalMtfColors.current.textHint,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = bottomBarContentPadding(),
                ),
            ) {
                item(key = "location-note") {
                    Text(
                        SAVE_LOCATION_NOTE,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = LocalMtfColors.current.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除已下载的帖子") },
            text = {
                Text("《${target.subject}》的离线副本将被删除，且不可恢复，需要重新下载。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.tid)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 单条已下载帖子：标题 + 版块/评论数/保存时间/大小，右侧删除图标。 */
@Composable
private fun DownloadedRow(
    entry: DownloadedThread,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp, top = 13.dp, bottom = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground),
            ) {
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    tint = accentTextColor(),
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = bodyTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    entry.forumName?.takeIf { it.isNotBlank() }?.let(::add)
                    entry.replyCount?.let { add("$it 条评论") }
                    add(formatSize(entry.sizeBytes))
                    add(formatSavedAt(entry.savedAt))
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "删除",
                    tint = colors.iconSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 保存时间：同一年省略年份，列表里只关心近期。 */
private fun formatSavedAt(millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    return if (sameYear) {
        String.format("%02d-%02d %02d:%02d",
            then.get(java.util.Calendar.MONTH) + 1,
            then.get(java.util.Calendar.DAY_OF_MONTH),
            then.get(java.util.Calendar.HOUR_OF_DAY),
            then.get(java.util.Calendar.MINUTE))
    } else {
        String.format("%d-%02d-%02d",
            then.get(java.util.Calendar.YEAR),
            then.get(java.util.Calendar.MONTH) + 1,
            then.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

/** 文件大小：HTML 体积通常在几十 KB 到几 MB。 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

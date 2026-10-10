package com.aazhou.mtforum.ui.screen.logs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.BuildConfig
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.log.LogEntry
import com.aazhou.mtforum.data.log.LogLevel
import com.aazhou.mtforum.data.log.toExportText
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfContentDialog
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.saveTextDocument
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    onOpenLog: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = context.container()
    val viewModel: LogsViewModel = appViewModel { LogsViewModel(it.logStore) }
    val entries by viewModel.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val logLevel by container.logSettings.level
        .collectAsStateWithLifecycle(initialValue = LogLevel.INFO)
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    var clearOpen by remember { mutableStateOf(false) }
    var levelOpen by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    val shown = if (filter == null) entries else entries.filter { it.level == filter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "日志",
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
                actions = {
                    // 日志级别设置按钮
                    IconButton(onClick = { levelOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "日志等级",
                            tint = colors.iconSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 导出
                    IconButton(onClick = {
                        val text = shown.toExportText(BuildConfig.VERSION_NAME, logLevel)
                        scope.launch {
                            val name = logFileName()
                            val ok = withContext(Dispatchers.IO) {
                                saveTextDocument(context, name, text) != null
                            }
                            AppToast.show(if (ok) "已导出到 下载/MTF" else "导出失败")
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "导出",
                            tint = colors.iconSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 分享
                    IconButton(onClick = { shareLogs(context, shown, logLevel) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = "分享",
                            tint = colors.iconSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 清空
                    IconButton(onClick = { clearOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "清空",
                            tint = colors.iconSecondary,
                            modifier = Modifier.size(20.dp),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 顶层状态卡片与筛选栏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.topBar)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                // 等级说明条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.quoteBackground)
                        .clickable { levelOpen = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "记录等级: ${logLevel.label}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "只记录该等级及以上，崩溃始终记录",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textHint,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = colors.textHint,
                        modifier = Modifier.size(12.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 分类筛选 Tab 胶囊条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogFilterTab(
                        text = "全部 (${entries.size})",
                        selected = filter == null,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { filter = null },
                        modifier = Modifier.weight(1f),
                    )
                    LogLevel.entries.forEach { level ->
                        val count = entries.count { it.level == level }
                        LogFilterTab(
                            text = "${level.label} ($count)",
                            selected = filter == level,
                            color = levelColor(level),
                            onClick = { filter = level },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyBox(
                            if (entries.isEmpty()) "暂无日志记录" else "该等级下没有日志",
                            Modifier,
                        )
                        Text(
                            "网络请求、风控拦截与运行时崩溃会自动记录于此",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textHint,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = bottomBarContentPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { entry ->
                        LogRow(
                            entry = entry,
                            onClick = { onOpenLog(entry.id) },
                            onLongClick = { deleteId = entry.id },
                        )
                    }
                }
            }
        }
    }

    if (levelOpen) {
        MtfContentDialog(
            title = "日志等级设置",
            subtitle = "设为较高等级后，更低等级的日常日志将不再写入本地存储",
            onDismissRequest = { levelOpen = false },
        ) {
            LogLevel.entries.forEach { level ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            levelOpen = false
                            scope.launch {
                                container.logSettings.setLevel(level)
                                container.logger.info("Settings", "日志等级已设为 ${level.label}")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    RadioButton(selected = level == logLevel, onClick = null)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(
                            level.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = levelColor(level),
                        )
                    }
                }
            }
        }
    }

    if (clearOpen) {
        MtfConfirmDialog(
            title = "清空日志",
            message = "将删除全部本地日志，已导出的文件不受影响。",
            confirmText = "确认清空",
            cancelText = "取消",
            onDismissRequest = { clearOpen = false },
            onConfirm = {
                clearOpen = false
                viewModel.clear()
                AppToast.show("日志已清空")
            },
        )
    }

    if (deleteId != null) {
        MtfConfirmDialog(
            title = "删除日志记录",
            message = "删除后不可恢复，已导出的文件不受影响。",
            confirmText = "确认删除",
            cancelText = "取消",
            onDismissRequest = { deleteId = null },
            onConfirm = {
                val id = deleteId ?: return@MtfConfirmDialog
                deleteId = null
                viewModel.remove(id)
                AppToast.show("已删除该条日志")
            },
        )
    }
}

@Composable
private fun LogFilterTab(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) color.copy(alpha = if (isDark) 0.22f else 0.12f)
                else colors.quoteBackground,
            )
            .then(
                if (selected) Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) color else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(entry: LogEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val lvlColor = levelColor(entry.level)

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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 等级徽章
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lvlColor.copy(alpha = if (isDark) 0.18f else 0.12f)),
        ) {
            Text(
                entry.level.label.take(1),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.ExtraBold,
                color = lvlColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    entry.tag,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTimestamp(entry.time),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textHint,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.textHint.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}

internal fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> Color(0xFF0284C7)
    LogLevel.WARN -> Color(0xFFF59E0B)
    LogLevel.ERROR -> Color(0xFFEF4444)
}

private fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}

internal fun logFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "MTF_log_$stamp.txt"
}

private fun shareLogs(context: Context, entries: List<LogEntry>, level: LogLevel) {
    val text = entries.toExportText(BuildConfig.VERSION_NAME, level)
    val cacheFile = File(context.cacheDir, "mtf_share_${System.currentTimeMillis()}.txt")
    cacheFile.writeText(text)
    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        cacheFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享日志"))
}

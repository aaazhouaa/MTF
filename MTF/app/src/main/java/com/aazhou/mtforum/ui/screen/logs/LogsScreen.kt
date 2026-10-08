package com.aazhou.mtforum.ui.screen.logs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.BiasAlignment
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
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.HintToast
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.saveTextDocument
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页：列表 + 等级筛选 + 导出/分享/清空，点条目进详情。
 *
 * 顶栏的导出与分享作用于「当前筛选可见的条目」，缺省不打筛选时即全部日志。
 */
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    var clearOpen by remember { mutableStateOf(false) }
    var levelOpen by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    val shown = if (filter == null) entries else entries.filter { it.level == filter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { levelOpen = true }) {
                        Icon(painterResource(R.drawable.ic_log_level), contentDescription = "日志等级")
                    }
                    IconButton(
                        onClick = {
                            val text = shown.toExportText(BuildConfig.VERSION_NAME, logLevel)
                            scope.launch {
                                val name = logFileName()
                                val ok = withContext(Dispatchers.IO) {
                                    saveTextDocument(context, name, text) != null
                                }
                                hint = if (ok) "已导出到 下载/MTF" else "导出失败"
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = "导出")
                    }
                    IconButton(
                        onClick = { shareLogs(context, shown, logLevel) },
                    ) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = "分享")
                    }
                    TextButton(onClick = { clearOpen = true }) { Text("清空") }
                },
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { listState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        // 宿主槽只覆盖内容区：提示卡片落在顶栏下方，位置与其它页一致
        snackbarHost = {
            Box(Modifier.fillMaxSize()) {
                HintToast(hint, { hint = null }, Modifier.align(BiasAlignment(0f, -0.45f)))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "当前记录等级 ${logLevel.label}：只记录该等级及更严重的日志，崩溃始终记录",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textHint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("全部") },
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = filter == level,
                        onClick = { filter = level },
                        label = { Text(level.label) },
                    )
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyBox(
                            if (entries.isEmpty()) "暂无日志" else "该等级下没有日志",
                            Modifier,
                        )
                        Text(
                            "网络风控、请求失败、图片加载失败与崩溃都会自动记录",
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
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
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
        AlertDialog(
            onDismissRequest = { levelOpen = false },
            title = { Text("日志等级") },
            text = {
                Column {
                    Text(
                        "设为 Warn 或 Error 后，更低等级的日志不再写入本地；" +
                            "网络风控、请求失败、图片加载失败与崩溃都会记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.size(10.dp))
                    LogLevel.entries.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    levelOpen = false
                                    scope.launch {
                                        container.logSettings.setLevel(level)
                                        container.logger.info("Settings", "日志等级已设为 ${level.label}")
                                    }
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(selected = level == logLevel, onClick = null)
                            Spacer(Modifier.size(6.dp))
                            Text(level.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { levelOpen = false }) { Text("完成") }
            },
        )
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("清空日志") },
            text = { Text("将删除全部本地日志，导出的文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    clearOpen = false
                    viewModel.clear()
                    hint = "日志已清空"
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { clearOpen = false }) { Text("取消") }
            },
        )
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("删除这条日志") },
            text = { Text("删除后不可恢复，导出的文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteId ?: return@TextButton
                    deleteId = null
                    viewModel.remove(id)
                    hint = "已删除该条日志"
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("取消") }
            },
        )
    }

    // 导出/清空的结果提示：内容区中央偏上的轻卡片，避开列表首屏
}

/** 单条日志：等级色 + tag + 摘要 + 时间；点开看全文与堆栈，长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(entry: LogEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.quoteBackground),
        ) {
            Text(
                entry.level.label.take(1),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = levelColor(entry.level),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.level.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = levelColor(entry.level),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.tag,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    rememberRowTime(entry.time),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textHint,
                )
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = bodyTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!entry.detail.isNullOrBlank()) {
                Text(
                    "含异常堆栈",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textHint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.textHint,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 等级配色：Warn 借金色、Error 用主题错误色，Info 退到次要文字色。 */
@Composable
internal fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> LocalMtfColors.current.textSecondary
    LogLevel.WARN -> LocalMtfColors.current.gold
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

/** 行内时间只到分秒：日期在导出文本里，列表看重近期条目。 */
@Composable
private fun rememberRowTime(millis: Long): String {
    val format = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    return format.format(Date(millis))
}

/** 导出/分享共用的文件名：同一秒内重复导出也能区分。 */
internal fun logFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "MTF-log-$stamp.log"
}

/**
 * 分享日志：先落 cacheDir/logs 再经 FileProvider 授权读出。
 *
 * 目录必须在 res/xml/file_paths.xml 里有对应 cache-path，否则拿不到 uri。
 */
private fun shareLogs(context: Context, entries: List<LogEntry>, level: LogLevel) {
    val dir = File(context.cacheDir, "logs").apply { mkdirs() }
    val file = File(dir, logFileName()).apply {
        writeText(entries.toExportText(BuildConfig.VERSION_NAME, level))
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享日志"))
}

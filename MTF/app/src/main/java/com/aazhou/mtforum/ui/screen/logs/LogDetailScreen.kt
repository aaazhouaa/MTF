package com.aazhou.mtforum.ui.screen.logs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.BuildConfig
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.log.LogEntry
import com.aazhou.mtforum.data.log.toText
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.HintToast
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.saveTextDocument
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单条日志详情：正文 + 异常堆栈（等宽字体），可单独分享或导出成文件。
 *
 * 条目可能已被「清空」，此时显示空态而不是崩溃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    id: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LogDetailViewModel = appViewModel { LogDetailViewModel(it.logStore, id) }
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val colors = LocalMtfColors.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "返回")
                    }
                },
                actions = {
                    val current = entry
                    if (current != null) {
                        IconButton(onClick = { shareEntry(context, current) }) {
                            Icon(painterResource(R.drawable.ic_share), contentDescription = "分享此条")
                        }
                        IconButton(
                            onClick = { exportEntry(context, current, logFileName(), scope) { hint = it } },
                        ) {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = "导出此条")
                        }
                    }
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
        val current = entry
        if (current == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyBox("日志不存在或已被清空", Modifier)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.quoteBackground),
                ) {
                    Text(
                        current.level.label.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = levelColor(current.level),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${current.level.label} · ${current.tag}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = bodyTextColor(),
                    )
                    Text(
                        formatFullTime(current.time),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                }
            }
            Text(
                current.message,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = bodyTextColor(),
                modifier = Modifier.padding(top = 16.dp),
            )
            current.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    "异常堆栈",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = bodyTextColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.quoteBackground)
                        .padding(12.dp),
                )
            }
        }
    }
}

/** 分享单条日志：正文与堆栈一起，纯文本即可，无需落文件。 */
private fun shareEntry(context: Context, entry: LogEntry) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "MTF v${BuildConfig.VERSION_NAME}\n${entry.toText()}")
    }
    context.startActivity(Intent.createChooser(intent, "分享此条日志"))
}

/** 导出单条日志到「下载/MTF」，结果经 [hint] 回调给界面。 */
private fun exportEntry(
    context: Context,
    entry: LogEntry,
    fileName: String,
    scope: CoroutineScope,
    hint: (String) -> Unit,
) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            saveTextDocument(context, fileName, entry.toText()) != null
        }
        hint(if (ok) "已导出到 下载/MTF" else "导出失败")
    }
}

/** 详情页的完整时间：精确到毫秒，与导出文本同一格式。 */
private fun formatFullTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))

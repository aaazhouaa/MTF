package com.aazhou.mtforum.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.aazhou.mtforum.data.log.LogLevel
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.ThemePresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val container = context.container()
    val themeStore = container.themeStore
    val scope = rememberCoroutineScope()
    val themeIndex by themeStore.themeIndex.collectAsStateWithLifecycle(initialValue = DefaultThemeIndex)
    val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
    val dimImagesInDark by themeStore.dimImagesInDark
        .collectAsStateWithLifecycle(initialValue = true)
    val colors = LocalMtfColors.current
    val scrollState = rememberScrollState()
    // 清理确认框
    var clearCacheOpen by remember { mutableStateOf(false) }
    // 缓存占用：JSON 缓存 + Coil 图片磁盘缓存，IO 上算，清理后重新触发
    var cacheTick by remember { mutableStateOf(0) }
    val cacheBytes by produceState(initialValue = 0L, cacheTick) {
        value = withContext(Dispatchers.IO) {
            val json = container.contentCache.sizeBytes()
            val images = context.imageLoader.diskCache?.size ?: 0L
            json + images
        }
    }
    // 日志等级与条数：等级改完立刻反映在副标题上，条数每次回到本页重新算
    val logLevel by container.logSettings.level
        .collectAsStateWithLifecycle(initialValue = LogLevel.INFO)
    var logTick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { logTick++ }
    val logCount by produceState(initialValue = 0, logTick) {
        value = withContext(Dispatchers.IO) { container.logStore.entries().size }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { scrollState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        containerColor = colors.background,
        // 见 UserProfileScreen：语义色取不到 contentColorFor 时会退回旧值
        contentColor = bodyTextColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("主题色彩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ThemePresets.indices.chunked(3).forEach { rowIndices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowIndices.forEach { index ->
                        ThemeColorCell(
                            name = ThemePresets[index].name,
                            color = ThemePresets[index].primaryDay,
                            selected = index == themeIndex,
                            onClick = { scope.launch { themeStore.setThemeIndex(index) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 补足末行空位，保持三列对齐
                    repeat(3 - rowIndices.size) { Box(Modifier.weight(1f)) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("跟随系统深色模式", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后固定为浅色，可用首页顶栏按钮切深色",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Switch(
                    checked = darkOverride == null,
                    onCheckedChange = { follow -> scope.launch { themeStore.setDarkOverride(if (follow) null else false) } },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("深色模式压暗图片", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后帖子配图与全屏查看按原始亮度显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Switch(
                    checked = dimImagesInDark,
                    onCheckedChange = { dim -> scope.launch { themeStore.setDimImagesInDark(dim) } },
                )
            }

            Text(
                "MTF v${com.aazhou.mtforum.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SettingsRow(
                title = "日志",
                subtitle = "当前等级 ${logLevel.label} · ${formatLogCount(logCount)}",
                onClick = onOpenLogs,
            )

            // 清理缓存：JSON 内容缓存与 Coil 图片磁盘缓存一起清，清完图片需重新下载
            SettingsRow(
                title = "清理缓存",
                subtitle = "已占用 ${formatCacheSize(cacheBytes)}",
                onClick = { clearCacheOpen = true },
            )
        }
    }

    if (clearCacheOpen) {
        AlertDialog(
            onDismissRequest = { clearCacheOpen = false },
            title = { Text("清理缓存") },
            text = {
                Text("将清除本地缓存的帖子内容与已下载的图片，下次浏览需要重新下载。")
            },
            confirmButton = {
                TextButton(onClick = {
                    clearCacheOpen = false
                    scope.launch {
                        container.contentCache.clear()
                        val loader = context.imageLoader
                        withContext(Dispatchers.IO) {
                            loader.diskCache?.clear()
                        }
                        loader.memoryCache?.clear()
                        cacheTick++
                        container.logger.info("Settings", "已清理缓存，清理前占用 ${formatCacheSize(cacheBytes)}")
                        AppToast.show("缓存已清理")
                    }
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheOpen = false }) { Text("取消") }
            },
        )
    }
}

/** 缓存占用的可读化展示：不足 1MB 按 KB，不足 1GB 按 MB。 */
private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0 -> "0"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

/** 日志条数的展示：没有日志时说明原因，避免副标题出现「0 条」这种无信息量文案。 */
private fun formatLogCount(count: Int): String = if (count <= 0) "暂无日志" else "$count 条"

/** 设置页的统一条目行：主标题 + 副标题（当前值或说明），整行可点。 */
@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ThemeColorCell(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painterResource(com.aazhou.mtforum.R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

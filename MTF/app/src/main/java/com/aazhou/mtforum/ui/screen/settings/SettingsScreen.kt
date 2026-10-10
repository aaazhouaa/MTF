package com.aazhou.mtforum.ui.screen.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.log.LogLevel
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.ThemePresets
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.container()
    val themeStore = container.themeStore
    val scope = rememberCoroutineScope()
    val themeIndex by themeStore.themeIndex.collectAsStateWithLifecycle(initialValue = DefaultThemeIndex)
    val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
    val dimImagesInDark by themeStore.dimImagesInDark
        .collectAsStateWithLifecycle(initialValue = true)
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val scrollState = rememberScrollState()

    var clearCacheOpen by remember { mutableStateOf(false) }
    var cacheTick by remember { mutableStateOf(0) }
    val cacheBytes by produceState(initialValue = 0L, cacheTick) {
        value = withContext(Dispatchers.IO) {
            val json = container.contentCache.sizeBytes()
            val images = context.imageLoader.diskCache?.size ?: 0L
            val smileys = container.smileyStore.approximateBytes()
            json + images + smileys
        }
    }

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
                title = {
                    Text(
                        "设置",
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
                    scope.launch { scrollState.scrollToTop() }
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
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 分组 1：主题与显示
            SettingsSection(title = "主题与显示") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                ) {
                    Text(
                        "主题色彩",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                        ),
                        color = bodyTextColor(),
                    )
                    Spacer(Modifier.height(14.dp))
                    ThemePresets.indices.chunked(3).forEachIndexed { rowIndex, rowIndices ->
                        if (rowIndex > 0) Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
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
                            repeat(3 - rowIndices.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }

                HorizontalDivider(color = colors.divider.copy(alpha = 0.6f), thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = "跟随系统深色模式",
                    subtitle = "关闭后固定为浅色，可用首页顶栏按钮切深色",
                    checked = darkOverride == null,
                    onCheckedChange = { follow ->
                        scope.launch { themeStore.setDarkOverride(if (follow) null else false) }
                    },
                )

                HorizontalDivider(color = colors.divider.copy(alpha = 0.6f), thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = "深色模式压暗图片",
                    subtitle = "关闭后帖子配图与全屏查看按原始亮度显示",
                    checked = dimImagesInDark,
                    onCheckedChange = { dim ->
                        scope.launch { themeStore.setDimImagesInDark(dim) }
                    },
                )
            }

            // 分组 2：系统与诊断
            SettingsSection(title = "系统与诊断") {
                SettingsActionRow(
                    icon = painterResource(R.drawable.ic_log),
                    title = "运行日志",
                    subtitle = "当前等级 ${logLevel.label} · ${formatLogCount(logCount)}",
                    onClick = onOpenLogs,
                )

                HorizontalDivider(color = colors.divider.copy(alpha = 0.6f), thickness = 0.5.dp)

                SettingsActionRow(
                    icon = painterResource(R.drawable.ic_delete),
                    title = "清理缓存",
                    subtitle = "已占用 ${formatCacheSize(cacheBytes)}",
                    onClick = { clearCacheOpen = true },
                )
            }

            // 分组 3：关于应用
            SettingsSection(title = "关于") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MTF 论坛客户端",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                            ),
                            color = bodyTextColor(),
                        )
                        Text(
                            "简洁流畅的 Discuz! 移动端体验",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        "v${com.aazhou.mtforum.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.textHint,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (clearCacheOpen) {
        MtfConfirmDialog(
            title = "清理缓存",
            message = "将清除本地缓存的帖子内容与已下载的图片，以及表情数据与最近使用记录，下次浏览需要重新加载。",
            confirmText = "确认清理",
            cancelText = "取消",
            onDismissRequest = { clearCacheOpen = false },
            onConfirm = {
                clearCacheOpen = false
                scope.launch {
                    container.contentCache.clear()
                    container.smileyStore.clear()
                    val loader = context.imageLoader
                    withContext(Dispatchers.IO) {
                        loader.diskCache?.clear()
                    }
                    loader.memoryCache?.clear()
                    cacheTick++
                    container.logger.info("Settings", "已清理缓存，清理前占用 ${formatCacheSize(cacheBytes)}")
                    AppToast.show("缓存已清理")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .then(
                    if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(16.dp))
                    else Modifier
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp,
                ),
                color = bodyTextColor(),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.quoteBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp,
                ),
                color = bodyTextColor(),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.textHint,
            modifier = Modifier.size(16.dp),
        )
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
    val scale by animateFloatAsState(if (selected) 1.05f else 1.0f, label = "color_scale")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .scale(scale)
                .then(
                    if (selected) Modifier.border(2.5.dp, color.copy(alpha = 0.35f), CircleShape)
                    else Modifier
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (selected) color else colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatLogCount(count: Int): String = if (count <= 0) "暂无日志" else "$count 条"

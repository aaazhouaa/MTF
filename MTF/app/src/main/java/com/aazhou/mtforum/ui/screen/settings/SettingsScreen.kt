package com.aazhou.mtforum.ui.screen.settings

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.log.LogLevel
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfContentDialog
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
    val customArgb by themeStore.customColor.collectAsStateWithLifecycle(initialValue = null)
    val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
    val dimImagesInDark by themeStore.dimImagesInDark
        .collectAsStateWithLifecycle(initialValue = true)
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val scrollState = rememberScrollState()

    var clearCacheOpen by remember { mutableStateOf(false) }
    var colorPickerOpen by remember { mutableStateOf(false) }
    var cacheTick by remember { mutableStateOf(0) }
    val cacheBytes by produceState(initialValue = 0L, cacheTick) {
        value = withContext(Dispatchers.IO) {
            val json = container.contentCache.sizeBytes()
            val images = context.imageLoader.diskCache?.size ?: 0L
            val smileys = container.smileyStore.approximateBytes()
            val drafts = container.draftStore.sizeBytes()
            json + images + smileys + drafts
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
                    // 末尾多一格「自定义」：预设与自定义色二选一，故预设的选中态要排除自定义色生效中
                    val cellCount = ThemePresets.size + 1
                    (0 until cellCount).chunked(3).forEachIndexed { rowIndex, rowIndices ->
                        if (rowIndex > 0) Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            rowIndices.forEach { index ->
                                if (index < ThemePresets.size) {
                                    ThemeColorCell(
                                        name = ThemePresets[index].name,
                                        color = ThemePresets[index].primaryDay,
                                        selected = customArgb == null && index == themeIndex,
                                        onClick = { scope.launch { themeStore.setThemeIndex(index) } },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    CustomThemeColorCell(
                                        color = customArgb?.let { Color(it) },
                                        selected = customArgb != null,
                                        onClick = { colorPickerOpen = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
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
            message = "将清除本地缓存的帖子内容与已下载的图片、表情数据与最近使用记录，以及发帖草稿箱，下次浏览需要重新加载。",
            confirmText = "确认清理",
            cancelText = "取消",
            onDismissRequest = { clearCacheOpen = false },
            onConfirm = {
                clearCacheOpen = false
                scope.launch {
                    container.contentCache.clear()
                    container.draftStore.clear()
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

    if (colorPickerOpen) {
        // 取色盘从当前生效的主题色起手：自定义色，或预设的日间主色
        val initial = customArgb?.let { Color(it) }
            ?: ThemePresets.getOrElse(themeIndex) { ThemePresets[DefaultThemeIndex] }.primaryDay
        ColorPickerDialog(
            initialColor = initial,
            onDismiss = { colorPickerOpen = false },
            onConfirm = { picked ->
                colorPickerOpen = false
                scope.launch { themeStore.setCustomColor(picked.toArgb()) }
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

/** 取色盘的色相条渐变：每 10 度一个色标、饱和度拉满，色标太少会刷出可见色带。 */
private val HueSweep = List(37) { Color.hsv(it * 10f, 1f, 1f) }

/** 取色盘里一键点选的常用色：先定个大概，再到板子上微调。 */
private val PickerSwatches = listOf(
    Color(0xFF0D9488),
    Color(0xFF2563EB),
    Color(0xFF7C3AED),
    Color(0xFFDB2777),
    Color(0xFFDC2626),
    Color(0xFFEA580C),
    Color(0xFF16A34A),
    Color(0xFF475569),
)

/**
 * 自定义主题色格子：[color] 为空表示还没自定过，圆点铺彩虹渐变当取色盘入口；
 * 非空则显示当前自定义色，选中态与预设格一致。
 */
@Composable
private fun CustomThemeColorCell(
    color: Color?,
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
                    if (selected && color != null) {
                        Modifier.border(2.5.dp, color.copy(alpha = 0.35f), CircleShape)
                    } else {
                        Modifier
                    }
                )
                .padding(3.dp)
                .clip(CircleShape)
                .then(
                    if (color != null) {
                        Modifier.background(color)
                    } else {
                        Modifier.background(Brush.horizontalGradient(HueSweep), CircleShape)
                    }
                ),
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
            text = "自定义",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (selected && color != null) color else colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 点按取落点、按住拖动连续取色。
 *
 * 单用 detectDragGestures 不吃轻点（要先走过 slop 才算拖动），单用 detectTapGestures
 * 又会把按下消费掉、拖动收不到；故自己合成：按下即取，之后跟随拖动。
 */
@Composable
private fun Modifier.onPickGesture(pick: (Offset) -> Unit): Modifier {
    val current by rememberUpdatedState(pick)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            current(down.position)
            drag(down.id) { change -> current(change.position) }
        }
    }
}

/**
 * 取色盘：上面是饱和度/明度板，下面是色相条，均可点按或拖动；确认后回调当前颜色。
 *
 * HSV 三值用手势在控件内的占比直接换算，落点即所得，不需要输入框。
 */
@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val hsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val current = Color.hsv(hue, saturation, brightness)
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var hueBarSize by remember { mutableStateOf(IntSize.Zero) }

    // 坐标换算闭包读的是 State，手势过程中取到的都是最新值
    fun pickSatValue(position: Offset) {
        if (padSize.width == 0 || padSize.height == 0) return
        saturation = (position.x / padSize.width).coerceIn(0f, 1f)
        brightness = 1f - (position.y / padSize.height).coerceIn(0f, 1f)
    }

    fun pickHue(position: Offset) {
        if (hueBarSize.width == 0) return
        hue = (position.x / hueBarSize.width * 360f).coerceIn(0f, 360f)
    }

    // 点常用色：整颗色块的颜色直接搬成 HSV 三值
    fun pickSwatch(swatch: Color) {
        FloatArray(3).also { AndroidColor.colorToHSV(swatch.toArgb(), it) }.let {
            hue = it[0]
            saturation = it[1]
            brightness = it[2]
        }
    }

    MtfContentDialog(
        title = "自定义主题色",
        subtitle = "点按或拖动取色，确认后立即生效",
        onDismissRequest = onDismiss,
    ) {
        // 饱和度/明度板：横向白到纯色相，纵向透明到黑；光标就是落点
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(16.dp))
                .onSizeChanged { padSize = it }
                .onPickGesture { pickSatValue(it) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            if (padSize.width > 0) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (saturation * padSize.width).roundToInt() - 11.dp.roundToPx(),
                                ((1f - brightness) * padSize.height).roundToInt() - 11.dp.roundToPx(),
                            )
                        }
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(current),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 色相条：滑块是带白边的当前色圆点，位置按色相占比连续算
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .onSizeChanged { hueBarSize = it }
                .onPickGesture { pickHue(it) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(HueSweep))
            }
            if (hueBarSize.width > 0) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (hue / 360f * hueBarSize.width).roundToInt() - 12.dp.roundToPx(),
                                0,
                            )
                        }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(current),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 当前色预览与色值
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(current),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "#%06X".format(current.toArgb() and 0xFFFFFF),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = bodyTextColor(),
            )
        }

        Spacer(Modifier.height(14.dp))

        // 常用色：一格一个，点一下整套换过去，省得每次从板子上捞
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PickerSwatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(swatch)
                        .clickable { pickSwatch(swatch) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        val colors = LocalMtfColors.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground)
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Medium,
                    color = bodyTextColor(),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(current)
                    .clickable { onConfirm(current) },
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    // 底色可能是接近白色的亮色：白字会整个看不见，按明度换黑字
                    color = if (current.luminance() > 0.5f) Color.Black else Color.White,
                )
            }
        }
    }
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatLogCount(count: Int): String = if (count <= 0) "暂无日志" else "$count 条"

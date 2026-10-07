package com.aazhou.mtforum.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.theme.DarkImageDimAlpha
import com.aazhou.mtforum.ui.theme.LocalDimImagesInDark
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 手势可缩放的上限。 */
private const val MAX_SCALE = 5f

/** 单击判定阈值：抬起位置离按下处超过该距离就不算单击。 */
private const val TAP_SLOP = 24f

/** 顶部操作栏每次显示后停留的时长。 */
private const val CONTROLS_VISIBLE_MS = 1000L

/** 亮度高于该值的图片视为浅色，前景改用深色。 */
private const val LIGHT_IMAGE_LUMINANCE = 0.6f

/**
 * 全屏图片查看：黑底，多图左右翻页，双指缩放、放大后拖动、双击放大/还原，
 * 底部提供保存到相册 / 分享 / 用浏览器打开。
 *
 * 前景色随当前图片亮度自适应：浅色图用深色字、深色图用白色字，并给文字垫一层
 * 同调半透明底衬，避免白字压在浅色截图上、深字压在深色图上看不清。
 *
 * [PostContent] 传出的是整组图片地址与命中下标，故同组图片可连续翻看。
 */
@Composable
fun ImagePreview(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.lastIndex)) {
        urls.size
    }
    var busy by remember { mutableStateOf(false) }
    // 顶部操作栏（返回/页码/操作图标）默认隐藏；翻页、缩放、拖动等交互都会重新计时，
    // 显示 [CONTROLS_VISIBLE_MS] 后自动隐去。单击图片不受影响，始终关闭查看器。
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionTick) {
        if (interactionTick > 0) {
            controlsVisible = true
            delay(CONTROLS_VISIBLE_MS)
            controlsVisible = false
        }
    }
    // 翻页也算一次交互；首帧出现时不触发，保持默认隐藏
    var pageInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (pageInitialized) interactionTick++ else pageInitialized = true
    }
    // 每页图片的平均亮度色；翻页时按当前页取用，预加载页算好也能马上用上
    val pageColors = remember { mutableStateMapOf<Int, Color>() }
    val background = pageColors[pagerState.currentPage]

    BackHandler(onBack = onDismiss)

    // 预览必须盖住玻璃底栏：底栏与 NavHost 同在 AppRoot 的 Box 里、且绘制在 NavHost 之后，
    // 作为页面内 composable 的预览无论 fillMaxSize 到哪都被它压住。Popup 挂在窗口装饰层，
    // 绘制层级高于整个窗口内容；返回键交给内容里的 BackHandler，故关掉 popup 自身的返回拦截。
    Popup(
        onDismissRequest = null,
        properties = PopupProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(
                    url = urls[page],
                    active = pagerState.currentPage == page,
                    onBackgroundColor = { color -> pageColors[page] = color },
                    onTap = onDismiss,
                    onInteraction = { interactionTick++ },
                )
            }

            val foreground = foregroundFor(background)
            val pill = pillFor(background)
            // 操作图标底衬比正文底衬更实一些，图标在图上更分明
            val actionPill = elevatedPill(pill)

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(pill)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = foreground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (urls.size > 1) {
                        Text(
                            "${pagerState.currentPage + 1} / ${urls.size}",
                            color = foreground,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pill)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PreviewActionIcon(R.drawable.ic_download, "保存到相册", foreground, actionPill) {
                        runBusy(scope, { busy = it }, context, "保存失败") {
                            val url = urls[pagerState.currentPage]
                            val bytes = withContext(Dispatchers.IO) { downloadBytes(context, url) }
                            withContext(Dispatchers.IO) { saveToGallery(context, url, bytes) } != null
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PreviewActionIcon(R.drawable.ic_share, "分享", foreground, actionPill) {
                        runBusy(scope, { busy = it }, context, "分享失败") {
                            val url = urls[pagerState.currentPage]
                            val bytes = withContext(Dispatchers.IO) { downloadBytes(context, url) }
                            val file = withContext(Dispatchers.IO) { writeCacheFile(context, url, bytes) }
                            shareImage(context, url, file)
                            true
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PreviewActionIcon(R.drawable.ic_browser, "浏览器打开", foreground, actionPill) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[pagerState.currentPage])))
                        }.onFailure { Toast.makeText(context, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show() }
                    }
                }
            }

            if (busy) {
                CircularProgressIndicator(
                    color = foreground,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
        }
    }
}

/**
 * 单张图片：双指缩放、放大后拖动、双击放大或还原、单击关闭。
 *
 * [active] 为 false（Pager 预加载的相邻页）时不处理手势，避免抢走翻页。
 * 仅在「多指」或「已放大」时才消费事件：原始比例下的单指拖动不消费，交给 Pager 翻页。
 * 加载成功后把图片平均亮度色经 [onBackgroundColor] 上报，供上层决定前景色。
 * 单击始终回调 [onTap]（关闭查看器）；缩放、拖动等手势会回调 [onInteraction]，
 * 由上层决定是否弹出操作栏，互不干扰。
 */
@Composable
private fun ZoomableImage(
    url: String,
    active: Boolean,
    onBackgroundColor: (Color) -> Unit,
    onTap: () -> Unit,
    onInteraction: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    // 压暗开启时，前景色按「膜覆盖后的实际亮度」判定：采样拿的是原图均值，
    // 直接按它选前景会让压暗后的浅图配深字，对比度失衡
    val dimScrim = if (LocalIsDarkTheme.current && LocalDimImagesInDark.current) {
        LocalMtfColors.current.background
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(url, active) {
                if (!active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedZoomed = scale > 1.01f
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val zooming = pointers > 1 || scale > 1.01f
                        if (zooming && (zoom != 1f || pan != Offset.Zero)) {
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            val maxX = boxSize.width * (newScale - 1f) / 2f
                            val maxY = boxSize.height * (newScale - 1f) / 2f
                            scale = newScale
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (event.changes.any { it.positionChanged() }) {
                            moved = true
                            // 缩放/拖动过程中持续上报交互，让操作栏在操作期间保持可见
                            onInteraction()
                        }
                        if (event.changes.none { it.pressed }) {
                            val upPos = event.changes.firstOrNull { it.id == down.id }?.position
                                ?: down.position
                            val isTap = !moved && (upPos - down.position).getDistance() < TAP_SLOP
                            if (isTap) {
                                onTap()
                            } else if (startedZoomed && scale <= 1.01f) {
                                // 拖到图片边界外回弹：复位位移
                                offset = Offset.Zero
                            }
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onLoading = { loading = true; failed = false },
            onSuccess = { state ->
                loading = false
                failed = false
                val drawable = state.result.drawable
                scope.launch {
                    val color = withContext(Dispatchers.Default) { sampleAverageColor(drawable) }
                    onBackgroundColor(dimScrim?.let { lerp(color, it, DarkImageDimAlpha) } ?: color)
                }
            },
            onError = { loading = false; failed = true },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                // 压暗膜排在变换之后：跟着图片一起缩放，放大后不会停在原处
                .dimImageInDark(),
        )
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        if (failed) {
            Text(
                "图片加载失败",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 顶部操作图标：前景色与圆形底衬随图片亮度动态取用，带点击涟漪。 */
@Composable
private fun PreviewActionIcon(
    iconRes: Int,
    contentDescription: String,
    foreground: Color,
    pill: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 图片为浅色时用深色前景，否则用白色；无采样结果（加载中/失败）时按深底用白色。 */
private fun foregroundFor(background: Color?): Color =
    if (background != null && background.isLight()) Color(0xE6000000) else Color(0xFFFFFFFF)

/** 文字底衬：浅色图配浅色半透明衬底 + 深字，深色图配深色衬底 + 白字。
 * 中间调图片上单纯的白字或黑字都发灰，垫底衬后对比稳定。
 */
private fun pillFor(background: Color?): Color =
    if (background != null && background.isLight()) Color(0x40FFFFFF) else Color(0x66000000)

/** 顶部操作图标的底衬：在普通底衬基础上把不透明度提高 30%，使其在图上更分明。 */
private fun elevatedPill(pill: Color): Color = pill.copy(alpha = (pill.alpha * 1.3f).coerceAtMost(1f))

private fun Color.isLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > LIGHT_IMAGE_LUMINANCE

/** 抽稀采样图片的平均色，用于判断亮度；大图也不至于逐像素遍历。 */
private fun sampleAverageColor(drawable: Drawable): Color {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return Color.Black
    val pixels = bitmap.asImageBitmap().toPixelMap()
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    var red = 0f
    var green = 0f
    var blue = 0f
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val color = pixels[x, y]
            red += color.red
            green += color.green
            blue += color.blue
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) Color.Black else Color(red / count, green / count, blue / count)
}


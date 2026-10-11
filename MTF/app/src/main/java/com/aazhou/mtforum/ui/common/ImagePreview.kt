package com.aazhou.mtforum.ui.common

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 手势可缩放的上限。 */
private const val MAX_SCALE = 5f

/** 单击判定阈值：抬起位置离按下处超过该距离就不算单击。 */
private const val TAP_SLOP = 24f

/**
 * 全屏图片查看：黑底，多图左右翻页，双指缩放、放大后拖动、双击放大/还原，
 * 单击图片关闭，长按图片经确认后保存到相册。多图时计数常驻显示在底部。
 *
 * 单击是否关闭只看触点有没有落在图片实际绘制到的区域内（见 [tapInsideImage]）：
 * 图片按 ContentScale.Fit 居中绘制，四周的黑边不算图片，点黑边不再误触关闭。
 *
 * [PostContent] 传出的是整组图片地址与命中下标，故同组图片可连续翻看。
 * 列表项也可以是本地文件（如发帖页刚上传的附件）：这类图没有网络地址可取，
 * 长按不弹保存弹窗，只留缩放与翻页。
 */
@Composable
fun ImagePreview(
    urls: List<Any>,
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
    var confirmDownload by remember { mutableStateOf(false) }

    // 上下滑动翻页：与左右翻页互斥，一次滑动只认一个方向
    val onVerticalSwipe: (Int) -> Unit = { direction ->
        val next = (pagerState.currentPage + direction).coerceIn(0, urls.lastIndex)
        if (next != pagerState.currentPage) {
            scope.launch { pagerState.animateScrollToPage(next) }
        }
    }

    BackHandler(enabled = !confirmDownload, onBack = onDismiss)

    // 预览必须盖住玻璃底栏：底栏与 NavHost 同在 AppRoot 的 Box 里、且绘制在 NavHost 之后，
    // 作为页面内 composable 的预览无论 fillMaxSize 到哪都被它压住。Popup 挂在窗口装饰层，
    // 绘制层级高于整个窗口内容；返回键交给内容里的 BackHandler，故关掉 popup 自身的返回拦截。
    // excludeFromSystemGesture 必须为 false：Popup 默认把整块弹窗区域登记成系统手势排除区，
    // 而系统对每侧的排除上限只有 200dp，超限时自屏幕底部往上生效——于是边侧上滑在屏幕
    // 下半段（约底部 200dp）被弹窗吃掉，系统返回手势不再触发，表现为「侧滑返回时好时坏」
    // （从屏幕上半段滑才生效）。这里放开排除区，让边滑回到 BackHandler。
    Popup(
        onDismissRequest = null,
        properties = PopupProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // 方向锁夹在 Pager 与图片之间：图片自己消费事件时（双指缩放、放大后拖动）直接退出；
                // 否则按主方向锁定，垂直锁定后吃掉水平位移，左右翻页不再跟着响应
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { swipeDirectionLock(onVerticalSwipe) },
                ) {
                    ZoomableImage(
                        model = urls[page],
                        active = pagerState.currentPage == page,
                        onTap = onDismiss,
                        onLongPress = {
                            // 只有远程图片（String 地址）拉得到字节；本地文件没有可下载的地址
                            if (urls[pagerState.currentPage] is String) confirmDownload = true
                        },
                    )
                }
            }

            if (urls.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }

            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
        }
    }

    if (confirmDownload) {
        val current = urls[pagerState.currentPage]
        MtfConfirmDialog(
            title = "保存到相册",
            message = "是否将当前图片保存到相册？",
            confirmText = "保存",
            onDismissRequest = { confirmDownload = false },
            onConfirm = {
                confirmDownload = false
                if (current is String) {
                    runBusy(scope, { busy = it }, "保存失败") {
                        val bytes = withContext(Dispatchers.IO) { downloadBytes(context, current) }
                        withContext(Dispatchers.IO) { saveToGallery(context, current, bytes) } != null
                    }
                }
            },
        )
    }
}

/**
 * 单张图片：双指缩放、放大后拖动、双击放大或还原、单击关闭、长按触发保存。
 *
 * [active] 为 false（Pager 预加载的相邻页）时不处理手势，避免抢走翻页。
 * 仅在「多指」或「已放大」时才消费事件：原始比例下的单指拖动不消费，交给 Pager 翻页。
 * 单击关闭前先判断触点是否落在图片实际绘制区域内（见 [tapInsideImage]），
 * 点到 ContentScale.Fit 留下的黑边不关闭；加载中/失败时没有可用矩形，维持点到即关。
 */
@Composable
private fun ZoomableImage(
    model: Any,
    active: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var intrinsic by remember(model) { mutableStateOf(IntSize.Zero) }
    var loading by remember(model) { mutableStateOf(true) }
    var failed by remember(model) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(model, active) {
                if (!active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedZoomed = scale > 1.01f
                    var moved = false
                    var longPressed = false
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
                        }
                        // 长按保存：单指静止超过系统长按阈值即触发；多指（缩放）或已拖动不触发
                        if (!longPressed && !moved && pointers <= 1) {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null &&
                                change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                            ) {
                                longPressed = true
                                onLongPress()
                            }
                        }
                        if (event.changes.none { it.pressed }) {
                            val upPos = event.changes.firstOrNull { it.id == down.id }?.position
                                ?: down.position
                            val isTap = !moved && !longPressed &&
                                (upPos - down.position).getDistance() < TAP_SLOP
                            if (isTap && (failed || tapInsideImage(upPos, boxSize, intrinsic, scale, offset))) {
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
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onLoading = { loading = true; failed = false },
            onSuccess = { state ->
                loading = false
                failed = false
                val drawable = state.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    intrinsic = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
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

/**
 * 预览滑动方向锁：一次手势只认一个方向，避免上下翻页与左右翻页互相带着走。
 *
 * 图片自己消费了事件（双指缩放、放大后拖动）时本层直接退出，手势交还图片。
 * 主方向位移要明显大于另一方向（1.5 倍）且超过 [DirectionSlop] 才锁定：
 * 锁水平则什么都不做，位移原样交给外层 Pager 左右翻页；锁垂直则把水平位移吃掉，
 * Pager 拿不到未消费的水平位移便不会翻页。松手时垂直位移超过屏高 22% 或甩动速度
 * 超过阈值才真正翻页，轻拖不翻。
 */
private suspend fun PointerInputScope.swipeDirectionLock(onVerticalSwipe: (Int) -> Unit) {
    val slop = DirectionSlop.toPx()
    val distanceThreshold = size.height * 0.22f
    val velocityThreshold = SwipeVelocity.toPx()
    val tracker = VelocityTracker()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        tracker.resetTracking()
        var axis = 0
        var total = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) {
                val velocity = tracker.calculateVelocity().y
                val far = kotlin.math.abs(total.y) > distanceThreshold
                val fast = kotlin.math.abs(velocity) > velocityThreshold
                if (axis == 2 && (far || fast)) {
                    // 上滑看上一张、下滑看下一张
                    onVerticalSwipe(if (total.y > 0f) 1 else -1)
                }
                break
            }
            // 未锁定时图片一旦消费事件（缩放/放大拖动），本次手势退出，不再判定方向
            if (axis == 0 && event.changes.any { it.isConsumed }) break
            var delta = Offset.Zero
            event.changes.forEach { change ->
                if (change.pressed && !change.isConsumed) {
                    delta += change.position - change.previousPosition
                }
            }
            total += delta
            when {
                axis == 0 && kotlin.math.abs(total.y) > slop &&
                    kotlin.math.abs(total.y) > kotlin.math.abs(total.x) * 1.5f -> {
                    axis = 2
                    event.changes.forEach { it.consume() }
                }
                axis == 0 && kotlin.math.abs(total.x) > slop &&
                    kotlin.math.abs(total.x) > kotlin.math.abs(total.y) * 1.5f -> axis = 1
                axis == 2 -> event.changes.forEach { it.consume() }
            }
            event.changes.forEach { change ->
                if (change.pressed) tracker.addPosition(change.uptimeMillis, change.position)
            }
        }
    }
}

/** 方向锁判定阈值：小于这个位移不认方向，抖动不触发翻页。 */
private val DirectionSlop = 32.dp

/** 垂直甩动翻页的速度阈值。 */
private val SwipeVelocity = 900.dp

/**
 * 判断触点是否落在图片经 ContentScale.Fit 居中绘制、再叠加缩放/位移后的实际区域内。
 *
 * 图片按 Fit 缩放到 [box] 内居中，得到未变换矩形；graphicsLayer 以画布中心为变换原点
 * 做缩放与平移，故把触点反向变换回局部坐标后再判是否落在该矩形内。加载中/失败
 * （[intrinsic] 非正）时返回 true，维持原来的「点到即关」。
 */
private fun tapInsideImage(
    pos: Offset,
    box: IntSize,
    intrinsic: IntSize,
    scale: Float,
    offset: Offset,
): Boolean {
    if (box.width <= 0 || box.height <= 0) return true
    if (intrinsic.width <= 0 || intrinsic.height <= 0) return true
    val scaleFit = minOf(box.width.toFloat() / intrinsic.width, box.height.toFloat() / intrinsic.height)
    val drawW = intrinsic.width * scaleFit
    val drawH = intrinsic.height * scaleFit
    val left = (box.width - drawW) / 2f
    val top = (box.height - drawH) / 2f
    val cx = box.width / 2f
    val cy = box.height / 2f
    val localX = cx + (pos.x - cx - offset.x) / scale
    val localY = cy + (pos.y - cy - offset.y) / scale
    return localX >= left && localX <= left + drawW && localY >= top && localY <= top + drawH
}

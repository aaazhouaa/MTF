package com.aazhou.mtforum.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.aazhou.mtforum.ui.common.BottomBarBottomMargin
import com.aazhou.mtforum.ui.common.BottomBarHeight
import com.aazhou.mtforum.ui.glass.DampedDragAnimation
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/** 底部导航项。 */
data class GlassTab(val label: String, val icon: Painter)

/**
 * 胶囊四周到容器边缘的统一留白。实测：胶囊靠边那侧与上下留白相等，
 * 因此胶囊尺寸由该值推导（高 = 容器高 - 2*留白，宽 = tab 宽 - 2*留白），
 * 而不是用固定宽高比——否则横向留白会远大于上下留白。
 */
private val PillMargin = 4.dp

/** 选中胶囊静止时的高度，占容器高约 86%（56 - 8）。 */
private val PillHeight = BottomBarHeight - PillMargin * 2

/** 按下放大倍数：约为默认的 1.4 倍，放大后上下溢出容器。 */
private const val PillPressedScale = 1.4f

/** 水珠每次跨到新 tab 时触发弹动的概率。 */
private const val BumpChance = 0.3f

/** 弹动时宽度收缩比例（0.10 即左右两端各向中心收，总长减少 10%）。 */
private const val BumpWidthShrink = 0.10f

/** 弹动时高度增加比例（0.20 即涨高 20%，上下溢出容器，纵向占满不留白）。 */
private const val BumpHeightGrow = 0.20f

/** 弹起很快、回落带轻微回弹。 */
private val BumpRiseSpec = spring<Float>(0.7f, 900f)
private val BumpFallSpec = spring<Float>(0.45f, 320f)

/** 长按时水平方向收缩比例（0.1 即长度减少 10%）。 */
private const val PressedLengthShrink = 0.10f

/** 手指速度归一化上限（px/s），用于液态形变强度。 */
private const val MaxFlow = 3500f

/** 水珠自身速度归一化上限（tab/s），用于判定急停反向。 */
private const val MaxPillVelocity = 4f

/** 反向滑动时的横向压缩系数（越大压得越窄）。 */
private const val OpposedSquash = 1.0f

private val LocalGlassTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 液态玻璃底部导航栏，移植自 backdrop catalog 的 LiquidBottomTabs。
 *
 * 三层结构（可见容器 / 隐形着色层 / 选中胶囊），胶囊采样「屏幕 + 着色图标层」，
 * 因此内部显示的是被放大、着主色、带菲涅尔折射与触点水珠高光的图标。
 *
 * 手势：整条底栏接管按下/滑动，胶囊立即跟到手指所在 tab；胶囊按下时放大溢出并出现水珠高光。
 * 胶囊宽度只覆盖当前选中 tab，故不会挡住相邻 tab 的点击。
 */
@Composable
fun GlassBottomBar(
    backdrop: LayerBackdrop,
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    // 毛玻璃：白色覆盖保持较低 alpha（底栏内亮度 ~253 而透出内容处 ~227，白色覆盖较薄）
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.28f) else Color(0xFF121212).copy(0.28f)

    val tabsBackdrop = rememberLayerBackdrop()
    val tabsCount = tabs.size

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomBarBottomMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) { constraints.maxWidth.toFloat() / tabsCount }
        val pillWidthPx = tabWidth - with(density) { (PillMargin * 2).toPx() }
        val pillWidthDp = with(density) { pillWidthPx.toDp() }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        // 手指是否按在底栏上，用于把手势拖动与点击（胶囊自动滚动）区分开
        val pointerDownState = remember { mutableStateOf(false) }
        val bumpAnimation = remember { Animatable(0f) }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PillPressedScale,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        // 外部选中态同步进 currentIndex
        LaunchedEffect(selectedIndex) {
            snapshotFlow { selectedIndex }.collectLatest { currentIndex = it }
        }
        // currentIndex 变化（点击或拖拽结束）时，驱动胶囊滑动并上报选中
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onSelect(index)
            }
        }
        // 水珠跨到新 tab 时，按概率轻微弹动一下（仅手指按下拖动时触发）
        LaunchedEffect(dampedDragAnimation) {
            var bumpJob: Job? = null
            snapshotFlow { dampedDragAnimation.value.roundToInt() }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (pointerDownState.value && Random.nextFloat() < BumpChance) {
                        bumpJob?.cancel()
                        bumpJob = launch {
                            bumpAnimation.snapTo(0f)
                            bumpAnimation.animateTo(1f, BumpRiseSpec)
                            bumpAnimation.animateTo(0f, BumpFallSpec)
                        }
                    }
                }
        }

        val content: @Composable RowScope.() -> Unit = {
            tabs.forEachIndexed { index, tab ->
                GlassTabItem {
                    Icon(tab.icon, contentDescription = tab.label, tint = contentColor)
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = contentColor)
                }
            }
        }

        // 整栏手势：按下/滑动到任意位置，胶囊立即跟到该 tab，抬起后吸附
        val barGesture = Modifier.pointerInput(tabWidth, tabsCount) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                dampedDragAnimation.press()
                // 按下即带动画滑向手指所在位置（滚过去，而非闪现）
                fun posToValue(x: Float): Float {
                    val raw = (x - panelOffset) / tabWidth - 0.5f
                    return raw.fastCoerceIn(0f, (tabsCount - 1).toFloat())
                }
                var lastValue = posToValue(down.position.x)
                pointerDownState.value = true
                dampedDragAnimation.updateValue(lastValue)
                var pointer = down.id
                var lastX = down.position.x
                var lastT = System.currentTimeMillis()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer }
                        if (change == null || change.changedToUpIgnoreConsumed()) break
                        lastValue = posToValue(change.position.x)
                        dampedDragAnimation.updateValue(lastValue)
                        val now = System.currentTimeMillis()
                        val dt = (now - lastT).coerceAtLeast(1L)
                        dampedDragAnimation.trackFlow((change.position.x - lastX) / dt * 1000f)
                        lastX = change.position.x
                        lastT = now
                        change.consume()
                        pointer = change.id
                    }
                } finally {
                    pointerDownState.value = false
                    dampedDragAnimation.releaseFlow()
                }
                // 以手指位置对应的 tab 为准，而非弹簧当前值——后者可能尚未滚到位
                val idx = lastValue.roundToInt().fastCoerceIn(0, tabsCount - 1)
                dampedDragAnimation.animateToValue(idx.toFloat())
                onSelect(idx)
            }
        }

        // 1) 可见容器：玻璃面板 + 普通配色的图标；整栏手势挂在最上层
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(barGesture)
                .height(BottomBarHeight)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // 2) 隐形着色层：把主色版图标灌进 tabsBackdrop，供胶囊采样
        CompositionLocalProvider(
            LocalGlassTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
        ) {
            Row(
                modifier = Modifier
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .height(BottomBarHeight)
                    .fillMaxWidth()
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // 3) 选中胶囊：采样「屏幕 + 着色图标层」，可拖拽，按下放大溢出并出现水珠高光
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val tabCenter = (dampedDragAnimation.value + 0.5f) * tabWidth
                    translationX = tabCenter - pillWidthPx / 2f + panelOffset
                }
                .width(pillWidthDp)
                .height(PillHeight)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            refractionHeight = 10f.dp.toPx() * progress,
                            refractionAmount = 14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8f.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        // 液态形变：速度越快，沿运动方向拉得越长、垂直方向压得越扁；
                        // 支点向运动前方偏移，像水滴被拉拽；速度反向时量值立即翻号重算。
                        val v = (dampedDragAnimation.flow / MaxFlow).fastCoerceIn(-1f, 1f)
                        val speed = abs(v)
                        // 水珠自身运动方向；与手指速度反向时（急停回甩）触发横向挤压
                        val pillV =
                            (dampedDragAnimation.velocity / MaxPillVelocity).fastCoerceIn(-1f, 1f)
                        val opposed = if (v * pillV < 0f) min(speed, abs(pillV)) else 0f
                        // 弹动：左右两端向中心收、上下涨高溢出，非等比缩放
                        val bumpX = 1f - BumpWidthShrink * bumpAnimation.value
                        val bumpY = 1f + BumpHeightGrow * bumpAnimation.value
                        // 长按时长度（水平方向）收缩，高度不受影响
                        val pressShrinkX = 1f - PressedLengthShrink * dampedDragAnimation.pressProgress
                        scaleX =
                            dampedDragAnimation.scaleX * (1f + 0.55f * speed - OpposedSquash * opposed) *
                                bumpX * pressShrinkX
                        scaleY = dampedDragAnimation.scaleY * (1f - 0.32f * speed * speed) * bumpY
                        transformOrigin = TransformOrigin(0.5f - 0.20f * v, 0.5f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                        )
                    },
                    onDrawFront = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.35f * progress),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.minDimension * 1.1f,
                                ),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    },
                ),
        )
    }
}

@Composable
private fun RowScope.GlassTabItem(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalGlassTabScale.current
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

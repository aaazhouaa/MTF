package com.aazhou.mtforum.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest

/** 头像/表情等小图的解码边长上限：列表头像 22~38dp、资料页头像 72dp 都在其内。 */
internal val SmallImageSize = 72.dp

/** 配图网格单格的解码边长上限：卡片两列格最宽约 190dp，正文三列格约 110dp。 */
internal val GridImageSize = 200.dp

/** 满宽配图的解码边长上限：正文单图与视频封面都是屏宽档，取略大于屏宽的一档。 */
private val FullWidthImageSize = 460.dp

/**
 * 小图（头像/图标/表情）请求：限定解码边长并强制硬件位图。
 *
 * 站点头像按上传原图返回（可达 2K），而显示位只有几十 dp；不设尺寸时 Coil 按原图
 * 解码，位图在内存里按原尺寸驻留，滑动中反复分配/回收大块内存正是掉帧的来源。
 */
@Composable
internal fun smallImageRequest(url: String?, maxSize: Dp = SmallImageSize): ImageRequest =
    sizedImageRequest(url, maxSize, crossfade = true)

/**
 * 帖子配图请求：限定解码边长、关闭渐显、强制硬件位图。
 *
 * 渐显会在每张图加载完成时额外触发一次重绘，快滑列表中多张图连续完成时会叠成可见卡顿。
 */
@Composable
internal fun postImageRequest(url: String?, maxSize: Dp = FullWidthImageSize): ImageRequest =
    sizedImageRequest(url, maxSize, crossfade = false)

@Composable
private fun sizedImageRequest(url: String?, maxSize: Dp, crossfade: Boolean): ImageRequest {
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { maxSize.roundToPx() }
    return ImageRequest.Builder(context)
        .data(url)
        .crossfade(crossfade)
        .allowHardware(true)
        .size(maxPx)
        .build()
}

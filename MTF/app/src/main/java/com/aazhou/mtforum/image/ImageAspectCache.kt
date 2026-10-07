package com.aazhou.mtforum.image

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 正文配图的宽高比缓存：图片地址 -> 宽 / 高。
 *
 * 详情页由一个 LazyColumn 承载，正文单图先用固定占位比测量、加载完成后再按真实
 * 宽高比重排；item 高度在滚动途中变化，列表的滚动锚点不会同步补偿，快速滑动就会
 * 跳出约半屏图片高度的位移（从评论区划向正文底部、或在带图评论中快滑时可见）。
 * 随楼层数据提前把真实宽高比解析进来，首次组合即按终态测量，加载完不再变布局。
 */
object ImageAspectCache {

    private val aspects = mutableStateMapOf<String, Float>()

    /** 组合期读取；命中返回真实宽高比，未命中返回 null 由调用方回落占位比。 */
    fun aspectOf(url: String): Float? = aspects[url]

    /**
     * 预取 [urls] 的宽高比：只解析缓存里没有的地址，按小尺寸采样解码（宽高比与
     * 原图一致，内存开销可忽略），并发受限；请求失败或取不到尺寸的地址不写入，
     * 渲染侧仍走原有占位逻辑。写缓存发生在调用方所在的（主线程）协程上。
     */
    suspend fun prefetch(context: Context, loader: ImageLoader, urls: List<String>) {
        val pending = urls.filter { it.isNotBlank() && !aspects.containsKey(it) }.distinct()
        if (pending.isEmpty()) return
        val resolved = coroutineScope {
            pending.chunked(PREFETCH_PARALLELISM)
                .flatten()
                .map { url -> async(Dispatchers.IO) { url to decodeAspect(context, loader, url) } }
                .awaitAll()
        }
        resolved.forEach { (url, aspect) ->
            if (aspect != null) aspects[url] = aspect
        }
    }

    private suspend fun decodeAspect(context: Context, loader: ImageLoader, url: String): Float? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(PREFETCH_SAMPLE_PX)
            .build()
        val result = runCatching { loader.execute(request) }.getOrNull() as? SuccessResult ?: return null
        return imageAspectRatio(result.drawable.intrinsicWidth, result.drawable.intrinsicHeight)
    }
}

/** 竖长图的高宽比下限：与正文单图的测量口径一致，竖图最多占 1.25 倍宽的高度。 */
private const val MIN_ASPECT = 0.8f

/** 预取解码的采样边长（像素）：只为拿宽高比，不需要全尺寸位图。 */
private const val PREFETCH_SAMPLE_PX = 256

/** 预取并发上限：站点媒体走 CDN，少量并发即可，避免抢占正文图片加载。 */
private const val PREFETCH_PARALLELISM = 4

/** 尺寸合法时返回宽 / 高；否则返回 null。 */
internal fun imageAspectRatio(width: Int, height: Int): Float? =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceAtLeast(MIN_ASPECT) else null

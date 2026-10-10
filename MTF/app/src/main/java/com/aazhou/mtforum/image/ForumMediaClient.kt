package com.aazhou.mtforum.image

import com.aazhou.mtforum.data.network.CDN_IP_RATE_LIMITED
import com.aazhou.mtforum.data.network.RateLimiter
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * 论坛媒体（图片与视频）共用的请求头构造。
 *
 * 站点的图片与附件 CDN 按 User-Agent 放行，缺浏览器 UA 时会被判为爬虫返回 403；头像与
 * 附件还可能要求携带会话 Cookie。Coil 默认请求与 ExoPlayer 默认数据源都不带这些头，
 * 于是媒体要么显示加载失败的灰底、或者视频直接黑屏。
 *
 * 会话 Cookie 每次请求实时读取 [cookieHeader]：登录态在应用生命周期内会变（登录、退出、
 * WAF 人机验证回灌），写死一份会在状态变化后失效。
 *
 * 媒体请求里，只有帖子配图与附件（[shouldRateLimitMedia]）才走限流与重试：
 * 列表一屏会同时发出十几张大配图（forum.php?mod=image），突发流量会被判超限并返回 [CDN_IP_RATE_LIMITED]。
 * [RateLimiter] 把这类请求排开间隔，[MediaRetryInterceptor] 给撞上限频的请求重试机会。
 *
 * 表情（/static/image/smiley/）与头像（avatar.mt2.cn）是纯静态/外站轻量资源，表情面板
 * 一屏需同时呈现数十张表情，绝不能排入 150ms 串行限流队列，否则不仅排队数秒造成表情
 * 大面积空白，还会因配图 514 退避而被连坐卡死。
 */
fun forumMediaClient(
    userAgent: String,
    cookieHeader: () -> String,
): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // 拦截器按添加顺序嵌套：重试在最外层，退避后的重发会再节一次流
        .addInterceptor(MediaRetryInterceptor(filter = { shouldRateLimitMedia(it.url) }))
        .addInterceptor(
            RateLimiter(
                minIntervalMs = MEDIA_MIN_INTERVAL_MS,
                backoffCodes = setOf(CDN_IP_RATE_LIMITED),
                filter = { shouldRateLimitMedia(it.url) },
            ),
        )
        .addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val builder: Request.Builder = request.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            if (host.endsWith("binmt.cc")) {
                builder.header("Referer", "https://bbs.binmt.cc/")
                val cookie = cookieHeader()
                if (cookie.isNotEmpty()) builder.header("Cookie", cookie)
            }
            chain.proceed(builder.build())
        }
        .build()

/**
 * 媒体请求重试：只重试限频与过载响应，且只对 [filter] 允许的媒体请求生效。
 *
 * 间隔逐次递增；重发会再次经过下层的 [RateLimiter]，同一批图片的重试自然被排开，
 * 不会又攒成一记齐射。403 不重试——那是真的被挡，重发多少次都一样。
 */
private class MediaRetryInterceptor(
    private val filter: (Request) -> Boolean = { true },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!filter(request)) {
            return chain.proceed(request)
        }
        var retries = 0
        while (true) {
            val response = chain.proceed(request)
            if (retries >= MAX_RETRIES || response.code !in RETRY_CODES) return response
            // 先关掉旧响应体再重发，否则连接与缓冲区要等 GC 才回收
            response.close()
            retries++
            Thread.sleep(BACKOFF_MS * retries)
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val BACKOFF_MS = 500L
        val RETRY_CODES = setOf(429, 503, CDN_IP_RATE_LIMITED)
    }
}

/**
 * 是否是需要限流与重试的媒体资源：
 * 只有 Discuz 论坛动态配图/附件接口（forum.php?mod=image 与 mod=attachment）易触发 CDN 514 限频。
 * 静态表情（/static/image/smiley/）、站点小图标与头像服务完全免除限流。
 */
internal fun shouldRateLimitMedia(url: HttpUrl): Boolean {
    val path = url.encodedPath
    if (path.contains("/static/image/") || path.contains("/smiley/")) return false
    if (url.host.contains("avatar.")) return false
    return path.contains("forum.php") ||
        url.queryParameter("mod") in setOf("image", "attachment") ||
        path.contains("/attachment/")
}

/**
 * 媒体请求的最小间隔：比页面请求的 400ms 松。
 *
 * 配图体积小且多数命中 CDN，排太密会撞上单 IP 限频；排太疏则一屏图要逐张等下来。
 */
private const val MEDIA_MIN_INTERVAL_MS = 150L

package com.aazhou.mtforum.image

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 论坛媒体（图片与视频）共用的请求头构造。
 *
 * 站点的图片与附件 CDN 按 User-Agent 放行，缺浏览器 UA 时会被判为爬虫返回 403；头像与
 * 附件还可能要求携带会话 Cookie。Coil 默认请求与 ExoPlayer 默认数据源都不带这些头，
 * 于是媒体要么显示加载失败的灰底、要么视频直接黑屏。
 *
 * 会话 Cookie 每次请求实时读取 [cookieHeader]：登录态在应用生命周期内会变（登录、退出、
 * WAF 人机验证回灌），写死一份会在状态变化后失效。
 */
fun forumMediaClient(
    userAgent: String,
    cookieHeader: () -> String,
): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

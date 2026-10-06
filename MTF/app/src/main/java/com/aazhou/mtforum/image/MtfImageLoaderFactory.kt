package com.aazhou.mtforum.image

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 图片加载器：给图片请求补上和网页请求一致的请求头。
 *
 * 站点的图片 CDN 按 User-Agent 放行，缺浏览器 UA 时会被判为爬虫返回 403；头像与附件
 * 还可能要求携带会话 Cookie。Coil 默认请求没有这些头，于是图片显示为加载失败的灰底。
 * 这里在 OkHttp 层统一补 UA 与 Referer，并仅对论坛域附加当前会话 Cookie，其余图片请求
 * 不受影响。
 */
class MtfImageLoaderFactory(
    private val context: Context,
    private val userAgent: String,
    private val cookieHeader: () -> String,
) : ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient {
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
            }
            .crossfade(true)
            .build()
}

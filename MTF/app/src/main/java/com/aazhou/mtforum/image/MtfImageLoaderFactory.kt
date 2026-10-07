package com.aazhou.mtforum.image

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

/**
 * 图片加载器：给图片请求补上和网页请求一致的请求头。
 *
 * 请求头与超时从 [forumMediaClient] 取，与视频播放器共用一份；GIF 与动画 WebP 需注册
 * [ImageDecoderDecoder]（Coil 2 的核心库对这两类只解首帧，动图会变成静态图）。
 */
class MtfImageLoaderFactory(
    private val context: Context,
    private val userAgent: String,
    private val cookieHeader: () -> String,
) : ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient {
                forumMediaClient(userAgent = userAgent, cookieHeader = cookieHeader)
            }
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

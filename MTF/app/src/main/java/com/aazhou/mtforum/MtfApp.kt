package com.aazhou.mtforum

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.image.MtfImageLoaderFactory

class MtfApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * 全局图片加载器：补上站点要求的 User-Agent、Referer 与会话 Cookie。
     *
     * 延迟到首次加载图片时调用，此时 [container] 已在 [onCreate] 初始化完毕。
     */
    override fun newImageLoader(): ImageLoader =
        MtfImageLoaderFactory(
            context = this,
            userAgent = HttpClient.MOBILE_UA,
            cookieHeader = { container.httpClient.cookieHeader() },
        ).newImageLoader()
}

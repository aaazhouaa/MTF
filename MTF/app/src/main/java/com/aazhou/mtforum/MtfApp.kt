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
        installCrashHandler()
    }

    /**
     * 未捕获异常统一先写进本地日志，再交回系统默认处理（崩溃弹窗 + 进程回收）。
     *
     * 进程马上要被杀掉，写盘必须同步，故走 [AppLogger.logCrash]；容器构造期间
     * （本方法执行前）抛出的异常进不了日志，属已知边界。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            container.logger.logCrash(thread, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
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

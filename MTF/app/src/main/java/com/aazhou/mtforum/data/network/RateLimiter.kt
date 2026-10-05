package com.aazhou.mtforum.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * 全局请求节流：站点挂阿里云 ESA，短时间高频请求会被 IP 级拦截。
 * 保证两次请求间隔不小于 [minIntervalMs]；命中 403/429 时退避一段时间。
 */
class RateLimiter(private val minIntervalMs: Long = 400L) : Interceptor {

    private val lastRequestAt = AtomicLong(0L)

    @Volatile
    private var blockedUntil = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) Thread.sleep(waitForBlock)

        synchronized(this) {
            val since = System.currentTimeMillis() - lastRequestAt.get()
            if (since < minIntervalMs) Thread.sleep(minIntervalMs - since)
            lastRequestAt.set(System.currentTimeMillis())
        }

        val response = chain.proceed(chain.request())
        if (response.code == 403 || response.code == 429) {
            blockedUntil = System.currentTimeMillis() + 3000
        }
        return response
    }
}

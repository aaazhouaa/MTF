package com.aazhou.mtforum.data.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * 站点 CDN 对单 IP 访问限频时返回的非标准状态码。
 *
 * 阿里云 ESA 的状态码表里没有它（ESA 的扩展段是 498/499 与 520~599），腾讯云 CDN
 * 把 514 定义为「超出 IP 访问限频」；站点边缘确实按 IP 做访问控制，未命中缓存的请求
 * 会拿到 `x-tengine-error: denied by ip_access_rule`，与这个码同源。
 */
const val CDN_IP_RATE_LIMITED = 514

/** 命中即退避的状态码：403 边缘拦截、429 频控、514 CDN 单 IP 访问限频。 */
private val DEFAULT_BACKOFF_CODES = setOf(403, 429, CDN_IP_RATE_LIMITED)

/**
 * 全局请求节流：站点挂阿里云 ESA，短时间高频请求会被 IP 级拦截。
 * 保证两次请求间隔不小于 [minIntervalMs]；命中 [backoffCodes] 时退避一段时间。
 *
 * [backoffCodes] 可收紧：媒体客户端只对限频码退避，避免一次 403 把所有图片拖停。
 * [filter] 可按请求过滤：静态表情、头像等无需限流的轻量资源直接放行，不走节流与退避。
 */
class RateLimiter(
    private val minIntervalMs: Long = 400L,
    private val backoffCodes: Set<Int> = DEFAULT_BACKOFF_CODES,
    private val filter: (Request) -> Boolean = { true },
) : Interceptor {

    private val lastRequestAt = AtomicLong(0L)

    @Volatile
    private var blockedUntil = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!filter(request)) {
            return chain.proceed(request)
        }

        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) Thread.sleep(waitForBlock)

        synchronized(this) {
            val since = System.currentTimeMillis() - lastRequestAt.get()
            if (since < minIntervalMs) Thread.sleep(minIntervalMs - since)
            lastRequestAt.set(System.currentTimeMillis())
        }

        val response = chain.proceed(request)
        if (response.code in backoffCodes) {
            blockedUntil = System.currentTimeMillis() + 3000
        }
        return response
    }
}

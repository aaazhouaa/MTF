package com.aazhou.mtforum.image

import com.aazhou.mtforum.data.network.CDN_IP_RATE_LIMITED
import com.aazhou.mtforum.data.network.RateLimiter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ForumMediaClientTest {

    @Test
    fun shouldRateLimitMediaDistinguishesStaticAndDynamicUrls() {
        // 静态表情：绝不限流
        assertFalse(shouldRateLimitMedia("https://cdn.binmt.cc/static/image/smiley/qq/qq001.gif".toHttpUrl()))
        assertFalse(shouldRateLimitMedia("https://cdn.binmt.cc/static/image/smiley/doge/7.png".toHttpUrl()))
        assertFalse(shouldRateLimitMedia("https://cdn.binmt.cc/static/image/smiley/comiis_tb/tb_1.png".toHttpUrl()))

        // 静态通用素材与图标：不限流
        assertFalse(shouldRateLimitMedia("https://cdn.binmt.cc/static/image/common/none.png".toHttpUrl()))

        // 头像服务：不限流
        assertFalse(shouldRateLimitMedia("https://avatar.mt2.cn/avatar.php?uid=156549".toHttpUrl()))
        assertFalse(shouldRateLimitMedia("https://avatar.mt2.cn/uc_server/avatar.php?uid=1&size=middle".toHttpUrl()))

        // 论坛配图与附件：必须限流与重试
        assertTrue(shouldRateLimitMedia("https://cdn.binmt.cc/forum.php?mod=image&aid=377738".toHttpUrl()))
        assertTrue(shouldRateLimitMedia("https://bbs.binmt.cc/forum.php?mod=attachment&aid=9".toHttpUrl()))
        assertTrue(shouldRateLimitMedia("https://cdn.binmt.cc/data/attachment/forum/202610/photo.jpg".toHttpUrl()))
    }

    @Test
    fun rateLimiterBypassesUnfilteredRequestsEvenUnderBackoff() {
        val limiter = RateLimiter(
            minIntervalMs = 200L,
            backoffCodes = setOf(CDN_IP_RATE_LIMITED),
            filter = { shouldRateLimitMedia(it.url) },
        )

        // 构造一个模拟拦截器，根据响应码模拟服务端返回
        var mockResponseCode = 200
        val client = OkHttpClient.Builder()
            .addInterceptor(limiter)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(mockResponseCode)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val postImageRequest = Request.Builder()
            .url("https://cdn.binmt.cc/forum.php?mod=image&aid=1")
            .build()
        val smileyRequest = Request.Builder()
            .url("https://cdn.binmt.cc/static/image/smiley/doge/7.png")
            .build()

        // 1. 配图请求返回 514，触发 3000ms 退避
        mockResponseCode = CDN_IP_RATE_LIMITED
        client.newCall(postImageRequest).execute().close()

        // 2. 此时静态表情请求进来：由于 filter 豁免，不应等待 3000ms，必须立即放行
        mockResponseCode = 200
        val smileyElapsed = measureTimeMillis {
            val response = client.newCall(smileyRequest).execute()
            response.close()
        }
        assertTrue("表情请求不应被 514 退避阻塞，实测耗时 ${smileyElapsed}ms", smileyElapsed < 500)
    }
}

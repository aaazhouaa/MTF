package com.aazhou.mtforum.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 论坛请求客户端：Cookie 会话 + WAF 挑战处理 + 请求节流。
 *
 * WAF 处理分两层：
 * 1. 本地求解（[WafSolver]）——无感，正常情况走这条；
 * 2. 真人验证兜底——本地求解后仍被拦（算法失配）时，[get] 会挂起并置位
 *    [pendingVerificationFlow]，由界面拉起 WebView 让用户过验证，拿到的 cookie 回灌后自动重试。
 */
class HttpClient(
    val baseUrl: String = BASE_URL,
    val cookieStore: CookieStore = CookieStore(),
) {

    val host: String = baseUrl.toHttpUrlOrNull()?.host ?: "bbs.binmt.cc"

    private val rateLimiter = RateLimiter()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieStore)
        .addInterceptor(rateLimiter)
        .build()

    private val _pendingVerification = MutableStateFlow(false)

    /** 为 true 时表示本地求解已失配、需要用户完成人机验证；界面据此拉起验证页。 */
    val pendingVerificationFlow: StateFlow<Boolean> = _pendingVerification.asStateFlow()

    private var pendingVerification: CompletableDeferred<String?>? = null

    /** 需要真人验证时打开的地址。 */
    val verificationUrl: String get() = baseUrl

    fun isLoggedIn(): Boolean =
        cookieStore.cookies(host).any { it.name.endsWith("_auth") && it.value.isNotEmpty() }

    fun cookieHeader(): String = cookieStore.cookieHeader(host)

    fun url(path: String): String =
        if (path.startsWith("http")) path else baseUrl + path.removePrefix("/")

    @Throws(IOException::class)
    suspend fun get(url: String, userAgent: String = MOBILE_UA): String {
        val body = executeGet(url, userAgent)
        if (!WafSolver.looksLikeChallenge(body)) return body

        WafSolver.solve(body)?.let { value ->
            cookieStore.setCookie(host, WafSolver.COOKIE_NAME, value)
            val retried = executeGet(url, userAgent)
            if (!WafSolver.looksLikeChallenge(retried)) return retried
        }

        // 本地求解无法通过：交给真人验证，验证完成后用新 cookie 重试
        val verifiedCookie = awaitVerification()
        if (verifiedCookie != null) {
            applyCookieHeader(verifiedCookie)
            return executeGet(url, userAgent)
        }
        return body
    }

    @Throws(IOException::class)
    fun post(url: String, params: Map<String, String>, referer: String? = null): String {
        val form = FormBody.Builder()
        for ((k, v) in params) form.add(k, v)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .header("Content-Type", "application/x-www-form-urlencoded")
        if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
        val request = builder.post(form.build()).build()
        val body = client.newCall(request).execute().use { it.body?.string() ?: "" }
        // 写操作不自动重放（避免重复提交），仅求解并留存 Cookie
        if (WafSolver.looksLikeChallenge(body)) {
            WafSolver.solve(body)?.let { cookieStore.setCookie(host, WafSolver.COOKIE_NAME, it) }
        }
        return body
    }

    @Throws(IOException::class)
    fun getBytes(url: String, referer: String? = null): ByteArray {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
        if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
        return client.newCall(builder.build()).execute().use { it.body?.bytes() ?: ByteArray(0) }
    }

    /** multipart 上传（附件/图片），携带当前会话 Cookie。 */
    @Throws(IOException::class)
    fun upload(
        url: String,
        file: File,
        fieldName: String = "Filedata",
        extraFields: Map<String, String> = emptyMap(),
        userAgent: String = MOBILE_UA,
    ): String {
        if (!file.isFile || file.length() <= 0L) throw IOException("上传文件不存在或为空")
        val mediaType = guessContentType(file).toMediaTypeOrNull()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(fieldName, file.name, file.asRequestBody(mediaType))
        for ((k, v) in extraFields) builder.addFormDataPart(k, v)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", ACCEPT)
            .header("Referer", baseUrl)
            .post(builder.build())
        val cookie = cookieHeader()
        if (cookie.isNotEmpty()) requestBuilder.header("Cookie", cookie)
        return client.newCall(requestBuilder.build()).execute().use {
            val body = it.body?.string() ?: ""
            if (!it.isSuccessful) throw IOException("上传失败（HTTP ${it.code}）")
            body
        }
    }

    /** 真人验证结束后由界面调用：传入 WebView 拿到的 Cookie 头；传 null 表示用户取消。 */
    fun completeVerification(cookieHeader: String?) {
        val deferred = synchronized(this) { pendingVerification }
        if (cookieHeader.isNullOrBlank()) {
            _pendingVerification.value = false
            deferred?.complete(null)
        } else {
            deferred?.complete(cookieHeader)
        }
    }

    /** 把 "name=value; name2=value2" 形式的 Cookie 头写入会话存储。 */
    fun applyCookieHeader(header: String) {
        for (pair in header.split(";")) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isEmpty() || value.isEmpty()) continue
            cookieStore.setCookie(host, name, value)
        }
    }

    private suspend fun awaitVerification(): String? {
        val deferred = synchronized(this) {
            pendingVerification ?: CompletableDeferred<String?>().also {
                pendingVerification = it
                _pendingVerification.value = true
            }
        }
        return try {
            withTimeoutOrNull(VERIFY_TIMEOUT_MS) { deferred.await() }
        } finally {
            synchronized(this) {
                if (pendingVerification === deferred) {
                    pendingVerification = null
                    _pendingVerification.value = false
                }
            }
        }
    }

    private fun executeGet(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .get()
            .build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun guessContentType(file: File): String = when {
        file.name.endsWith(".png", true) -> "image/png"
        file.name.endsWith(".gif", true) -> "image/gif"
        file.name.endsWith(".webp", true) -> "image/webp"
        file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "application/octet-stream"
    }

    companion object {
        const val BASE_URL = "https://bbs.binmt.cc/"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8"

        private const val VERIFY_TIMEOUT_MS = 5 * 60 * 1000L

        fun httpUrl(url: String): HttpUrl? = url.toHttpUrlOrNull()
    }
}

package com.aazhou.mtforum.data.network

import com.aazhou.mtforum.data.log.LogLevel
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

/** 条件 GET 取回的正文，[etag] 是本次响应带的校验值，下次原样回传即可。 */
data class FetchedPage(val body: String, val etag: String?)

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

    /**
     * 会话发生变化时的回调（WebView 验证/登录后 cookie 回灌、退出登录等）。
     *
     * 由应用容器装配：cookie 一变，上层就要重新拉当前用户资料，否则内置浏览器里
     * 登录成功后头像与资料页仍是未登录状态。
     */
    var onSessionChanged: (() -> Unit)? = null

    /**
     * 日志回调（WAF 挑战、真人验证流程、请求失败），由应用容器接到日志门面。
     * 缺省不接：客户端本身不依赖日志，单测里也不必接。
     */
    var onLog: ((LogLevel, String, String, Throwable?) -> Unit)? = null

    /** 需要真人验证时打开的地址。 */
    val verificationUrl: String get() = baseUrl

    fun isLoggedIn(): Boolean =
        cookieStore.cookies(host).any { it.name.endsWith("_auth") && it.value.isNotEmpty() }

    fun cookieHeader(): String = cookieStore.cookieHeader(host)

    fun url(path: String): String =
        if (path.startsWith("http")) path else baseUrl + path.removePrefix("/")

    @Throws(IOException::class)
    suspend fun get(url: String, userAgent: String = MOBILE_UA): String =
        getWithFinalUrl(url, userAgent).second

    /**
     * 同 [get]，但同时返回跟随重定向后的最终地址。
     *
     * 楼层中转链接（redirect&goto=findpost）会 302 到带页码的帖子页，响应体就是那一页；
     * 拿最终地址既能知道页码，也能直接把这一页当作内容来源，省掉再一次取页。
     */
    @Throws(IOException::class)
    suspend fun getWithFinalUrl(url: String, userAgent: String = MOBILE_UA): Pair<String, String> {
        var latest = try {
            executeGet(url, userAgent)
        } catch (e: IOException) {
            onLog?.invoke(LogLevel.ERROR, TAG, "GET 失败：$url", e)
            throw e
        }
        if (!WafSolver.looksLikeChallenge(latest.body)) return latest.finalUrl to latest.body

        onLog?.invoke(LogLevel.WARN, TAG, "命中 WAF 挑战，尝试本地求解：$url", null)
        WafSolver.solve(latest.body)?.let { value ->
            cookieStore.setCookie(host, WafSolver.COOKIE_NAME, value)
            val retried = executeGet(url, userAgent)
            if (!WafSolver.looksLikeChallenge(retried.body)) {
                onLog?.invoke(LogLevel.INFO, TAG, "WAF 本地求解通过：$url", null)
                return retried.finalUrl to retried.body
            }
            latest = retried
        }

        // 本地求解无法通过：交给真人验证，验证完成后用新 cookie 重试
        onLog?.invoke(LogLevel.WARN, TAG, "WAF 本地求解失配，需要真人验证：$url", null)
        val verifiedCookie = awaitVerification()
        if (verifiedCookie != null) {
            applyCookieHeader(verifiedCookie)
            onLog?.invoke(LogLevel.INFO, TAG, "真人验证 cookie 已回灌：$url", null)
            val retried = executeGet(url, userAgent)
            return retried.finalUrl to retried.body
        }
        onLog?.invoke(LogLevel.WARN, TAG, "真人验证未完成（超时或取消），按挑战页返回：$url", null)
        return latest.finalUrl to latest.body
    }

    /**
     * 带 If-None-Match 的条件 GET：站点内容没变时返回 null（一个 304，不带正文），
     * 变了才返回正文与新的校验值。
     *
     * 表情表这类「不常变但站点会更新」的静态资源靠它保鲜：[etag] 由调用方从上次的响应里
     * 存着，原样回传即可。挑战页与 [get] 一样本地求解一次；仍不过就抛异常——调用方手上
     * 已有旧数据，留着它比把一段挑战 JS 当正文收下强。
     */
    @Throws(IOException::class)
    suspend fun getIfChanged(
        url: String,
        etag: String?,
        userAgent: String = MOBILE_UA,
    ): FetchedPage? {
        var latest = try {
            executeGet(url, userAgent, etag)
        } catch (e: IOException) {
            onLog?.invoke(LogLevel.ERROR, TAG, "条件 GET 失败：$url", e)
            throw e
        }
        if (WafSolver.looksLikeChallenge(latest.body)) {
            onLog?.invoke(LogLevel.WARN, TAG, "命中 WAF 挑战，尝试本地求解：$url", null)
            WafSolver.solve(latest.body)?.let { value ->
                cookieStore.setCookie(host, WafSolver.COOKIE_NAME, value)
                latest = executeGet(url, userAgent, etag)
            }
            if (WafSolver.looksLikeChallenge(latest.body)) {
                onLog?.invoke(LogLevel.WARN, TAG, "WAF 本地求解失配：$url", null)
                throw IOException("WAF 挑战未能通过：$url")
            }
            onLog?.invoke(LogLevel.INFO, TAG, "WAF 本地求解通过：$url", null)
        }
        return if (latest.notModified) null else FetchedPage(latest.body, latest.etag)
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
        fun execute(): String =
            client.newCall(request).execute().use { response ->
                // 登录提交的回执要定位「为什么没登上」：看状态码、重定向终址与站点是否下发会话 cookie。
                // 只记 cookie 名，值里是凭据不能入库。
                if (url.contains("loginsubmit")) {
                    val names = response.headers("Set-Cookie")
                        .map { it.substringBefore('=') }
                        .joinToString(",")
                    onLog?.invoke(
                        LogLevel.INFO,
                        TAG,
                        "登录 POST：HTTP ${response.code} 终址=${response.request.url} Set-Cookie=[$names]",
                        null,
                    )
                }
                response.body?.string() ?: ""
            }
        var body = execute()
        // 写操作命中 WAF 挑战：本地解出 cookie 后按原请求体重放一次。Discuz 的这几个写操作
        // 都幂等（重复关注/重复拉黑/重复删好友返回的都是提示而非重复生效），重放安全；
        // 不解就重放的话，调用方只会收到一段挑战页，写操作全变成假成功。
        if (WafSolver.looksLikeChallenge(body)) {
            onLog?.invoke(LogLevel.WARN, TAG, "写操作命中 WAF 挑战，求解后重放：$url", null)
            WafSolver.solve(body)?.let { cookieStore.setCookie(host, WafSolver.COOKIE_NAME, it) }
            val retried = execute()
            if (!WafSolver.looksLikeChallenge(retried)) {
                onLog?.invoke(LogLevel.INFO, TAG, "写操作 WAF 求解通过，已重放：$url", null)
                body = retried
            }
        }
        return body
    }

    /**
     * 取重定向目标地址本身，不下载目标页面。
     *
     * 只读 Location 头：提醒列表要给每条回复摘出正文，得先知道它在第几页，
     * 跟着 302 把整页下回来太贵（一页几十 KB、还有节流）。
     * 非 302（风控挑战页等）返回 null，调用方按「取不到」处理。
     */
    @Throws(IOException::class)
    fun redirectLocation(url: String, userAgent: String = MOBILE_UA): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .get()
            .build()
        client.newBuilder().followRedirects(false).build()
            .newCall(request).execute().use { response ->
                if (!response.isRedirect) return null
                val location = response.header("Location") ?: return null
                // 站点多数给绝对地址；给相对地址时按当前 host 拼完整
                return if (location.startsWith("http")) {
                    location
                } else {
                    response.request.url.newBuilder().encodedPath(location.removePrefix("/")).build()
                        .toString()
                }
            }
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
            if (!it.isSuccessful) {
                onLog?.invoke(LogLevel.ERROR, TAG, "上传失败（HTTP ${it.code}）：$url", null)
                throw IOException("上传失败（HTTP ${it.code}）")
            }
            body
        }
    }

    /** 真人验证结束后由界面调用：传入 WebView 拿到的 Cookie 头；传 null 表示用户取消。 */
    fun completeVerification(cookieHeader: String?) {
        val deferred = synchronized(this) { pendingVerification }
        if (cookieHeader.isNullOrBlank()) {
            _pendingVerification.value = false
            deferred?.complete(null)
            onLog?.invoke(LogLevel.WARN, TAG, "用户取消了真人验证", null)
        } else {
            deferred?.complete(cookieHeader)
            // cookie 已变（可能刚在 WebView 里登录）：通知上层刷新会话态
            onLog?.invoke(LogLevel.INFO, TAG, "真人验证完成，cookie 已回灌", null)
            onSessionChanged?.invoke()
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

/** 一次 GET 的原始结果：最终地址、正文、站点给的校验值、是否 304 未修改。 */
private data class GetResult(
    val finalUrl: String,
    val body: String,
    val etag: String?,
    val notModified: Boolean,
)

    private fun executeGet(url: String, userAgent: String, etag: String? = null): GetResult {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .get()
        if (!etag.isNullOrEmpty()) builder.header("If-None-Match", etag)
        val request = builder.build()
        return client.newCall(request).execute().use { response ->
            GetResult(
                // 跟随重定向后的最终地址（中转链接 302 到帖子页）
                finalUrl = response.request.url.toString(),
                body = response.body?.string() ?: "",
                etag = response.header("ETag"),
                notModified = response.code == HTTP_NOT_MODIFIED,
            )
        }
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

        private const val HTTP_NOT_MODIFIED = 304

        /** 网络层事件的日志 tag：日志页按它筛选用。 */
        private const val TAG = "HttpClient"

        fun httpUrl(url: String): HttpUrl? = url.toHttpUrlOrNull()
    }
}

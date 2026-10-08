package com.aazhou.mtforum

import android.app.Application
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.download.ThreadArchiveStore
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.DownloadStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.data.repo.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用级依赖容器：手动 DI，避免引入注解处理框架。 */
class AppContainer(application: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionStore = SessionStore(application)
    val themeStore = ThemeStore(application)
    val actionStore = ActionStore(application)
    /** 已下载帖子的登记表（列表、打开、手动删除）。 */
    val downloadStore = DownloadStore(application)
    /** 已下载帖子的应用内存档（快照 + 本地图片，供离线按原格式看帖）。 */
    val archiveStore = ThreadArchiveStore(application)
    val httpClient = HttpClient()
    val repository = ForumRepository(httpClient)

    /**
     * 当前登录用户的资料；未登录为 null。
     *
     * 登录可能发生在三处：原生登录页、人机验证页里的 WebView（cookie 回灌后由
     * [HttpClient.onSessionChanged] 通知）、以及启动时从持久化会话恢复。三者都
     * 汇到 [refreshCurrentUser]，首页头像与各页资料才拿得到同一份数据。
     */
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    /** 本地内容缓存：一个 key 一个 JSON 文件，置于 cacheDir（临时性质，系统空间紧张可回收）。 */
    val contentCache = ContentCache(java.io.File(application.cacheDir, "mtf_content"))

    init {
        httpClient.onSessionChanged = { refreshCurrentUserAsync() }
        appScope.launch {
            val saved = sessionStore.cookieHeader.first()
            if (saved.isNotEmpty()) restoreCookies(saved)
            // 先用上次登录保存的资料上屏（免一次请求的白屏），网络回来再覆盖
            val savedUid = sessionStore.uid.first()
            if (savedUid.isNotEmpty()) {
                _currentUser.value = UserProfile(
                    uid = savedUid,
                    name = sessionStore.username.first().ifEmpty { null },
                    avatarUrl = sessionStore.avatar.first().ifEmpty { null },
                )
            }
            if (httpClient.isLoggedIn()) refreshCurrentUser()
        }
    }

    /** 拉取并缓存当前登录用户资料；未登录时清空。失败则保留旧值，不清空已有资料。 */
    suspend fun refreshCurrentUser() {
        if (!httpClient.isLoggedIn()) {
            _currentUser.value = null
            return
        }
        val profile = runCatching { repository.me() }.getOrNull() ?: return
        if (!profile.isLoggedIn) {
            _currentUser.value = null
            return
        }
        _currentUser.value = profile
        // 顺手落盘：WebView 里登录的会话此前只存在于内存，重启即丢
        persistSession(profile.uid.orEmpty(), profile.name.orEmpty(), profile.avatarUrl)
    }

    /** 界面/回调用的非挂起版本。 */
    fun refreshCurrentUserAsync() {
        appScope.launch { refreshCurrentUser() }
    }

    /** 将持久化的 Cookie 头写回内存 CookieJar。 */
    private fun restoreCookies(header: String) {
        for (pair in header.split(";")) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isEmpty() || value.isEmpty()) continue
            httpClient.cookieStore.setCookie(httpClient.host, name, value)
        }
    }

    /** 登录成功后调用：把当前会话落盘。 */
    fun persistSession(uid: String, username: String, avatar: String?) {
        appScope.launch {
            sessionStore.saveSession(httpClient.cookieHeader(), uid, username, avatar)
        }
    }

    fun logout() {
        httpClient.cookieStore.clear()
        _currentUser.value = null
        appScope.launch { sessionStore.clearSession() }
    }
}

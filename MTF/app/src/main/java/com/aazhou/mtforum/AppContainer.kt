package com.aazhou.mtforum

import android.app.Application
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.data.repo.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用级依赖容器：手动 DI，避免引入注解处理框架。 */
class AppContainer(application: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionStore = SessionStore(application)
    val themeStore = ThemeStore(application)
    val httpClient = HttpClient()
    val repository = ForumRepository(httpClient)

    init {
        appScope.launch {
            val saved = sessionStore.cookieHeader.first()
            if (saved.isNotEmpty()) restoreCookies(saved)
        }
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
        appScope.launch { sessionStore.clearSession() }
    }
}

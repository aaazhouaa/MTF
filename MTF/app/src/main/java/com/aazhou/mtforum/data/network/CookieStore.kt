package com.aazhou.mtforum.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/** 内存 Cookie 存储，按 host 归类；同名 Cookie 覆盖。 */
class CookieStore : CookieJar {

    private val byHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = byHost.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            put(list, cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = byHost[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return list.filter { it.expiresAt > now }
    }

    @Synchronized
    fun setCookie(host: String, name: String, value: String) {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(host)
            .path("/")
            .expiresAt(Long.MAX_VALUE)
            .build()
        put(byHost.getOrPut(host) { mutableListOf() }, cookie)
    }

    @Synchronized
    fun cookies(host: String): List<Cookie> = byHost[host]?.toList() ?: emptyList()

    /** 序列化为 "name=value; name2=value2" 形式，供持久化或外部客户端复用会话。 */
    @Synchronized
    fun cookieHeader(host: String): String =
        cookies(host).joinToString("; ") { "${it.name}=${it.value}" }

    @Synchronized
    fun clear(host: String? = null) {
        if (host == null) byHost.clear() else byHost.remove(host)
    }

    private fun put(list: MutableList<Cookie>, cookie: Cookie) {
        val idx = list.indexOfFirst { it.name == cookie.name }
        if (idx >= 0) list[idx] = cookie else list.add(cookie)
    }
}

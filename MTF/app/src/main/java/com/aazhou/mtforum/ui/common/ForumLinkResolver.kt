package com.aazhou.mtforum.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 正文链接归类：站点内指向帖子的链接跳到原生帖子详情页，指向用户空间的跳到原生个人主页，
 * 其余（站外地址、站内其它页面）一律交给系统浏览器打开。
 */
object ForumLinkResolver {

    /** 站点帖子地址：/thread-<tid>-<page>-<page>.html。 */
    private val THREAD_PATH = Regex("thread-(\\d+)-\\d+-\\d+\\.html$")

    /** 站点用户空间地址（伪静态）：/space-uid-<uid>.html。 */
    private val SPACE_UID_PATH = Regex("space-uid-(\\d+)\\.html$")

    /** [url] 指向站点内某帖子时返回其 tid，否则返回 null。 */
    fun siteThreadId(url: String?, siteHost: String): String? {
        if (url.isNullOrBlank()) return null
        val target = absolutize(url, siteHost) ?: return null
        if (target.host != siteHost) return null
        THREAD_PATH.find(target.encodedPath)?.let { return it.groupValues[1] }
        // 老形态：forum.php?mod=viewthread&tid=123（tid 还见于 redirect/reply 等帖子上下文）
        if (target.encodedPath.endsWith("forum.php")) {
            return target.queryParameter("tid")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }
        return null
    }

    /**
     * [url] 指向站点内某个用户空间时返回其 uid，否则返回 null。
     *
     * 评论里 @ 到某人后，站点把「@用户名」烘成指向该用户空间的链接，故认用户空间即覆盖全部 @。
     * 两种形态都要认：伪静态 space-uid-N.html 与 home.php?mod=space&uid=N（带 do=profile 等参数）。
     * mod 必须是 space：spacecp 下的操作链接（加好友、备注）也带 uid，但不是用户空间。
     */
    fun siteUserId(url: String?, siteHost: String): String? {
        if (url.isNullOrBlank()) return null
        val target = absolutize(url, siteHost) ?: return null
        if (target.host != siteHost) return null
        SPACE_UID_PATH.find(target.encodedPath)?.let { return it.groupValues[1] }
        if (target.encodedPath.endsWith("home.php") && target.queryParameter("mod") == "space") {
            return target.queryParameter("uid")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }
        return null
    }

    /** 正文 href 可能是相对地址，需先按站点根补全再解析。 */
    private fun absolutize(url: String, siteHost: String): HttpUrl? =
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url.toHttpUrlOrNull()
        } else {
            "https://$siteHost/${url.removePrefix("/")}".toHttpUrlOrNull()
        }
}

/**
 * 站内帖子链接在 App 内跳到原生详情页，站内用户空间链接跳到原生个人主页，
 * 其余链接交系统浏览器。[onUserClick] 为空时用户空间链接也走系统浏览器。
 */
fun openForumLink(
    context: Context,
    url: String,
    siteHost: String,
    onThreadClick: (String) -> Unit,
    onUserClick: ((String) -> Unit)? = null,
) {
    val tid = ForumLinkResolver.siteThreadId(url, siteHost)
    if (tid != null) {
        onThreadClick(tid)
        return
    }
    val uid = ForumLinkResolver.siteUserId(url, siteHost)
    if (uid != null && onUserClick != null) {
        onUserClick(uid)
        return
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

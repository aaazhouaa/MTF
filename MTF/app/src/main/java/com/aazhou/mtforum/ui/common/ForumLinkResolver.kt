package com.aazhou.mtforum.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 正文链接归类：站点内指向帖子的链接可在 App 内直接跳到原生帖子详情页，
 * 其余（站外地址、站内非帖子页面）一律交给系统浏览器打开。
 */
object ForumLinkResolver {

    /** 站点帖子地址：/thread-<tid>-<page>-<page>.html。 */
    private val THREAD_PATH = Regex("thread-(\\d+)-\\d+-\\d+\\.html$")

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

    /** 正文 href 可能是相对地址，需先按站点根补全再解析。 */
    private fun absolutize(url: String, siteHost: String): HttpUrl? =
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url.toHttpUrlOrNull()
        } else {
            "https://$siteHost/${url.removePrefix("/")}".toHttpUrlOrNull()
        }
}

/** 站内帖子链接在 App 内跳到原生详情页，其余链接交系统浏览器。 */
fun openForumLink(
    context: Context,
    url: String,
    siteHost: String,
    onThreadClick: (String) -> Unit,
) {
    val tid = ForumLinkResolver.siteThreadId(url, siteHost)
    if (tid != null) {
        onThreadClick(tid)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

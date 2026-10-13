package com.aazhou.mtforum.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Location 头解析：站点给的是带查询串与片段的相对地址，解析错页码就取不出来，
 * 消息提醒的「直达楼层」会静默失效。
 */
class RedirectLocationTest {

    private val request = "https://bbs.binmt.cc/forum.php?mod=redirect&goto=findpost&pid=1&ptid=2"
        .toHttpUrl()

    private fun pageOf(url: String?): String? =
        url?.let { Regex("""[?&]page=(\d+)""").find(it)?.groupValues?.get(1) }

    @Test
    fun relativeLocationKeepsQueryAndFragment() {
        // 站点真实的 Location：相对路径 + 查询串 + 楼层锚点
        val resolved = resolveRedirectLocation(
            request,
            "forum.php?mod=viewthread&tid=174032&page=22&mobile=2#pid11952371",
        )
        assertEquals(
            "https://bbs.binmt.cc/forum.php?mod=viewthread&tid=174032&page=22&mobile=2#pid11952371",
            resolved,
        )
        assertEquals("22", pageOf(resolved))
    }

    @Test
    fun rootRelativeLocation() {
        val resolved = resolveRedirectLocation(request, "/forum.php?mod=viewthread&tid=1&page=3")
        assertEquals("https://bbs.binmt.cc/forum.php?mod=viewthread&tid=1&page=3", resolved)
        assertEquals("3", pageOf(resolved))
    }

    @Test
    fun absoluteLocationUnchanged() {
        val resolved = resolveRedirectLocation(request, "https://bbs.binmt.cc/forum.php?mod=viewthread&tid=9&page=5")
        assertEquals("https://bbs.binmt.cc/forum.php?mod=viewthread&tid=9&page=5", resolved)
        assertEquals("5", pageOf(resolved))
    }

    @Test
    fun locationWithoutPageMeansFirstPage() {
        // 目标楼就在第 1 页时站点不给 page 参数，调用方按 1 算
        val resolved = resolveRedirectLocation(request, "forum.php?mod=viewthread&tid=1&mobile=2#pid5")
        assertEquals("https://bbs.binmt.cc/forum.php?mod=viewthread&tid=1&mobile=2#pid5", resolved)
        assertNull(pageOf(resolved))
    }

    @Test
    fun unresolvableLocationReturnsNull() {
        assertNull(resolveRedirectLocation(request, "http://"))
    }
}

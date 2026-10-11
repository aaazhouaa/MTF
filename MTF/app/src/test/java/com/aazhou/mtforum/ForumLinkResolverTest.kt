package com.aazhou.mtforum

import com.aazhou.mtforum.ui.common.ForumLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumLinkResolverTest {

    private val host = "bbs.binmt.cc"

    @Test
    fun prettyThreadUrlIsOpenedInApp() {
        assertEquals(
            "160969",
            ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/thread-160969-1-1.html", host),
        )
        // 跳到第 3 页的地址同样是帖子
        assertEquals(
            "85763",
            ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/thread-85763-1-3.html", host),
        )
    }

    @Test
    fun legacyForumPhpUrlIsOpenedInApp() {
        assertEquals(
            "160969",
            ForumLinkResolver.siteThreadId(
                "https://bbs.binmt.cc/forum.php?mod=viewthread&tid=160969&extra=page%3D1&mobile=2",
                host,
            ),
        )
    }

    @Test
    fun relativeHrefIsResolvedAgainstSite() {
        assertEquals("123", ForumLinkResolver.siteThreadId("thread-123-1-1.html", host))
        assertEquals(
            "456",
            ForumLinkResolver.siteThreadId("/forum.php?mod=viewthread&tid=456", host),
        )
    }

    @Test
    fun otherHostIsNotTreatedAsInternal() {
        assertNull(ForumLinkResolver.siteThreadId("https://example.com/thread-1-1-1.html", host))
        assertNull(ForumLinkResolver.siteThreadId("https://avatar.mt2.cn/avatar.php?uid=1", host))
    }

    @Test
    fun internalNonThreadPageFallsBackToBrowser() {
        assertNull(ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/forum-2-1.html", host))
        assertNull(ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/forum-37-1.html", host))
        assertNull(
            ForumLinkResolver.siteThreadId(
                "https://bbs.binmt.cc/home.php?mod=space&uid=156549&do=profile",
                host,
            ),
        )
        assertNull(
            ForumLinkResolver.siteThreadId(
                "https://bbs.binmt.cc/forum.php?mod=attachment&aid=9",
                host,
            ),
        )
        // forum.php 上带非数字 tid 时不当成帖子
        assertNull(
            ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/forum.php?mod=forumdisplay&tid=x", host),
        )
    }

    @Test
    fun prettySpaceUrlIsOpenedAsUserProfile() {
        assertEquals("113924", ForumLinkResolver.siteUserId("https://bbs.binmt.cc/space-uid-113924.html", host))
        assertEquals("113924", ForumLinkResolver.siteUserId("space-uid-113924.html", host))
    }

    @Test
    fun homePhpSpaceUrlIsOpenedAsUserProfile() {
        assertEquals(
            "156549",
            ForumLinkResolver.siteUserId("https://bbs.binmt.cc/home.php?mod=space&uid=156549&do=profile", host),
        )
        assertEquals("156549", ForumLinkResolver.siteUserId("home.php?mod=space&uid=156549", host))
    }

    @Test
    fun nonSpaceUrlsAreNotUserProfiles() {
        // spacecp 下的操作链接（加好友、备注、发消息）也带 uid/touid，但不是用户空间
        assertNull(ForumLinkResolver.siteUserId("https://bbs.binmt.cc/home.php?mod=spacecp&ac=friend&op=add&uid=1", host))
        assertNull(ForumLinkResolver.siteUserId("https://bbs.binmt.cc/home.php?mod=space&do=pm&subop=view&touid=1", host))
        assertNull(ForumLinkResolver.siteUserId("https://bbs.binmt.cc/space-uid-.html", host))
        assertNull(ForumLinkResolver.siteUserId("https://example.com/space-uid-1.html", host))
        assertNull(ForumLinkResolver.siteUserId("https://bbs.binmt.cc/thread-160969-1-1.html", host))
        assertNull(ForumLinkResolver.siteUserId(null, host))
    }

    @Test
    fun blankOrBrokenUrlYieldsNull() {
        assertNull(ForumLinkResolver.siteThreadId(null, host))
        assertNull(ForumLinkResolver.siteThreadId("   ", host))
        assertNull(ForumLinkResolver.siteThreadId("https://bbs.binmt.cc/thread--1-1.html", host))
        assertNull(ForumLinkResolver.siteThreadId("not a url", host))
    }
}

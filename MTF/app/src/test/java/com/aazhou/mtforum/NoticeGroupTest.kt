package com.aazhou.mtforum

import com.aazhou.mtforum.data.model.NoticeGroup
import com.aazhou.mtforum.data.model.NoticeTab
import com.aazhou.mtforum.data.model.tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息提醒的分类结构：一级 6 项对齐网页端入口菜单，二级子类对齐每个 view 下的 type Tab。
 *
 * 站点不带 type 的请求只返回默认子类，所以「拿全一类提醒」依赖这里把子类列全。
 */
class NoticeGroupTest {

    @Test
    fun groupsMatchSiteMenu() {
        assertEquals(
            listOf("我的消息", "我的粉丝", "我的帖子", "坛友互动", "系统提醒", "应用提醒"),
            NoticeGroup.entries.map { it.title },
        )
    }

    @Test
    fun myPostHasSixTypes() {
        assertEquals(
            listOf("帖子", "点评", "活动", "悬赏", "商品", "提到我的"),
            NoticeGroup.MY_POST.tabs().map { it.title },
        )
        assertEquals(
            listOf("post", "pcomment", "activity", "reward", "goods", "at"),
            NoticeGroup.MY_POST.tabs().map { it.type },
        )
    }

    @Test
    fun interactiveHasSixTypes() {
        assertEquals(
            listOf("打招呼", "好友", "留言", "评论", "挺你", "分享"),
            NoticeGroup.INTERACTIVE.tabs().map { it.title },
        )
        assertEquals(
            listOf("poke", "friend", "wall", "comment", "click", "sharenotice"),
            NoticeGroup.INTERACTIVE.tabs().map { it.type },
        )
    }

    @Test
    fun systemAndAppHaveNoType() {
        // 这两个 view 没有二级 Tab：站点也不认 type 参数
        assertTrue(NoticeGroup.SYSTEM.tabs().all { it.type == null })
        assertTrue(NoticeGroup.APP.tabs().all { it.type == null })
        assertEquals(listOf("system"), NoticeGroup.SYSTEM.tabs().map { it.view })
        assertEquals(listOf("app"), NoticeGroup.APP.tabs().map { it.view })
    }

    @Test
    fun privateAndAnnounceUsePmFilter() {
        val pmTabs = NoticeGroup.PM.tabs()
        assertEquals(listOf("privatepm", "announcepm"), pmTabs.map { it.pmFilter })
        // 私信两类不走 notice 接口，view 为 null
        assertTrue(pmTabs.all { it.view == null })
    }

    @Test
    fun fansIsNotNoticeData() {
        // 「我的粉丝」在站点菜单里，但它是关注/粉丝列表，不是提醒
        assertTrue(NoticeGroup.FANS.tabs().isEmpty())
    }

    @Test
    fun everyTabKeyIsUnique() {
        // 未读记账与缓存 key 用 name，重名会让两个子类共用一份账
        val keys = NoticeTab.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
    }
}

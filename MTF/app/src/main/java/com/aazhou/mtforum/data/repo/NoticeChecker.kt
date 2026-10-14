package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeTab
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 后台消息轮询：拉一次提醒列表，更新未读状态，返回本次新出现的提醒。
 *
 * 与页面用的 [ForumRepository.notices] 是同一套请求，但这里不取楼层正文摘要——
 * 后台每分钟级的调用不能再顺带下载几个帖子页。推送内容用列表里的原文就够。
 *
 * 提醒列表与私信两类全部并发：这是回前台/后台轮询的公共路径，冷启动早一点拿到
 * 结果，红点就少等一个请求的往返。
 *
 * 私信两类也汇总进全局红点：站点的私信行未读标记是「同一会话又来新消息」唯一可用的
 * 信号（会话 id 不变，id 差集判不出新），取两类之或写入未读账；tab 圆点照旧按分类记。
 *
 * 网络失败向上抛，由调用方决定重试（后台任务）还是忽略（进应用时顺手查一次）。
 */
class NoticeChecker(
    private val repository: ForumRepository,
    private val unreadStore: NoticeUnreadStore,
) {
    suspend fun check(tabs: List<NoticeTab>): List<Notice> {
        val pmTabs = tabs.filter { it.pmFilter != null }
        val noticeTabs = tabs.filter { it.view != null }
        val (notices, pmPages) = coroutineScope {
            val noticesDeferred = async { repository.notices(noticeTabs).notices }
            val pmDeferred = pmTabs.map { tab ->
                async { runCatching { repository.pmMessages(tab.pmFilter!!) }.getOrNull() }
            }
            noticesDeferred.await() to pmDeferred.awaitAll()
        }
        var pmUnread = false
        var pmHasResult = false
        for ((tab, page) in pmTabs.zip(pmPages)) {
            if (page == null || page.loggedOut) continue
            pmHasResult = true
            val ids = page.messages.map { it.plid ?: it.touid ?: it.username }
            unreadStore.onCategoryPolled(tab, ids)
            // 私信行上的站点未读标记：会话 id 不变，差集判不出同一会话的新消息，
            // 只能用站点标记；标记只在真的拿到列表时才刷新，请求失败时保留上次值
            pmUnread = pmUnread || page.messages.any { it.unread }
        }
        if (pmHasResult) unreadStore.setPmUnread(pmUnread)
        if (notices.isEmpty()) return emptyList()
        val freshIds = unreadStore.onPolled(notices.map { it.id }).toSet()
        return notices.filter { it.id in freshIds }
    }
}

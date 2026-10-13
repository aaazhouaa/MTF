package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeTab

/**
 * 后台消息轮询：拉一次提醒列表，更新未读状态，返回本次新出现的提醒。
 *
 * 与页面用的 [ForumRepository.notices] 是同一套请求，但这里不取楼层正文摘要——
 * 后台每分钟级的调用不能再顺带下载几个帖子页。推送内容用列表里的原文就够。
 *
 * 私信两类单独处理：站点自己给私信行做未读标记，客户端只据此记 tab 圆点，
 * 不进全局红点、也不发通知（与既有行为一致）。
 *
 * 网络失败向上抛，由调用方决定重试（后台任务）还是忽略（进应用时顺手查一次）。
 */
class NoticeChecker(
    private val repository: ForumRepository,
    private val unreadStore: NoticeUnreadStore,
) {
    suspend fun check(tabs: List<NoticeTab>): List<Notice> {
        for (tab in tabs) {
            val filter = tab.pmFilter ?: continue
            val page = runCatching { repository.pmMessages(filter) }.getOrNull() ?: continue
            if (page.loggedOut) continue
            val ids = page.messages.map { it.plid ?: it.touid ?: it.username }
            unreadStore.onCategoryPolled(tab, ids)
        }
        val notices = repository.notices(tabs.filter { it.view != null }).notices
        if (notices.isEmpty()) return emptyList()
        val freshIds = unreadStore.onPolled(notices.map { it.id }).toSet()
        return notices.filter { it.id in freshIds }
    }
}

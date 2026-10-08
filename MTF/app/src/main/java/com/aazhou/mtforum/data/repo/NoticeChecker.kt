package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.Notice

/**
 * 后台消息轮询：拉一次提醒列表，更新未读状态，返回本次新出现的提醒。
 *
 * 与页面用的 [ForumRepository.notices] 是同一套请求，但这里不取楼层正文摘要——
 * 后台每分钟级的调用不能再顺带下载几个帖子页。推送内容用列表里的原文就够。
 *
 * 网络失败向上抛，由调用方决定重试（后台任务）还是忽略（进应用时顺手查一次）。
 */
class NoticeChecker(
    private val repository: ForumRepository,
    private val unreadStore: NoticeUnreadStore,
) {
    suspend fun check(): List<Notice> {
        val notices = repository.notices().notices
        if (notices.isEmpty()) return emptyList()
        val fresh = unreadStore.onPolled(notices.map { it.id })
        return if (fresh.isEmpty()) emptyList() else notices
    }
}

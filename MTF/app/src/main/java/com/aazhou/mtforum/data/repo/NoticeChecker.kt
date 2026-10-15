package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.NoticeGroup

/**
 * 后台消息轮询：拉一次「消息提醒」总览页，更新未读状态，返回本次新出现的分类。
 *
 * 未读权威来源是站点总览页各分类入口的 `notice_r` 角标（服务端渲染），一个请求拿全 6 类，
 * 不再逐 view 拉列表。推送内容按分类名提示即可，不必再下载列表正文。
 *
 * 网络失败向上抛，由调用方决定重试（后台任务）还是忽略（进应用时顺手查一次）。
 */
class NoticeChecker(
    private val repository: ForumRepository,
    private val unreadStore: NoticeUnreadStore,
) {
    suspend fun check(): Set<NoticeGroup> {
        val unread = repository.noticeOverview()
        return unreadStore.onOverview(unread)
    }
}

package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.NoticeTab
import com.aazhou.mtforum.data.model.PREFETCH_TABS
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.NoticePollStore
import com.aazhou.mtforum.data.repo.NoticeRepo
import com.aazhou.mtforum.data.repo.NoticeUnreadStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息提醒一级页（网页端「消息提醒」入口菜单）。
 *
 * 自身不展示任何提醒内容，只做两件事：把各子类的未读状态汇成组级圆点，以及管后台
 * 轮询开关。列表数据在二级页 [NoticeTabViewModel] 里按组加载。
 */
class NoticeViewModel(
    private val repository: ForumRepository,
    private val noticeRepo: NoticeRepo,
    private val unreadStore: NoticeUnreadStore,
    private val pollStore: NoticePollStore,
    /** 看过消息页：把 [ids] 记为已读，首页红点随之灭掉。 */
    private val onRead: (List<String>) -> Unit,
) : ViewModel() {

    /** 有未读的子类：一级页按组聚合出圆点。 */
    private val _unreadTabs = MutableStateFlow<Set<NoticeTab>>(emptySet())
    val unreadTabs: StateFlow<Set<NoticeTab>> = _unreadTabs.asStateFlow()

    private val _pollEnabled = MutableStateFlow(false)
    val pollEnabled: StateFlow<Boolean> = _pollEnabled.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            unreadStore.unreadTabs.collect { tabs -> _unreadTabs.value = tabs }
        }
        viewModelScope.launch {
            pollStore.enabled.collect { enabled -> _pollEnabled.value = enabled }
        }
        prefetch()
    }

    /** 并发拉预拉集合：只为了记账，不上屏。 */
    fun prefetch() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = coroutineScope {
                PREFETCH_TABS.map { tab -> async { runCatching { firstPageOf(tab) }.getOrNull() } }
                    .awaitAll()
            }
            val readIds = mutableListOf<String>()
            for ((tab, content) in PREFETCH_TABS.zip(loaded)) {
                if (content == null) continue
                val ids = content.noticeIds()
                // 没被打开的子类也要能亮圆点
                unreadStore.onCategoryPolled(tab, ids)
                // 首页全局红点：三类提醒的 id 记为已读
                if (tab.view != null) readIds += ids
            }
            if (readIds.isNotEmpty()) onRead(readIds.distinct())
        }
    }

    /** 某子类的第一页内容；未登录或请求失败返回 null。 */
    private suspend fun firstPageOf(tab: NoticeTab): NoticeContentState? {
        val filter = tab.pmFilter
        if (filter != null) {
            return repository.pmMessages(filter).takeIf { !it.loggedOut }
                ?.let { NoticeContentState.PrivateMessages(it.messages) }
        }
        val view = tab.view ?: return null
        return noticeRepo.notices(view, tab.type).takeIf { !it.loggedOut }?.let { page ->
            NoticeContentState.Notices(
                items = page.notices.sortedByDescending { recencyOf(it.timeText) },
                page = 1,
                hasNext = 1 < page.totalPages,
            )
        }
    }

    /** 后台轮询开关：由界面写存储，worker 的排期/取消也由界面触发。 */
    fun setPollEnabled(enabled: Boolean) {
        viewModelScope.launch { pollStore.setEnabled(enabled) }
    }
}

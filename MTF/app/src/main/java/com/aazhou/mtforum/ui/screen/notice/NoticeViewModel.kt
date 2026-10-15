package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.NoticeGroup
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.NoticePollStore
import com.aazhou.mtforum.data.repo.NoticeUnreadStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息提醒一级页（网页端「消息提醒」入口菜单）。
 *
 * 自身不展示任何提醒内容，只做两件事：把有未读的一级分类汇成圆点，以及管后台轮询开关。
 * 未读来自站点总览页角标（见 [NoticeUnreadStore]），列表数据在二级页 [NoticeTabViewModel]。
 */
class NoticeViewModel(
    private val repository: ForumRepository,
    private val unreadStore: NoticeUnreadStore,
    private val pollStore: NoticePollStore,
) : ViewModel() {

    /** 有未读的一级分类：一级页据此给各入口亮圆点。 */
    private val _unreadGroups = MutableStateFlow<Set<NoticeGroup>>(emptySet())
    val unreadGroups: StateFlow<Set<NoticeGroup>> = _unreadGroups.asStateFlow()

    private val _pollEnabled = MutableStateFlow(false)
    val pollEnabled: StateFlow<Boolean> = _pollEnabled.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            unreadStore.unreadGroups.collect { groups -> _unreadGroups.value = groups }
        }
        viewModelScope.launch {
            pollStore.enabled.collect { enabled -> _pollEnabled.value = enabled }
        }
        prefetch()
    }

    /** 拉一次总览页，刷新未读分类。进页面与从二级页返回都调用。 */
    fun prefetch() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { repository.noticeOverview() }
                .onSuccess { unreadStore.onOverview(it) }
        }
    }

    /** 后台轮询开关：由界面写存储，worker 的排期/取消也由界面触发。 */
    fun setPollEnabled(enabled: Boolean) {
        viewModelScope.launch { pollStore.setEnabled(enabled) }
    }
}

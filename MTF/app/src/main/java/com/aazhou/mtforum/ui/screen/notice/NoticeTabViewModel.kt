package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeGroup
import com.aazhou.mtforum.data.model.NoticeTab
import com.aazhou.mtforum.data.model.tabs
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.NoticeRepo
import com.aazhou.mtforum.data.repo.NoticeUnreadStore
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 单个子类 tab 的加载状态。 */
data class NoticeTabState(
    val state: UiState<NoticeContentState> = UiState.Loading,
    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = false,
)

/**
 * 消息提醒二级页：一个一级分类下的全部子类。
 *
 * 站点把每类提醒拆成 view+type 若干页，各页数据、翻页、未读都按子类独立记，
 * 与 [com.aazhou.mtforum.ui.screen.follow.FollowListViewModel] 同构。
 */
class NoticeTabViewModel(
    private val group: NoticeGroup,
    private val repository: ForumRepository,
    private val noticeRepo: NoticeRepo,
    private val contentCache: ContentCache,
    private val unreadStore: NoticeUnreadStore,
) : ViewModel() {

    private val tabs: List<NoticeTab> = group.tabs()

    private val _tabStates = MutableStateFlow<Map<NoticeTab, NoticeTabState>>(emptyMap())
    val tabStates: StateFlow<Map<NoticeTab, NoticeTabState>> = _tabStates.asStateFlow()

    /** 操作结果提示（屏蔽成功/失败等）。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _currentTab = MutableStateFlow(tabs.firstOrNull())

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /** 内存快照：切回已看过的 tab 秒开。 */
    private val memoryCache = mutableMapOf<NoticeTab, NoticeContentState>()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // 首帧用磁盘快照顶出来，避免进页面先转圈
        viewModelScope.launch {
            tabs.forEach { tab -> readDiskCache(tab)?.let { memoryCache[tab] = it } }
            if (memoryCache.isNotEmpty()) publishAll(memoryCache)
        }
    }

    /** 加载指定子类（tab 停稳后由界面调用）。 */
    fun load(tab: NoticeTab) {
        if (tab !in tabs) return
        loadJob?.cancel()
        loadMoreJob?.cancel()
        update(tab) { it.copy(loadingMore = false, hasNext = false) }
        loadJob = viewModelScope.launch {
            val cached = memoryCache[tab] ?: readDiskCache(tab)
            if (cached != null) publish(tab, cached)
            reload(tab)
        }
    }

    /** 当前停在哪个子类：只看不加载，加载由 [load] 触发。 */
    fun selectTab(tab: NoticeTab) {
        if (tab !in tabs) return
        _currentTab.value = tab
        val content = memoryCache[tab] ?: (_tabStates.value[tab]?.state as? UiState.Success)?.data
        val ids = content?.noticeIds().orEmpty()
        if (ids.isNotEmpty()) {
            viewModelScope.launch { unreadStore.markCategoryRead(tab, ids) }
        }
    }

    /** 下拉刷新当前子类：回到第 1 页，已追加的页一并作废。 */
    fun refresh(tab: NoticeTab) {
        if (_tabStates.value[tab]?.refreshing == true) return
        loadMoreJob?.cancel()
        update(tab) { it.copy(loadingMore = false) }
        viewModelScope.launch {
            update(tab) { it.copy(refreshing = true) }
            reload(tab)
            update(tab) { it.copy(refreshing = false) }
        }
    }

    private suspend fun reload(tab: NoticeTab) {
        try {
            val content = when (tab.pmFilter) {
                null -> {
                    val view = tab.view ?: return
                    val page = noticeRepo.notices(view, tab.type)
                    if (page.loggedOut) {
                        fail(tab, "请先登录后查看消息提醒")
                        return
                    }
                    NoticeContentState.Notices(
                        items = page.notices.sortedByDescending { recencyOf(it.timeText) },
                        page = 1,
                        hasNext = 1 < page.totalPages,
                    )
                }

                else -> {
                    val page = repository.pmMessages(tab.pmFilter)
                    if (page.loggedOut) {
                        fail(tab, "请先登录后查看消息")
                        return
                    }
                    NoticeContentState.PrivateMessages(page.messages)
                }
            }
            updateContent(tab, content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(tab, e.message ?: "加载失败")
        }
    }

    /** 滑到接近末尾时追加下一页；站点同页条目不重复，仍按 id 去重。 */
    fun loadMore(tab: NoticeTab) {
        val current = _tabStates.value[tab]
        if (current == null || current.loadingMore || !current.hasNext) return
        val data = (current.state as? UiState.Success)?.data as? NoticeContentState.Notices ?: return
        update(tab) { it.copy(loadingMore = true) }
        val view = tab.view ?: return
        loadMoreJob = viewModelScope.launch {
            val next = data.page + 1
            runCatching { noticeRepo.notices(view, tab.type, next) }
                .onSuccess { page ->
                    if (page.loggedOut) {
                        update(tab) { it.copy(loadingMore = false, hasNext = false) }
                        return@onSuccess
                    }
                    // 现读当前列表：翻页可能在首屏请求还在飞时就被触发，用请求前的旧快照
                    // 拼会把首屏刚到手的新数据整块盖掉（新提醒在第 1 页，于是看不到）
                    val latest = (_tabStates.value[tab]?.state as? UiState.Success)?.data
                        as? NoticeContentState.Notices ?: data
                    val merged = (latest.items + page.notices).distinctBy { it.id }
                    // 一条新提醒都没有：站点把页码夹回了已给过的内容，或这一类已经见底。
                    // 此时必须收手，否则每滑到底都会再去要一遍同一页
                    val added = merged.size - latest.items.size
                    updateContent(
                        tab,
                        NoticeContentState.Notices(
                            items = merged.sortedByDescending { recencyOf(it.timeText) },
                            page = next,
                            hasNext = added > 0 && next < page.totalPages,
                        ),
                    )
                }
                .onFailure { update(tab) { it.copy(loadingMore = false, hasNext = false) } }
            update(tab) { it.copy(loadingMore = false) }
        }
    }

    /**
     * 屏蔽某位用户的某类提醒（网页端行内那个「屏蔽」）。
     *
     * 站点是两段式提交，仓库里封装好了；成功后本地把这条移除，用户不用等刷新。
     */
    fun ignore(notice: Notice) {
        val authorid = notice.ignoreAuthorid ?: return
        val type = notice.ignoreType ?: return
        viewModelScope.launch {
            val ok = runCatching { repository.ignoreNotice(authorid, type, "") }.getOrDefault(false)
            if (ok) {
                memoryCache.keys.toList().forEach { tab ->
                    val content = memoryCache[tab] as? NoticeContentState.Notices ?: return@forEach
                    val kept = content.items.filterNot { it.id == notice.id }
                    if (kept.size != content.items.size) {
                        updateContent(tab, content.copy(items = kept))
                    }
                }
                _actionMessage.value = "已屏蔽该用户的此类提醒"
            } else {
                _actionMessage.value = "屏蔽失败，请稍后重试"
            }
        }
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private suspend fun updateContent(tab: NoticeTab, content: NoticeContentState) {
        memoryCache[tab] = content
        publish(tab, content)
        writeDiskCache(tab, content)
        // tab 圆点：新内容里出现没看过的 id 就点亮；正在看的子类顺手记为已读
        val ids = content.noticeIds()
        unreadStore.onCategoryPolled(tab, ids)
        if (_currentTab.value == tab) markRead(tab, ids)
    }

    private suspend fun markRead(tab: NoticeTab, ids: List<String>) {
        if (ids.isEmpty()) return
        unreadStore.markCategoryRead(tab, ids)
    }

    private fun publish(tab: NoticeTab, content: NoticeContentState) {
        val hasNext = (content as? NoticeContentState.Notices)?.hasNext ?: false
        update(tab) { it.copy(state = UiState.Success(content), hasNext = hasNext) }
    }

    private fun publishAll(contents: Map<NoticeTab, NoticeContentState>) {
        contents.forEach { (tab, content) -> publish(tab, content) }
    }

    private fun fail(tab: NoticeTab, message: String) {
        val existing = _tabStates.value[tab]?.state
        if (existing is UiState.Success) return
        update(tab) { it.copy(state = UiState.Error(message)) }
    }

    private fun update(tab: NoticeTab, transform: (NoticeTabState) -> NoticeTabState) {
        val current = _tabStates.value[tab] ?: NoticeTabState()
        _tabStates.value = _tabStates.value + (tab to transform(current))
    }

    private suspend fun readDiskCache(tab: NoticeTab): NoticeContentState? {
        val filter = tab.pmFilter
        return if (filter != null) {
            contentCache.read(CacheKeys.notice(tab.key), CacheKeys.LIST_TTL, allowExpired = true) {
                NoticeContentState.PrivateMessages(cacheJson.decodeFromString(it))
            }
        } else {
            contentCache.read(CacheKeys.notice(tab.key), CacheKeys.LIST_TTL, allowExpired = true) {
                val cached = cacheJson.decodeFromString<CachedNotices>(it)
                NoticeContentState.Notices(cached.notices, cached.page, cached.hasNext)
            }
        }
    }

    private suspend fun writeDiskCache(tab: NoticeTab, content: NoticeContentState) {
        when (content) {
            is NoticeContentState.Notices -> contentCache.write(
                key = CacheKeys.notice(tab.key),
                ttlMillis = CacheKeys.LIST_TTL,
                value = CachedNotices(content.items, content.page, content.hasNext),
                encode = { cacheJson.encodeToString(CachedNotices.serializer(), it) },
            )

            is NoticeContentState.PrivateMessages -> contentCache.write(
                key = CacheKeys.notice(tab.key),
                ttlMillis = CacheKeys.LIST_TTL,
                value = content.items,
                encode = { cacheJson.encodeToString(it) },
            )
        }
    }
}

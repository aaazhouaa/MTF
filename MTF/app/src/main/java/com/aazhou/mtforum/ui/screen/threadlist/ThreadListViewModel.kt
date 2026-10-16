package com.aazhou.mtforum.ui.screen.threadlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.ForumSort
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 单个排序档位（最新/热门/精华/投票）的列表状态。 */
data class ForumTabState(
    val state: UiState<ForumPage> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    val refreshing: Boolean = false,
    /** 第 1 页数据替换上屏的次数，供界面在顶部时把视口拉回最新。 */
    val resetScrollTick: Int = 0,
)

class ThreadListViewModel(
    private val fid: String,
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    /** 各档位的列表状态，key 为档位；未加载过的档位不在表内。 */
    private val _tabStates = MutableStateFlow<Map<ForumSort, ForumTabState>>(emptyMap())
    val tabStates: StateFlow<Map<ForumSort, ForumTabState>> = _tabStates.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /** 各档位追加下一页的 Job：刷新时取消，避免旧页又拼回新拉的第 1 页后面。 */
    private val loadMoreJobs = mutableMapOf<ForumSort, Job>()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /**
     * 首次进入某个档位：命中缓存先上屏，不走全屏 Loading；请求回来原地替换。
     *
     * 已加载过的档位直接返回：切 tab 不重新请求，滚动位置与已翻页的数据都留着。
     */
    fun load(sort: ForumSort) {
        if (_tabStates.value.containsKey(sort)) return
        update(sort) { ForumTabState() }
        viewModelScope.launch {
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(
                CacheKeys.forum(fid, sort.name),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<ForumPage>(it)
            }
            if (cached != null) {
                update(sort) {
                    it.copy(
                        state = UiState.Success(cached),
                        page = cached.page,
                        hasNext = cached.hasNext,
                    )
                }
            }
            // 自动拉取也走下拉刷新的指示器：有缓存时列表已上屏，圆圈转起来才看得到在拉数据
            update(sort) { it.copy(refreshing = true) }
            reload(sort)
            update(sort) { it.copy(refreshing = false) }
        }
    }

    /** 加载失败后的重试：不受「已加载过」限制，直接重拉第一页。 */
    fun retry(sort: ForumSort) {
        viewModelScope.launch { reload(sort) }
    }

    /** 下拉刷新：回到第 1 页，已追加的页一并作废，刷新期间保留旧列表不清屏。 */
    fun refresh(sort: ForumSort) {
        if (_tabStates.value[sort]?.refreshing == true) return
        loadMoreJobs[sort]?.cancel()
        update(sort) { it.copy(loadingMore = false) }
        viewModelScope.launch {
            update(sort) { it.copy(refreshing = true) }
            reload(sort)
            update(sort) { it.copy(refreshing = false) }
        }
    }

    private suspend fun reload(sort: ForumSort) {
        runCatching { repository.forumPage(fid, 1, sort) }
            .onSuccess { data ->
                update(sort) {
                    it.copy(
                        state = UiState.Success(data),
                        page = 1,
                        hasNext = data.hasNext,
                        resetScrollTick = it.resetScrollTick + 1,
                    )
                }
                saveToCache(sort)
            }
            .onFailure { e ->
                // 有缓存就留着旧内容，只提示这次没拿到新的
                if (_tabStates.value[sort]?.state is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                } else {
                    update(sort) { it.copy(state = UiState.Error(e.message ?: "加载失败")) }
                }
            }
    }

    /** 滑到底部时追加下一页；公告只保留第 1 页那份（站点每页 HTML 都含置顶公告）。 */
    fun loadMore(sort: ForumSort) {
        val current = _tabStates.value[sort] ?: return
        if (current.loadingMore || !current.hasNext) return
        if (current.state !is UiState.Success) return
        update(sort) { it.copy(loadingMore = true) }
        loadMoreJobs[sort] = viewModelScope.launch {
            val next = current.page + 1
            runCatching { repository.forumPage(fid, next, sort) }
                .onSuccess { pageData ->
                    val existing = (_tabStates.value[sort]?.state as? UiState.Success)?.data
                    update(sort) {
                        it.copy(
                            state = UiState.Success(
                                existing?.copy(
                                    threads = existing.threads + pageData.threads,
                                    hasNext = pageData.hasNext,
                                ) ?: pageData,
                            ),
                            page = next,
                            hasNext = pageData.hasNext,
                        )
                    }
                    saveToCache(sort)
                }
                .onFailure { update(sort) { it.copy(hasNext = false) } }
            update(sort) { it.copy(loadingMore = false) }
        }
    }

    private fun update(sort: ForumSort, transform: (ForumTabState) -> ForumTabState) {
        val current = _tabStates.value[sort] ?: ForumTabState()
        _tabStates.value = _tabStates.value + (sort to transform(current))
    }

    /** 缓存记的是合并后的完整列表与已加载到的页，命中后翻页状态可续上。 */
    private suspend fun saveToCache(sort: ForumSort) {
        val data = (_tabStates.value[sort]?.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.forum(fid, sort.name),
            ttlMillis = CacheKeys.LIST_TTL,
            value = data,
            encode = { cacheJson.encodeToString(ForumPage.serializer(), it) },
        )
    }
}

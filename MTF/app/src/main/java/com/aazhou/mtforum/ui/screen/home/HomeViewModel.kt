package com.aazhou.mtforum.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class HomeViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    /** 顶部热帖排行（毛玻璃卡片头部用）。 */
    private val _hot = MutableStateFlow<HomeData?>(null)
    val hot: StateFlow<HomeData?> = _hot.asStateFlow()

    /** 帖子卡片流。 */
    private val _state = MutableStateFlow<UiState<List<Thread>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Thread>>> = _state.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasNext = MutableStateFlow(true)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private var page = 1

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    fun load() {
        _hasNext.value = true
        viewModelScope.launch {
            // 有缓存就先摆出来（不走全屏 Loading），网络回来再原地替换。
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(CacheKeys.home(), CacheKeys.LIST_TTL, allowExpired = true) {
                cacheJson.decodeFromString<CachedFeed>(it)
            }
            if (cached != null) {
                _hot.value = cached.hot
                _state.value = UiState.Success(cached.threads)
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            reload()
        }
    }

    /**
     * 下拉刷新：不动 [state]，刷新成功后原地替换列表，失败时保留旧内容。
     *
     * 不复用 [load]：那会把 state 置回 Loading，整页变成全屏转圈，
     * 下拉指示器也会随列表一起被撤掉。
     */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            reload()
            _refreshing.value = false
        }
    }

    private suspend fun reload() {
        // 热帖排行失败不影响主列表
        runCatching { repository.home() }.onSuccess { _hot.value = it }
        _state.value = try {
            val first = repository.homeFeed(1)
            page = 1
            _hasNext.value = first.hasNext
            _state.value = UiState.Success(first.threads)
            saveFeedToCache()
            _state.value
        } catch (e: Exception) {
            // 有缓存就留着旧内容，只提示这次没拿到新的
            if (_state.value is UiState.Success) {
                _actionMessage.value = "新数据获取失败"
                _state.value
            } else {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /** 滑到底部时追加下一页。 */
    fun loadMore() {
        if (_loadingMore.value || !_hasNext.value) return
        if (_state.value !is UiState.Success) return
        _loadingMore.value = true
        viewModelScope.launch {
            val next = page + 1
            runCatching { repository.homeFeed(next) }
                .onSuccess { pageData ->
                    val current = (_state.value as? UiState.Success)?.data.orEmpty()
                    _state.value = UiState.Success(current + pageData.threads)
                    page = next
                    _hasNext.value = pageData.hasNext
                    saveFeedToCache()
                }
                .onFailure { _hasNext.value = false }
            _loadingMore.value = false
        }
    }

    /** 首页缓存 = 热帖排行 + 当前已加载的帖子流（含翻页追加的部分）。 */
    private suspend fun saveFeedToCache() {
        val threads = (_state.value as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.home(),
            ttlMillis = CacheKeys.LIST_TTL,
            value = CachedFeed(hot = _hot.value, threads = threads),
            encode = { cacheJson.encodeToString(CachedFeed.serializer(), it) },
        )
    }
}

/** 首页缓存内容：排行与帖子流一起存，命中时两处同时上屏。 */
@Serializable
private data class CachedFeed(
    val hot: HomeData? = null,
    val threads: List<Thread> = emptyList(),
)

package com.aazhou.mtforum.ui.screen.threadlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ThreadListViewModel(
    private val fid: String,
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ForumPage>>(UiState.Loading)
    val state: StateFlow<UiState<ForumPage>> = _state.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasNext = MutableStateFlow(true)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    var page: Int = 1
        private set

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load(1)
    }

    fun load(target: Int = page) {
        page = target
        _hasNext.value = true
        viewModelScope.launch {
            // 命中缓存先上屏，不走全屏 Loading；请求回来原地替换
            if (target == 1) {
                val cached = contentCache.read(CacheKeys.forum(fid), CacheKeys.LIST_TTL) {
                    cacheJson.decodeFromString<ForumPage>(it)
                }
                if (cached != null) {
                    page = cached.page
                    _state.value = UiState.Success(cached)
                } else if (_state.value !is UiState.Success) {
                    _state.value = UiState.Loading
                }
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            _state.value = try {
                val data = repository.forumPage(fid, target)
                page = target
                saveToCache(data)
                UiState.Success(data)
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
    }

    /** 滑到底部时追加下一页；公告只保留第 1 页那份（站点每页 HTML 都含置顶公告）。 */
    fun loadMore() {
        if (_loadingMore.value || !_hasNext.value) return
        if (_state.value !is UiState.Success) return
        _loadingMore.value = true
        viewModelScope.launch {
            val next = page + 1
            runCatching { repository.forumPage(fid, next) }
                .onSuccess { pageData ->
                    val current = (_state.value as? UiState.Success)?.data
                    _state.value = UiState.Success(
                        current?.copy(threads = current.threads + pageData.threads, hasNext = pageData.hasNext)
                            ?: pageData,
                    )
                    page = next
                    _hasNext.value = pageData.hasNext
                    saveToCache((_state.value as UiState.Success).data)
                }
                .onFailure { _hasNext.value = false }
            _loadingMore.value = false
        }
    }

    /** 缓存记的是合并后的完整列表与已加载到的页，命中后翻页状态可续上。 */
    private suspend fun saveToCache(data: ForumPage) {
        contentCache.write(
            key = CacheKeys.forum(fid),
            value = data,
            encode = { cacheJson.encodeToString(ForumPage.serializer(), it) },
        )
    }
}

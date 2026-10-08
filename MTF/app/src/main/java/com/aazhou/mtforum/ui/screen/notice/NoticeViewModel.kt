package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NoticeViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Notice>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Notice>>> = _state.asStateFlow()

    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    /**
     * 进页面与失败重取的公共入口：有缓存先整份上屏，网络回来原地替换。
     *
     * 与首页/详情同一套做法——断网或风控时也看得见上次的提醒，不必对着一张失败页。
     */
    fun load() {
        viewModelScope.launch {
            val cached = contentCache.read(CacheKeys.notice(), CacheKeys.LIST_TTL) {
                cacheJson.decodeFromString<CachedNotices>(it).notices
            }
            if (cached != null) {
                _state.value = UiState.Success(cached)
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            reload()
        }
    }

    /**
     * 下拉刷新：不动 [state]，成功就原地替换列表，失败保留旧内容。
     *
     * 不复用 [load]：那会在没有缓存时把 state 置回 Loading，整页变成全屏转圈，
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
        val page = try {
            repository.notices()
        } catch (e: Exception) {
            // 有缓存就留着旧内容（只当这次没刷新成功），没有缓存才整页转失败
            if (_state.value !is UiState.Success) {
                _state.value = UiState.Error(e.message ?: "加载失败")
            }
            return
        }
        _state.value = when {
            page.loggedOut -> UiState.Error("请先登录后查看消息提醒")
            // 空列表不落盘：解析失配返回空时不能把上一份好数据覆盖掉
            page.notices.isEmpty() -> UiState.Success(emptyList())
            else -> {
                saveToCache(page.notices)
                UiState.Success(page.notices)
            }
        }
    }

    private suspend fun saveToCache(notices: List<Notice>) {
        contentCache.write(
            key = CacheKeys.notice(),
            value = CachedNotices(notices),
            encode = { cacheJson.encodeToString(CachedNotices.serializer(), it) },
        )
    }
}

/** 消息提醒的缓存内容。 */
@Serializable
private data class CachedNotices(
    val notices: List<Notice> = emptyList(),
)

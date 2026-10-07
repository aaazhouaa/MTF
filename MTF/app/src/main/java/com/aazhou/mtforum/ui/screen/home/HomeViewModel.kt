package com.aazhou.mtforum.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ForumRepository,
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

    private var page = 1

    init {
        load()
    }

    fun load() {
        page = 1
        _state.value = UiState.Loading
        _hasNext.value = true
        viewModelScope.launch {
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
            UiState.Success(first.threads)
        } catch (e: Exception) {
            if (_state.value is UiState.Success) _state.value else UiState.Error(e.message ?: "加载失败")
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
                }
                .onFailure { _hasNext.value = false }
            _loadingMore.value = false
        }
    }
}

package com.aazhou.mtforum.ui.screen.threadlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThreadListViewModel(
    private val fid: String,
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ForumPage>>(UiState.Loading)
    val state: StateFlow<UiState<ForumPage>> = _state.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasNext = MutableStateFlow(true)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    var page: Int = 1
        private set

    init {
        load(1)
    }

    fun load(target: Int = page) {
        page = target
        _state.value = UiState.Loading
        _hasNext.value = true
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(repository.forumPage(fid, target))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
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
                }
                .onFailure { _hasNext.value = false }
            _loadingMore.value = false
        }
    }
}

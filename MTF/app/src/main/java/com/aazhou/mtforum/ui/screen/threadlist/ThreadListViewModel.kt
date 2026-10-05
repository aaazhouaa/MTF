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

    var page: Int = 1
        private set

    init {
        load(1)
    }

    fun load(target: Int = page) {
        page = target
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(repository.forumPage(fid, target))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun nextPage() {
        val current = _state.value
        if (current is UiState.Success && current.data.hasNext) load(page + 1)
    }

    fun prevPage() {
        if (page > 1) load(page - 1)
    }
}

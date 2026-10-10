package com.aazhou.mtforum.ui.screen.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForumListViewModel(
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ForumGroup>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ForumGroup>>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(repository.forumGroups())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val groups = repository.forumGroups()
                _state.value = UiState.Success(groups)
            } catch (e: Exception) {
                if (_state.value !is UiState.Success) {
                    _state.value = UiState.Error(e.message ?: "刷新失败")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

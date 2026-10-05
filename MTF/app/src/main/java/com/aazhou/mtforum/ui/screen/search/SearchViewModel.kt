package com.aazhou.mtforum.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SearchResult>?>(null)
    val state: StateFlow<UiState<SearchResult>?> = _state.asStateFlow()

    var keyword: String = ""
        private set
    var page: Int = 1
        private set

    fun search(keyword: String, page: Int = 1) {
        if (keyword.isBlank()) return
        this.keyword = keyword.trim()
        this.page = page
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(repository.search(this@SearchViewModel.keyword, page))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "搜索失败")
            }
        }
    }
}

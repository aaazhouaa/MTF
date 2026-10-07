package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoticeViewModel(
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Notice>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Notice>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val page = repository.notices()
                when {
                    page.loggedOut -> UiState.Error("请先登录后查看消息提醒")
                    page.notices.isEmpty() -> UiState.Success(emptyList())
                    else -> UiState.Success(page.notices)
                }
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }
}

package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.repo.ForumRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「@朋友」选择页状态：关键字为空时显示好友 + 关注，否则显示全局搜索结果。 */
data class AtUserPickerState(
    val loadingLists: Boolean = true,
    val friends: List<AtUser> = emptyList(),
    val follows: List<AtUser> = emptyList(),
    val searching: Boolean = false,
    val results: List<AtUser> = emptyList(),
)

class AtUserPickerViewModel(private val repository: ForumRepository) : ViewModel() {

    private val _state = MutableStateFlow(AtUserPickerState())
    val state: StateFlow<AtUserPickerState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val friends = runCatching { repository.fetchFriends() }.getOrDefault(emptyList())
            val follows = runCatching { repository.fetchFollows() }.getOrDefault(emptyList())
            _state.update { it.copy(loadingLists = false, friends = friends, follows = follows) }
        }
    }

    /** 输入即搜：连续敲键只认最后一次，[SEARCH_DEBOUNCE_MS] 内的中间态不发请求。 */
    fun search(keyword: String) {
        searchJob?.cancel()
        val trimmed = keyword.trim()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (trimmed.isEmpty()) {
                _state.update { it.copy(searching = false, results = emptyList()) }
                return@launch
            }
            _state.update { it.copy(searching = true) }
            val results = runCatching { repository.searchUsers(trimmed) }.getOrDefault(emptyList())
            _state.update { it.copy(searching = false, results = results) }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

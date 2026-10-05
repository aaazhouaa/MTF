package com.aazhou.mtforum.ui.screen.postedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.repo.ForumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostEditViewModel(
    private val fid: String,
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PostState>(PostState.Idle)
    val state: StateFlow<PostState> = _state.asStateFlow()

    val canPost: Boolean get() = repository.isLoggedIn()

    fun submit(subject: String, message: String) {
        if (!repository.isLoggedIn()) {
            _state.value = PostState.Error("请先登录")
            return
        }
        if (subject.isBlank() || message.isBlank()) {
            _state.value = PostState.Error("标题和内容不能为空")
            return
        }
        _state.value = PostState.Sending
        viewModelScope.launch {
            try {
                val formhash = repository.postFormhash(fid)
                if (formhash.isNullOrBlank()) {
                    _state.value = PostState.Error("无法获取发帖令牌，请稍后重试")
                    return@launch
                }
                repository.newThread(fid, formhash, subject.trim(), message)
                _state.value = PostState.Success
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "发帖失败")
            }
        }
    }

    fun consume() {
        _state.value = PostState.Idle
    }

    sealed interface PostState {
        data object Idle : PostState
        data object Sending : PostState
        data object Success : PostState
        data class Error(val message: String) : PostState
    }
}

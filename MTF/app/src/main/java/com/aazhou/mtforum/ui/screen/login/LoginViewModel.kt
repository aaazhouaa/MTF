package com.aazhou.mtforum.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error("请输入用户名和密码")
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.login(username.trim(), password)
                if (!result.success) {
                    _state.value = LoginUiState.Error(result.message ?: "登录失败")
                    return@launch
                }
                val profile = repository.me()
                if (!profile.isLoggedIn) {
                    _state.value = LoginUiState.Error("登录未生效，请重试")
                    return@launch
                }
                sessionStore.saveSession(
                    cookieHeader = repository.sessionCookieHeader(),
                    uid = profile.uid.orEmpty(),
                    username = profile.name.orEmpty(),
                    avatar = profile.avatarUrl,
                )
                _state.value = LoginUiState.Success
            } catch (e: Exception) {
                _state.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun reset() {
        _state.value = LoginUiState.Idle
    }

    sealed interface LoginUiState {
        data object Idle : LoginUiState
        data object Loading : LoginUiState
        data object Success : LoginUiState
        data class Error(val message: String) : LoginUiState
    }
}

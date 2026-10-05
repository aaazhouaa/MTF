package com.aazhou.mtforum.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.repo.ForumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ForumRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        load()
    }

    fun load() {
        _loading.value = true
        viewModelScope.launch {
            _profile.value = try {
                repository.me()
            } catch (e: Exception) {
                UserProfile()
            }
            _loading.value = false
        }
    }

    fun loggedIn(): Boolean = repository.isLoggedIn()
}

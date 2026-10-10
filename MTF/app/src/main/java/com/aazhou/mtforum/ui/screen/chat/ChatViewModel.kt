package com.aazhou.mtforum.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.ChatMessage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val touid: String,
    initialTargetName: String?,
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ChatConversation>>(UiState.Loading)
    val state: StateFlow<UiState<ChatConversation>> = _state.asStateFlow()

    private val _targetName = MutableStateFlow(initialTargetName?.ifBlank { null } ?: "聊天")
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val myAvatarUrl: StateFlow<String?> = sessionStore.avatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val conversation = repository.chatConversation(touid)
                conversation.targetName?.takeIf { it.isNotBlank() }?.let {
                    _targetName.value = it
                }
                UiState.Success(conversation)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载聊天记录失败")
            }
        }
    }

    fun sendMessage(text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return
        _isSending.value = true

        viewModelScope.launch {
            try {
                val currentConv = (_state.value as? UiState.Success)?.data
                var formhash = currentConv?.formhash
                if (formhash.isNullOrBlank()) {
                    formhash = runCatching { repository.userProfile(touid).formhash }.getOrNull()
                }
                if (formhash.isNullOrBlank()) {
                    AppToast.show("获取发送凭据失败，请重试")
                    _isSending.value = false
                    return@launch
                }

                val result = repository.sendPrivateMessage(touid, formhash, trimmed)
                if (result.isSuccess) {
                    onSent()
                    // 乐观更新：在当前会话末尾追加自己的新消息
                    val currentList = currentConv?.messages.orEmpty()
                    val newMessage = ChatMessage(
                        id = "temp_${System.currentTimeMillis()}",
                        text = trimmed,
                        time = "刚刚",
                        isSelf = true,
                        avatarUrl = myAvatarUrl.value,
                    )
                    val updatedConv = currentConv?.copy(
                        messages = currentList + newMessage,
                    ) ?: ChatConversation(
                        touid = touid,
                        targetName = _targetName.value,
                        messages = listOf(newMessage),
                        formhash = formhash,
                    )
                    _state.value = UiState.Success(updatedConv)

                    // 静默拉取最新服务端状态
                    runCatching {
                        val refreshed = repository.chatConversation(touid)
                        _state.value = UiState.Success(refreshed)
                    }
                } else {
                    AppToast.show(result.exceptionOrNull()?.message ?: "发送失败")
                }
            } catch (e: Exception) {
                AppToast.show(e.message ?: "发送失败")
            } finally {
                _isSending.value = false
            }
        }
    }
}

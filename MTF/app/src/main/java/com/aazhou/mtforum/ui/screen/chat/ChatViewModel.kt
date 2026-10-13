package com.aazhou.mtforum.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.ChatMessage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.UiState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 输入里的贴图标记：`[img]地址[/img]`，与发送时插入的形式一致。 */
private val RE_IMG_TAG = Regex("""\[img\](\S+?)\[/img\]""", RegexOption.IGNORE_CASE)

class ChatViewModel(
    val touid: String,
    initialTargetName: String?,
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ChatConversation>>(UiState.Loading)
    val state: StateFlow<UiState<ChatConversation>> = _state.asStateFlow()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val _targetName = MutableStateFlow(initialTargetName?.ifBlank { null } ?: "聊天")
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    private val _targetAvatarUrl = MutableStateFlow<String?>(null)
    val targetAvatarUrl: StateFlow<String?> = _targetAvatarUrl.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    /** 发送失败需回填到输入框的文本（一次性，界面消费后调 [consumeFailedSend]）。 */
    private val _failedSend = MutableStateFlow<String?>(null)
    val failedSend: StateFlow<String?> = _failedSend.asStateFlow()

    fun consumeFailedSend() {
        _failedSend.value = null
    }

    /** 贴图上传凭据（uid + hash）：会话级，取到一次就能一直用，懒加载。 */
    private var uploadCredentials: Pair<String, String>? = null

    val myAvatarUrl: StateFlow<String?> = sessionStore.avatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // 先用磁盘快照秒开历史记录，后台再拉最新覆盖
            val cached = readDiskCache()
            if (cached != null) {
                applyConversation(cached)
            } else {
                _state.value = UiState.Loading
            }
            reload()
        }
    }

    private suspend fun reload() {
        try {
            val conversation = repository.chatConversation(touid)
            applyConversation(conversation)
            writeDiskCache(conversation)
        } catch (e: Exception) {
            // 已有缓存顶着就别清屏，只在什么都没拿到时才报错
            if ((_state.value as? UiState.Success) == null) {
                _state.value = UiState.Error(e.message ?: "加载聊天记录失败")
            }
        }
    }

    private fun applyConversation(conversation: ChatConversation) {
        conversation.targetName?.takeIf { it.isNotBlank() }?.let {
            _targetName.value = it
        }
        val convAvatar = conversation.messages.firstOrNull { !it.isSelf && !it.avatarUrl.isNullOrBlank() }?.avatarUrl
        if (!convAvatar.isNullOrBlank()) {
            _targetAvatarUrl.value = convAvatar
        } else if (_targetAvatarUrl.value.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { repository.userProfile(touid).avatarUrl }
                    .getOrNull()?.takeIf { it.isNotBlank() }?.let {
                        _targetAvatarUrl.value = it
                    }
            }
        }
        _state.value = UiState.Success(conversation)
    }

    private suspend fun readDiskCache(): ChatConversation? =
        contentCache.read(CacheKeys.chat(touid), CacheKeys.LIST_TTL, allowExpired = true) {
            cacheJson.decodeFromString<ChatConversation>(it)
        }

    private suspend fun writeDiskCache(conversation: ChatConversation) {
        contentCache.write(
            key = CacheKeys.chat(touid),
            ttlMillis = CacheKeys.LIST_TTL,
            value = conversation,
            encode = { cacheJson.encodeToString(ChatConversation.serializer(), it) },
        )
    }

    fun sendMessage(text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return
        _isSending.value = true
        // 立刻用自己的输入占屏、并清空输入框：发送往返要等站点回执，等回来再上屏会明显迟一步
        onSent()
        val pendingId = "pending_${System.currentTimeMillis()}"
        val pending = ChatMessage(
            id = pendingId,
            text = trimmed,
            contentHtml = localContentHtml(trimmed),
            time = "刚刚",
            isSelf = true,
            avatarUrl = myAvatarUrl.value,
            sending = true,
        )
        val before = (_state.value as? UiState.Success)?.data
        val baseList = before?.messages.orEmpty()
        _state.value = UiState.Success(
            before?.copy(messages = baseList + pending) ?: ChatConversation(
                touid = touid,
                targetName = _targetName.value,
                messages = listOf(pending),
            ),
        )

        viewModelScope.launch {
            try {
                val currentConv = (_state.value as? UiState.Success)?.data
                var formhash = currentConv?.formhash
                if (formhash.isNullOrBlank()) {
                    formhash = runCatching { repository.userProfile(touid).formhash }.getOrNull()
                }
                if (formhash.isNullOrBlank()) {
                    AppToast.show("获取发送凭据失败，请重试")
                    rollbackPending(pendingId, trimmed)
                    return@launch
                }

                val result = repository.sendPrivateMessage(touid, formhash, trimmed)
                if (result.isSuccess) {
                    // 静默拉取最新服务端状态；拉不到就留下本地那条已发送的消息
                    runCatching {
                        val refreshed = repository.chatConversation(touid)
                        _state.value = UiState.Success(refreshed)
                        writeDiskCache(refreshed)
                    }.onFailure {
                        dropSendingFlag(pendingId)
                    }
                } else {
                    AppToast.show(result.exceptionOrNull()?.message ?: "发送失败")
                    rollbackPending(pendingId, trimmed)
                }
            } catch (e: Exception) {
                AppToast.show(e.message ?: "发送失败")
                rollbackPending(pendingId, trimmed)
            } finally {
                _isSending.value = false
            }
        }
    }

    /** 把本地那条置为已发送（服务端刷新失败时用，避免气泡永远转圈）。 */
    private fun dropSendingFlag(pendingId: String) {
        val conv = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(
            conv.copy(
                messages = conv.messages.map {
                    if (it.id == pendingId) it.copy(sending = false) else it
                },
            ),
        )
    }

    /**
     * 发送失败：把乐观插入的气泡打回输入框，不留在会话里。
     *
     * 文本经 [failedSend] 交给界面回填；气泡当场移除，否则刷新时它会与真正的
     * 服务端消息共存。
     */
    private fun rollbackPending(pendingId: String, failedMessage: String) {
        val conv = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(
            conv.copy(messages = conv.messages.filterNot { it.id == pendingId }),
        )
        _failedSend.value = failedMessage
    }

    /**
     * 私聊贴图：选完图先把图传到站点附件，再拿 CDN 地址回来（私聊没有附件，
     * 消息里只能插 `[img]地址[/img]`）。上传失败返回 null，由界面提示。
     */
    fun uploadChatImage(file: File, onResult: (String?) -> Unit) {
        if (_isUploading.value) return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val credentials = uploadCredentials
                    ?: repository.fetchUploadCredentials()?.also { uploadCredentials = it }
                if (credentials == null) {
                    AppToast.show("上传凭据获取失败，请重试")
                    onResult(null)
                    return@launch
                }
                val upload = repository.uploadImage(file, credentials.first, credentials.second)
                if (upload == null) {
                    AppToast.show("上传失败，可能超出大小限制")
                }
                onResult(upload?.url)
            } catch (e: Exception) {
                AppToast.show(e.message ?: "上传失败")
                onResult(null)
            } finally {
                _isUploading.value = false
            }
        }
    }

    /**
     * 本地乐观上屏那条消息的 HTML：把输入里的 `[img]地址[/img]` 先变成图片，
     * 气泡立刻就是终态图，不必等站点回执把 BBCode 渲染成图后再翻一次。
     */
    private fun localContentHtml(text: String): String? {
        if (!RE_IMG_TAG.containsMatchIn(text)) return null
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return RE_IMG_TAG.replace(escaped) { match ->
            "<img src=\"${match.groupValues[1].trim()}\" />"
        }
    }
}


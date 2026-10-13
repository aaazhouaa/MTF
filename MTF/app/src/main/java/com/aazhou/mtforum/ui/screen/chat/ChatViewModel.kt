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

/** 正文（本地 BBCode 原文或站点 HTML）里的图片地址。 */
private val RE_CONTENT_IMG = Regex("""<img[^>]*?src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)

/** 站点表情码 `[#呵呵]`：站点渲染成行内小图，比对消息时按不存在处理。 */
private val RE_SMILEY_CODE = Regex("""\[#[^\]]+\]""")

/** 普通 BBCode 标签 `[b]`、`[/b]` 等。 */
private val RE_BBCODE_TAG = Regex("""\[/?[a-zA-Z*][^\]]*\]""")

/**
 * 会话页消息的「身份」，用于把本地消息与站点快照里的同一条对上。
 *
 * 站点不给消息 id（实测消息节点无 id），时间只有「上午 06:45:45」这种文本，无法按
 * 标识或时间戳比对；正文是唯一可用的线索。同一条消息在本地与站点两个形态下正文不同：
 * 本地是自己输入的 BBCode 原文（`[img]网址[/img] 测试`），站点是渲染后的 HTML
 * （`<img src="网址"> 测试`）。故统一归一成「图片地址集合 + 去标签去表情去空白后的文本」：
 * 图片地址已过滤表情小图，文本压掉 `&nbsp;` 与所有空白，两个形态才能落到同一个键上。
 */
internal fun chatMessageIdentity(message: ChatMessage): String {
    val urls = LinkedHashSet<String>()
    RE_IMG_TAG.findAll(message.text).forEach { urls += it.groupValues[1].replace("&amp;", "&") }
    val html = message.contentHtml
    if (!html.isNullOrBlank()) {
        RE_CONTENT_IMG.findAll(html).forEach { match ->
            val src = match.groupValues[1].replace("&amp;", "&").trim()
            // 表情图带 static/image/smiley，不是消息正文里的贴图
            if (src.isNotEmpty() && !src.contains("smiley", true) && !src.contains("static/image", true)) {
                urls += src
            }
        }
    }
    val text = message.text
        .replace(RE_IMG_TAG, "")
        .replace(RE_SMILEY_CODE, "")
        .replace(RE_BBCODE_TAG, "")
        .replace(RE_CONTENT_IMG, "")
        .replace("&nbsp;", "")
        .replace("\u00A0", "")
        .filterNot { it.isWhitespace() }
    return urls.joinToString("|") + "\u0000" + text
}

/**
 * 把站点快照与本地消息合成一份会话。
 *
 * 站点快照是全量历史，但**刚发出的那条有同步延迟**：发完立刻取可能还没收录。
 * 原先按「站点自己消息条数是否变多」判断收录，一旦快照没收录（或被本地幽灵消息抬高
 * 基数），判据就再也回不到「已收录」，导致同一条既在快照里又作为本地待确认追加一次
 * （重复），或本地已确认的那条被快照整体覆盖（丢消息）。这里改成逐条按 [chatMessageIdentity]
 * 消耗：能对上的交给快照（顺序与内容以站点为准，对方新消息不丢），对不上的才补回末尾；
 * 用下标记录最后一次命中位置，只补它之后的本地消息，避免把站点已删除的历史又从本地缓存复活。
 * 站点快照里没有任何自己的消息时没有锚点，就地保留本地全部自己消息；一条都对不上
 * （站点改了正文渲染等）时保守处理：只补本地待确认的（[ChatMessage.sending]）。
 */
internal fun mergeChatMessages(remote: ChatConversation, local: List<ChatMessage>): ChatConversation {
    val localSelf = local.filter { it.isSelf }
    if (localSelf.isEmpty()) return remote
    val siteSelf = remote.messages.filter { it.isSelf }
    val siteKeys = siteSelf.map(::chatMessageIdentity).toMutableList()

    var lastMatched = -1
    localSelf.forEachIndexed { index, message ->
        val at = siteKeys.indexOf(chatMessageIdentity(message))
        if (at >= 0) {
            siteKeys.removeAt(at)
            lastMatched = index
        }
    }
    val tail = when {
        // 快照里一条自己的消息都没有：新会话刚发出一条。没有可对齐的锚点，就地保留，
        // 否则本地唯一那条（已非 sending）会被当成站点的旧历史丢掉
        siteSelf.isEmpty() -> localSelf
        lastMatched >= 0 -> localSelf.subList(lastMatched + 1, localSelf.size)
        // 有站点消息但一条都对不上（站点改了正文渲染等）：只补待确认的，不复活整段本地缓存
        else -> localSelf.filter { it.sending }
    }
    if (tail.isEmpty()) return remote
    return remote.copy(messages = remote.messages + tail.map { it.copy(sending = false) })
}

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
            // 与发送回执同样走按身份合并：磁盘快照里可能有一条站点还没来得及收录的自己消息，
            // 直接拿网络快照整体替换会把它抹掉（表现为重进会话就少一条）
            val local = (_state.value as? UiState.Success)?.data?.messages.orEmpty()
            val merged = mergeChatMessages(conversation, local)
            applyConversation(merged)
            writeDiskCache(merged)
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
                    // 站点会话页对刚发的这条有同步延迟：网页端发完也是把气泡直接插进页面、
                    // 再靠轮询增量补齐。此刻拿快照整体替换会把刚上屏的气泡抹掉，表现为
                    // 发完不显示、退出重进才回来。改走按身份去重的合并：站点还没收录的本地
                    // 消息保留在末尾，已收录的交给快照，既不重复也不丢。
                    runCatching {
                        val refreshed = repository.chatConversation(touid)
                        val local = (_state.value as? UiState.Success)?.data?.messages.orEmpty()
                        val merged = mergeChatMessages(refreshed, local)
                        _state.value = UiState.Success(merged)
                        writeDiskCache(merged)
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


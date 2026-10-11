package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.download.ThreadArchiveStore
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.ThreadSnapshot
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.isWaterReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 已下载帖子的只读 ViewModel：从应用内存档读快照，装配成与在线详情同构的一页。
 *
 * 灌水折叠判定复用在线详情的同一套规则，两个入口看到的折叠条数一致。
 * 存档只读，没有任何网络与会话态字段。
 */
class OfflineThreadViewModel(
    private val tid: String,
    private val archiveStore: ThreadArchiveStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ThreadPage>>(UiState.Loading)
    val state: StateFlow<UiState<ThreadPage>> = _state.asStateFlow()

    private val _waterPids = MutableStateFlow<Set<String>>(emptySet())
    val waterPids: StateFlow<Set<String>> = _waterPids.asStateFlow()

    private val _foldExpanded = MutableStateFlow(false)
    val foldExpanded: StateFlow<Boolean> = _foldExpanded.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = archiveStore.read(tid)
            if (snapshot == null) {
                _state.value = UiState.Error("存档不存在或已被删除")
                return@launch
            }
            val page = snapshot.toThreadPage(tid)
            val water = withContext(Dispatchers.Default) {
                val op = page.posts.firstOrNull { it.isFirstPost } ?: page.posts.firstOrNull()
                page.posts.filter { it !== op && isWaterReply(it.contentHtml) }.map { it.pid }.toSet()
            }
            _state.value = UiState.Success(page)
            _waterPids.value = water
        }
    }

    /** 展开/收起灌水回复。 */
    fun toggleFoldExpanded() {
        _foldExpanded.value = !_foldExpanded.value
    }
}

/** 存档快照转详情页结构；页码按单页计，会话态字段一概不需要。 */
private fun ThreadSnapshot.toThreadPage(tid: String): ThreadPage = ThreadPage(
    tid = tid,
    subject = subject,
    forumName = forumName,
    posts = posts,
    page = 1,
    totalPages = 1,
    formhash = null,
    replyUrl = null,
    fid = fid,
    opUid = opUid,
    totalReplies = totalReplies,
)

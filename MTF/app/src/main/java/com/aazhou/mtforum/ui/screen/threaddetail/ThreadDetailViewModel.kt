package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThreadDetailViewModel(
    private val tid: String,
    private val repository: ForumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ThreadPage>>(UiState.Loading)
    val state: StateFlow<UiState<ThreadPage>> = _state.asStateFlow()

    private val _replyState = MutableStateFlow<ReplyState>(ReplyState.Idle)
    val replyState: StateFlow<ReplyState> = _replyState.asStateFlow()

    /** 已折叠楼中楼的楼层 pid。 */
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    val collapsed: StateFlow<Set<String>> = _collapsed.asStateFlow()

    /** 排序：0 正序，1 倒序。 */
    private val _orderType = MutableStateFlow(0)
    val orderType: StateFlow<Int> = _orderType.asStateFlow()

    /** 只看楼主。 */
    private val _opOnly = MutableStateFlow(false)
    val opOnly: StateFlow<Boolean> = _opOnly.asStateFlow()

    /** 灌水回复是否展开显示（默认折叠）。 */
    private val _foldExpanded = MutableStateFlow(false)
    val foldExpanded: StateFlow<Boolean> = _foldExpanded.asStateFlow()

    /** 当前回复目标：null 表示回复主题，否则为该楼层。 */
    private val _replyTarget = MutableStateFlow<Post?>(null)
    val replyTarget: StateFlow<Post?> = _replyTarget.asStateFlow()

    private val _followState = MutableStateFlow<FollowState>(FollowState.Idle)
    val followState: StateFlow<FollowState> = _followState.asStateFlow()

    /** 点赞状态与计数（客户端维护，初始取站点计数）。 */
    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()
    private val _likeCount = MutableStateFlow(0)
    val likeCount: StateFlow<Int> = _likeCount.asStateFlow()
    private var likeInitialized = false

    /** 收藏状态与计数。 */
    private val _favorited = MutableStateFlow(false)
    val favorited: StateFlow<Boolean> = _favorited.asStateFlow()
    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /** 是否正在加载下一页评论。 */
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /** 当前展示的页码。 */
    var page: Int = 1
        private set

    /** 是否还有下一页。 */
    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    init {
        load(1)
    }

    /** 加载指定页；target 为 1 时视为刷新（重置列表）。 */
    fun load(target: Int = page) {
        // 只看楼主与排序都由站点参数决定，必须重新请求；authorid 取自上一次加载的结果
        val authorId = if (_opOnly.value) (_state.value as? UiState.Success)?.data?.opUid else null
        page = target
        _state.value = UiState.Loading
        _collapsed.value = emptySet()
        _foldExpanded.value = false
        viewModelScope.launch {
            _state.value = try {
                val data = repository.threadPage(tid, target, _orderType.value, authorId)
                if (!likeInitialized) {
                    _likeCount.value = data.likeCount ?: 0
                    _favoriteCount.value = data.favoriteCount ?: 0
                    likeInitialized = true
                }
                _hasNext.value = target < data.totalPages
                UiState.Success(data)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /**
     * 追加下一页评论：把新页的楼层并入当前列表（而非切页重置），
     * 失败时保持原状并提示。
     */
    fun loadMore() {
        if (_loadingMore.value || !_hasNext.value) return
        val current = _state.value as? UiState.Success ?: return
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val next = page + 1
                val nextData = repository.threadPage(tid, next, _orderType.value, null)
                val merged = current.data.copy(
                    posts = current.data.posts + nextData.posts.filter { p ->
                        current.data.posts.none { it.pid == p.pid }
                    },
                    totalPages = nextData.totalPages,
                )
                page = next
                _hasNext.value = next < nextData.totalPages
                _state.value = UiState.Success(merged)
            } catch (e: Exception) {
                _actionMessage.value = e.message ?: "加载更多失败"
            }
            _loadingMore.value = false
        }
    }

    fun setOrder(orderType: Int) {
        if (_orderType.value == orderType) return
        _orderType.value = orderType
        load(1)
    }

    fun setOpOnly(opOnly: Boolean) {
        if (_opOnly.value == opOnly) return
        _opOnly.value = opOnly
        load(1)
    }

    fun toggleReplies(pid: String) {
        _collapsed.value = if (_collapsed.value.contains(pid)) {
            _collapsed.value - pid
        } else {
            _collapsed.value + pid
        }
    }

    /** 展开/收起灌水回复。 */
    fun toggleFoldExpanded() {
        _foldExpanded.value = !_foldExpanded.value
    }

    /** 设置回复目标；传 null 表示取消引用回复。 */
    fun setReplyTarget(post: Post?) {
        _replyTarget.value = post
    }

    fun toggleFollow() {
        if (_followState.value is FollowState.Sending) return
        val current = _state.value as? UiState.Success ?: return
        val uid = current.data.opUid
        val formhash = current.data.formhash
        if (uid.isNullOrBlank() || formhash.isNullOrBlank()) {
            _followState.value = FollowState.Error("缺少关注令牌，请登录后重新进入帖子")
            return
        }
        val following = (_followState.value as? FollowState.Done)?.following ?: false
        _followState.value = FollowState.Sending
        viewModelScope.launch {
            _followState.value = try {
                repository.followTopic(uid, formhash, follow = !following)
                FollowState.Done(!following)
            } catch (e: Exception) {
                FollowState.Error(e.message ?: "操作失败")
            }
        }
    }

    fun consumeFollowState() {
        _followState.value = FollowState.Idle
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /** 点赞：非登录或未拿到 formhash 时返回 false（界面据此提示登录）。 */
    fun toggleLike(): Boolean {
        val current = _state.value as? UiState.Success ?: return false
        val formhash = current.data.formhash
        if (!repository.isLoggedIn() || formhash.isNullOrBlank()) return false
        val target = !_liked.value
        // 乐观更新，失败再回滚
        _liked.value = target
        _likeCount.value = (_likeCount.value + if (target) 1 else -1).coerceAtLeast(0)
        viewModelScope.launch {
            val ok = runCatching { repository.setLike(tid, formhash, target) }.getOrDefault(false)
            if (!ok) {
                _liked.value = !target
                _likeCount.value = (_likeCount.value + if (target) -1 else 1).coerceAtLeast(0)
                _actionMessage.value = if (target) "点赞失败，请稍后重试" else "取消点赞失败"
            }
        }
        return true
    }

    /** 收藏：与点赞同样先做乐观更新。 */
    fun toggleFavorite(): Boolean {
        val current = _state.value as? UiState.Success ?: return false
        val formhash = current.data.formhash
        if (!repository.isLoggedIn() || formhash.isNullOrBlank()) return false
        val target = !_favorited.value
        _favorited.value = target
        _favoriteCount.value = (_favoriteCount.value + if (target) 1 else -1).coerceAtLeast(0)
        viewModelScope.launch {
            val ok = runCatching { repository.setFavorite(tid, formhash, target) }.getOrDefault(false)
            if (!ok) {
                _favorited.value = !target
                _favoriteCount.value = (_favoriteCount.value + if (target) -1 else 1).coerceAtLeast(0)
                _actionMessage.value = if (target) "收藏失败，请稍后重试" else "取消收藏失败"
            }
        }
        return true
    }

    fun reply(message: String) {
        val current = _state.value
        if (current !is UiState.Success) {
            _replyState.value = ReplyState.Error("页面未就绪")
            return
        }
        val formhash = current.data.formhash
        val fid = current.data.fid
        if (formhash.isNullOrBlank() || fid.isNullOrBlank()) {
            _replyState.value = ReplyState.Error("缺少回复令牌，请重新进入帖子")
            return
        }
        val quotePid = _replyTarget.value?.pid
        _replyState.value = ReplyState.Sending
        viewModelScope.launch {
            try {
                val ok = repository.reply(fid, tid, formhash, message, quotePid)
                if (ok) {
                    _replyTarget.value = null
                    _replyState.value = ReplyState.Success
                } else {
                    _replyState.value = ReplyState.Error("回复被拒绝（可能需登录或触发风控）")
                }
            } catch (e: Exception) {
                _replyState.value = ReplyState.Error(e.message ?: "回复失败")
            }
        }
    }

    fun consumeReplyState() {
        _replyState.value = ReplyState.Idle
    }

    sealed interface ReplyState {
        data object Idle : ReplyState
        data object Sending : ReplyState
        data object Success : ReplyState
        data class Error(val message: String) : ReplyState
    }

    sealed interface FollowState {
        data object Idle : FollowState
        data object Sending : FollowState
        data class Done(val following: Boolean) : FollowState
        data class Error(val message: String) : FollowState
    }
}

package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.isWaterReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThreadDetailViewModel(
    private val tid: String,
    private val repository: ForumRepository,
    private val actionStore: ActionStore,
    myUidFlow: Flow<String>,
) : ViewModel() {

    /** 当前登录用户 uid（用于判定某条回复是否本人发表）。 */
    val myUid: StateFlow<String> =
        myUidFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    /** 列表底部状态：Idle 不显示，Loading 转圈，End 到底，Retry 加载失败可点重试。 */
    private val _loadMoreState = MutableStateFlow<LoadMoreState>(LoadMoreState.Idle)
    val loadMoreState: StateFlow<LoadMoreState> = _loadMoreState.asStateFlow()

    /** 楼中楼树（顶层楼层 + 子楼层）；树未算好前为 null。 */
    private val _tree = MutableStateFlow<ReplyTree?>(null)
    val tree: StateFlow<ReplyTree?> = _tree.asStateFlow()

    /** 需要默认折叠的灌水楼层 pid。 */
    private val _waterPids = MutableStateFlow<Set<String>>(emptySet())
    val waterPids: StateFlow<Set<String>> = _waterPids.asStateFlow()

    /** 点赞用户列表；为 null 表示未加载/已关闭。 */
    private val _likeUsers = MutableStateFlow<List<LikeUser>?>(null)
    val likeUsers: StateFlow<List<LikeUser>?> = _likeUsers.asStateFlow()
    private val _likeUsersLoading = MutableStateFlow(false)
    val likeUsersLoading: StateFlow<Boolean> = _likeUsersLoading.asStateFlow()

    private var treeJob: Job? = null

    /** 下拉刷新中。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load(1)
    }

    /** 加载指定页；target 为 1 时视为刷新（重置列表）。 */
    fun load(target: Int = page) {
        // 只看楼主与排序都由站点参数决定，必须重新请求；authorid 取自上一次加载的结果
        val authorId = if (_opOnly.value) (_state.value as? UiState.Success)?.data?.opUid else null
        page = target
        // 已有内容时保持显示（切换排序/只看楼主不清屏、不闪 Loading），请求回来原地替换；
        // 首次进入或失败重试（当前非 Success）才置 Loading 兜底。
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        _tree.value = null
        _waterPids.value = emptySet()
        _collapsed.value = emptySet()
        _foldExpanded.value = false
        viewModelScope.launch {
            _state.value = try {
                val data = repository.threadPage(tid, target, _orderType.value, authorId)
                if (!likeInitialized) {
                    _likeCount.value = data.likeCount ?: 0
                    _favoriteCount.value = data.favoriteCount ?: 0
                    // 登录态下站点给出明确状态，以其为准并落盘；否则沿用本地记录，
                    // 否则已点赞/已收藏的帖子再次进入会被显示成未操作。
                    if (data.likedStateKnown) {
                        _liked.value = data.isLiked
                        actionStore.setLiked(tid, data.isLiked)
                    } else {
                        _liked.value = actionStore.liked(tid) ?: false
                    }
                    if (data.favoritedStateKnown) {
                        _favorited.value = data.isFavorited
                        actionStore.setFavorited(tid, data.isFavorited)
                    } else {
                        _favorited.value = actionStore.favorited(tid) ?: false
                    }
                    likeInitialized = true
                }
                _hasNext.value = target < data.totalPages
                _loadMoreState.value =
                    if (target < data.totalPages) LoadMoreState.Idle else LoadMoreState.End
                UiState.Success(data)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
            refreshDerived()
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
        _loadMoreState.value = LoadMoreState.Loading
        viewModelScope.launch {
            val accumulated = mutableListOf<Post>()
            val known = current.data.posts.mapTo(mutableSetOf()) { it.pid }
            var next = page + 1
            var totalPages = current.data.totalPages
            var pagesFetched = 0
            var failed = false
            // 一次连拉多页凑满一屏：论坛每页条数不定，只拉一页会“一次没几条”，观感卡顿
            try {
                while (pagesFetched < LOAD_PAGE_BATCH && next <= totalPages && accumulated.size < REPLIES_PER_PAGE) {
                    val data = repository.threadPage(tid, next, _orderType.value, null)
                    totalPages = data.totalPages
                    for (p in data.posts) {
                        if (known.add(p.pid)) accumulated.add(p)
                    }
                    page = next
                    next++
                    pagesFetched++
                }
            } catch (e: Exception) {
                failed = true
                // 已有部分页拿到时仍先展示，避免白等一下全丢；完全没拿到才提示
                if (accumulated.isEmpty()) _actionMessage.value = e.message ?: "加载更多失败"
            }
            val merged = current.data.copy(
                posts = current.data.posts + accumulated,
                totalPages = totalPages,
            )
            _state.value = UiState.Success(merged)
            _hasNext.value = page < totalPages
            _loadMoreState.value = when {
                failed && accumulated.isEmpty() -> LoadMoreState.Retry
                page < totalPages -> LoadMoreState.Idle
                else -> LoadMoreState.End
            }
            refreshDerived()
            _loadingMore.value = false
        }
    }

    /**
     * 回复成功后的局部刷新：重新拉第 1 页，用新首页楼层替换旧首页，
     * 其余已加载的后续页按 pid 去重后保留，避免整页回退与丢失已翻页数据。
     */
    fun refreshAfterReply() {
        val current = _state.value as? UiState.Success ?: run { load(1); return }
        val authorId = if (_opOnly.value) current.data.opUid else null
        viewModelScope.launch {
            val fresh = try {
                repository.threadPage(tid, 1, _orderType.value, authorId)
            } catch (e: Exception) {
                _actionMessage.value = e.message ?: "刷新失败"
                return@launch
            }
            val latest = _state.value as? UiState.Success ?: return@launch
            val freshPids = fresh.posts.map { it.pid }.toSet()
            val kept = latest.data.posts.filter { it.pid !in freshPids }
            val totalPages = maxOf(fresh.totalPages, latest.data.totalPages)
            _state.value = UiState.Success(
                fresh.copy(posts = fresh.posts + kept, totalPages = totalPages),
            )
            refreshDerived()
            _hasNext.value = page < totalPages
            _loadMoreState.value =
                if (page < totalPages) LoadMoreState.Idle else LoadMoreState.End
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

    /**
     * 三档分段（楼主/正序/倒序）一次切换：先改状态、再统一请求一次。
     * 避免 onOpOnly(false)+onOrder(x) 两个回调各触发一次 load 造成竞态与重复请求。
     */
    fun setReplyFilter(opOnly: Boolean, orderType: Int) {
        val changed = _opOnly.value != opOnly || _orderType.value != orderType
        if (!changed) return
        _opOnly.value = opOnly
        _orderType.value = orderType
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

    /** 提交举报（[pid] 为空为举报本帖）；理由与说明由弹窗确认后传入。 */
    fun report(pid: String?, reason: String, message: String) {
        val data = (_state.value as? UiState.Success)?.data ?: return
        if (!repository.isLoggedIn() || data.formhash.isNullOrBlank() || data.fid.isNullOrBlank()) {
            _actionMessage.value = "请先登录"
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                repository.reportPost(tid, data.fid, data.formhash, pid, reason, message)
            }.getOrDefault(false)
            _actionMessage.value = if (ok) "举报已提交" else "举报失败，请稍后重试"
        }
    }

    /** 删除自己的回复。 */
    fun deleteReply(pid: String) {
        val data = (_state.value as? UiState.Success)?.data ?: return
        if (!repository.isLoggedIn() || data.formhash.isNullOrBlank()) {
            _actionMessage.value = "请先登录"
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                repository.deletePost(tid, pid, data.formhash)
            }.getOrDefault(false)
            if (ok) {
                // 直接从当前列表移除该楼层并重算树/灌水；
                // 不能调 refreshAfterReply：它的保留式合并会把已删楼层按 pid 加回来。
                val latest = _state.value as? UiState.Success
                if (latest != null) {
                    _state.value = UiState.Success(
                        latest.data.copy(posts = latest.data.posts.filterNot { it.pid == pid }),
                    )
                    refreshDerived()
                }
            }
            _actionMessage.value = if (ok) "已删除该回复" else "删除未生效，站点可能不允许删除"
        }
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

    /**
     * 点赞 / 取消点赞。
     * 取消只撤销帖子上的赞，不退还当日已用的评价次数（站点如此记账）。
     * 非登录或未拿到 formhash 时返回 false（界面据此提示登录）。
     */
    fun toggleLike(): Boolean {
        val current = _state.value as? UiState.Success ?: return false
        val formhash = current.data.formhash
        if (!repository.isLoggedIn() || formhash.isNullOrBlank()) return false
        val target = !_liked.value
        // 乐观更新，失败再回滚；本地记录同步落盘
        _liked.value = target
        _likeCount.value = (_likeCount.value + if (target) 1 else -1).coerceAtLeast(0)
        viewModelScope.launch { actionStore.setLiked(tid, target) }
        viewModelScope.launch {
            val result = runCatching { repository.setLike(tid, formhash, target) }
                .getOrDefault(LikeResult(false))
            if (result.success) {
                // 用服务端返回的总赞数校正，避免本地乐观计数与真实值偏差
                result.serverCount?.let { _likeCount.value = it }
                // 成功不弹提示：图标与计数已反馈状态
            } else {
                _liked.value = !target
                _likeCount.value = (_likeCount.value + if (target) -1 else 1).coerceAtLeast(0)
                actionStore.setLiked(tid, !target)
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
        viewModelScope.launch { actionStore.setFavorited(tid, target) }
        viewModelScope.launch {
            val ok = runCatching { repository.setFavorite(tid, formhash, target) }.getOrDefault(false)
            if (!ok) {
                _favorited.value = !target
                _favoriteCount.value = (_favoriteCount.value + if (target) -1 else 1).coerceAtLeast(0)
                actionStore.setFavorited(tid, !target)
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

    /** 列表底部加载状态。 */
    sealed interface LoadMoreState {
        data object Idle : LoadMoreState
        data object Loading : LoadMoreState
        data object End : LoadMoreState
        data object Retry : LoadMoreState
    }

    /**
     * 下拉刷新：重拉第 1 页并与已加载的后续页按 pid 合并，不置 Loading、不丢失滚动数据。
     */
    fun refresh() {
        if (_isRefreshing.value) return
        val current = _state.value as? UiState.Success
        if (current == null) {
            load(1)
            return
        }
        _isRefreshing.value = true
        val authorId = if (_opOnly.value) current.data.opUid else null
        viewModelScope.launch {
            try {
                val fresh = repository.threadPage(tid, 1, _orderType.value, authorId)
                val latest = _state.value as? UiState.Success
                if (latest == null) {
                    _state.value = UiState.Success(fresh)
                } else {
                    val freshPids = fresh.posts.map { it.pid }.toSet()
                    val kept = latest.data.posts.filter { it.pid !in freshPids }
                    val totalPages = maxOf(fresh.totalPages, latest.data.totalPages)
                    _state.value = UiState.Success(fresh.copy(posts = fresh.posts + kept, totalPages = totalPages))
                    _hasNext.value = page < totalPages
                }
                refreshDerived()
            } catch (e: Exception) {
                _actionMessage.value = e.message ?: "刷新失败"
            }
            _isRefreshing.value = false
        }
    }

    /** 后台重算楼中楼树与灌水集合；楼层数可上百，含 O(n²) 匹配与 Jsoup，不放主线程。 */
    private fun refreshDerived() {
        val data = (_state.value as? UiState.Success)?.data ?: return
        treeJob?.cancel()
        treeJob = viewModelScope.launch(Dispatchers.Default) {
            val op = data.posts.firstOrNull { it.isFirstPost } ?: data.posts.firstOrNull()
            val replies = data.posts.filter { it !== op }
            val built = buildReplyTree(replies)
            // 灌水回复默认折叠；带子回复的不折，否则会连子回复一起藏掉
            val water = built.topLevel
                .filter { isWaterReply(it.contentHtml) && built.children[it.pid].isNullOrEmpty() }
                .map { it.pid }
                .toSet()
            _tree.value = built
            _waterPids.value = water
        }
    }

    /** 打赏（pid 为空为打赏首帖）。 */
    fun reward(pid: String?, amount: Int, message: String?) {
        val data = (_state.value as? UiState.Success)?.data ?: return
        if (!repository.isLoggedIn() || data.formhash.isNullOrBlank()) {
            _actionMessage.value = "请先登录"
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                repository.rewardAuthor(tid, pid, data.formhash, amount, message)
            }.getOrDefault(false)
            _actionMessage.value =
                if (ok) "打赏成功" else "打赏失败（可能今日已评分或积分不足）"
        }
    }

    /** 加载点赞用户列表（免登录）。 */
    fun loadLikeUsers() {
        if (_likeUsersLoading.value) return
        _likeUsersLoading.value = true
        viewModelScope.launch {
            _likeUsers.value = runCatching { repository.likeUsers(tid) }.getOrDefault(emptyList())
            _likeUsersLoading.value = false
        }
    }

    fun clearLikeUsers() {
        _likeUsers.value = null
    }

    sealed interface FollowState {
        data object Idle : FollowState
        data object Sending : FollowState
        data class Done(val following: Boolean) : FollowState
        data class Error(val message: String) : FollowState
    }
}

/** 归属匹配用：去掉 HTML 标签、URL 与全部空白（含不换行空格），便于比较正文快照。 */
private fun normalizeForMatch(s: String?): String = s.orEmpty()
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("https?://\\S+"), "")
    .replace(Regex("[\\s\\u00A0]+"), "")

/** 引用块短于该长度（纯表情/单个词）时区分不了楼层，宁可不匹配。 */
private const val MATCH_MIN_CHARS = 6

/** 楼层树：顶层楼层顺序列表 + 「父 pid -> 子楼层列表」。 */
class ReplyTree(
    val topLevel: List<Post>,
    val children: Map<String, List<Post>>,
    /** 子回复 pid -> 被回复者昵称（仅当回复对象不是顶层楼层时用于展示「回复 xx：」）。 */
    val subReplyTo: Map<String, String> = emptyMap(),
)

private class PostNode(val post: Post) {
    var isSubReply: Boolean = false
    var orderIndex: Int = -1
    val children: MutableList<PostNode> = mutableListOf()
}

/**
 * 把当前页平铺的楼层组织成楼中楼。
 *
 * 站点只有少数引用块带 pid，故按证据强度降级：pid -> 引用正文最长公共子串 -> 昵称。
 * 三者都要求被引楼层排在引用者之前，否则两条互相引用的楼层会因遍历顺序把父子方向弄反。
 */
private fun buildReplyTree(replies: List<Post>): ReplyTree {
    if (replies.isEmpty()) return ReplyTree(emptyList(), emptyMap())
    val nodes = replies.map { PostNode(it) }
    val byPid = HashMap<String, PostNode>()
    val byAuthor = HashMap<String, MutableList<PostNode>>()
    nodes.forEachIndexed { index, node ->
        node.orderIndex = index
        node.post.pid.takeIf { it.isNotBlank() }?.let { byPid[it] = node }
        node.post.authorName.takeIf { it.isNotBlank() }?.let {
            byAuthor.getOrPut(it) { mutableListOf() }.add(node)
        }
    }

    val childrenByParent = HashMap<String, MutableList<Post>>()
    val subReplyTo = HashMap<String, String>()
    for (node in nodes) {
        val quote = node.post.quote ?: continue
        val parent = resolveParent(node, nodes, byPid, byAuthor) ?: continue
        // 被引者本身是子回复时压平到它所属的顶层楼层，同一条子线程内的回复只平铺一层
        val root = if (parent.isSubReply) {
            nodes.firstOrNull { it !== parent && !it.isSubReply && it.children.any { c -> c === parent } }
        } else {
            parent
        } ?: continue
        if (root.orderIndex >= node.orderIndex) continue
        node.isSubReply = true
        root.children.add(node)
        childrenByParent.getOrPut(root.post.pid) { mutableListOf() }.add(node.post)
        // 回复对象不是顶层楼层时，子回复需要标明「回复 xx：」
        if (root !== parent) {
            parent.post.authorName.takeIf { it.isNotBlank() }?.let { subReplyTo[node.post.pid] = it }
        }
    }
    return ReplyTree(
        topLevel = nodes.filter { !it.isSubReply }.map { it.post },
        children = childrenByParent,
        subReplyTo = subReplyTo,
    )
}

private fun resolveParent(
    node: PostNode,
    nodes: List<PostNode>,
    byPid: Map<String, PostNode>,
    byAuthor: Map<String, MutableList<PostNode>>,
): PostNode? {
    val quote = node.post.quote ?: return null
    quote.pid?.takeIf { it.isNotBlank() }?.let { pid ->
        byPid[pid]?.takeIf { it !== node && it.orderIndex < node.orderIndex }?.let { return it }
    }
    matchByQuotedContent(node, nodes)?.let { return it }
    // 昵称是最弱的证据：同一作者的每条楼层都同名，只在无同名歧义时采用
    val name = quote.author?.takeIf { it.isNotBlank() } ?: return null
    val candidates = byAuthor[name].orEmpty().filter { it !== node && it.orderIndex < node.orderIndex }
    return candidates.singleOrNull()
}

/**
 * pid 缺失或指向未加载楼层时的降级匹配：拿引用块正文去比对各楼层正文。
 *
 * 引用块开头还挂着「回复 X 发表于 …」这段 meta，前缀比较必然对不齐，故用最长公共子串；
 * 只重合寥寥几字更像巧合同词，命中度不足时放弃，交给更弱的昵称证据。
 */
private fun matchByQuotedContent(node: PostNode, nodes: List<PostNode>): PostNode? {
    val quoted = normalizeForMatch(node.post.quote?.text ?: return null)
    if (quoted.length < MATCH_MIN_CHARS) return null
    var best: PostNode? = null
    var bestScore = 0
    for (candidate in nodes) {
        if (candidate === node || candidate.orderIndex >= node.orderIndex) continue
        val body = normalizeForMatch(candidate.post.contentHtml)
        if (body.isEmpty()) continue
        val score = longestCommonSubstring(quoted, body)
        if (score > bestScore) {
            bestScore = score
            best = candidate
        }
    }
    if (best == null) return null
    return if (bestScore >= MATCH_MIN_CHARS && bestScore >= quoted.length / 3) best else null
}

/** 两个字符串的最长公共子串长度（滚动数组）。 */
private fun longestCommonSubstring(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    var prev = IntArray(shorter.length + 1)
    var cur = IntArray(shorter.length + 1)
    var best = 0
    for (i in 1..longer.length) {
        for (j in 1..shorter.length) {
            cur[j] = if (longer[i - 1] == shorter[j - 1]) prev[j - 1] + 1 else 0
            if (cur[j] > best) best = cur[j]
        }
        val swap = prev
        prev = cur
        cur = swap
        cur.fill(0)
    }
    return best
}

/** 单次「加载更多」最多连拉的页数。 */
private const val LOAD_PAGE_BATCH = 3

/** 单次「加载更多」凑够多少条就停（论坛每页条数不定，用于避免只拉一页太少）。 */
private const val REPLIES_PER_PAGE = 20

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** 切换排序/只看楼主后待界面恢复的滚动锚点（首个可见项下标 + 像素偏移）。 */
    private val _pendingScrollAnchor = MutableStateFlow<ScrollAnchor?>(null)
    val pendingScrollAnchor: StateFlow<ScrollAnchor?> = _pendingScrollAnchor.asStateFlow()

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

    /** 列表底部状态：Idle 不显示，Loading 转圈，End 到底。 */
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

    /** 自动续拉链；暂停/切换筛选时取消，进入帖子与刷新后重新拉起。 */
    private var autoLoadJob: Job? = null

    /** 自动续拉链是否在跑（界面据此切换暂停/继续图标，并让开滚动触发的续拉）。 */
    private val _autoLoadRunning = MutableStateFlow(false)
    val autoLoadRunning: StateFlow<Boolean> = _autoLoadRunning.asStateFlow()

    /** 用户按了暂停；刷新、切排序都不自动恢复，只能手动点继续。 */
    private val _autoLoadPaused = MutableStateFlow(false)
    val autoLoadPaused: StateFlow<Boolean> = _autoLoadPaused.asStateFlow()

    /**
     * 自动续拉链标识：暂停后紧接着又开始的新链，不能被旧链收尾时置回「未在跑」。
     */
    private var autoLoadChainId = 0

    /** 切换排序/只看楼主时界面传来的滚动锚点，新楼层上屏时才交给界面。 */
    private var requestedAnchor: ScrollAnchor? = null

    /** 数据请求代次：每次 load 递增，用于丢弃过期请求的结果，避免旧数据覆盖新数据。 */
    private var dataGeneration = 0

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
        val previousOpOnly = _opOnly.value
        val previousOrderType = _orderType.value
        page = target
        // 已有内容时保持显示（切换排序/只看楼主不清屏、不闪 Loading），请求回来原地替换。
        // 楼层树不能在这里清空：楼层 items 一塌成只剩头部，LazyColumn 会被截到列表尾部
        // （视觉上跳顶），塌陷后的短列表还会误触发底部的自动续拉。
        // 新数据与楼层树一起上屏，见 publish()。
        val keepContent = _state.value is UiState.Success
        if (!keepContent) {
            _state.value = UiState.Loading
            _tree.value = null
            _waterPids.value = emptySet()
        }
        val generation = ++dataGeneration
        viewModelScope.launch {
            val data = try {
                repository.threadPage(tid, target, _orderType.value, authorId)
            } catch (e: Exception) {
                if (generation != dataGeneration) return@launch
                requestedAnchor = null
                if (keepContent) {
                    // 旧内容原样留着，只把分段控件退回原档并提示失败
                    _opOnly.value = previousOpOnly
                    _orderType.value = previousOrderType
                    _actionMessage.value = e.message ?: "加载失败"
                } else {
                    _state.value = UiState.Error(e.message ?: "加载失败")
                }
                return@launch
            }
            // 被更新的请求（又一次切排序/只看楼主）取代，这一帧结果作废
            if (generation != dataGeneration) return@launch
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
            publish(data, target)
        }
    }

    /**
     * 新楼层与楼中楼树、灌水集合一起上屏（折叠态也在这一刻复位）。
     *
     * 树在后台线程算完再发布，省掉「帖子已换、楼层还是旧树」的中间帧；
     * 有切换筛选时记录的锚点，就同时交给界面在新楼层上屏后原地定位，屏幕不跳。
     */
    private fun publish(data: ThreadPage, target: Int) {
        treeJob?.cancel()
        treeJob = viewModelScope.launch {
            val (tree, water) = withContext(Dispatchers.Default) { deriveTree(data) }
            _collapsed.value = emptySet()
            _foldExpanded.value = false
            _state.value = UiState.Success(data)
            _tree.value = tree
            _waterPids.value = water
            _hasNext.value = target < data.totalPages
            _loadMoreState.value =
                if (target < data.totalPages) LoadMoreState.Idle else LoadMoreState.End
            requestedAnchor?.let {
                _pendingScrollAnchor.value = it
                requestedAnchor = null
            }
            // 首页楼层上屏后再起自动链：首屏先出来，后续页在后台限速补齐
            syncAutoLoad()
        }
    }

    /**
     * 追加下一页评论：把新页的楼层并入当前列表（而非切页重置）。
     * 由界面滚动到底触发；自动续拉见 [startAutoLoad]。
     */
    fun loadMore() {
        viewModelScope.launch {
            val failed = fetchNextBatch()
            // 手动续拉成功后把自动链接上（用户没暂停时），把剩下的页拉完
            if (!failed && !_autoLoadPaused.value) syncAutoLoad()
        }
    }

    /**
     * 拉取一批楼层并入当前列表。
     *
     * @return true 表示这批一条都没拿到（网络失败）。此时 [loadMoreState] 停在 Idle、
     * 不做提示也不自动重试，用户滑动到底会再次触发 [loadMore]。
     */
    private suspend fun fetchNextBatch(): Boolean {
        if (_loadingMore.value || !_hasNext.value) return false
        val current = _state.value as? UiState.Success ?: return false
        val generation = dataGeneration
        val currentPage = page
        _loadingMore.value = true
        _loadMoreState.value = LoadMoreState.Loading
        val accumulated = mutableListOf<Post>()
        val known = current.data.posts.mapTo(mutableSetOf()) { it.pid }
        var next = page + 1
        var totalPages = current.data.totalPages
        var pagesFetched = 0
        var failed = false
        // 一次连拉多页凑满一屏：论坛每页条数不定，只拉一页会“一次没几条”，观感卡顿
        try {
            while (pagesFetched < AUTO_LOAD_BATCH_PAGES && next <= totalPages && accumulated.size < AUTO_LOAD_BATCH_REPLIES) {
                val data = repository.threadPage(tid, next, _orderType.value, authorIdForRequest())
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
        }
        // 期间又切换了排序/只看楼主：这批旧数据已无意义，丢弃并还原页码与加载态
        if (generation != dataGeneration) {
            page = currentPage
            _loadingMore.value = false
            return failed
        }
        val merged = current.data.copy(
            posts = current.data.posts + accumulated,
            totalPages = totalPages,
        )
        _state.value = UiState.Success(merged)
        _hasNext.value = page < totalPages
        _loadMoreState.value =
            if (page < totalPages) LoadMoreState.Idle else LoadMoreState.End
        refreshDerived(merged)
        _loadingMore.value = false
        return failed
    }

    /** 续拉时的楼主过滤参数：只看楼主必须带上，否则会把别人的楼层混进来。 */
    private fun authorIdForRequest(): String? =
        if (_opOnly.value) (_state.value as? UiState.Success)?.data?.opUid else null

    /**
     * 自动把评论分批拉完：批间 [AUTO_LOAD_DELAY_MS] 限速，避免连续请求触发站点风控。
     *
     * 失败时在失败处（[page] + 1）再试一次，仍失败就停下：底部停在 Idle，
     * 之后用户滑动到底会再次触发 [loadMore]，不做无休止的自动重试。
     * 切换排序/只看楼主会让 [dataGeneration] 变化，旧链据此自行退出。
     */
    private fun startAutoLoad() {
        autoLoadJob?.cancel()
        if (!_hasNext.value) return
        val dataGen = dataGeneration
        val chainId = ++autoLoadChainId
        _autoLoadPaused.value = false
        _autoLoadRunning.value = true
        autoLoadJob = viewModelScope.launch {
            try {
                while (isActive) {
                    delay(AUTO_LOAD_DELAY_MS)
                    if (dataGen != dataGeneration || !_hasNext.value) break
                    if (fetchNextBatch()) {
                        delay(AUTO_LOAD_DELAY_MS)
                        if (dataGen != dataGeneration || !_hasNext.value) break
                        if (fetchNextBatch()) break
                    }
                }
            } finally {
                if (chainId == autoLoadChainId) _autoLoadRunning.value = false
            }
        }
    }

    /** 有后续页、用户没暂停、且链没在跑时，把自动链接上。 */
    private fun syncAutoLoad() {
        if (_hasNext.value && !_autoLoadPaused.value && autoLoadJob?.isActive != true) {
            startAutoLoad()
        }
    }

    /** 暂停/继续自动续拉；只在还有后续页时由界面调。 */
    fun toggleAutoLoad() {
        if (_autoLoadRunning.value) {
            _autoLoadPaused.value = true
            // 先翻转图标再取消协程：取消后 finally 要等下一轮主线程消息才跑
            _autoLoadRunning.value = false
            autoLoadJob?.cancel()
        } else {
            startAutoLoad()
        }
    }

    /**
     * 回复成功后的局部刷新：重新拉第 1 页，用新首页楼层替换旧首页，
     * 其余已加载的后续页按 pid 去重后保留，避免整页回退与丢失已翻页数据。
     */
    fun refreshAfterReply() {
        val current = _state.value as? UiState.Success ?: run { load(1); return }
        val authorId = if (_opOnly.value) current.data.opUid else null
        val generation = dataGeneration
        viewModelScope.launch {
            val fresh = try {
                repository.threadPage(tid, 1, _orderType.value, authorId)
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = e.message ?: "刷新失败"
                return@launch
            }
            if (generation != dataGeneration) return@launch
            val latest = _state.value as? UiState.Success ?: return@launch
            val freshPids = fresh.posts.map { it.pid }.toSet()
            val kept = latest.data.posts.filter { it.pid !in freshPids }
            val totalPages = maxOf(fresh.totalPages, latest.data.totalPages)
            val merged = fresh.copy(posts = fresh.posts + kept, totalPages = totalPages)
            _state.value = UiState.Success(merged)
            refreshDerived(merged)
            _hasNext.value = page < totalPages
            _loadMoreState.value =
                if (page < totalPages) LoadMoreState.Idle else LoadMoreState.End
            syncAutoLoad()
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
     * [anchor] 是切换前的滚动位置，新楼层上屏后原样恢复，屏幕不跳。
     */
    fun setReplyFilter(opOnly: Boolean, orderType: Int, anchor: ScrollAnchor? = null) {
        val changed = _opOnly.value != opOnly || _orderType.value != orderType
        if (!changed) return
        requestedAnchor = anchor
        _opOnly.value = opOnly
        _orderType.value = orderType
        load(1)
    }

    /** 界面已按锚点定位完，清掉待恢复状态。 */
    fun consumePendingScrollAnchor() {
        _pendingScrollAnchor.value = null
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
                    val remaining = latest.data.copy(
                        posts = latest.data.posts.filterNot { it.pid == pid },
                    )
                    _state.value = UiState.Success(remaining)
                    refreshDerived(remaining)
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

    /** 列表底部状态：Idle 不显示，Loading 转圈，End 到底。 */
    sealed interface LoadMoreState {
        data object Idle : LoadMoreState
        data object Loading : LoadMoreState
        data object End : LoadMoreState
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
        val generation = dataGeneration
        viewModelScope.launch {
            try {
                val fresh = repository.threadPage(tid, 1, _orderType.value, authorId)
                // 期间又切换了排序/只看楼主：这次刷新的结果已过时，丢弃
                if (generation != dataGeneration) return@launch
                val latest = _state.value as? UiState.Success
                if (latest == null) {
                    _state.value = UiState.Success(fresh)
                    refreshDerived(fresh)
                } else {
                    val freshPids = fresh.posts.map { it.pid }.toSet()
                    val kept = latest.data.posts.filter { it.pid !in freshPids }
                    val totalPages = maxOf(fresh.totalPages, latest.data.totalPages)
                    val merged = fresh.copy(posts = fresh.posts + kept, totalPages = totalPages)
                    _state.value = UiState.Success(merged)
                    _hasNext.value = page < totalPages
                    refreshDerived(merged)
                    syncAutoLoad()
                }
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = e.message ?: "刷新失败"
            }
            if (generation == dataGeneration) _isRefreshing.value = false
        }
    }

    /** 后台重算楼中楼树与灌水集合；楼层数可上百，含 O(n²) 匹配与 Jsoup，不放主线程。 */
    private fun refreshDerived(data: ThreadPage) {
        treeJob?.cancel()
        treeJob = viewModelScope.launch(Dispatchers.Default) {
            val (tree, water) = deriveTree(data)
            _tree.value = tree
            _waterPids.value = water
        }
    }

    /** 归属树 + 默认折叠的灌水楼层 pid；带子回复的水帖不折，否则会连子回复一起藏掉。 */
    private fun deriveTree(data: ThreadPage): Pair<ReplyTree, Set<String>> {
        val op = data.posts.firstOrNull { it.isFirstPost } ?: data.posts.firstOrNull()
        val replies = data.posts.filter { it !== op }
        val built = buildReplyTree(replies)
        val water = built.topLevel
            .filter { isWaterReply(it.contentHtml) && built.children[it.pid].isNullOrEmpty() }
            .map { it.pid }
            .toSet()
        return built to water
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

/** 切换筛选前界面的滚动位置：首个可见项下标 + 该项被遮住的像素高度。 */
data class ScrollAnchor(val index: Int, val offset: Int)

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

/**
 * 每批楼层条数：论坛每页条数不定，一次多拉几页凑满一屏，避免只拉一页太少、观感卡顿。
 */
private const val AUTO_LOAD_BATCH_REPLIES = 10

/** 每批最多连拉的页数，与 [AUTO_LOAD_BATCH_REPLIES] 共同决定一批的量。 */
private const val AUTO_LOAD_BATCH_PAGES = 2

/**
 * 自动续拉的批间隔（首批同样等这么久，让首屏先渲染完）。
 * 翻页是纯 GET，但连续请求可能触发站点/WAF 的风控挑战，故统一限速。
 */
private const val AUTO_LOAD_DELAY_MS = 800L

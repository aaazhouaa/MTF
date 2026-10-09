package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.PageProgress
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.ThreadSnapshot
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.isWaterReply
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.Json

class ThreadDetailViewModel(
    private val tid: String,
    private val repository: ForumRepository,
    private val actionStore: ActionStore,
    private val contentCache: ContentCache,
    myUidFlow: Flow<String>,
    /**
     * 从消息提醒跳进来时要定位的楼层 pid。
     * 该楼层常在最末几页，故额外把所在页拉回来并让界面滚过去（见 [fetchAnchorPage]）。
     */
    private val anchorPid: String? = null,
) : ViewModel() {

    /** 当前登录用户 uid（用于判定某条回复是否本人发表）。 */
    val myUid: StateFlow<String> =
        myUidFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _state = MutableStateFlow<UiState<ThreadPage>>(UiState.Loading)
    val state: StateFlow<UiState<ThreadPage>> = _state.asStateFlow()

    /** 待定位的楼层 pid；界面滚到位后调 [consumeAnchor] 清掉。 */
    private val _anchor = MutableStateFlow(anchorPid)
    val anchor: StateFlow<String?> = _anchor.asStateFlow()

    /** 定位楼层所在页是否已拉回过（只做一次）。 */
    private var anchorPageFetched = false

    /** 本次进帖为定位展开过折叠：退出重进（VM 重建）即恢复用户原来的折叠偏好。 */
    private var anchorRevealed = false

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

    /**
     * 灌水回复是否展开显示（默认折叠）。
     *
     * 不随数据重装重置：用户手动展开/收起后，本次浏览内一直保持（切排序/刷新/续拉都
     * 不收回），退出帖子 VM 重建才恢复默认。
     */
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

    /** 关注请求在途：连点不能变成「关注后又立刻取消」。 */
    private var followPending = false

    /** 点赞状态与计数（客户端维护，初始取站点计数）。 */
    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()
    private val _likeCount = MutableStateFlow(0)
    val likeCount: StateFlow<Int> = _likeCount.asStateFlow()

    /**
     * 互动态（点赞/收藏/关注）是否已按首次真实响应初始化过。
     *
     * 只初始化一次：用户操作后的本地状态才是最新，后续分页响应里的旧状态不能把它
     * 覆盖回去（取消点赞后再翻页又变回已点赞）。
     */
    private var actionInitialized = false

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

    /**
     * 已加载楼层的唯一池：按 pid 去重，顺序固定为「正序」（最旧在前）。
     *
     * 楼主 / 正序 / 倒序只是同一批楼层的三种看法，池子共用一份：切视图不再重新请求
     * 已加载过的楼层，也不会因为某个视图只加载到一半就切走而漏评论（重复加载几条
     * 没关系，按 pid 覆盖写只会更新数据，不会多占一行）。
     * 楼层一律按正序口径拉，倒序页由客户端反排，池子顺序才不会两种口径混在一起。
     */
    private val pool = LinkedHashMap<String, Post>()

    /** 每种拉取口径（全部 / 只看楼主）各自拉到第几页、总页数多少。 */
    private val viewProgress = LinkedHashMap<String, PageProgress>()

    /**
     * 每个口径「为补齐差额而重拉过、却没补上任何楼层」的页码与当时的应收条数。
     * 同一页码、同一应收条数下重拉一次仍无果就收手：那说明差额来自站点计数与可见
     * 楼层本身不一致，而不是漏拉，继续重拉只会空转。池子一补齐就清掉；站点计数一变
     * （有新评论）记录自然失效，末页会被重新捞一次。
     */
    private val shortPageRetry = HashMap<String, Pair<Int, Int>>()

    /**
     * 已判定「拉到的都是真实末尾之后的空页」的口径。
     *
     * 越界探测页会被站点夹成末页：池子不再有新增，页码也不再前进。此时若继续按
     * maxPage + 1 发请求，每批都会去敲同一页，自动续拉链与滑动预加载交替空转成
     * 无限请求。故整批空转一次即判定该口径到底，收手不发请求；一次真实页拉回来
     * （进帖、切筛选、下拉刷新、回复后刷新）会清掉标记，重新按最新计数评估。
     */
    private val exhaustedViews = HashSet<String>()

    /** 不随分页与排序变化的帖子级字段。 */
    private var subject: String? = null
    private var forumName: String? = null
    private var opUid: String? = null
    private var fid: String? = null
    private var totalReplies: Int? = null

    /**
     * 最近一次真实响应里的会话态（formhash / replyUrl / 点赞收藏态 / 总页数）。
     * 缓存不落这些字段，故纯缓存上屏后该值为 null，需要补拉一次第 1 页拿回令牌。
     */
    private var session: ThreadPage? = null

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

    /** 当前显示的内容来自缓存且尚未被网络数据替换过；失败提示据此区分「只有缓存」。 */
    private var cacheOnly = false

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /** 下拉刷新中。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadCachedThenNetwork()
    }

    /**
     * 进入帖子与切换排序/只看楼主的公共入口：
     * 有缓存就整份上屏（三种视图共用一份，切换不重拉），再只把缺的页补齐；
     * 没有缓存才从头加载。断网时有缓存也能照常看，不再整页转圈后摆一张失败页。
     */
    private fun loadCachedThenNetwork() {
        viewModelScope.launch {
            val snapshot = readThreadCache()
            if (snapshot != null) {
                dataGeneration++
                cacheOnly = true
                restore(snapshot)
                // 关注态先按本地记录上屏：等补拉的第 1 页回来才翻胶囊，观感就是
                // 「关注过的人重进帖子还是显示关注」
                actionStore.following(snapshot.opUid.orEmpty())?.let {
                    _followState.value = FollowState.Done(it, fromUser = false)
                }
                // 定位楼层先拉回来合入楼层池再上屏：目标楼层通常在很后面的页，
                // 先上屏再拉，界面会先渲染到帖子正文、等目标页到达后再跳过去。
                // 续拉链也等锚点消费掉再开：定位期间后续页会把目标挤出处屏幕
                fetchAnchorPage()
                publish(resetCollapsed = true, startChain = _anchor.value == null)
                // formhash / replyUrl 是一次性令牌，不落盘。缓存命中后补拉一次第 1 页
                // 把会话态拿回来：楼层按 pid 覆盖写，列表内容不变，只是多一个请求。
                // 顺带发现新评论——总评论数一变，池子就不再算收齐，续拉会继续。
                if (session == null) load(1) else resumeAfterCache()
            } else {
                load(1)
            }
        }
    }

    /** 读本地详情快照（楼主 / 正序 / 倒序共用）。 */
    private suspend fun readThreadCache(): ThreadSnapshot? =
        contentCache.read(
            key = CacheKeys.thread(tid),
            ttlMillis = CacheKeys.THREAD_TTL,
        ) { cacheJson.decodeFromString<ThreadSnapshot>(it) }

    /** 快照灌进楼层池与帖子级字段；会话态仍为空，等一次真实响应补齐。 */
    private fun restore(snapshot: ThreadSnapshot) {
        pool.clear()
        snapshot.posts.forEach { pool[it.pid] = it }
        viewProgress.clear()
        snapshot.progress.forEach { viewProgress[progressKey(it.authorId)] = it }
        subject = snapshot.subject
        forumName = snapshot.forumName
        opUid = snapshot.opUid
        fid = snapshot.fid
        totalReplies = snapshot.totalReplies
    }

    /**
     * 缓存上屏后把「缓存之后的新页」补齐：已缓存的楼层不再请求，只取新增部分。
     * 缓存本身不必是全量的（自动链拉到哪存到哪），故从缓存页码接着拉即可。
     * 失败就停在已加载处并提示，用户滑到底会再次触发 [loadMore]，不做无休止的重试。
     */
    private fun resumeAfterCache() {
        autoLoadJob?.cancel()
        if (!_hasNext.value) return
        val dataGen = dataGeneration
        val chainId = ++autoLoadChainId
        _autoLoadPaused.value = false
        _autoLoadRunning.value = true
        autoLoadJob = viewModelScope.launch {
            var failed = false
            try {
                while (isActive && _hasNext.value) {
                    failed = fetchNextBatch()
                    if (failed) break
                    delay(AUTO_LOAD_DELAY_MS)
                }
            } finally {
                if (chainId == autoLoadChainId) {
                    _autoLoadRunning.value = false
                    if (failed && dataGen == dataGeneration) {
                        _actionMessage.value = "网络不稳定，评论只显示已缓存的部分"
                    }
                }
            }
        }
    }

    /**
     * 拉取当前口径的第 [target] 页并入楼层池；target 为 1 表示从头拉。
     * 排序只影响客户端怎么排，请求一律按正序口径发，故这里不带 orderType。
     */
    fun load(target: Int = currentMaxPage) {
        // 只看楼主必须带上楼主 uid，否则会把别人的楼层混进来；uid 取自缓存或上一次响应
        val authorId = opOnlyAuthorId()
        val previousOpOnly = _opOnly.value
        val previousOrderType = _orderType.value
        // 已有内容时保持显示（切换排序/只看楼主不清屏、不闪 Loading），请求回来原地替换。
        // 楼层树不能在这里清空：楼层 items 一塌成只剩头部，LazyColumn 会被截到列表尾部
        // （视觉上跳顶），塌陷后的短列表还会误触发底部的自动续拉。
        // 新数据与楼层树一起上屏，见 publish()。
        val keepContent = pool.isNotEmpty()
        if (!keepContent) {
            _state.value = UiState.Loading
            _tree.value = null
            _waterPids.value = emptySet()
        }
        val generation = ++dataGeneration
        viewModelScope.launch {
            val data = try {
                repository.threadPage(tid, target, FETCH_ORDER, authorId)
            } catch (e: Exception) {
                if (generation != dataGeneration) return@launch
                requestedAnchor = null
                if (keepContent) {
                    // 旧内容原样留着，只把分段控件退回原档并提示失败
                    _opOnly.value = previousOpOnly
                    _orderType.value = previousOrderType
                    _actionMessage.value = if (cacheOnly) {
                        "加载失败，显示的是缓存内容"
                    } else {
                        friendlyError(e)
                    }
                } else {
                    _state.value = UiState.Error(friendlyError(e))
                }
                return@launch
            }
            // 被更新的请求（又一次切排序/只看楼主）取代，这一帧结果作废
            if (generation != dataGeneration) return@launch
            if (!actionInitialized) {
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
                // 关注态同理：页面给出就以页面为准，否则读本地记录，避免关注过的人
                // 退出重进又显示成可关注
                val following = if (data.followingStateKnown) {
                    actionStore.setFollowing(data.opUid.orEmpty(), data.isFollowingOp)
                    data.isFollowingOp
                } else {
                    actionStore.following(data.opUid.orEmpty()) ?: false
                }
                _followState.value = FollowState.Done(following, fromUser = false)
                actionInitialized = true
            }
            mergeIntoPool(data)
            recordProgress(authorId, target, data.totalPages)
            adoptMeta(data)
            cacheOnly = false
            // 目标楼层通常在很后面的页，先拉回来合入楼层池再上屏；反过来先上屏再拉，
            // 界面会先渲染到帖子正文、等目标页到达后再跳过去。续拉链等锚点消费掉再开
            fetchAnchorPage()
            publish(startChain = _anchor.value == null)
        }
    }

    /**
     * 把提醒指向的楼层所在页拉回来并楼层池。
     *
     * 被回复的楼层通常在最末几页，从第 1 页顺着自动链翻过去要几十个请求；这里先按
     * redirect 中转地址拿到页码、直接取那一页，界面随即滚到该楼层。不推进页码记录：
     * 自动链仍从第 2 页继续，中间的页照旧在后台补齐，列表不会留下空洞。
     */
    private suspend fun fetchAnchorPage() {
        val pid = anchorPid ?: return
        if (anchorPageFetched || pool.containsKey(pid)) return
        anchorPageFetched = true
        val generation = dataGeneration
        val page = try {
            repository.noticeReplyPage(tid, pid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        if (page <= 1 || generation != dataGeneration) return
        val data = try {
            repository.threadPage(tid, page, FETCH_ORDER, opOnlyAuthorId())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (generation != dataGeneration) return
        mergeIntoPool(data)
        // 定位场景不开自动续拉：后续页按楼层插在目标前面，会把目标挤出处屏幕。
        // 底部仍是「还有更多」的状态，用户往下滑或点「继续」照样能接着看。
        autoLoadJob?.cancel()
        _autoLoadRunning.value = false
        publish(startChain = false)
    }

    /** 界面已滚到定位楼层，清掉待定位状态。 */
    fun consumeAnchor() {
        _anchor.value = null
    }

    /**
     * 定位前把藏住目标楼层的折叠展开。
     *
     * 只看本次：折叠态随 ViewModel 生灭，退出重进仍是用户原来的偏好。
     * 楼中楼父楼层被收起时，目标子回复整段不渲染，滚到父楼层也看不见它。
     */
    fun revealAnchorReply(pid: String) {
        val parentPid = _tree.value?.children?.entries
            ?.firstOrNull { (_, kids) -> kids.any { it.pid == pid } }?.key
        val collapsedHidden = listOfNotNull(pid, parentPid).filter { _collapsed.value.contains(it) }
        val folded = listOfNotNull(pid, parentPid).any { _waterPids.value.contains(it) }
        if (collapsedHidden.isEmpty() && !folded) return
        if (collapsedHidden.isNotEmpty()) {
            _collapsed.value = _collapsed.value - collapsedHidden.toSet()
        }
        if (folded) _foldExpanded.value = true
        // 展开过就一路保持到退出：否则紧接着的 publish 会把刚展开的楼层收回去
        anchorRevealed = true
    }

    /**
     * 把楼层池按当前视图（楼主过滤 + 排序）装配成一页上屏。
     *
     * 楼中楼树始终按正序口径算：被引楼层必须排在引用者之前，正序天然满足。
     * 倒序不重算树，只把顶层楼层与子楼层各自反向——父子关系与灌水折叠判定
     * 在两种排序下完全一致，已折叠条数也不会因为切排序而变。
     * 有切换筛选时记录的锚点，就同时交给界面在新楼层上屏后原地定位，屏幕不跳。
     */
    private suspend fun publish(resetCollapsed: Boolean = true, startChain: Boolean = true) {
        treeJob?.cancel()
        val view = viewPage()
        val (tree, water) = withContext(Dispatchers.Default) { treeFor(view) }
        // 灌水折叠态不在这里重置：用户手动展开/收起后本次浏览内一直保持，退出帖子
        // VM 重建才恢复默认；楼中楼收起态仍随重装重置，定位未完成时连它也不重置
        // （刚为定位展开的楼层会被紧接着的 publish 收回去）。
        if (resetCollapsed && !anchorRevealed) {
            _collapsed.value = emptySet()
        }
        _state.value = UiState.Success(view)
        _tree.value = tree
        _waterPids.value = water
        _hasNext.value = hasNextForView()
        _loadMoreState.value =
            if (_hasNext.value) LoadMoreState.Idle else LoadMoreState.End
        requestedAnchor?.let {
            _pendingScrollAnchor.value = it
            requestedAnchor = null
        }
        saveToCache()
        // 首页楼层上屏后再起自动链：首屏先出来，后续页在后台限速补齐
        if (startChain) syncAutoLoad()
    }

    /** [view] 的楼中楼树与灌水集合；树按正序算，倒序只把展示顺序反过来。 */
    private fun treeFor(view: ThreadPage): Pair<ReplyTree, Set<String>> {
        val ascending = if (_orderType.value == 1) view.posts.asReversed() else view.posts
        val (built, water) = deriveTree(view.copy(posts = ascending))
        if (_orderType.value != 1) return built to water
        return ReplyTree(
            topLevel = built.topLevel.asReversed(),
            children = built.children.mapValues { it.value.asReversed() },
            subReplyTo = built.subReplyTo,
        ) to water
    }

    /** 手动续拉下一页：把新页并入楼层池（界面滚动到底时触发）。 */
    fun loadMore() {
        viewModelScope.launch {
            val failed = fetchNextBatch()
            // 手动续拉成功后把自动链接上（用户没暂停时），把剩下的页拉完
            if (!failed && !_autoLoadPaused.value) syncAutoLoad()
        }
    }

    /**
     * 拉取一批楼层并入楼层池（由界面滚动到底触发；自动续拉见 [startAutoLoad]）。
     *
     * @return true 表示这批一条都没拿到（网络失败）。此时 [loadMoreState] 停在 Idle、
     * 不做提示也不自动重试，用户滑动到底会再次触发 [loadMore]。
     */
    private suspend fun fetchNextBatch(): Boolean {
        if (_loadingMore.value || !_hasNext.value) return false
        val current = _state.value as? UiState.Success ?: return false
        val generation = dataGeneration
        _loadingMore.value = true
        _loadMoreState.value = LoadMoreState.Loading
        val authorId = opOnlyAuthorId()
        val startPage = nextPageToFetch(authorId)
        if (startPage == 0) {
            // 池子已收齐（或差额来自站点计数本身，重拉过一次仍没补上）：收手，不再空转
            _hasNext.value = false
            _loadMoreState.value = LoadMoreState.End
            return false
        }
        val knownTotalPages = maxOf(current.data.totalPages, progressOf(authorId).totalPages)
        var next = startPage
        // 差额未补齐时允许越过已知总页数探一页（见 nextPageToFetch），故上界至少放到 startPage
        var totalPages = maxOf(knownTotalPages, startPage)
        var pagesFetched = 0
        // 本批新入库的条数：一次连拉多页凑满一屏，论坛每页条数不定，只拉一页太少、观感卡顿
        var newInBatch = 0
        var failed = false
        var lastFetched = 0
        // 最近一个真实交付过楼层的页码：越界探测被站点夹成末页、什么也没带来时，
        // 用它把 recordProgress 收回去，免得幻觉页码（如池里 58 楼却去敲第 178 页）
        // 每次进帖都重放
        var lastProductivePage = progressOf(authorId).maxPage
        try {
            while (pagesFetched < AUTO_LOAD_BATCH_PAGES &&
                next <= totalPages &&
                newInBatch < AUTO_LOAD_BATCH_REPLIES
            ) {
                val data = repository.threadPage(tid, next, FETCH_ORDER, authorId)
                // 空响应的总页数不可信（越界夹页/解析失败常给出离谱页数），拿它推进
                // progress 会让 nextPageToFetch 一直去追不存在的页码
                val saneTotalPages =
                    if (data.posts.isEmpty() && data.totalPages > totalPages) totalPages
                    else data.totalPages
                totalPages = saneTotalPages
                val added = mergeIntoPool(data)
                newInBatch += added
                lastFetched = next
                // 拉到过就记页码，哪怕整页都是已入库楼层：定位楼层会把目标页提前合入
                // 池子却不记 progress，续拉走到那一页时若守住页码不动，就会反复敲同一页
                recordProgress(authorId, next, saneTotalPages)
                if (added > 0) {
                    lastProductivePage = next
                } else if (next > knownTotalPages) {
                    // 越界探测页什么都没带来：站点把它夹成了末页，解析出的页数跟着请求
                    // 页码涨。把 maxPage/totalPages 收回到真实交付过楼层的最后一页，
                    // 续拉才不会每次进入都去敲 178、179 这种不存在的页码；真正的新
                    // 评论由末页重拉与站点计数变化发现。
                    val healed = lastProductivePage.coerceAtLeast(1)
                    viewProgress[progressKey(authorId)] =
                        PageProgress(authorId = authorId, maxPage = healed, totalPages = healed)
                }
                next++
                pagesFetched++
            }
        } catch (e: Exception) {
            // 越界探测失败只说明后面没有更多页，不算网络故障（否则会误弹「网络不稳定」）
            failed = startPage <= knownTotalPages
        }
        // 期间又切换了排序/只看楼主：这批旧数据已无意义，直接丢弃。
        // 不动页码：新的加载会自己记页码，旧批次收尾时把页码拨回旧筛选的值会漏页。
        if (generation != dataGeneration) {
            _loadingMore.value = false
            return failed
        }
        val key = progressKey(authorId)
        // 本批确实拉到过页、却一条新楼层都没入库：后面都是真实末尾之后的空页，
        // 再拉只会空转。failed 不判定——那只是没拉到，不是没有。
        if (!failed && newInBatch == 0) {
            exhaustedViews.add(key)
        }
        // 池子仍少于站点计数：记下这次补拉的页码，下次同一页码再来一次还没补上就收手
        val stillShort = expectedPoolSize()?.let { pool.size < it } ?: false
        if (stillShort) {
            val page = if (lastFetched > 0) lastFetched else startPage
            shortPageRetry[key] = page to (expectedPoolSize() ?: -1)
        } else {
            shortPageRetry.remove(key)
        }
        cacheOnly = false
        publish(startChain = false)
        _loadingMore.value = false
        return failed
    }

    /**
     * 点「下载」那一刻的楼层快照：楼层池当前的内容整体冻结，按正序给出，不受
     * 「只看楼主 / 倒序」筛选影响。只取这一帧、不补拉后续评论：自动续拉在界面侧
     * 继续跑不影响这份快照，想收进新评论就等加载完再点一次下载，按 tid 覆盖旧副本。
     * 详情首屏还没上屏时返回 null——那时连正文都没有，调用方应提示稍后再试。
     */
    fun snapshotForDownload(): ThreadPage? {
        if (_state.value !is UiState.Success) return null
        return viewPage().copy(posts = poolOrder())
    }

    /** [authorId] 口径已加载到的最大页（续拉位置）。 */
    private val currentMaxPage: Int
        get() = progressOf(opOnlyAuthorId()).maxPage.coerceAtLeast(1)

    /** 续拉时的楼主过滤参数：只看楼主必须带上，否则会把别人的楼层混进来。 */
    private fun opOnlyAuthorId(): String? = if (_opOnly.value) opUid else null

    private fun progressKey(authorId: String?): String = authorId.orEmpty()

    private fun progressOf(authorId: String?): PageProgress =
        viewProgress[progressKey(authorId)] ?: PageProgress(authorId = authorId)

    /** 记下某口径拉到第几页；页码只增不减，免得重复请求刚拉过的页。 */
    private fun recordProgress(authorId: String?, fetchedPage: Int, totalPages: Int) {
        val current = progressOf(authorId)
        viewProgress[progressKey(authorId)] = PageProgress(
            authorId = authorId,
            maxPage = maxOf(current.maxPage, fetchedPage),
            totalPages = maxOf(current.totalPages, totalPages),
        )
    }

    /**
     * 把一页楼层并入池子，返回新入库的条数。
     *
     * 按 pid 覆盖写：同一条评论重复加载只会更新数据、不会重复占一行。这是
     * 「切视图不漏不重」的根——池子是全集，任何视图都只是它的一个投影。
     */
    private fun mergeIntoPool(page: ThreadPage): Int {
        var added = 0
        for (post in page.posts) {
            if (!pool.containsKey(post.pid)) added++
            pool[post.pid] = post
        }
        return added
    }

    /** [data] 里的帖子级字段只在缺失时补，会话态整体换新（每次请求都是最新的）。 */
    private fun adoptMeta(data: ThreadPage) {
        if (subject == null) subject = data.subject
        if (forumName == null) forumName = data.forumName
        if (opUid == null) opUid = data.opUid
        if (fid == null) fid = data.fid
        data.totalReplies?.let { totalReplies = it }
        session = data.copy(posts = emptyList())
        // 这次页数与计数都是新鲜的：之前判定的「已到底」作废，续拉重新评估，
        // 下拉刷新与回复后刷新才能重新发现新增评论
        exhaustedViews.clear()
    }

    /**
     * 池子应收满的条数：站点的全部评论数（不含首帖）加回首帖。
     * 站点没给计数时为 null，此时只能按页码判断。
     */
    private fun expectedPoolSize(): Int? = totalReplies?.let { it + 1 }

    /**
     * 当前口径下一个该拉的页码；0 表示不用再拉。
     *
     * 「页拉完了」不等于「评论拉完了」：新评论追加在最后一个 page 之内，总页数不变，
     * 只按 maxPage + 1 判断永远等不到「还有下一页」，新评论就再也不会出现。故以站点
     * 自己的评论数为准——池子仍少于该计数时，重拉该口径的最后一页把页内的新增与
     * 漏掉的楼层一起捞回来。
     */
    private fun nextPageToFetch(authorId: String?): Int {
        val key = progressKey(authorId)
        // 上一次已整批空转（请求到的都是真实末尾之后的空页）：收手，不再发请求
        if (key in exhaustedViews) return 0
        val expected = expectedPoolSize()
        // 池子已按站点计数收齐：任何口径（楼主/正序/倒序）都不必再发请求
        if (expected != null && pool.size >= expected) return 0
        val progress = viewProgress[key]
        // 这个口径还没拉过（例如第一次切只看楼主），从第 1 页开始才知道有多少
        if (progress == null || progress.maxPage == 0) return 1
        if (progress.maxPage < progress.totalPages) return progress.maxPage + 1
        // 站点没给评论数，无从判断页内是否还有漏掉的楼层，回到「页拉完就不拉」
        if (expected == null) return 0
        // 页都拉过了但池子还没收齐：先把最后一页重拉一次——新评论追加在末页之内，
        // 总页数不变；重拉没补上再往后探一页，站点的分页元素可能漏解析，真实页数比
        // 记录的多。越界请求要么返回空、要么夹到末页，按 pid 去重不会重复入库。
        val lastPage = maxOf(progress.totalPages, progress.maxPage).coerceAtLeast(1)
        val (retriedPage, retriedExpected) = shortPageRetry[key] ?: (0 to 0)
        if (retriedExpected != expected) return lastPage
        if (retriedPage == lastPage + 1) return 0
        return lastPage + 1
    }

    /** 当前口径还要不要接着拉。 */
    private fun hasNextForView(): Boolean = nextPageToFetch(opOnlyAuthorId()) > 0

    /**
     * 池子的正序口径。
     *
     * 楼层号是站点给的绝对序号，按它排才与正序视图一致；楼层号缺失的（老缓存、
     * 或站点改版漏给标签）按插入序在前一个楼层之后顺延编号，而不是垫到末尾——
     * 垫末尾会把父楼层排到子回复后面，楼中楼的父子匹配（要求被引楼层在前）整体失效。
     */
    private fun poolOrder(): List<Post> {
        val ordered = ArrayList<Post>(pool.size)
        var last = 0
        for (post in pool.values) {
            val floor = post.floor
            if (floor != null) {
                ordered.add(post)
                last = floor
                continue
            }
            last += 1
            ordered.add(post.copy(floor = last, floorLabel = post.floorLabel ?: "$last#"))
        }
        return ordered.sortedBy { it.floor }
    }

    /**
     * 当前视图的楼层：只看楼主时按楼主 uid 过滤，倒序时整表反向。
     * 三者都是同一份池子的投影，不需要各自请求。
     */
    private fun viewPosts(): List<Post> {
        val all = poolOrder()
        val filtered = if (_opOnly.value) {
            val op = opUid
            all.filter { it.isFirstPost || it.isOp || (op != null && it.authorUid == op) }
        } else {
            all
        }
        return if (_orderType.value == 1) filtered.asReversed() else filtered
    }

    /** 按当前视图装配出的「一页」：内容来自池子，页码与会话态来自各自的记录。 */
    private fun viewPage(): ThreadPage {
        val latest = session
        return ThreadPage(
            tid = tid,
            subject = subject,
            forumName = forumName,
            posts = viewPosts(),
            page = currentMaxPage,
            totalPages = latest?.totalPages ?: progressOf(opOnlyAuthorId()).totalPages,
            formhash = latest?.formhash,
            replyUrl = latest?.replyUrl,
            fid = fid ?: latest?.fid,
            opUid = opUid ?: latest?.opUid,
            totalReplies = totalReplies ?: latest?.totalReplies,
            likeCount = latest?.likeCount,
            favoriteCount = latest?.favoriteCount,
            isLiked = latest?.isLiked ?: false,
            likedStateKnown = latest?.likedStateKnown ?: false,
            isFavorited = latest?.isFavorited ?: false,
            favoritedStateKnown = latest?.favoritedStateKnown ?: false,
        )
    }

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
     * 回复成功后的局部刷新：重新拉第 1 页并入楼层池。
     *
     * 楼层按 pid 去重，已加载的后续页一条不丢；新回复排在末页，总评论数一变
     * 就不再算收齐，自动链接着把最后一页（含刚发的那条）拉回来。
     */
    fun refreshAfterReply() {
        if (_state.value !is UiState.Success && pool.isEmpty()) {
            load(1)
            return
        }
        val authorId = opOnlyAuthorId()
        val generation = dataGeneration
        viewModelScope.launch {
            val fresh = try {
                repository.threadPage(tid, 1, FETCH_ORDER, authorId)
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = friendlyError(e, "刷新失败")
                return@launch
            }
            if (generation != dataGeneration) return@launch
            cacheOnly = false
            mergeIntoPool(fresh)
            recordProgress(authorId, 1, fresh.totalPages)
            adoptMeta(fresh)
            // 不清用户的展开/折叠态：回复成功后正在看的楼层不该被重置
            publish(resetCollapsed = false, startChain = false)
            syncAutoLoad()
        }
    }

    /**
     * 三档分段（楼主/正序/倒序）一次切换：先改状态、再统一走一遍缓存优先的加载。
     * 避免 onOpOnly(false)+onOrder(x) 两个回调各触发一次 load 造成竞态与重复请求。
     * [anchor] 是切换前的滚动位置，新楼层上屏后原样恢复，屏幕不跳。
     */
    fun setReplyFilter(opOnly: Boolean, orderType: Int, anchor: ScrollAnchor? = null) {
        val changed = _opOnly.value != opOnly || _orderType.value != orderType
        if (!changed) return
        requestedAnchor = anchor
        _opOnly.value = opOnly
        _orderType.value = orderType
        loadCachedThenNetwork()
    }

    /**
     * 失败提示文案：OkHttp 抛出的原话是英文（connection closed 等），不能直接摆给用户；
     * 解析器等自有中文消息的场景保留原消息。
     */
    private fun friendlyError(e: Exception, fallback: String = "加载失败"): String = when (e) {
        is IOException -> "网络连接失败，请检查网络"
        else -> e.message?.takeIf { it.isNotBlank() } ?: fallback
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
                // 直接从楼层池移除该楼层并重算树/灌水；
                // 不能调 refreshAfterReply：它会重新拉第 1 页，把已删楼层按 pid 再加回来。
                pool.remove(pid)
                publish(resetCollapsed = false, startChain = false)
            }
            _actionMessage.value = if (ok) "已删除该回复" else "删除未生效，站点可能不允许删除"
        }
    }

    fun toggleFollow() {
        if (followPending) return
        val current = _state.value as? UiState.Success ?: return
        val uid = current.data.opUid
        val formhash = current.data.formhash
        if (uid.isNullOrBlank() || formhash.isNullOrBlank()) {
            // 令牌没拿到：已登录说明是缓存上屏、补拉的第 1 页还没回来
            _followState.value = FollowState.Error(
                if (repository.isLoggedIn()) "页面还在加载，稍等一下再关注" else "请先登录后再关注",
            )
            return
        }
        val following = (_followState.value as? FollowState.Done)?.following ?: false
        // 先翻胶囊再发请求：等响应回来才更新，观感就是「点了没反应」
        _followState.value = FollowState.Done(!following)
        followPending = true
        viewModelScope.launch {
            try {
                repository.followTopic(uid, formhash, follow = !following)
                // 关注态要跨帖子进入保持：本地落一份，页面没给出状态时靠它兜底
                actionStore.setFollowing(uid, !following)
            } catch (e: Exception) {
                // 失败回滚成未关注，并照旧弹提示
                _followState.value = FollowState.Error(e.message ?: "操作失败")
            } finally {
                followPending = false
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
        val optimistic = _likeCount.value
        viewModelScope.launch { actionStore.setLiked(tid, target) }
        viewModelScope.launch {
            val result = runCatching { repository.setLike(tid, formhash, target) }
                .getOrDefault(LikeResult(false))
            if (result.success) {
                // 站点回传的总赞数可能停在本次操作之前的旧值（首赞实测回 0，把 1 覆盖成 0），
                // 与操作方向矛盾时不予采信，保留本地计数；方向一致才用它校正漂移
                val corrected = result.serverCount
                if (corrected != null &&
                    ((target && corrected >= optimistic) || (!target && corrected <= optimistic))
                ) {
                    _likeCount.value = corrected
                }
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
        val target = _replyTarget.value
        val quotePid = target?.pid
        val noticeAuthor = if (target == null) current.data.noticeAuthor else target.authorName
        val cleanSnippet = target?.contentHtml
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace("&nbsp;", " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(80)
            .orEmpty()
        _replyState.value = ReplyState.Sending
        viewModelScope.launch {
            try {
                val result = repository.reply(
                    fid = fid,
                    tid = tid,
                    formhash = formhash,
                    message = message,
                    quotePid = quotePid,
                    noticeAuthor = noticeAuthor,
                    noticeTrimStr = if (target != null) cleanSnippet else null,
                    noticeAuthorMsg = if (target != null) cleanSnippet else null,
                )
                if (result.success) {
                    _replyTarget.value = null
                    _replyState.value = ReplyState.Success
                } else {
                    _replyState.value = ReplyState.Error(result.message ?: "回复被拒绝（可能需登录或触发风控）")
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
     * 下拉刷新：重拉第 1 页并入楼层池，不置 Loading、不丢失滚动数据。
     * 楼层按 pid 覆盖写，列表不会整页回退；总评论数若变了，自动链接着补新页。
     */
    fun refresh() {
        if (_isRefreshing.value) return
        if (_state.value !is UiState.Success && pool.isEmpty()) {
            load(1)
            return
        }
        _isRefreshing.value = true
        val authorId = opOnlyAuthorId()
        val generation = dataGeneration
        viewModelScope.launch {
            try {
                val fresh = repository.threadPage(tid, 1, FETCH_ORDER, authorId)
                // 期间又切换了排序/只看楼主：这次刷新的结果已过时，丢弃
                if (generation != dataGeneration) return@launch
                cacheOnly = false
                mergeIntoPool(fresh)
                recordProgress(authorId, 1, fresh.totalPages)
                adoptMeta(fresh)
                publish(resetCollapsed = false, startChain = false)
                syncAutoLoad()
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = friendlyError(e, "刷新失败")
            }
            if (generation == dataGeneration) _isRefreshing.value = false
        }
    }

    /**
     * 把当前楼层池写进本地缓存（楼主 / 正序 / 倒序共用一份）。
     * 会话态字段不入库：一次性令牌与点赞态缓存住会在下次进入时用错。
     */
    private suspend fun saveToCache() {
        contentCache.write(
            key = CacheKeys.thread(tid),
            value = ThreadSnapshot(
                subject = subject,
                forumName = forumName,
                opUid = opUid,
                fid = fid,
                totalReplies = totalReplies,
                posts = poolOrder(),
                progress = viewProgress.values.toList(),
            ),
            encode = { cacheJson.encodeToString(ThreadSnapshot.serializer(), it) },
        )
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

        /**
         * 关注结果。[fromUser] 为 false 表示这是进帖时从页面/本地记录同步来的初始态，
         * 界面不应据此弹提示。
         */
        data class Done(val following: Boolean, val fromUser: Boolean = true) : FollowState
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

/**
 * 归属树 + 默认折叠的灌水楼层 pid；带子回复的水帖不折，否则会连子回复一起藏掉。
 *
 * 在线详情与已下载帖子的离线页共用同一套规则：两处看到的父子归属与折叠条数一致。
 */
internal fun deriveTree(data: ThreadPage): Pair<ReplyTree, Set<String>> {
    val op = data.posts.firstOrNull { it.isFirstPost } ?: data.posts.firstOrNull()
    val replies = data.posts.filter { it !== op }
    val built = buildReplyTree(replies)
    val water = built.topLevel
        .filter { isWaterReply(it.contentHtml) && built.children[it.pid].isNullOrEmpty() }
        .map { it.pid }
        .toSet()
    return built to water
}

/** 切换筛选前界面的滚动位置：首个可见项下标 + 该项被遮住的像素高度。 */
data class ScrollAnchor(val index: Int, val offset: Int)

/** 闪烁总时长（两轮淡入淡出）；界面据此撤掉高亮态，滚出屏幕再回来不会重复闪。 */
internal const val FLASH_HIGHLIGHT_MS = 1500L

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
 * 拉楼层的排序口径固定「正序」：楼主过滤只是站点参数，倒序由客户端反排。
 * 翻页序列只有一条，池子顺序才不会两种口径混在一起；每页条数与总页数
 * 也只用这一条序列衡量，视图再怎么切都不影响。
 */
private const val FETCH_ORDER = 0

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

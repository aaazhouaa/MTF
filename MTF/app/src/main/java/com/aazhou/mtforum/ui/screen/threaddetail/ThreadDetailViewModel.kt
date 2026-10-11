package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.PageProgress
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.parser.ForumParser
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.isWaterReply
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    /**
     * 已知可 @ 的用户名：站点 getatuser 名单（关注 + 好友）加上选择器里实际选中的名字。
     * 提交前用它把「@用户名」后缺失的 ASCII 空白补回来（见 [normalizeAtMentions]）。
     */
    private val atNames = mutableSetOf<String>()

    private val _replyState = MutableStateFlow<ReplyState>(ReplyState.Idle)
    val replyState: StateFlow<ReplyState> = _replyState.asStateFlow()

    /**
     * 编辑自己的楼层（帖子或评论）。
     *
     * 原文不在客户端手上（[Post.contentHtml] 是渲染后的 HTML，反推不出 BBCode），
     * 故编辑分两步：[beginEdit] 先把站点编辑表单取回，[submitEdit] 再提交。
     */
    private val _editTarget = MutableStateFlow<Post?>(null)
    val editTarget: StateFlow<Post?> = _editTarget.asStateFlow()

    private val _editForm = MutableStateFlow<EditFormState>(EditFormState.Idle)
    val editForm: StateFlow<EditFormState> = _editForm.asStateFlow()

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
     * 楼层号是站点给的绝对序号，装配时按它排，所以从哪条序列拉回来的都融得进同一份。
     */
    private val pool = LinkedHashMap<String, Post>()

    /**
     * 一条拉取序列：楼主过滤 × 排序方向。
     *
     * 站点两边的页码编号不是一回事：正序第 1 页是最旧的评论，倒序（ordertype=1）
     * 第 1 页是全帖最新的评论。所以「拉到第几页」「拉过哪些页」必须按序列分开记，
     * 只记一套会让切排序后把没拉过的页当成拉过。
     */
    private data class Sequence(val authorId: String?, val orderType: Int)

    /** 当前序列：只看楼主时带楼主 uid，排序方向取当前档位。 */
    private val currentSequence: Sequence
        get() = Sequence(opOnlyAuthorId(), _orderType.value)

    /** 每条序列各自拉到第几页、总页数多少。 */
    private val viewProgress = LinkedHashMap<Sequence, PageProgress>()

    /**
     * 每条序列已经拉过哪些页码。
     *
     * 续拉位置由它算：只记「最大页」的话，合入过任意一页（定位楼层所在页、编辑后
     * 重拉的那一页）就会把中间没拉过的页永久跳过，评论从此缺一截。
     */
    private val fetchedPages = HashMap<Sequence, MutableSet<Int>>()

    /**
     * 每条序列里每个楼层出自哪一页。
     *
     * 站点页面不给楼层的修改时间，别人改过的评论只有重拉那一页才会更新（楼层按
     * pid 覆盖写）。下拉刷新据此把「刚看过的那几楼」所在的页再拉一遍。
     */
    private val pageOf = HashMap<Sequence, MutableMap<String, Int>>()

    /**
     * 每条序列「为补齐差额而重拉过、却没补上任何楼层」的页码与当时的应收条数。
     * 同一页码、同一应收条数下重拉一次仍无果就收手：那说明差额来自站点计数与可见
     * 楼层本身不一致，而不是漏拉，继续重拉只会空转。池子一补齐就清掉；站点计数一变
     * （有新评论）记录自然失效，末页会被重新捞一次。
     */
    private val shortPageRetry = HashMap<Sequence, Pair<Int, Int>>()

    /**
     * 已判定「拉到的都是真实末尾之后的空页」的序列。
     *
     * 越界探测页会被站点夹成末页：池子不再有新增，页码也不再前进。此时若继续按
     * maxPage + 1 发请求，每批都会去敲同一页，自动续拉链与滑动预加载交替空转成
     * 无限请求。故整批空转一次即判定该序列到底，收手不发请求；一次真实页拉回来
     * （进帖、切筛选、下拉刷新、回复后刷新）会清掉标记，重新按最新计数评估。
     */
    private val exhaustedViews = HashSet<Sequence>()

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

    /** 需要默认折叠的灌水楼层 pid。 */
    private val _waterPids = MutableStateFlow<Set<String>>(emptySet())
    val waterPids: StateFlow<Set<String>> = _waterPids.asStateFlow()

    /** 点赞用户列表；为 null 表示未加载/已关闭。 */
    private val _likeUsers = MutableStateFlow<List<LikeUser>?>(null)
    val likeUsers: StateFlow<List<LikeUser>?> = _likeUsers.asStateFlow()
    private val _likeUsersLoading = MutableStateFlow(false)
    val likeUsersLoading: StateFlow<Boolean> = _likeUsersLoading.asStateFlow()

    /** 切换排序/只看楼主时界面传来的滚动锚点，新楼层上屏后才交给界面。 */
    private var requestedAnchor: ScrollAnchor? = null

    /** 界面最近可见的楼层 id（列表里那几个固定项的 key 也会进来，比对时自然滤掉）。 */
    private var recentVisiblePids: Set<String> = emptySet()

    /** 数据请求代次：每次 load 递增，用于丢弃过期请求的结果，避免旧数据覆盖新数据。 */
    private var dataGeneration = 0

    /** 下拉刷新中。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load(1)
    }

    /**
     * 拉取 [seq] 序列的第 [target] 页并入楼层池；target 为 1 表示该序列的头一页。
     * 请求按序列自己的排序口径发：正序第 1 页是最旧的评论，倒序（ordertype=1）
     * 第 1 页是全帖最新的评论，与站点网页端同一套翻页。
     *
     * [stalePages] 是主页面之后要顺手重拉的页：切换档位时把该档的头页、末页与切换前
     * 正在看的页再拉一遍换成最新版。串行、尽力而为，失败不影响主加载。
     */
    fun load(
        target: Int = currentMaxPage,
        orderType: Int = _orderType.value,
        stalePages: Set<Int> = emptySet(),
    ) {
        val seq = Sequence(opOnlyAuthorId(), orderType)
        val previousOpOnly = _opOnly.value
        val previousOrderType = _orderType.value
        // 已有内容时保持显示（切换排序/只看楼主不清屏、不闪 Loading），请求回来原地替换。
        val keepContent = pool.isNotEmpty()
        if (!keepContent) {
            _state.value = UiState.Loading
        }
        val generation = ++dataGeneration
        viewModelScope.launch {
            val data = try {
                repository.threadPage(tid, target, seq.orderType, seq.authorId)
            } catch (e: Exception) {
                if (generation != dataGeneration) return@launch
                requestedAnchor = null
                if (keepContent) {
                    // 旧内容原样留着，只把分段控件退回原档并提示失败
                    _opOnly.value = previousOpOnly
                    _orderType.value = previousOrderType
                    _actionMessage.value = friendlyError(e)
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
            mergeIntoPool(seq, target, data)
            recordProgress(seq, target, data.totalPages)
            adoptMeta(data)
            publish()
            // 主页面已经上屏，再把要更新的几页拉回来：楼层按 pid 覆盖写，界面就地换版
            val stale = stalePages - target
            if (stale.isNotEmpty() && generation == dataGeneration) {
                refetchStale(seq, stale, generation)
                publish()
            }
        }
    }

    /**
     * 把楼层池按当前视图（楼主过滤 + 排序）装配成一页上屏。
     *
     * 灌水折叠态不在这里重置：用户手动展开/收起后本次浏览内一直保持，退出帖子
     * VM 重建才恢复默认。
     * 有切换筛选时记录的锚点，就同时交给界面在新楼层上屏后原地定位，屏幕不跳。
     */
    private suspend fun publish() {
        val view = viewPage()
        // 灌水判定逐楼层过一遍 Jsoup，长帖上是主线程级别的开销，挪到后台算
        val water = withContext(Dispatchers.Default) {
            val op = view.posts.firstOrNull { it.isFirstPost } ?: view.posts.firstOrNull()
            view.posts.filter { it !== op && isWaterReply(it.contentHtml) }.map { it.pid }.toSet()
        }
        _state.value = UiState.Success(view)
        _waterPids.value = water
        _hasNext.value = hasNextForView()
        _loadMoreState.value =
            if (_hasNext.value) LoadMoreState.Idle else LoadMoreState.End
        requestedAnchor?.let {
            _pendingScrollAnchor.value = it
            requestedAnchor = null
        }
    }

    /** 手动续拉下一页：把新页并入楼层池（界面滚动到底时触发）。 */
    fun loadMore() {
        viewModelScope.launch {
            fetchNextPage()
        }
    }

    /**
     * 拉取下一页楼层并入楼层池（由界面滚动到底触发）。
     *
     * @return true 表示这一页没拿到（网络失败）。此时 [loadMoreState] 停在 Idle、
     * 不做提示也不自动重试，用户滑动到底会再次触发 [loadMore]。
     */
    private suspend fun fetchNextPage(): Boolean {
        if (_loadingMore.value || !_hasNext.value) return false
        val current = _state.value as? UiState.Success ?: return false
        val generation = dataGeneration
        _loadingMore.value = true
        _loadMoreState.value = LoadMoreState.Loading
        val seq = currentSequence
        val startPage = nextPageToFetch(seq)
        if (startPage == 0) {
            // 池子已收齐（或差额来自站点计数本身，重拉过一次仍没补上）：收手，不再空转
            _hasNext.value = false
            _loadMoreState.value = LoadMoreState.End
            return false
        }
        val knownTotalPages = maxOf(current.data.totalPages, progressOf(seq).totalPages)
        // 最近一个真实交付过楼层的页码：越界探测被站点夹成末页、什么也没带来时，
        // 用它把 recordProgress 收回去，免得幻觉页码（如池里 58 楼却去敲第 178 页）
        // 每次进帖都重放
        var lastProductivePage = progressOf(seq).maxPage
        var added = 0
        val failed = try {
            val data = repository.threadPage(tid, startPage, seq.orderType, seq.authorId)
            // 空响应的总页数不可信（越界夹页/解析失败常给出离谱页数），拿它推进
            // progress 会让 nextPageToFetch 一直去追不存在的页码
            val saneTotalPages =
                if (data.posts.isEmpty() && data.totalPages > knownTotalPages) knownTotalPages
                else data.totalPages
            added = mergeIntoPool(seq, startPage, data)
            // 拉到过就记页码，哪怕整页都是已入库楼层：定位楼层会把目标页提前合入
            // 池子却不记 progress，续拉走到那一页时若守住页码不动，就会反复敲同一页
            recordProgress(seq, startPage, saneTotalPages)
            if (added > 0) {
                lastProductivePage = startPage
            } else if (startPage > knownTotalPages) {
                // 越界探测页什么都没带来：站点把它夹成了末页，解析出的页数跟着请求
                // 页码涨。把 maxPage/totalPages 收回到真实交付过楼层的最后一页，
                // 续拉才不会每次进入都去敲 178、179 这种不存在的页码；真正的新
                // 评论由末页重拉与站点计数变化发现。
                val healed = lastProductivePage.coerceAtLeast(1)
                viewProgress[seq] =
                    PageProgress(authorId = seq.authorId, maxPage = healed, totalPages = healed)
            }
            false
        } catch (e: Exception) {
            // 越界探测失败只说明后面没有更多页，不算网络故障（否则会误弹「网络不稳定」）
            startPage <= knownTotalPages
        }
        // 期间又切换了排序/只看楼主：这次的结果已无意义，直接丢弃。
        // 不动页码：新的加载会自己记页码，旧请求收尾时把页码拨回旧筛选的值会漏页。
        if (generation != dataGeneration) {
            _loadingMore.value = false
            return failed
        }
        // 确实拉到过页、却一条新楼层都没入库：后面都是真实末尾之后的空页，
        // 再拉只会空转。failed 不判定——那只是没拉到，不是没有。
        if (!failed && added == 0) {
            exhaustedViews.add(seq)
        }
        // 池子仍少于站点计数：记下这次补拉的页码，下次同一页码再来一次还没补上就收手
        val expected = expectedPoolSize()
        if (expected != null && pool.size < expected) {
            shortPageRetry[seq] = startPage to expected
        } else {
            shortPageRetry.remove(seq)
        }
        publish()
        _loadingMore.value = false
        return failed
    }

    /**
     * 点「下载」那一刻的楼层快照：楼层池当前的内容整体冻结，按正序给出，不受
     * 「只看楼主 / 倒序」筛选影响。只取这一帧、不补拉后续评论：想收进新评论就等
     * 加载完再点一次下载，按 tid 覆盖旧副本。
     * 详情首屏还没上屏时返回 null——那时连正文都没有，调用方应提示稍后再试。
     */
    fun snapshotForDownload(): ThreadPage? {
        if (_state.value !is UiState.Success) return null
        return viewPage().copy(posts = poolOrder())
    }

    /** 当前序列已加载到的最大页（续拉位置）。 */
    private val currentMaxPage: Int
        get() = progressOf(currentSequence).maxPage.coerceAtLeast(1)

    /** 续拉时的楼主过滤参数：只看楼主必须带上，否则会把别人的楼层混进来。 */
    private fun opOnlyAuthorId(): String? = if (_opOnly.value) opUid else null

    private fun progressOf(seq: Sequence): PageProgress =
        viewProgress[seq] ?: PageProgress(authorId = seq.authorId)

    /** 记下某序列拉到第几页；页码只增不减，免得重复请求刚拉过的页。 */
    private fun recordProgress(seq: Sequence, fetchedPage: Int, totalPages: Int) {
        val current = progressOf(seq)
        noteFetchedPage(seq, fetchedPage)
        viewProgress[seq] = PageProgress(
            authorId = seq.authorId,
            maxPage = maxOf(current.maxPage, fetchedPage),
            totalPages = maxOf(current.totalPages, totalPages),
        )
    }

    /** 只记「这个页码拉过了」，不动页码记录：合入任意页（定位楼层所在页）时用。 */
    private fun noteFetchedPage(seq: Sequence, page: Int) {
        fetchedPages.getOrPut(seq) { mutableSetOf() } += page
    }

    /**
     * 把 [seq] 序列第 [pageNo] 页的楼层并入池子，返回新入库的条数。
     *
     * 按 pid 覆盖写：同一条评论重复加载只会更新数据、不会重复占一行。这是
     * 「切视图不漏不重」的根——池子是全集，任何视图都只是它的一个投影。
     * 同时记下每个楼层来自哪一页，供下拉刷新重拉「刚看过的楼层」用。
     */
    private fun mergeIntoPool(seq: Sequence, pageNo: Int, page: ThreadPage): Int {
        var added = 0
        val pageMap = pageOf.getOrPut(seq) { mutableMapOf() }
        for (post in page.posts) {
            val existing = pool[post.pid]
            // authorid 过滤页的楼层号是过滤后集合里的相对编号（「沙发」指第 2 条，不是全帖
            // 第 2 楼），当绝对楼层用会把共用池的排序打乱、正序视图里整条跳走。过滤页只用
            // 来更新内容：已入池的沿用原来的楼层号与标签，首次入池的楼层号留空、由
            // poolOrder 按入池顺序补（标签留着——网页端在过滤页显示的正是「沙发」这类）。
            val merged = when {
                seq.authorId == null -> post
                existing != null -> post.copy(floor = existing.floor, floorLabel = existing.floorLabel)
                else -> post.copy(floor = null)
            }
            if (existing == null) added++
            pool[post.pid] = merged
            pageMap[post.pid] = pageNo
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
     * 当前序列下一个该拉的页码；0 表示不用再拉。
     *
     * 每条序列都从自己的第 1 页往上顺翻：正序第 1 页是最旧的评论，倒序（ordertype=1）
     * 第 1 页是全帖最新的评论——和网页端点「倒序」看到的是同一屏。
     *
     * 「页拉完了」不等于「评论拉完了」：新评论追加在最后一个 page 之内，总页数不变，
     * 只按 maxPage + 1 判断永远等不到「还有下一页」，新评论就再也不会出现。故以站点
     * 自己的评论数为准——池子仍少于该计数时，重拉该序列的最后一页把页内的新增与
     * 漏掉的楼层一起捞回来。
     */
    private fun nextPageToFetch(seq: Sequence): Int {
        // 上一次已整批空转（请求到的都是真实末尾之后的空页）：收手，不再发请求
        if (seq in exhaustedViews) return 0
        val expected = expectedPoolSize()
        // 池子已按站点计数收齐：任何序列（楼主/正序/倒序）都不必再发请求
        if (expected != null && pool.size >= expected) return 0
        val progress = viewProgress[seq]
        // 这条序列还没拉过（第一次切只看楼主/倒序），从它的第 1 页开始
        if (progress == null || progress.maxPage == 0) return 1
        val lastPage = maxOf(progress.totalPages, progress.maxPage).coerceAtLeast(1)
        val loaded = fetchedPages[seq].orEmpty()
        val missing = (1..lastPage).firstOrNull { it !in loaded }
        if (missing != null) return missing
        // 站点没给评论数，无从判断页内是否还有漏掉的楼层，回到「页拉完就不拉」
        if (expected == null) return 0
        // 页都拉过了但池子还没收齐：先把最后一页重拉一次——新评论追加在末页之内，
        // 总页数不变；重拉没补上再往后探一页，站点的分页元素可能漏解析，真实页数比
        // 记录的多。越界请求要么返回空、要么夹到末页，按 pid 去重不会重复入库。
        val (retriedPage, retriedExpected) = shortPageRetry[seq] ?: (0 to 0)
        if (retriedExpected != expected) return lastPage
        if (retriedPage == lastPage + 1) return 0
        return lastPage + 1
    }

    /** 当前序列还要不要接着拉。 */
    private fun hasNextForView(): Boolean = nextPageToFetch(currentSequence) > 0

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
     * 池子里可能同时有两条序列拉回来的楼层（正序从旧往新、倒序从新往旧），按楼层号
     * 排序再反向才融得成一份；倒序第一屏来自站点倒序第 1 页，即全帖最新评论。
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
            totalPages = latest?.totalPages ?: progressOf(currentSequence).totalPages,
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
     * 编辑成功后把改后的那一楼上屏。
     *
     * 站点不单独回传改后的内容，只有重拉的页面里有，所以重拉该楼层所在那一页、按 pid
     * 覆盖写进楼层池。拿本地记录的页码（[pageOf]）先拉，那一页上没有它再问站点的
     * findpost 跳转；两条路都没找到才退回刷第 1 页。
     */
    private fun refreshEditedFloor(pid: String) {
        viewModelScope.launch {
            if (refetchFloorPage(pid) == true) {
                publish()
                return@launch
            }
            // 本地页码没派上用场：走下拉刷新那套——第 1 页加最近可见页。把刚编辑的楼层并进
            // 可见集，它所在页就一定会被重拉（用户刚才正看着它，多半早就在集里了）。
            recentVisiblePids = recentVisiblePids + pid
            refresh()
        }
    }

    /**
     * 回复成功后的局部刷新：重新拉第 1 页并入楼层池。
     *
     * 楼层按 pid 去重，已加载的后续页一条不丢；新回复排在末页，总评论数一变
     * 就不再算收齐，自动链接着把最后一页（含刚发的那条）拉回来。
     */
    fun refreshFirstPage() {
        if (_state.value !is UiState.Success && pool.isEmpty()) {
            load(1)
            return
        }
        val seq = currentSequence
        val generation = dataGeneration
        viewModelScope.launch {
            val fresh = try {
                repository.threadPage(tid, 1, seq.orderType, seq.authorId)
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = friendlyError(e, "刷新失败")
                return@launch
            }
            if (generation != dataGeneration) return@launch
            mergeIntoPool(seq, 1, fresh)
            recordProgress(seq, 1, fresh.totalPages)
            adoptMeta(fresh)
            publish()
        }
    }

    /**
     * 三档分段（楼主/正序/倒序）一次切换：先改状态、再统一走一遍加载。
     * 避免 onOpOnly(false)+onOrder(x) 两个回调各触发一次 load 造成竞态与重复请求。
     * [anchor] 是新楼层上屏后界面要恢复到的位置；切正序/倒序时传评论区顶部，倒序的
     * 第一屏就是全帖最新评论，切只看楼主才恢复切换前的位置。
     */
    fun setReplyFilter(opOnly: Boolean, orderType: Int, anchor: ScrollAnchor? = null) {
        val changed = _opOnly.value != opOnly || _orderType.value != orderType
        if (!changed) return
        requestedAnchor = anchor
        _opOnly.value = opOnly
        _orderType.value = orderType
        val seq = currentSequence
        // 拉「新序列的第 1 页」：站点倒序的第 1 页就是全帖最新评论，与网页端同一套
        // 翻页；这条序列已经拉过了就接着往下，全都拉过就只换视图重发、不再发请求
        val target = nextPageToFetch(seq)
        // 只换排法看到的是旧数据：切换时把该档的头页、末页与切换前正在看的页重拉一遍
        val stale = stalePagesOnSwitch(seq, skip = target)
        if (target > 0) {
            load(target, orderType = seq.orderType, stalePages = stale)
            return
        }
        // 这一档全都拉过了：没有主加载可搭车，自己走一遍重拉再上屏
        val generation = dataGeneration
        viewModelScope.launch {
            refetchStale(seq, stale, generation)
            if (generation == dataGeneration) publish()
        }
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
            val submitted = runCatching {
                repository.deletePost(tid, pid, data.formhash)
            }.getOrDefault(false)
            // 回执是字符串匹配，不能单独作准：删除成功会被重定向回帖子页，页面里自己其它
            // 楼层的「编辑」链接会被误判成「还在编辑表单」。真正该问的是「楼层还在不在」，
            // 所以重拉该楼层所在页来定论；一页都没拉到时才退回采信回执。
            val stillThere = refetchFloorPage(pid)
            val gone = if (stillThere == null) submitted else !stillThere
            if (gone) {
                // 直接从楼层池移除该楼层并重算树/灌水
                pool.remove(pid)
                publish()
                _actionMessage.value = "已删除该回复"
            } else {
                _actionMessage.value = "删除未生效，站点可能不允许删除"
            }
        }
    }

    /** 拉一页；网络不通、被 WAF 挡了、解析出一条都没有时返回 null。 */
    private suspend fun fetchPageOrNull(seq: Sequence, page: Int): ThreadPage? = try {
        repository.threadPage(tid, page, seq.orderType, seq.authorId).takeIf { it.posts.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * 重拉 [pid] 所在的那一页合入楼层池，返回这个楼层在不在新鲜页面上；一页都没拉到
     * 返回 null，此时「不在」不能当结论。
     *
     * 先按本地记录的页码拉：[pageOf] 在楼层入池时记下了它来自哪一页，连序列一起记，
     * 页码就是那个序列自己的口径。同一条楼层可能在几个序列下都记过（正序拉过一次、
     * 倒序又拉过一次），只挑一条来拉的话，万一那条的页码已被新增评论挤过期，这一轮就
     * 白跑，所以记到的页全拉一遍。都没有它再问站点的 findpost 跳转要它现在的页码
     * （站点给的是正序口径，故按正序拉）。
     *
     * 顺带把那几页合入楼层池：删掉一条之后站点会把后一页的楼层挪上来，池子里的内容和
     * 页面对不上，合入一次正好换成最新版。
     */
    private suspend fun refetchFloorPage(pid: String): Boolean? {
        var fetched = false
        for ((seq, page) in pageOf.entries.mapNotNull { (seq, map) -> map[pid]?.let { seq to it } }) {
            // 只看楼主序列记的页也跳过：那一路的楼层号不带绝对意义，合进来只更新内容
            // （见 mergeIntoPool），对「这轮有没有拿到新数据」没有帮助，省一次请求。
            if (seq.authorId != null) continue
            val data = fetchPageOrNull(seq, page) ?: continue
            mergeIntoPool(seq, page, data)
            fetched = true
            if (data.posts.any { it.pid == pid }) return true
        }
        return if (fetched) false else null
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

    /** 记录可 @ 的用户名（站点名单与选择器选中项），供提交前补 @ 分隔空白用。 */
    fun noteAtUsers(names: List<String>) {
        atNames += names.filter { it.isNotBlank() }
    }

    /**
     * 打开某楼的编辑：拉站点编辑表单，把表单里的 BBCode 原文交给界面填进输入面板。
     * 首帖与评论走同一个站点入口，差别只在表单里有没有标题输入框。
     */
    fun beginEdit(post: Post) {
        if (!repository.isLoggedIn()) {
            _actionMessage.value = "请先登录"
            return
        }
        _editTarget.value = post
        _editForm.value = EditFormState.Loading
        viewModelScope.launch {
            try {
                val data = repository.fetchEditPostInit(tid, post.pid)
                _editForm.value = when {
                    data.permissionDenied != null -> EditFormState.Error(data.permissionDenied)
                    else -> EditFormState.Ready(post.pid, data.message)
                }
            } catch (e: Exception) {
                _editForm.value = EditFormState.Error(friendlyError(e, "加载编辑内容失败"))
            }
        }
    }

    /** 表单态已被界面取用（面板已打开、原文已填入），复位以免重复触发。 */
    fun consumeEditForm() {
        _editForm.value = EditFormState.Idle
    }

    /** 放弃编辑：连目标一起清掉，界面据此收起输入面板。 */
    fun cancelEdit() {
        _editForm.value = EditFormState.Idle
        _editTarget.value = null
    }

    /** 提交编辑；成功后把改后的那一楼取回来上屏。 */
    fun submitEdit(message: String) {        val current = _state.value
        if (current !is UiState.Success) {
            _editForm.value = EditFormState.Error("页面未就绪")
            return
        }
        val formhash = current.data.formhash
        val target = _editTarget.value
        if (target == null || formhash.isNullOrBlank()) {
            _editForm.value = EditFormState.Error("缺少编辑令牌，请重新进入帖子")
            return
        }
        // 站点只认「@用户名 + ASCII 空白」，提交前按已知名单补回分隔空白（同回复）
        val text = normalizeAtMentions(message, atNames)
        _editForm.value = EditFormState.Loading
        viewModelScope.launch {
            try {
                val result = repository.editPost(
                    tid = tid,
                    pid = target.pid,
                    formhash = formhash,
                    message = text,
                    // 站点只在首帖表单里给标题，评论编辑不提交 subject
                    subject = if (target.isFirstPost) subject else null,
                )
                if (result.success) {
                    _editForm.value = EditFormState.Idle
                    _editTarget.value = null
                    _actionMessage.value = result.message ?: "已保存"
                    refreshEditedFloor(target.pid)
                } else {
                    _editForm.value = EditFormState.Error(result.message ?: "编辑未生效，请稍后重试")
                }
            } catch (e: Exception) {
                _editForm.value = EditFormState.Error(friendlyError(e, "编辑失败"))
            }
        }
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
        val notice = target?.let {
            ForumParser.replyQuoteNotice(it.authorName, it.timeText, it.contentHtml, it.pid, tid)
        }
        // 站点只认「@用户名 + ASCII 空白」，候选还按出现顺序受用户组 @ 名额截断，
        // 少一个空格整条消息的 @ 都会静默失效，故提交前按已知名单补回分隔空白
        val message = normalizeAtMentions(message, atNames)
        // 站点用的 noticeauthor 是 authcode 加密的作者 id，客户端构造不出来；只有不指定
        // 回复对象时才沿用详情页快速回复框里站点给的隐藏域。
        val noticeAuthor = if (target == null) current.data.noticeAuthor else null
        _replyState.value = ReplyState.Sending
        viewModelScope.launch {
            try {
                val result = repository.reply(
                    fid = fid,
                    tid = tid,
                    formhash = formhash,
                    message = message,
                    noticeAuthor = noticeAuthor,
                    noticeTrimStr = notice,
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

    /** 编辑表单态：[Loading] 期间界面显示提交中，[Ready] 带着站点取回的原文。 */
    sealed interface EditFormState {
        data object Idle : EditFormState
        data object Loading : EditFormState
        data class Ready(val pid: String, val text: String) : EditFormState
        data class Error(val message: String) : EditFormState
    }

    /** 列表底部状态：Idle 不显示，Loading 转圈，End 到底。 */
    sealed interface LoadMoreState {
        data object Idle : LoadMoreState
        data object Loading : LoadMoreState
        data object End : LoadMoreState
    }

    /**
     * 下拉刷新：重拉当前序列的第 1 页，外加「刚看过的那几楼」所在的页，不置
     * Loading、不丢失滚动数据。楼层按 pid 覆盖写，列表不会整页回退；总评论数若变了，
     * 自动链接着补新页。倒序视图下拉拉的是站点倒序第 1 页，新评论本来就排在那，一刷就到。
     *
     * 多拉的那几页是为了抓别人对已加载楼层的编辑：站点不给修改时间，只有重拉那一页
     * 才会更新。楼层就在屏上，用户感知是「老的先留着，拉到新的就地换版」。
     */
    fun refresh() {
        if (_isRefreshing.value) return
        if (_state.value !is UiState.Success && pool.isEmpty()) {
            load(1)
            return
        }
        _isRefreshing.value = true
        val seq = currentSequence
        val generation = dataGeneration
        // 第 1 页这次刷新本来就拉，剔掉避免重复请求
        val seenPages = recentVisiblePids
            .mapNotNull { pageOf[seq]?.get(it) }
            .filter { it != 1 }
            .toSortedSet()
        viewModelScope.launch {
            try {
                val fresh = repository.threadPage(tid, 1, seq.orderType, seq.authorId)
                // 期间又切换了排序/只看楼主：这次刷新的结果已过时，丢弃
                if (generation != dataGeneration) return@launch
                mergeIntoPool(seq, 1, fresh)
                recordProgress(seq, 1, fresh.totalPages)
                adoptMeta(fresh)
                for (page in seenPages) {
                    if (generation != dataGeneration) break
                    val extra = try {
                        repository.threadPage(tid, page, seq.orderType, seq.authorId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 附带的那几页是尽力而为：失败不影响第 1 页的刷新结果，也不提示
                        continue
                    }
                    mergeIntoPool(seq, page, extra)
                }
                publish()
            } catch (e: Exception) {
                if (generation == dataGeneration) _actionMessage.value = friendlyError(e, "刷新失败")
            }
            if (generation == dataGeneration) _isRefreshing.value = false
        }
    }

    /** 界面回报最近可见的楼层 id：下拉刷新据此决定额外重拉哪几页。 */
    fun noteVisiblePids(pids: Set<String>) {
        recentVisiblePids = pids
    }

    /**
     * 把 [pages] 里的页重拉一遍合入楼层池。
     *
     * 切换档位与下拉刷新都用它把旧数据换成最新版：楼层按 pid 覆盖写，列表就地换版、
     * 不闪不跳。尽力而为——某一页失败就跳过，不提示也不中断剩下的页，这些页不是
     * 用户主动点的，弹失败提示只会刷屏。也不推进页码记录：这些页本来就记过，且站点
     * 会把越界页夹成末页，据它推进页码会重演幻觉页码那个坑。
     */
    private suspend fun refetchStale(seq: Sequence, pages: Set<Int>, generation: Int) {
        for (page in pages) {
            if (page <= 0 || generation != dataGeneration) return
            val data = try {
                repository.threadPage(tid, page, seq.orderType, seq.authorId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            mergeIntoPool(seq, page, data)
        }
    }

    /**
     * 切换档位时要重拉的页：该序列的头页与末页，加上切换前正在看的那几页。
     *
     * 站点两边的新评论落在不同位置：倒序第 1 页就是全帖最新，正序/楼主则追加在最后
     * 一页之内，所以头页和末页都得拉。[skip] 是这次主加载正要拉的那一页，剔掉避免
     * 同一页连发两次请求。从没拉过的序列没有末页可记，算出来只剩头页，减掉 [skip]
     * 后为空集，即只有主加载那一个请求。
     */
    private fun stalePagesOnSwitch(seq: Sequence, skip: Int): Set<Int> {
        val progress = progressOf(seq)
        val lastPage = maxOf(progress.totalPages, progress.maxPage).coerceAtLeast(1)
        val seen = recentVisiblePids.mapNotNull { pageOf[seq]?.get(it) }
        return (setOf(1, lastPage) + seen).filter { it != skip }.toSortedSet()
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

/** 切换筛选前界面的滚动位置：首个可见项下标 + 该项被遮住的像素高度。 */
data class ScrollAnchor(val index: Int, val offset: Int)

/** 闪烁总时长（两轮淡入淡出）；界面据此撤掉高亮态，滚出屏幕再回来不会重复闪。 */

/**
 * 站点正序口径：不带 ordertype 参数。倒序走 ordertype=1，是站点自己的另一套
 * 翻页（第 1 页即全帖最新评论），页码与正序不是一套编号，故按序列分开记。
 *
 * 仍保留这个常量：定位楼层、编辑后重拉拿到的页码都是正序口径的编号，与当前视图
 * 是不是倒序无关，那两处按它拉。
 */
private const val FETCH_ORDER = 0


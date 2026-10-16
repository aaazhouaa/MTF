package com.aazhou.mtforum.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 单个 tab 的列表状态。 */
data class FeedState(
    val state: UiState<List<Thread>> = UiState.Loading,
    val page: Int = 1,
    val loadingMore: Boolean = false,
    val hasNext: Boolean = true,
    /** 第 1 页数据替换上屏时、替换前视口贴顶才递增；界面据此把视口拉回最新。 */
    val resetScrollTick: Int = 0,
)

class HomeViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    /** 顶部热帖排行（「热门」tab 的卡片头部用）。 */
    private val _hot = MutableStateFlow<HomeData?>(null)
    val hot: StateFlow<HomeData?> = _hot.asStateFlow()

    /** 各 tab 的列表状态，key 为导读 view；未加载过的 tab 不在表内。 */
    private val _feeds = MutableStateFlow<Map<String, FeedState>>(emptyMap())
    val feeds: StateFlow<Map<String, FeedState>> = _feeds.asStateFlow()

    /** 下拉刷新中的 view 集合：与列表状态独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /**
     * 各 tab 的视口是否贴顶（由界面持续回报）。
     *
     * 归位判据必须在数据替换**之前**取：替换后 LazyList 已按 item key 把旧首条锚回原处，
     * 那时读到的 index 是「头部新插了几条」而不是用户位置，插得多就再也判不出「人在顶部」。
     */
    private val viewportAtTop = mutableMapOf<String, Boolean>()

    /** 界面回报某 tab 的视口位置；只在贴顶状态变化时被调用。 */
    fun noteViewportAtTop(view: String, atTop: Boolean) {
        viewportAtTop[view] = atTop
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /**
     * 首次进入某个 tab：有缓存就先摆出来（不走全屏 Loading），网络回来再原地替换。
     *
     * 已加载过的 tab 直接返回：切 tab 不重新请求，滚动位置与已翻页的数据都留着。
     */
    fun load(view: String) {
        if (_feeds.value.containsKey(view)) return
        _feeds.value = _feeds.value + (view to FeedState())
        viewModelScope.launch {
            // 排行快照必须先于帖子流落位：后插入的 item 会被 LazyList 按 key 锚定顶到视口外
            if (view == HomeTabs.HOT) readHomeCache()?.let { _hot.value = it }
            // 过期也照摆：内容旧一点也好过对着转圈等网络，刷新失败时下面保留旧内容只提示
            val cached = contentCache.read(CacheKeys.guide(view), CacheKeys.LIST_TTL, allowExpired = true) {
                cacheJson.decodeFromString<CachedFeed>(it)
            }
            if (cached != null) {
                update(view) {
                    it.copy(state = UiState.Success(cached.threads), page = cached.page)
                }
            }
            // 自动拉取也走下拉刷新的指示器：有缓存时列表已上屏，圆圈转起来才看得到在拉数据
            _refreshing.value = _refreshing.value + view
            reload(view)
            _refreshing.value = _refreshing.value - view
        }
    }

    /**
     * 下拉刷新：不动列表状态，刷新成功后原地替换，失败时保留旧内容。
     *
     * 不复用 [load]：那会清掉已有数据把整页变成全屏转圈，下拉指示器也会随列表一起被撤掉。
     */
    fun refresh(view: String) {
        if (view in _refreshing.value) return
        _refreshing.value = _refreshing.value + view
        viewModelScope.launch {
            reload(view)
            _refreshing.value = _refreshing.value - view
        }
    }

    /**
     * 加载失败后的重试：不受「已加载过」限制，直接重拉第一页。
     */
    fun retry(view: String) {
        viewModelScope.launch { reload(view) }
    }

    private suspend fun reload(view: String) {
        if (view == HomeTabs.HOT) {
            // 热帖排行失败不影响主列表
            runCatching { repository.home() }.onSuccess {
                _hot.value = it
                saveHomeToCache(it)
            }
        }
        runCatching { repository.guideFeed(view, 1) }
            .onSuccess { data ->
                update(view) {
                    it.copy(
                        state = UiState.Success(data.threads),
                        page = 1,
                        hasNext = data.hasNext,
                        // 替换前贴顶才置位：中段用户不打扰，且由数据层在替换时取真值
                        resetScrollTick = it.resetScrollTick +
                            if (viewportAtTop[view] == true) 1 else 0,
                    )
                }
                saveFeedToCache(view)
            }
            .onFailure { e ->
                // 有缓存就留着旧内容，只提示这次没拿到新的
                if (_feeds.value[view]?.state is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                } else {
                    update(view) { it.copy(state = UiState.Error(e.message ?: "加载失败")) }
                }
            }
    }

    /** 滑到底部时追加下一页。 */
    fun loadMore(view: String) {
        val current = _feeds.value[view] ?: return
        if (current.loadingMore || !current.hasNext) return
        if (current.state !is UiState.Success) return
        update(view) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = current.page + 1
            runCatching { repository.guideFeed(view, next) }
                .onSuccess { data ->
                    // 合并时现读，避免把刷新刚换上的新列表又拼回旧数据后面
                    val existing = (_feeds.value[view]?.state as? UiState.Success)?.data.orEmpty()
                    // 门户流固定 10 页、超出后原样重复最后一页，只能靠「这页有没有新帖」判断到底
                    val known = existing.mapTo(HashSet()) { it.tid }
                    val fresh = data.threads.filter { known.add(it.tid) }
                    update(view) {
                        it.copy(
                            state = UiState.Success(existing + fresh),
                            page = next,
                            hasNext = data.hasNext && fresh.isNotEmpty(),
                        )
                    }
                    saveFeedToCache(view)
                }
                .onFailure { update(view) { it.copy(hasNext = false) } }
            update(view) { it.copy(loadingMore = false) }
        }
    }

    private fun update(view: String, transform: (FeedState) -> FeedState) {
        val current = _feeds.value[view] ?: FeedState()
        _feeds.value = _feeds.value + (view to transform(current))
    }

    /** 列表缓存 = 当前已加载的帖子流（含翻页追加的部分）与其页码。 */
    private suspend fun saveFeedToCache(view: String) {
        val feed = _feeds.value[view] ?: return
        val threads = (feed.state as? UiState.Success)?.data ?: return
        contentCache.write(
            key = CacheKeys.guide(view),
            ttlMillis = CacheKeys.LIST_TTL,
            value = CachedFeed(page = feed.page, threads = threads),
            encode = { cacheJson.encodeToString(CachedFeed.serializer(), it) },
        )
    }

    /**
     * 首页热帖排行的磁盘快照。
     *
     * 排行与帖子流不同源：只有它有这份快照，才能在 [load] 里和帖子流一起上屏；
     * 否则帖子流命中缓存秒出、排行等网络回来，插入时被 LazyList 的 key 锚定顶出首屏。
     */
    private suspend fun readHomeCache(): HomeData? =
        contentCache.read(CacheKeys.home(), CacheKeys.LIST_TTL, allowExpired = true) {
            cacheJson.decodeFromString<HomeData>(it)
        }

    private suspend fun saveHomeToCache(data: HomeData) {
        contentCache.write(
            key = CacheKeys.home(),
            ttlMillis = CacheKeys.LIST_TTL,
            value = data,
            encode = { cacheJson.encodeToString(HomeData.serializer(), it) },
        )
    }
}

/** 列表缓存内容：已加载的帖子流与其页码。 */
@Serializable
private data class CachedFeed(
    val page: Int = 1,
    val threads: List<Thread> = emptyList(),
)
